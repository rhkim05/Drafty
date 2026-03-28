package com.tabletnoteapp.canvas

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.tabletnoteapp.canvas.engine.StrokeEngine
import com.tabletnoteapp.canvas.models.Point
import com.tabletnoteapp.canvas.models.Stroke
import com.tabletnoteapp.canvas.models.StrokeStyle
import com.tabletnoteapp.canvas.models.ToolType
import com.tabletnoteapp.canvas.pdf.PdfPageCache
import com.tabletnoteapp.canvas.pdf.PdfTouchRouter
import com.tabletnoteapp.canvas.pdf.PdfTransformManager
import com.tabletnoteapp.canvas.utils.BezierSmoother

/**
 * Unified native View: PDF rendering + scroll + pinch zoom + stroke annotation.
 * Delegates to PdfPageCache, PdfTransformManager, PdfTouchRouter, and StrokeEngine.
 */
class PdfDrawingView(context: Context) : View(context) {

    // ── Delegates ─────────────────────────────────────────────────────────────

    private val transform = PdfTransformManager()
    val strokeEngine = StrokeEngine()
    private val pageCache = PdfPageCache(
        renderWidth = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels),
        onPageReady = { evictAndInvalidate() },
    )
    private val touchRouter = PdfTouchRouter(context, transform,
        onInvalidate  = { invalidate() },
        onPageChanged = { notifyPageChanged() },
    )

    private val pageGapPx get() = (20 * resources.displayMetrics.density).toInt()

    // ── Drawing state ─────────────────────────────────────────────────────────

    private var activeStroke: Stroke? = null

    var currentTool:      ToolType = ToolType.SELECT
    var penColor:         Int      = Color.BLACK
    var penThickness:     Float    = 4f
    var eraserThickness:  Float    = 24f
    var eraserMode:       String   = "pixel"

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val strokeEraserFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(55, 150, 150, 150)
    }
    private val strokeEraserBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(170, 90, 90, 90)
    }
    private val pageBgPaint = Paint().apply { color = Color.WHITE }
    private val scrollbarActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 120, 120, 120) }
    private val scrollbarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 200, 200, 200) }

    private var eraserCursorX = 0f
    private var eraserCursorY = 0f
    private var showEraserCursor = false

    // ── Callbacks ─────────────────────────────────────────────────────────────

    var onPageChanged:         ((Int) -> Unit)?             = null
    var onLoadComplete:        ((Int) -> Unit)?             = null
    var onUndoRedoStateChanged: ((Boolean, Boolean) -> Unit)?
        get() = strokeEngine.onUndoRedoStateChanged
        set(value) { strokeEngine.onUndoRedoStateChanged = value }
    var onEraserLift: (() -> Unit)? = null
    private var lastReportedPage = -1

    private var pendingPdfPath: String? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        pageCache.postToUi = { runnable -> post(runnable) }
        touchRouter.requestDisallowIntercept = { disallow -> parent?.requestDisallowInterceptTouchEvent(disallow) }
    }

    // ── PDF management ────────────────────────────────────────────────────────

    fun openPdf(filePath: String) {
        if (width == 0 || height == 0) { pendingPdfPath = filePath; return }
        pendingPdfPath = null
        transform.reset()
        invalidate()

        pageCache.open(filePath, width) { pageCount, aspects ->
            transform.layoutPages(width, aspects, pageGapPx)
            onLoadComplete?.invoke(pageCount)
            invalidate()
        }
    }

    // ── View lifecycle ────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        transform.viewWidth = w
        transform.viewHeight = h
        if (pendingPdfPath != null) {
            openPdf(pendingPdfPath!!)
        } else if (w != oldw && w > 0 && pageCache.pageAspects.isNotEmpty()) {
            transform.layoutPages(w, pageCache.pageAspects, pageGapPx)
            transform.constrain()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val logVisTop    = transform.logVisTop()
        val logVisBottom = transform.logVisBottom()

        canvas.save()
        canvas.translate(transform.translateX, -transform.scrollY)
        canvas.scale(transform.scale, transform.scale)

        // 1. PDF pages
        for (i in transform.pageYOffsets.indices) {
            val top = transform.pageYOffsets[i]
            val ph  = transform.pageHeights.getOrNull(i) ?: continue
            if (top + ph < logVisTop || top > logVisBottom) continue
            val bm = pageCache[i]
            val dst = RectF(0f, top, width.toFloat(), top + ph)
            if (bm != null) canvas.drawBitmap(bm, null, dst, null)
            else { canvas.drawRect(dst, pageBgPaint); pageCache.ensureRendered(i) }
        }

        // 2. Annotation strokes (use saveLayer when erasers present)
        val hasEraser = strokeEngine.committedStrokes.any { it.style.tool == ToolType.ERASER }
                     || activeStroke?.style?.tool == ToolType.ERASER
        val layerSave = if (hasEraser) canvas.saveLayer(null, null) else -1

        for (stroke in strokeEngine.committedStrokes) {
            BezierSmoother.buildPath(stroke.points)?.let { canvas.drawPath(it, paintForStroke(stroke)) }
        }
        activeStroke?.let { s ->
            BezierSmoother.buildPath(s.points)?.let { canvas.drawPath(it, paintForStroke(s)) }
        }

        if (layerSave != -1) canvas.restoreToCount(layerSave)
        canvas.restore()

        // 3. Scrollbar (screen coords)
        drawScrollbar(canvas)

        // 4. Eraser cursor (screen coords)
        if (showEraserCursor) {
            val r = if (eraserMode == "stroke") eraserThickness else eraserThickness * transform.scale / 2f
            canvas.drawCircle(eraserCursorX, eraserCursorY, r, strokeEraserFillPaint)
            canvas.drawCircle(eraserCursorX, eraserCursorY, r, strokeEraserBorderPaint)
        }

        if (!touchRouter.scroller.isFinished) postInvalidateOnAnimation()
    }

    private fun drawScrollbar(canvas: Canvas) {
        val totalH = transform.totalDocHeight * transform.scale
        if (totalH <= height) return
        val thumbH = (height.toFloat() * height / totalH).coerceIn(32f, height.toFloat())
        val thumbY = transform.scrollY / (totalH - height) * (height - thumbH)
        val dp     = resources.displayMetrics.density
        val barW   = if (touchRouter.isScrollbarDragging) 7f * dp else 4f * dp
        val barX   = width - barW - 3f * dp
        val paint  = if (touchRouter.isScrollbarDragging) scrollbarActivePaint else scrollbarPaint
        canvas.drawRoundRect(barX, thumbY, barX + barW, thumbY + thumbH, barW / 2, barW / 2, paint)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) return onTouchEvent(event)
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isScrollbarDown = event.actionMasked == MotionEvent.ACTION_DOWN && touchRouter.isOnScrollbar(event.x, event.y)
        if (touchRouter.isScrollbarDragging || isScrollbarDown) return touchRouter.handleScrollbar(event)
        if (currentTool == ToolType.SELECT) return touchRouter.handleScroll(event)
        return handleDraw(event)
    }

    private fun handleDraw(event: MotionEvent): Boolean {
        val xDoc = transform.toLogicalX(event.x)
        val yDoc = transform.toLogicalY(event.y)
        val pressure = event.pressure.coerceIn(0f, 1f)

        // Stroke-eraser
        if (currentTool == ToolType.ERASER && eraserMode == "stroke") {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    strokeEngine.beginStrokeErase()
                    eraserCursorX = event.x; eraserCursorY = event.y; showEraserCursor = true
                    val threshold = eraserThickness / transform.scale
                    strokeEngine.eraseStrokesAtPoint(xDoc, yDoc, threshold * threshold)
                    strokeEngine.notifyUndoRedoState(); invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    eraserCursorX = event.x; eraserCursorY = event.y
                    val threshold = eraserThickness / transform.scale
                    strokeEngine.eraseStrokesAtPoint(xDoc, yDoc, threshold * threshold)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    showEraserCursor = false
                    strokeEngine.finishStrokeErase()
                    onEraserLift?.invoke()
                    invalidate()
                }
            }
            return true
        }

        // Pixel-eraser / pen
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                strokeEngine.redoStack.clear()
                if (currentTool == ToolType.ERASER) {
                    eraserCursorX = event.x; eraserCursorY = event.y; showEraserCursor = true
                }
                val style = StrokeStyle(
                    color     = if (currentTool == ToolType.ERASER) Color.TRANSPARENT else penColor,
                    thickness = if (currentTool == ToolType.ERASER) eraserThickness else penThickness,
                    tool      = currentTool,
                )
                activeStroke = Stroke(style = style).also { it.addPoint(Point(xDoc, yDoc, pressure)) }
                strokeEngine.notifyUndoRedoState(); invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentTool == ToolType.ERASER) {
                    eraserCursorX = event.x; eraserCursorY = event.y
                }
                activeStroke?.addPoint(Point(xDoc, yDoc, pressure)); invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                showEraserCursor = false
                activeStroke?.let { s ->
                    s.addPoint(Point(xDoc, yDoc, pressure))
                    strokeEngine.commitStroke(s)
                    activeStroke = null
                    strokeEngine.notifyUndoRedoState()
                    if (currentTool == ToolType.ERASER) onEraserLift?.invoke()
                }
                invalidate()
            }
        }
        return true
    }

    override fun computeScroll() { touchRouter.computeScroll() }

    // ── Public commands ───────────────────────────────────────────────────────

    fun undo() { if (strokeEngine.undo()) invalidate() }
    fun redo() { if (strokeEngine.redo()) invalidate() }

    fun clearCanvas() {
        strokeEngine.clear()
        invalidate()
    }

    fun getStrokesJson(): String = strokeEngine.toJson()

    fun loadStrokesJson(json: String) {
        strokeEngine.loadJson(json)
        invalidate()
    }

    fun scrollToPage(page: Int) {
        transform.scrollToPage(page)
        notifyPageChanged()
        invalidate()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun paintForStroke(s: Stroke) =
        if (s.style.tool == ToolType.ERASER) eraserPaint.also { it.strokeWidth = s.style.thickness }
        else strokePaint.also { it.color = s.style.color; it.strokeWidth = s.style.thickness }

    private fun notifyPageChanged() {
        val page = transform.getCurrentPage() + 1
        if (page != lastReportedPage) { lastReportedPage = page; onPageChanged?.invoke(page) }
    }

    private fun evictAndInvalidate() {
        pageCache.evictDistantPages(transform.getCurrentPage())
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pageCache.shutdown()
    }
}
