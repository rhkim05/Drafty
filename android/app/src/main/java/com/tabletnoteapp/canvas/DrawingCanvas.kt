package com.tabletnoteapp.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import android.view.View
import com.tabletnoteapp.canvas.engine.StrokeEngine
import com.tabletnoteapp.canvas.models.Point
import com.tabletnoteapp.canvas.models.Stroke
import com.tabletnoteapp.canvas.models.StrokeStyle
import com.tabletnoteapp.canvas.models.ToolType
import com.tabletnoteapp.canvas.utils.BezierSmoother

class DrawingCanvas(context: Context) : View(context) {

    init {
        // Required for PorterDuff.Mode.CLEAR (eraser) to work — hardware acceleration ignores it
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // ── Stroke engine (shared logic) ─────────────────────────────────────────

    val strokeEngine = StrokeEngine()

    // ── State ────────────────────────────────────────────────────────────────

    private var activeStroke: Stroke? = null

    // ── Tool settings (can be changed from RN bridge) ─────────────────────

    var penColor: Int          = Color.BLACK
    var penThickness: Float    = 4f
    var eraserThickness: Float = 24f
    var currentTool: ToolType  = ToolType.PEN
    var eraserMode: String     = "pixel"   // "pixel" | "stroke"

    // ── Callbacks ─────────────────────────────────────────────────────────

    var onUndoRedoStateChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)?
        get() = strokeEngine.onUndoRedoStateChanged
        set(value) { strokeEngine.onUndoRedoStateChanged = value }

    var onEraserLift: (() -> Unit)? = null

    // ── Rendering ────────────────────────────────────────────────────────────

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val strokeEraserFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(55, 150, 150, 150)
    }
    private val strokeEraserBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(170, 90, 90, 90)
    }

    private var eraserCursorX = 0f
    private var eraserCursorY = 0f
    private var showEraserCursor = false

    // ── View lifecycle ───────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val newCanvas = Canvas(newBitmap)
        bitmap?.let { newCanvas.drawBitmap(it, 0f, 0f, null) }
        bitmap = newBitmap
        bitmapCanvas = newCanvas
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val isEraserActive = activeStroke?.style?.tool == ToolType.ERASER
        val layerSave = if (isEraserActive) canvas.saveLayer(null, null) else -1

        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        activeStroke?.let { stroke ->
            val path = BezierSmoother.buildPath(stroke.points) ?: return@let
            canvas.drawPath(path, paintForStroke(stroke))
        }

        if (layerSave != -1) canvas.restoreToCount(layerSave)

        if (showEraserCursor) {
            val r = if (eraserMode == "stroke") eraserThickness else eraserThickness / 2f
            canvas.drawCircle(eraserCursorX, eraserCursorY, r, strokeEraserFillPaint)
            canvas.drawCircle(eraserCursorX, eraserCursorY, r, strokeEraserBorderPaint)
        }
    }

    // ── Touch handling ────────────────────────────────────────────────────────

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            return onTouchEvent(event)
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val rawPressure = event.pressure.coerceIn(0f, 1f)
        val pressure = if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) 1f else rawPressure

        // Stroke-eraser mode
        if (currentTool == ToolType.ERASER && eraserMode == "stroke") {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    strokeEngine.beginStrokeErase()
                    eraserCursorX = x; eraserCursorY = y; showEraserCursor = true
                    strokeEngine.eraseStrokesAtPoint(x, y, eraserThickness * eraserThickness)
                    redrawBitmap()
                    strokeEngine.notifyUndoRedoState()
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    eraserCursorX = x; eraserCursorY = y
                    strokeEngine.eraseStrokesAtPoint(x, y, eraserThickness * eraserThickness)
                    redrawBitmap()
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

        // Pixel-eraser / pen mode
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                strokeEngine.redoStack.clear()
                if (currentTool == ToolType.ERASER) {
                    eraserCursorX = x; eraserCursorY = y; showEraserCursor = true
                }
                val style = StrokeStyle(
                    color     = if (currentTool == ToolType.ERASER) Color.TRANSPARENT else penColor,
                    thickness = if (currentTool == ToolType.ERASER) eraserThickness else penThickness,
                    tool      = currentTool,
                )
                activeStroke = Stroke(style = style).also {
                    it.addPoint(Point(x, y, pressure))
                }
                strokeEngine.notifyUndoRedoState()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentTool == ToolType.ERASER) {
                    eraserCursorX = x; eraserCursorY = y
                }
                activeStroke?.let { stroke ->
                    stroke.addPoint(Point(x, y, pressure))
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                showEraserCursor = false
                activeStroke?.let { stroke ->
                    stroke.addPoint(Point(x, y, pressure))
                    commitStroke(stroke)
                    activeStroke = null
                    strokeEngine.notifyUndoRedoState()
                    if (currentTool == ToolType.ERASER) onEraserLift?.invoke()
                }
            }
        }
        return true
    }

    // ── Stroke commit ─────────────────────────────────────────────────────────

    private fun commitStroke(stroke: Stroke) {
        if (stroke.isEmpty) return
        strokeEngine.commitStroke(stroke)
        val path = BezierSmoother.buildPath(stroke.points) ?: return
        bitmapCanvas?.drawPath(path, paintForStroke(stroke))
        invalidate()
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────────

    fun undo() {
        if (strokeEngine.undo()) redrawBitmap()
    }

    fun redo() {
        val action = strokeEngine.redoStack.lastOrNull()
        if (strokeEngine.redo()) {
            // Optimization: if we just re-added a stroke, draw it on the bitmap directly
            if (action is com.tabletnoteapp.canvas.models.UndoAction.AddStroke) {
                val path = BezierSmoother.buildPath(action.stroke.points)
                if (path != null) {
                    bitmapCanvas?.drawPath(path, paintForStroke(action.stroke))
                    invalidate()
                    return
                }
            }
            redrawBitmap()
        }
    }

    fun canUndo() = strokeEngine.canUndo()
    fun canRedo() = strokeEngine.canRedo()

    private fun redrawBitmap() {
        val w = width; val h = height
        if (w == 0 || h == 0) return
        val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val newCanvas = Canvas(newBitmap)
        for (stroke in strokeEngine.committedStrokes) {
            val path = BezierSmoother.buildPath(stroke.points) ?: continue
            newCanvas.drawPath(path, paintForStroke(stroke))
        }
        bitmap = newBitmap
        bitmapCanvas = newCanvas
        invalidate()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun paintForStroke(stroke: Stroke): Paint {
        return if (stroke.style.tool == ToolType.ERASER) {
            eraserPaint.also { it.strokeWidth = stroke.style.thickness }
        } else {
            strokePaint.also {
                it.color = stroke.style.color
                it.strokeWidth = stroke.style.thickness
            }
        }
    }

    fun clearCanvas() {
        strokeEngine.clear()
        bitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    fun getStrokesJson(): String = strokeEngine.toJson()

    fun loadStrokesJson(json: String) {
        strokeEngine.loadJson(json)
        redrawBitmap()
    }
}
