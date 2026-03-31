package com.tabletnoteapp.canvas

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.View
import com.tabletnoteapp.canvas.models.Point
import com.tabletnoteapp.canvas.models.Stroke
import com.tabletnoteapp.canvas.models.ToolType
import com.tabletnoteapp.canvas.models.UndoAction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UnifiedCanvasView(context: Context) : View(context), DrawingEngineHost {

    val drawingEngine = BaseDrawingEngine(this)
    val scrollEngine = PagedScrollEngine(context, this)

    var isPdf: Boolean = false
        private set

    var currentTool: ToolType = ToolType.PEN
    var penColor: Int = Color.BLACK
    var penThickness: Float = 4f
    var eraserThickness: Float = 24f
    var eraserMode: String = "pixel"

    // PDF-specific
    private val pdfLock = Any()
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderExecutor: ExecutorService? = null
    private val pageCache = HashMap<Int, Bitmap>()
    private val renderingPages = HashSet<Int>()
    private val maxCachedPages = 6
    private val renderWidth: Int by lazy {
        val dm = resources.displayMetrics
        maxOf(dm.widthPixels, dm.heightPixels)
    }
    private var pendingPdfPath: String? = null

    // Blank page
    private val defaultAspectRatio = 1.4142f // A4
    private var savedCanvasWidth: Int = 0

    private val pageBgPaint = Paint().apply { color = Color.WHITE }

    // Callbacks
    var onPageChanged: ((Int) -> Unit)? = null
    var onLoadComplete: ((Int) -> Unit)? = null
    var onUndoRedoStateChanged: ((Boolean, Boolean) -> Unit)? = null
    var onEraserLift: (() -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        scrollEngine.onPageChanged = { page -> onPageChanged?.invoke(page) }
    }

    // ── DrawingEngineHost ────────────────────────────────────────────────────

    override fun invalidateView() = invalidate()
    override fun notifyUndoRedoState(canUndo: Boolean, canRedo: Boolean) {
        onUndoRedoStateChanged?.invoke(canUndo, canRedo)
    }
    override fun notifyEraserLift() { onEraserLift?.invoke() }

    // ── PDF mode ─────────────────────────────────────────────────────────────

    fun openPdf(filePath: String) {
        isPdf = true
        currentTool = ToolType.SELECT
        if (width == 0 || height == 0) { pendingPdfPath = filePath; return }
        pendingPdfPath = null
        pageCache.values.forEach { it.recycle() }
        pageCache.clear(); renderingPages.clear()
        scrollEngine.scrollY = 0f; scrollEngine.translateX = 0f; scrollEngine.scale = 1f

        val executor = renderExecutor ?: Executors.newSingleThreadExecutor().also { renderExecutor = it }
        executor.submit {
            synchronized(pdfLock) { pdfRenderer?.close(); fileDescriptor?.close()
                pdfRenderer = null; fileDescriptor = null }
            try {
                val fd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                synchronized(pdfLock) { fileDescriptor = fd; pdfRenderer = renderer }

                val vw = width
                val aspects = mutableListOf<Float>()
                for (i in 0 until renderer.pageCount) {
                    synchronized(pdfLock) {
                        val page = renderer.openPage(i)
                        aspects.add(page.height.toFloat() / page.width.toFloat())
                        page.close()
                    }
                }
                post {
                    scrollEngine.initPages(renderer.pageCount, aspects)
                    onLoadComplete?.invoke(renderer.pageCount)
                    invalidate()
                }
            } catch (_: Exception) {}
        }
    }

    private fun renderPageAsync(index: Int) {
        val aspect = scrollEngine.pageAspects.getOrNull(index) ?: return
        if (renderingPages.contains(index)) return
        renderingPages.add(index)
        val rw = renderWidth
        val rh = (rw * aspect).toInt()
        val executor = renderExecutor ?: return
        executor.submit {
            try {
                val bm = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                bm.eraseColor(Color.WHITE)
                synchronized(pdfLock) {
                    val r = pdfRenderer ?: return@submit
                    val p = r.openPage(index)
                    p.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    p.close()
                }
                post {
                    pageCache[index] = bm; renderingPages.remove(index)
                    evictDistantPages(); invalidate()
                }
            } catch (_: Exception) { renderingPages.remove(index) }
        }
    }

    private fun evictDistantPages() {
        if (pageCache.size <= maxCachedPages) return
        val cur = scrollEngine.getCurrentPage()
        pageCache.keys.sortedBy { Math.abs(it - cur) }.drop(maxCachedPages)
            .forEach { pageCache.remove(it)?.recycle() }
    }

    // ── Blank page mode ──────────────────────────────────────────────────────

    fun initBlankPages(count: Int, aspectRatio: Float = defaultAspectRatio) {
        isPdf = false
        val aspects = List(count) { aspectRatio }
        scrollEngine.initPages(count, aspects)
        invalidate()
    }

    fun addPage(aspectRatio: Float = defaultAspectRatio) {
        if (isPdf) return
        scrollEngine.addPage(aspectRatio)
        val pageIndex = scrollEngine.getPageCount() - 1
        drawingEngine.pushUndoAction(UndoAction.AddPage(pageIndex))
        scrollEngine.scrollToPage(pageIndex + 1)
        invalidate()
    }

    fun prependPage(aspectRatio: Float = defaultAspectRatio) {
        if (isPdf) return
        val offset = scrollEngine.prependPage(aspectRatio)
        // Shift all stroke Y-coordinates by the new page height
        for (stroke in drawingEngine.committedStrokes) {
            val shifted = stroke.points.map { Point(it.x, it.y + offset, it.pressure, it.timestamp) }
            stroke.points.clear()
            stroke.points.addAll(shifted)
        }
        drawingEngine.pushUndoAction(UndoAction.AddPage(0))
        invalidate()
    }

    // ── View lifecycle ───────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (pendingPdfPath != null) {
            openPdf(pendingPdfPath!!)
        } else if (w != oldw && w > 0) {
            scrollEngine.reLayoutPages(w)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val logVisTop = scrollEngine.scrollY / scrollEngine.scale
        val logVisBottom = (scrollEngine.scrollY + height) / scrollEngine.scale

        canvas.save()
        scrollEngine.applyTransform(canvas)

        // Draw page backgrounds
        for (i in scrollEngine.pageYOffsets.indices) {
            val top = scrollEngine.pageYOffsets[i]
            val ph = scrollEngine.pageHeights.getOrNull(i) ?: continue
            if (top + ph < logVisTop || top > logVisBottom) continue
            val dst = RectF(0f, top, width.toFloat(), top + ph)
            if (isPdf) {
                val bm = pageCache[i]
                if (bm != null) canvas.drawBitmap(bm, null, dst, null)
                else { canvas.drawRect(dst, pageBgPaint); renderPageAsync(i) }
            } else {
                canvas.drawRect(dst, pageBgPaint)
            }
        }

        // Strokes
        val hasEraser = drawingEngine.hasEraserStrokes()
        val layerSave = if (hasEraser) canvas.saveLayer(null, null) else -1

        drawingEngine.drawCommittedStrokes(canvas)
        drawingEngine.drawActiveStroke(canvas)

        if (layerSave != -1) canvas.restoreToCount(layerSave)
        canvas.restore()

        // UI overlays in screen space
        scrollEngine.drawScrollbar(canvas)
        drawingEngine.drawEraserCursor(canvas, eraserThickness, eraserMode, scrollEngine.scale)

        if (!isPdf) {
            scrollEngine.drawOverscrollIndicator(canvas)
        }

        if (scrollEngine.computeScroll()) postInvalidateOnAnimation()
    }

    // ── Touch ────────────────────────────────────────────────────────────────

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            return onTouchEvent(event)
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isScrollbarDown = event.actionMasked == MotionEvent.ACTION_DOWN &&
            scrollEngine.isOnScrollbar(event.x, event.y)
        if (isScrollbarDown) return scrollEngine.handleScrollbar(event)

        if (currentTool == ToolType.SELECT) {
            val prevState = scrollEngine.lastOverscrollState()
            val handled = scrollEngine.handleScroll(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                val state = prevState
                if (!isPdf) {
                    when (state) {
                        OverscrollState.BOTTOM_READY -> addPage()
                        OverscrollState.TOP_READY -> prependPage()
                        OverscrollState.NONE -> {}
                    }
                }
            }
            return handled
        }

        return handleDraw(event)
    }

    private fun handleDraw(event: MotionEvent): Boolean {
        val xDoc = scrollEngine.toLogicalX(event.x)
        val yDoc = scrollEngine.toLogicalY(event.y)
        val pressure = event.pressure.coerceIn(0f, 1f)
        val s = scrollEngine.currentScale()

        if (currentTool == ToolType.ERASER && eraserMode == "stroke") {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    drawingEngine.handleStrokeEraserDown(xDoc, yDoc, event.x, event.y, eraserThickness, s)
                }
                MotionEvent.ACTION_MOVE -> {
                    drawingEngine.handleStrokeEraserMove(xDoc, yDoc, event.x, event.y, eraserThickness, s)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    drawingEngine.handleStrokeEraserUp()
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                drawingEngine.handleDrawDown(xDoc, yDoc, pressure, event.x, event.y, currentTool, penColor, penThickness, eraserThickness)
            }
            MotionEvent.ACTION_MOVE -> {
                drawingEngine.handleDrawMove(xDoc, yDoc, pressure, event.x, event.y, currentTool)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drawingEngine.handleDrawUp(xDoc, yDoc, pressure, currentTool)
            }
        }
        return true
    }

    override fun computeScroll() {
        if (scrollEngine.computeScroll()) {
            invalidate()
        }
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    fun undo() {
        val action = drawingEngine.undo() ?: return
        if (action is UndoAction.AddPage && !isPdf) {
            handleUndoAddPage(action)
        }
        invalidate()
    }

    fun redo() {
        val action = drawingEngine.redo() ?: return
        if (action is UndoAction.AddPage && !isPdf) {
            handleRedoAddPage(action)
        }
        invalidate()
    }

    private fun handleUndoAddPage(action: UndoAction.AddPage) {
        val pageIdx = action.index
        if (pageIdx < 0 || pageIdx >= scrollEngine.getPageCount()) return

        // Save strokes on this page for redo
        val pageTop = scrollEngine.pageYOffsets.getOrNull(pageIdx) ?: return
        val pageBottom = pageTop + (scrollEngine.pageHeights.getOrNull(pageIdx) ?: return)
        val strokesToSave = drawingEngine.committedStrokes.filter { stroke ->
            stroke.points.any { it.y >= pageTop && it.y < pageBottom }
        }
        drawingEngine.committedStrokes.removeAll(strokesToSave.toSet())

        // Remove the page
        val removedHeight = scrollEngine.pageHeights[pageIdx].toFloat() + scrollEngine.pageGapPx
        scrollEngine.pageAspects.removeAt(pageIdx)
        scrollEngine.pageHeights.removeAt(pageIdx)
        scrollEngine.pageYOffsets.removeAt(pageIdx)
        for (i in pageIdx until scrollEngine.pageYOffsets.size) {
            scrollEngine.pageYOffsets[i] -= removedHeight
        }
        scrollEngine.totalDocHeight = scrollEngine.totalDocHeight - removedHeight
        if (pageIdx == 0) {
            // Shift strokes back
            for (stroke in drawingEngine.committedStrokes) {
                val shifted = stroke.points.map { Point(it.x, it.y - removedHeight, it.pressure, it.timestamp) }
                stroke.points.clear()
                stroke.points.addAll(shifted)
            }
            scrollEngine.scrollY = (scrollEngine.scrollY - removedHeight * scrollEngine.scale).coerceAtLeast(0f)
        }
        scrollEngine.constrain()
    }

    private fun handleRedoAddPage(action: UndoAction.AddPage) {
        if (action.index == 0) {
            prependPage()
        } else {
            addPage()
        }
        // Restore saved strokes
        drawingEngine.committedStrokes.addAll(action.strokes)
    }

    fun clearCanvas() { drawingEngine.clear() }

    fun scrollToPage(page: Int) { scrollEngine.scrollToPage(page) }
    fun getPageCount(): Int = scrollEngine.getPageCount()

    // ── Serialization ────────────────────────────────────────────────────────

    fun getStrokesJson(): String {
        if (isPdf) return drawingEngine.getStrokesJson()

        // Version 2 format with page tagging
        val wrapper = JSONObject()
        wrapper.put("version", 2)
        wrapper.put("canvasWidth", width)

        val arr = JSONArray()
        for (s in drawingEngine.committedStrokes) {
            val obj = JSONObject()
            obj.put("tool", s.style.tool.name)
            obj.put("color", s.style.color)
            obj.put("thickness", s.style.thickness.toDouble())
            obj.put("page", computePageForStroke(s))
            val pts = JSONArray()
            for (p in s.points) pts.put(JSONObject().apply {
                put("x", p.x.toDouble()); put("y", p.y.toDouble()); put("pressure", p.pressure.toDouble())
            })
            obj.put("points", pts); arr.put(obj)
        }
        wrapper.put("strokes", arr)
        return wrapper.toString()
    }

    fun loadStrokesJson(json: String) {
        val trimmed = json.trim()
        if (trimmed.startsWith("{")) {
            // Version 2 format
            val wrapper = JSONObject(trimmed)
            val version = wrapper.optInt("version", 1)
            val arr = wrapper.getJSONArray("strokes")
            val savedWidth = wrapper.optInt("canvasWidth", 0)

            drawingEngine.loadStrokesFromArray(arr)

            if (!isPdf && savedWidth > 0 && width > 0 && savedWidth != width) {
                val scaleFactor = width.toFloat() / savedWidth.toFloat()
                scaleAllStrokes(scaleFactor)
            }
        } else if (trimmed.startsWith("[")) {
            // Legacy format (bare array)
            val arr = JSONArray(trimmed)
            drawingEngine.loadStrokesFromArray(arr)

            if (!isPdf) {
                // Legacy blank-note: assign page 0 to all strokes (single-page)
                // No coordinate conversion needed (see plan)
            }
        }
        invalidate()
    }

    private fun scaleAllStrokes(factor: Float) {
        for (stroke in drawingEngine.committedStrokes) {
            val scaled = stroke.points.map { Point(it.x * factor, it.y * factor, it.pressure, it.timestamp) }
            stroke.points.clear()
            stroke.points.addAll(scaled)
        }
    }

    private fun computePageForStroke(stroke: Stroke): Int {
        val avgY = stroke.points.map { it.y }.average().toFloat()
        for (i in scrollEngine.pageYOffsets.indices.reversed()) {
            if (avgY >= scrollEngine.pageYOffsets[i]) return i
        }
        return 0
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderExecutor?.submit {
            synchronized(pdfLock) { pdfRenderer?.close(); fileDescriptor?.close() }
        }
        renderExecutor?.shutdown()
    }
}
