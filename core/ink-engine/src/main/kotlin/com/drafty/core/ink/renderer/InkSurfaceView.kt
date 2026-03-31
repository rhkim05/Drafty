package com.drafty.core.ink.renderer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.drafty.core.domain.model.CanvasMode
import com.drafty.core.domain.model.InkPoint
import com.drafty.core.domain.model.PaperTemplate
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.model.TemplateConfig
import com.drafty.core.domain.model.Tool
import com.drafty.core.ink.input.GestureDetector
import com.drafty.core.ink.input.InputProcessor
import com.drafty.core.ink.input.PalmRejectionFilter
import com.drafty.core.ink.spatial.HitTester
import com.drafty.core.ink.spatial.RTree
import com.drafty.core.ink.stroke.StrokeBuilder

/**
 * Custom [SurfaceView] for high-performance, low-latency ink rendering.
 *
 * Architecture:
 * - **Main thread**: receives MotionEvents, feeds InputProcessor/PalmRejectionFilter
 * - **Render thread**: draws completed-strokes bitmap + active stroke overlay
 * - **Direct rendering**: all strokes (completed + active) are rendered each
 *   frame with zoom-independent thickness (see ISS-017, ISS-018)
 *
 * See: docs/research.md §4 (SurfaceView Architecture)
 */
class InkSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    // --- Input pipeline ---
    private val inputProcessor = InputProcessor()
    private val palmFilter = PalmRejectionFilter()
    private val strokeBuilder = StrokeBuilder()

    // --- Rendering ---
    private val strokeRenderer = StrokeRenderer()
    private val templateRenderer = TemplateRenderer()
    private var renderThread: RenderThread? = null

    // --- Spatial index & hit testing ---
    val spatialIndex = RTree()
    val hitTester = HitTester(spatialIndex)

    // --- Viewport ---
    val viewportManager = ViewportManager()

    // --- Gesture detection (pinch zoom, two-finger pan) ---
    private val gestureDetector = GestureDetector(
        context = context,
        viewportManager = viewportManager,
        onViewportChanged = { renderThread?.requestRender() },
    )

    // --- State ---
    private val completedStrokes = mutableListOf<Stroke>()
    private val lock = Object()

    // --- Eraser state ---
    private var lastEraserX = Float.NaN
    private var lastEraserY = Float.NaN
    private val eraserPendingRemovals = mutableListOf<Stroke>()
    private val eraserRemovedIds = mutableSetOf<String>()

    // --- Lasso selection state ---
    private val lassoPoints = mutableListOf<Pair<Float, Float>>()
    private var selectedStrokeIds = mutableSetOf<String>()
    private var isLassoDrawing = false

    // --- Lasso drag/move state ---
    private var isLassoDragging = false
    private var lassoDragStartX = 0f
    private var lassoDragStartY = 0f
    private var lassoDragLastX = 0f
    private var lassoDragLastY = 0f

    // --- Tool state (set externally) ---
    var currentTool: Tool = Tool.PEN
    var currentColor: Long = 0xFF000000
    var currentThickness: Float = 4f
    var canvasBackgroundColor: Int = Color.parseColor("#E0E0E0")

    // --- Paper template (set externally when page loads) ---
    var templateConfig: TemplateConfig = TemplateConfig(PaperTemplate.BLANK)
        set(value) { field = value; renderThread?.requestRender() }

    /** Background paint for the page area (drawn under template). */
    private val pagePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    /** Callback when a stroke is completed. */
    var onStrokeCompleted: ((Stroke) -> Unit)? = null

    /** Callback when the active stroke should trigger undo state update. */
    var onStrokeStarted: (() -> Unit)? = null

    /** Callback when strokes are erased (for undo recording). */
    var onStrokesErased: ((List<Stroke>) -> Unit)? = null

    /** Callback when strokes are selected via lasso. */
    var onStrokesSelected: ((List<Stroke>) -> Unit)? = null

    /** Callback when selected strokes are moved via lasso drag (ids, totalDx, totalDy). */
    var onStrokesMoved: ((Set<String>, Float, Float) -> Unit)? = null

    /** Paint for lasso path (dashed line). */
    private val lassoPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.argb(200, 30, 100, 255)
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    /** Paint for selection highlight. */
    private val selectionPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.argb(120, 30, 100, 255)
        strokeWidth = 2f
    }

    /** Paint for selection fill overlay. */
    private val selectionFillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(30, 30, 100, 255)
    }

    init {
        holder.addCallback(this)
        // Make background transparent so paper template shows through
        setZOrderOnTop(false)
    }

    // ==================== Surface lifecycle ====================

    override fun surfaceCreated(holder: SurfaceHolder) {
        renderThread = RenderThread().apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        viewportManager.screenWidth = width.toFloat()
        viewportManager.screenHeight = height.toFloat()
        renderThread?.requestRender()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderThread?.stopRendering()
        renderThread = null
    }

    // ==================== Touch handling ====================

    /**
     * Gesture arbitration:
     * - Stylus → draw (or erase, depending on tool type)
     * - Two-finger touch → zoom/pan (forwarded to [GestureDetector])
     * - Single-finger touch → pan (in default stylus-only mode)
     *
     * See: docs/research.md — Phase 2 §2 (Gesture Arbitration)
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Two-finger gestures always go to gesture detector
        if (event.pointerCount >= 2 && !hasStylusPointer(event)) {
            cancelActiveStroke()
            return gestureDetector.onTouchEvent(event)
        }

        val filterResult = palmFilter.filter(event)

        when (filterResult) {
            PalmRejectionFilter.FilterResult.ACCEPT_DRAW -> {
                when (currentTool) {
                    Tool.ERASER -> handleEraseEvent(event)
                    Tool.LASSO -> handleLassoEvent(event)
                    else -> handleDrawEvent(event)
                }
            }
            PalmRejectionFilter.FilterResult.ACCEPT_ERASE -> handleEraseEvent(event)
            PalmRejectionFilter.FilterResult.CANCELED -> cancelActiveStroke()
            PalmRejectionFilter.FilterResult.ACCEPT_GESTURE -> {
                return gestureDetector.onTouchEvent(event)
            }
            PalmRejectionFilter.FilterResult.IGNORED -> {
                // Single finger in stylus-only mode → pan
                return gestureDetector.onTouchEvent(event)
            }
        }

        // Request a render frame
        renderThread?.requestRender()
        return true
    }

    /**
     * Checks if any pointer in the event is a stylus.
     */
    private fun hasStylusPointer(event: MotionEvent): Boolean {
        for (i in 0 until event.pointerCount) {
            val toolType = event.getToolType(i)
            if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                return true
            }
        }
        return false
    }

    private fun handleDrawEvent(event: MotionEvent) {
        val pointerIndex = findStylusPointerIndex(event)
        if (pointerIndex < 0) return

        synchronized(lock) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    val point = inputProcessor.extractCurrentPoint(event, pointerIndex)
                    val transformed = transformToCanvas(point)
                    strokeBuilder.begin(transformed, currentColor, currentThickness, currentTool)
                    onStrokeStarted?.invoke()
                }

                MotionEvent.ACTION_MOVE -> {
                    val points = inputProcessor.extractPoints(event, pointerIndex)
                    val transformed = points.map { transformToCanvas(it) }
                    strokeBuilder.addPoints(transformed)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    val point = inputProcessor.extractCurrentPoint(event, pointerIndex)
                    val transformed = transformToCanvas(point)
                    strokeBuilder.addPoints(listOf(transformed))
                    finalizeStroke()
                }
            }
        }
    }

    /**
     * Transforms an [InkPoint] from screen coordinates to canvas coordinates
     * using the current viewport transform.
     */
    private fun transformToCanvas(point: InkPoint): InkPoint {
        val (cx, cy) = viewportManager.screenToCanvas(point.x, point.y)
        return point.copy(x = cx, y = cy)
    }

    /**
     * Handles eraser touch events using spatial-index-backed hit testing.
     *
     * Batches all stroke removals during a drag gesture and only rebuilds
     * the completed-strokes bitmap once per frame (not per hit). This
     * eliminates lag when erasing across many strokes.
     *
     * Uses a generous hit tolerance ([ERASER_TOLERANCE] in canvas coords,
     * scaled by zoom) so the eraser feels responsive.
     *
     * See: docs/research.md — Phase 2 §4 (HitTester — Stroke Eraser)
     */
    private fun handleEraseEvent(event: MotionEvent) {
        val pointerIndex = findStylusPointerIndex(event)
        if (pointerIndex < 0) return

        val eraserRadius = ERASER_TOLERANCE / viewportManager.zoom

        synchronized(lock) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    eraserPendingRemovals.clear()
                    eraserRemovedIds.clear()
                    val point = inputProcessor.extractCurrentPoint(event, pointerIndex)
                    val (cx, cy) = viewportManager.screenToCanvas(point.x, point.y)
                    lastEraserX = cx
                    lastEraserY = cy
                    collectEraserHits(cx, cy, eraserRadius)
                    flushEraserRemovals()
                }

                MotionEvent.ACTION_MOVE -> {
                    val points = inputProcessor.extractPoints(event, pointerIndex)
                    for (point in points) {
                        val (cx, cy) = viewportManager.screenToCanvas(point.x, point.y)
                        if (lastEraserX.isNaN()) {
                            collectEraserHits(cx, cy, eraserRadius)
                        } else {
                            collectEraserHitsSwept(
                                lastEraserX, lastEraserY, cx, cy, eraserRadius,
                            )
                        }
                        lastEraserX = cx
                        lastEraserY = cy
                    }
                    // Flush once per move event (not per point)
                    flushEraserRemovals()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    val point = inputProcessor.extractCurrentPoint(event, pointerIndex)
                    val (cx, cy) = viewportManager.screenToCanvas(point.x, point.y)
                    if (!lastEraserX.isNaN()) {
                        collectEraserHitsSwept(
                            lastEraserX, lastEraserY, cx, cy, eraserRadius,
                        )
                    }
                    flushEraserRemovals()
                    lastEraserX = Float.NaN
                    lastEraserY = Float.NaN
                }
            }
        }
    }

    /**
     * Collects strokes hit by a single eraser point (no bitmap rebuild yet).
     */
    private fun collectEraserHits(x: Float, y: Float, tolerance: Float) {
        val hits = hitTester.hitTest(x, y, tolerance)
        for (stroke in hits) {
            if (stroke.id !in eraserRemovedIds) {
                eraserPendingRemovals.add(stroke)
                eraserRemovedIds.add(stroke.id)
            }
        }
    }

    /**
     * Collects strokes hit by a swept eraser segment (no bitmap rebuild yet).
     */
    private fun collectEraserHitsSwept(
        x0: Float, y0: Float, x1: Float, y1: Float, tolerance: Float,
    ) {
        val hits = hitTester.hitTestSwept(x0, y0, x1, y1, tolerance)
        for (stroke in hits) {
            if (stroke.id !in eraserRemovedIds) {
                eraserPendingRemovals.add(stroke)
                eraserRemovedIds.add(stroke.id)
            }
        }
    }

    /**
     * Flushes pending eraser removals: removes from stroke list and spatial
     * index, rebuilds the bitmap once, and notifies the ViewModel.
     */
    private fun flushEraserRemovals() {
        if (eraserPendingRemovals.isEmpty()) return

        val batch = eraserPendingRemovals.toList()
        eraserPendingRemovals.clear()

        completedStrokes.removeAll { it.id in eraserRemovedIds }
        for (stroke in batch) {
            spatialIndex.remove(stroke)
        }
        renderThread?.requestRender()
        onStrokesErased?.invoke(batch)
    }

    // ==================== Lasso selection ====================

    /**
     * Handles lasso tool touch events with two modes:
     *
     * 1. **Draw mode** — when there is no current selection, or the touch starts
     *    outside the selection bounding box: draws a freeform closed polygon and
     *    selects strokes inside it on ACTION_UP.
     *
     * 2. **Drag/move mode** — when there IS a current selection and the touch
     *    starts inside the selection's bounding box: drags the selected strokes
     *    by the touch delta. On ACTION_UP, the move is finalized and the
     *    `onStrokesMoved` callback is invoked for undo recording.
     *
     * See: docs/research.md — Phase 2 §4 (Lasso Selection)
     */
    private fun handleLassoEvent(event: MotionEvent) {
        val pointerIndex = findStylusPointerIndex(event)
        if (pointerIndex < 0) return

        synchronized(lock) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    val point = inputProcessor.extractCurrentPoint(event, pointerIndex)
                    val (cx, cy) = viewportManager.screenToCanvas(point.x, point.y)

                    // Check if touching inside an existing selection bbox → drag mode
                    if (selectedStrokeIds.isNotEmpty() && isTouchInsideLassoPolygon(cx, cy)) {
                        isLassoDragging = true
                        isLassoDrawing = false
                        lassoDragStartX = cx
                        lassoDragStartY = cy
                        lassoDragLastX = cx
                        lassoDragLastY = cy
                    } else {
                        // Start a new lasso selection — clear previous
                        isLassoDragging = false
                        lassoPoints.clear()
                        selectedStrokeIds.clear()
                        isLassoDrawing = true
                        lassoPoints.add(cx to cy)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isLassoDragging) {
                        // Drag selected strokes
                        val point = inputProcessor.extractCurrentPoint(event, pointerIndex)
                        val (cx, cy) = viewportManager.screenToCanvas(point.x, point.y)
                        val dx = cx - lassoDragLastX
                        val dy = cy - lassoDragLastY
                        if (dx != 0f || dy != 0f) {
                            moveSelectedStrokesInternal(dx, dy)
                            lassoDragLastX = cx
                            lassoDragLastY = cy
                        }
                    } else if (isLassoDrawing) {
                        val points = inputProcessor.extractPoints(event, pointerIndex)
                        for (point in points) {
                            val (cx, cy) = viewportManager.screenToCanvas(point.x, point.y)
                            lassoPoints.add(cx to cy)
                        }
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    if (isLassoDragging) {
                        // Finalize drag — compute total delta for undo recording
                        val point = inputProcessor.extractCurrentPoint(event, pointerIndex)
                        val (cx, cy) = viewportManager.screenToCanvas(point.x, point.y)
                        val dx = cx - lassoDragLastX
                        val dy = cy - lassoDragLastY
                        if (dx != 0f || dy != 0f) {
                            moveSelectedStrokesInternal(dx, dy)
                        }
                        isLassoDragging = false

                        // Notify ViewModel with total delta for MoveStrokesCommand
                        val totalDx = cx - lassoDragStartX
                        val totalDy = cy - lassoDragStartY
                        if (totalDx != 0f || totalDy != 0f) {
                            onStrokesMoved?.invoke(
                                selectedStrokeIds.toSet(), totalDx, totalDy,
                            )
                        }
                    } else if (isLassoDrawing) {
                        isLassoDrawing = false

                        // Close the path and find selected strokes
                        if (lassoPoints.size >= 3) {
                            val selected = hitTester.intersectsPath(lassoPoints)
                            selectedStrokeIds.clear()
                            selectedStrokeIds.addAll(selected.map { it.id })
                            onStrokesSelected?.invoke(selected)
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks whether a canvas-coordinate point (cx, cy) is inside the
     * lasso polygon that was drawn to create the current selection.
     * This lets the user tap anywhere inside the lasso shape to drag.
     */
    private fun isTouchInsideLassoPolygon(cx: Float, cy: Float): Boolean {
        if (lassoPoints.size < 3) return false
        return hitTester.isPointInPolygon(cx, cy, lassoPoints)
    }

    /**
     * Moves selected strokes by delta without triggering external callbacks.
     * Used internally during drag gestures where movement is incremental.
     */
    private fun moveSelectedStrokesInternal(deltaX: Float, deltaY: Float) {
        val movedStrokes = completedStrokes.mapIndexed { _, stroke ->
            if (stroke.id in selectedStrokeIds) {
                val moved = stroke.copy(
                    points = stroke.points.map { p ->
                        p.copy(x = p.x + deltaX, y = p.y + deltaY)
                    }
                )
                spatialIndex.update(moved)
                moved
            } else {
                stroke
            }
        }
        completedStrokes.clear()
        completedStrokes.addAll(movedStrokes)

        // Move the lasso polygon along with the strokes so it stays aligned
        for (i in lassoPoints.indices) {
            val (px, py) = lassoPoints[i]
            lassoPoints[i] = (px + deltaX) to (py + deltaY)
        }

        renderThread?.requestRender()
    }

    /**
     * Clears the current lasso selection.
     */
    fun clearSelection() {
        synchronized(lock) {
            lassoPoints.clear()
            selectedStrokeIds.clear()
            isLassoDrawing = false
            isLassoDragging = false
        }
        renderThread?.requestRender()
    }

    /**
     * Returns the currently selected stroke IDs.
     */
    fun getSelectedStrokeIds(): Set<String> {
        synchronized(lock) {
            return selectedStrokeIds.toSet()
        }
    }

    /**
     * Moves selected strokes by a delta offset.
     * Updates both the stroke list and the spatial index.
     */
    fun moveSelectedStrokes(deltaX: Float, deltaY: Float) {
        synchronized(lock) {
            moveSelectedStrokesInternal(deltaX, deltaY)
        }
        renderThread?.requestRender()
    }

    /**
     * Deletes currently selected strokes.
     * Returns the deleted strokes for undo recording.
     */
    fun deleteSelectedStrokes(): List<Stroke> {
        synchronized(lock) {
            val deleted = completedStrokes.filter { it.id in selectedStrokeIds }
            if (deleted.isEmpty()) return emptyList()
            removeStrokes(deleted)
            selectedStrokeIds.clear()
            lassoPoints.clear()
            return deleted
        }
    }

    private fun finalizeStroke() {
        val stroke = strokeBuilder.finish() ?: return

        completedStrokes.add(stroke)
        spatialIndex.insert(stroke)

        onStrokeCompleted?.invoke(stroke)
    }

    private fun cancelActiveStroke() {
        synchronized(lock) {
            strokeBuilder.cancel()
        }
    }

    // ==================== Stroke management (external API) ====================

    /**
     * Loads a set of strokes (e.g., from persistence) and renders them.
     */
    fun loadStrokes(strokes: List<Stroke>) {
        synchronized(lock) {
            completedStrokes.clear()
            completedStrokes.addAll(strokes)
            spatialIndex.clear()
            spatialIndex.insertAll(strokes)
        }
        renderThread?.requestRender()
    }

    /**
     * Removes the last completed stroke (undo).
     * Returns the removed stroke, or null if empty.
     */
    fun removeLastStroke(): Stroke? {
        synchronized(lock) {
            if (completedStrokes.isEmpty()) return null
            val removed = completedStrokes.removeAt(completedStrokes.size - 1)
            spatialIndex.remove(removed)
            renderThread?.requestRender()
            return removed
        }
    }

    /**
     * Adds a stroke back (redo).
     */
    fun addStroke(stroke: Stroke) {
        synchronized(lock) {
            completedStrokes.add(stroke)
            spatialIndex.insert(stroke)
        }
        renderThread?.requestRender()
    }

    /** Returns a snapshot of all completed strokes. */
    fun getStrokes(): List<Stroke> {
        synchronized(lock) {
            return completedStrokes.toList()
        }
    }

    /** Clears all strokes and resets the canvas. */
    fun clearAll() {
        synchronized(lock) {
            completedStrokes.clear()
            spatialIndex.clear()
            strokeBuilder.cancel()
        }
        renderThread?.requestRender()
    }

    // ==================== Render thread ====================

    private inner class RenderThread : Thread("InkRenderThread") {

        @Volatile
        private var running = true

        @Volatile
        private var renderRequested = true

        fun stopRendering() {
            running = false
            interrupt()
            try {
                join(500)
            } catch (_: InterruptedException) {
            }
        }

        fun requestRender() {
            renderRequested = true
        }

        override fun run() {
            while (running) {
                if (!renderRequested) {
                    try {
                        sleep(4) // ~250fps max polling rate
                    } catch (_: InterruptedException) {
                        if (!running) return
                    }
                    continue
                }

                renderRequested = false
                drawFrame()
            }
        }

        private fun drawFrame() {
            val canvas: Canvas
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas() ?: return
                } else {
                    holder.lockCanvas() ?: return
                }
            } catch (_: IllegalStateException) {
                return // Surface not ready
            }

            try {
                synchronized(lock) {
                    // Clear background (outside page area — dark gray for paginated)
                    canvas.drawColor(canvasBackgroundColor)

                    // Apply viewport transform
                    canvas.save()
                    canvas.translate(viewportManager.panOffsetX, viewportManager.panOffsetY)
                    canvas.scale(viewportManager.zoom, viewportManager.zoom)

                    // Draw page background (white rectangle)
                    if (viewportManager.canvasMode == CanvasMode.PAGINATED) {
                        canvas.drawRect(
                            0f, 0f,
                            viewportManager.pageWidth, viewportManager.pageHeight,
                            pagePaint,
                        )
                    }

                    // Draw paper template (lines, grid, dots, Cornell)
                    templateRenderer.render(
                        canvas = canvas,
                        config = templateConfig,
                        pageWidth = viewportManager.pageWidth,
                        pageHeight = viewportManager.pageHeight,
                        zoom = viewportManager.zoom,
                    )

                    // Clip to page bounds in paginated mode
                    if (viewportManager.canvasMode == CanvasMode.PAGINATED) {
                        canvas.save()
                        canvas.clipRect(
                            0f, 0f,
                            viewportManager.pageWidth, viewportManager.pageHeight,
                        )
                    }

                    // Draw completed strokes directly to the hardware canvas.
                    // No intermediate bitmap — this keeps strokes crisp at all
                    // zoom levels (avoids bitmap stretch blur). Strokes are in
                    // canvas coords with canvas-space thickness, so they scale
                    // naturally with the viewport transform.
                    // See: issues.md ISS-017, ISS-018.
                    strokeRenderer.renderStrokes(canvas, completedStrokes)

                    // Draw active stroke (real-time, canvas coords).
                    // Thickness is the raw canvas-space value — the pen always
                    // lays down the same size mark on the canvas regardless of
                    // zoom. See: issues.md ISS-017.
                    if (strokeBuilder.isActive && strokeBuilder.pointCount > 0) {
                        strokeRenderer.renderActiveStroke(
                            canvas = canvas,
                            points = strokeBuilder.currentPoints,
                            color = currentColor,
                            thickness = currentThickness,
                            tool = currentTool,
                        )
                    }

                    // Draw lasso path (while drawing) or selection highlight
                    drawLassoOverlay(canvas)

                    // Restore page clip (if paginated)
                    if (viewportManager.canvasMode == CanvasMode.PAGINATED) {
                        canvas.restore()
                    }

                    canvas.restore() // Restore viewport transform
                }
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: IllegalStateException) {
                    // Surface was destroyed between lock and unlock
                }
            }
        }
    }

    // ==================== Lasso rendering ====================

    /**
     * Draws the lasso path — both while drawing and after selection is
     * complete. The closed lasso polygon remains visible as long as the
     * selection is active, serving as the drag handle (the user can tap
     * anywhere inside it to move the selected strokes).
     *
     * No bounding-box rectangle is drawn — the lasso shape itself is the
     * only visual indicator.
     *
     * Called within the viewport transform, so coordinates are in canvas space.
     */
    private fun drawLassoOverlay(canvas: Canvas) {
        if (lassoPoints.size < 2) return

        val zoom = viewportManager.zoom
        lassoPaint.strokeWidth = 2f / zoom
        lassoPaint.pathEffect = DashPathEffect(
            floatArrayOf(10f / zoom, 8f / zoom), 0f,
        )

        val path = Path()
        val (startX, startY) = lassoPoints[0]
        path.moveTo(startX, startY)
        for (i in 1 until lassoPoints.size) {
            val (px, py) = lassoPoints[i]
            path.lineTo(px, py)
        }
        // Close the path once selection is finalized
        if (!isLassoDrawing && lassoPoints.size >= 3) {
            path.close()
        }
        canvas.drawPath(path, lassoPaint)

        // Draw a subtle fill inside the lasso polygon when strokes are selected
        if (selectedStrokeIds.isNotEmpty() && !isLassoDrawing && lassoPoints.size >= 3) {
            canvas.drawPath(path, selectionFillPaint)
        }
    }

    // ==================== Helpers ====================

    /**
     * Removes a list of strokes from completed strokes and spatial index,
     * then requests a re-render.
     */
    private fun removeStrokes(strokes: List<Stroke>) {
        val ids = strokes.map { it.id }.toSet()
        completedStrokes.removeAll { it.id in ids }
        for (stroke in strokes) {
            spatialIndex.remove(stroke)
        }
        renderThread?.requestRender()
    }

    /**
     * Finds the pointer index for the active stylus pointer.
     */
    private fun findStylusPointerIndex(event: MotionEvent): Int {
        val targetId = palmFilter.activeStylusPointerId
        if (targetId >= 0) {
            val index = event.findPointerIndex(targetId)
            if (index >= 0) return index
        }
        // Fallback: find any stylus pointer
        for (i in 0 until event.pointerCount) {
            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER
            ) {
                return i
            }
        }
        // Last resort: use pointer 0
        return if (event.pointerCount > 0) 0 else -1
    }

    companion object {
        /**
         * Eraser hit-test radius in screen pixels, divided by zoom at runtime
         * to produce canvas-coordinate tolerance. A generous radius (24px)
         * ensures the eraser feels responsive even with thin strokes.
         */
        const val ERASER_TOLERANCE = 24f
    }
}
