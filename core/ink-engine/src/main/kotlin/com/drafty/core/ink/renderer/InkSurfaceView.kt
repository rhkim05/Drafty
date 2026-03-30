package com.drafty.core.ink.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.drafty.core.domain.model.InkPoint
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.model.Tool
import com.drafty.core.ink.input.InputProcessor
import com.drafty.core.ink.input.PalmRejectionFilter
import com.drafty.core.ink.stroke.StrokeBuilder

/**
 * Custom [SurfaceView] for high-performance, low-latency ink rendering.
 *
 * Architecture:
 * - **Main thread**: receives MotionEvents, feeds InputProcessor/PalmRejectionFilter
 * - **Render thread**: draws completed-strokes bitmap + active stroke overlay
 * - **Double buffer**: completed strokes cached as [Bitmap]; only the active
 *   stroke is re-rendered each frame
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
    private var completedBitmap: Bitmap? = null
    private var completedCanvas: Canvas? = null
    private var renderThread: RenderThread? = null

    // --- State ---
    private val completedStrokes = mutableListOf<Stroke>()
    private val lock = Object()

    // --- Tool state (set externally) ---
    var currentTool: Tool = Tool.PEN
    var currentColor: Long = 0xFF000000
    var currentThickness: Float = 4f
    var canvasBackgroundColor: Int = Color.WHITE

    /** Callback when a stroke is completed. */
    var onStrokeCompleted: ((Stroke) -> Unit)? = null

    /** Callback when the active stroke should trigger undo state update. */
    var onStrokeStarted: (() -> Unit)? = null

    init {
        holder.addCallback(this)
        // Make background transparent so paper template shows through
        setZOrderOnTop(false)
    }

    // ==================== Surface lifecycle ====================

    override fun surfaceCreated(holder: SurfaceHolder) {
        ensureBitmapCache()
        renderThread = RenderThread().apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        ensureBitmapCache()
        rebuildCompletedBitmap()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderThread?.stopRendering()
        renderThread = null
    }

    // ==================== Touch handling ====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val filterResult = palmFilter.filter(event)

        when (filterResult) {
            PalmRejectionFilter.FilterResult.ACCEPT_DRAW -> handleDrawEvent(event)
            PalmRejectionFilter.FilterResult.ACCEPT_ERASE -> handleEraseEvent(event)
            PalmRejectionFilter.FilterResult.CANCELED -> cancelActiveStroke()
            PalmRejectionFilter.FilterResult.ACCEPT_GESTURE -> {
                // TODO: Forward to gesture detector for pan/zoom
                return false
            }
            PalmRejectionFilter.FilterResult.IGNORED -> return false
        }

        // Request a render frame
        renderThread?.requestRender()
        return true
    }

    private fun handleDrawEvent(event: MotionEvent) {
        val pointerIndex = findStylusPointerIndex(event)
        if (pointerIndex < 0) return

        synchronized(lock) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    val point = inputProcessor.extractCurrentPoint(event, pointerIndex)
                    strokeBuilder.begin(point, currentColor, currentThickness, currentTool)
                    onStrokeStarted?.invoke()
                }

                MotionEvent.ACTION_MOVE -> {
                    val points = inputProcessor.extractPoints(event, pointerIndex)
                    strokeBuilder.addPoints(points)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    val point = inputProcessor.extractCurrentPoint(event, pointerIndex)
                    strokeBuilder.addPoints(listOf(point))
                    finalizeStroke()
                }
            }
        }
    }

    private fun handleEraseEvent(event: MotionEvent) {
        // TODO: Phase 1 stub — eraser tool hit-testing against completed strokes
        // For now, treat as draw with eraser tool
        val saved = currentTool
        currentTool = Tool.ERASER
        handleDrawEvent(event)
        currentTool = saved
    }

    private fun finalizeStroke() {
        val stroke = strokeBuilder.finish() ?: return

        completedStrokes.add(stroke)

        // Render the new stroke onto the cached bitmap
        completedCanvas?.let { canvas ->
            strokeRenderer.renderStroke(canvas, stroke)
        }

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
            rebuildCompletedBitmap()
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
            rebuildCompletedBitmap()
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
            completedCanvas?.let { strokeRenderer.renderStroke(it, stroke) }
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
            strokeBuilder.cancel()
            rebuildCompletedBitmap()
        }
        renderThread?.requestRender()
    }

    // ==================== Bitmap cache management ====================

    private fun ensureBitmapCache() {
        if (width <= 0 || height <= 0) return

        if (completedBitmap == null ||
            completedBitmap!!.width != width ||
            completedBitmap!!.height != height
        ) {
            completedBitmap?.recycle()
            completedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            completedCanvas = Canvas(completedBitmap!!)
        }
    }

    /**
     * Rebuilds the completed-strokes bitmap from scratch.
     * Called after undo, load, or surface size change.
     */
    private fun rebuildCompletedBitmap() {
        ensureBitmapCache()
        val canvas = completedCanvas ?: return
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        strokeRenderer.renderStrokes(canvas, completedStrokes)
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
                    // Clear background
                    canvas.drawColor(canvasBackgroundColor)

                    // Draw completed strokes from cached bitmap
                    completedBitmap?.let { bitmap ->
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                    }

                    // Draw active stroke (real-time)
                    if (strokeBuilder.isActive && strokeBuilder.pointCount > 0) {
                        strokeRenderer.renderActiveStroke(
                            canvas = canvas,
                            points = strokeBuilder.currentPoints,
                            color = currentColor,
                            thickness = currentThickness,
                            tool = currentTool,
                        )
                    }
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

    // ==================== Helpers ====================

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
}
