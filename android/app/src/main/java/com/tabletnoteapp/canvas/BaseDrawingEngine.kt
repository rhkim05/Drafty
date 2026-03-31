package com.tabletnoteapp.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.tabletnoteapp.canvas.models.Point
import com.tabletnoteapp.canvas.models.Stroke
import com.tabletnoteapp.canvas.models.StrokeStyle
import com.tabletnoteapp.canvas.models.ToolType
import com.tabletnoteapp.canvas.models.UndoAction
import com.tabletnoteapp.canvas.utils.BezierSmoother
import org.json.JSONArray
import org.json.JSONObject

interface DrawingEngineHost {
    fun invalidateView()
    fun notifyUndoRedoState(canUndo: Boolean, canRedo: Boolean)
    fun notifyEraserLift()
}

class BaseDrawingEngine(private val host: DrawingEngineHost) {

    val committedStrokes = mutableListOf<Stroke>()
    private val undoStack = mutableListOf<UndoAction>()
    private val redoStack = mutableListOf<UndoAction>()
    var activeStroke: Stroke? = null
        private set
    private val strokeEraserBuffer = mutableListOf<Pair<Int, Stroke>>()
    private var strokeOriginalIndexMap: Map<Stroke, Int> = emptyMap()

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    val strokeEraserFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(55, 150, 150, 150)
    }

    val strokeEraserBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(170, 90, 90, 90)
    }

    var eraserCursorX = 0f
    var eraserCursorY = 0f
    var showEraserCursor = false

    fun handleStrokeEraserDown(
        logicalX: Float, logicalY: Float,
        screenX: Float, screenY: Float,
        eraserThickness: Float, scale: Float
    ) {
        redoStack.clear()
        strokeEraserBuffer.clear()
        strokeOriginalIndexMap = committedStrokes.mapIndexed { i, s -> s to i }.toMap()
        eraserCursorX = screenX; eraserCursorY = screenY; showEraserCursor = true
        eraseStrokesAtPoint(logicalX, logicalY, eraserThickness, scale)
        notifyUndoRedoState()
        host.invalidateView()
    }

    fun handleStrokeEraserMove(
        logicalX: Float, logicalY: Float,
        screenX: Float, screenY: Float,
        eraserThickness: Float, scale: Float
    ) {
        eraserCursorX = screenX; eraserCursorY = screenY
        eraseStrokesAtPoint(logicalX, logicalY, eraserThickness, scale)
        host.invalidateView()
    }

    fun handleStrokeEraserUp() {
        showEraserCursor = false
        if (strokeEraserBuffer.isNotEmpty()) {
            undoStack.add(UndoAction.EraseStrokes(strokeEraserBuffer.toList()))
            strokeEraserBuffer.clear()
            strokeOriginalIndexMap = emptyMap()
        }
        notifyUndoRedoState()
        host.notifyEraserLift()
        host.invalidateView()
    }

    fun handleDrawDown(
        logicalX: Float, logicalY: Float, pressure: Float,
        screenX: Float, screenY: Float,
        tool: ToolType, penColor: Int, penThickness: Float, eraserThickness: Float
    ) {
        redoStack.clear()
        if (tool == ToolType.ERASER) {
            eraserCursorX = screenX; eraserCursorY = screenY; showEraserCursor = true
        }
        val style = StrokeStyle(
            color = if (tool == ToolType.ERASER) Color.TRANSPARENT else penColor,
            thickness = if (tool == ToolType.ERASER) eraserThickness else penThickness,
            tool = tool,
        )
        activeStroke = Stroke(style = style).also { it.addPoint(Point(logicalX, logicalY, pressure)) }
        notifyUndoRedoState()
        host.invalidateView()
    }

    fun handleDrawMove(
        logicalX: Float, logicalY: Float, pressure: Float,
        screenX: Float, screenY: Float,
        tool: ToolType
    ) {
        if (tool == ToolType.ERASER) {
            eraserCursorX = screenX; eraserCursorY = screenY
        }
        activeStroke?.addPoint(Point(logicalX, logicalY, pressure))
        host.invalidateView()
    }

    fun handleDrawUp(
        logicalX: Float, logicalY: Float, pressure: Float,
        tool: ToolType
    ) {
        showEraserCursor = false
        activeStroke?.let { s ->
            s.addPoint(Point(logicalX, logicalY, pressure))
            commitStroke(s)
            activeStroke = null
            notifyUndoRedoState()
            if (tool == ToolType.ERASER) host.notifyEraserLift()
        }
        host.invalidateView()
    }

    fun commitStroke(stroke: Stroke) {
        if (stroke.isEmpty) return
        committedStrokes.add(stroke)
        undoStack.add(UndoAction.AddStroke(stroke))
    }

    fun pushUndoAction(action: UndoAction) {
        undoStack.add(action)
        redoStack.clear()
        notifyUndoRedoState()
    }

    fun undo(): UndoAction? {
        val action = undoStack.removeLastOrNull() ?: return null
        when (action) {
            is UndoAction.AddStroke -> committedStrokes.remove(action.stroke)
            is UndoAction.EraseStrokes -> {
                for ((idx, stroke) in action.entries.sortedByDescending { it.first }) {
                    committedStrokes.add(idx.coerceIn(0, committedStrokes.size), stroke)
                }
            }
            is UndoAction.AddPage -> { /* handled by UnifiedCanvasView */ }
        }
        redoStack.add(action)
        notifyUndoRedoState()
        host.invalidateView()
        return action
    }

    fun redo(): UndoAction? {
        val action = redoStack.removeLastOrNull() ?: return null
        when (action) {
            is UndoAction.AddStroke -> committedStrokes.add(action.stroke)
            is UndoAction.EraseStrokes -> committedStrokes.removeAll(action.entries.map { it.second }.toSet())
            is UndoAction.AddPage -> { /* handled by UnifiedCanvasView */ }
        }
        undoStack.add(action)
        notifyUndoRedoState()
        host.invalidateView()
        return action
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    private fun eraseStrokesAtPoint(logicalX: Float, logicalY: Float, eraserThickness: Float, scale: Float) {
        val threshold = eraserThickness / scale
        val thresholdSq = threshold * threshold
        val toRemove = committedStrokes.filter {
            it.style.tool != ToolType.ERASER && strokeHitsPoint(it, logicalX, logicalY, thresholdSq)
        }
        if (toRemove.isNotEmpty()) {
            committedStrokes.removeAll(toRemove.toSet())
            toRemove.forEach { stroke ->
                val origIdx = strokeOriginalIndexMap[stroke] ?: 0
                strokeEraserBuffer.add(origIdx to stroke)
            }
        }
    }

    fun drawCommittedStrokes(canvas: Canvas) {
        for (stroke in committedStrokes) {
            BezierSmoother.buildPath(stroke.points)?.let { canvas.drawPath(it, paintForStroke(stroke)) }
        }
    }

    fun drawActiveStroke(canvas: Canvas) {
        activeStroke?.let { s ->
            BezierSmoother.buildPath(s.points)?.let { canvas.drawPath(it, paintForStroke(s)) }
        }
    }

    fun drawEraserCursor(canvas: Canvas, eraserThickness: Float, eraserMode: String, scale: Float) {
        if (!showEraserCursor) return
        val r = if (eraserMode == "stroke") eraserThickness else eraserThickness * scale / 2f
        canvas.drawCircle(eraserCursorX, eraserCursorY, r, strokeEraserFillPaint)
        canvas.drawCircle(eraserCursorX, eraserCursorY, r, strokeEraserBorderPaint)
    }

    fun hasEraserStrokes(): Boolean {
        return committedStrokes.any { it.style.tool == ToolType.ERASER }
                || activeStroke?.style?.tool == ToolType.ERASER
    }

    fun paintForStroke(stroke: Stroke): Paint {
        return if (stroke.style.tool == ToolType.ERASER) {
            eraserPaint.also { it.strokeWidth = stroke.style.thickness }
        } else {
            strokePaint.also {
                it.color = stroke.style.color
                it.strokeWidth = stroke.style.thickness
            }
        }
    }

    private fun notifyUndoRedoState() {
        host.notifyUndoRedoState(canUndo(), canRedo())
    }

    fun clear() {
        committedStrokes.clear()
        undoStack.clear()
        redoStack.clear()
        activeStroke = null
        notifyUndoRedoState()
        host.invalidateView()
    }

    fun getStrokesJson(): String {
        val arr = JSONArray()
        for (s in committedStrokes) {
            val obj = JSONObject()
            obj.put("tool", s.style.tool.name)
            obj.put("color", s.style.color)
            obj.put("thickness", s.style.thickness.toDouble())
            val pts = JSONArray()
            for (p in s.points) pts.put(JSONObject().apply {
                put("x", p.x.toDouble()); put("y", p.y.toDouble()); put("pressure", p.pressure.toDouble())
            })
            obj.put("points", pts); arr.put(obj)
        }
        return arr.toString()
    }

    fun loadStrokesJson(json: String) {
        val arr = JSONArray(json)
        loadStrokesFromArray(arr)
    }

    fun loadStrokesFromArray(arr: JSONArray) {
        committedStrokes.clear()
        undoStack.clear()
        redoStack.clear()
        activeStroke = null
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val tool = try { ToolType.valueOf(obj.getString("tool")) } catch (_: Exception) { ToolType.PEN }
            val s = Stroke(style = StrokeStyle(
                color = obj.getInt("color"),
                thickness = obj.getDouble("thickness").toFloat(),
                tool = tool,
            ))
            val pts = obj.getJSONArray("points")
            for (j in 0 until pts.length()) {
                val p = pts.getJSONObject(j)
                s.addPoint(Point(
                    x = p.getDouble("x").toFloat(),
                    y = p.getDouble("y").toFloat(),
                    pressure = p.getDouble("pressure").toFloat(),
                ))
            }
            committedStrokes.add(s)
        }
        notifyUndoRedoState()
        host.invalidateView()
    }

    companion object {
        fun strokeHitsPoint(stroke: Stroke, x: Float, y: Float, thresholdSq: Float): Boolean {
            val pts = stroke.points
            if (pts.isEmpty()) return false
            if (pts.size == 1) {
                val dx = pts[0].x - x; val dy = pts[0].y - y
                return dx * dx + dy * dy <= thresholdSq
            }
            for (i in 0 until pts.size - 1) {
                if (segmentDistSq(x, y, pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y) <= thresholdSq) return true
            }
            return false
        }

        fun segmentDistSq(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax; val dy = by - ay
            val lenSq = dx * dx + dy * dy
            if (lenSq == 0f) { val ex = px - ax; val ey = py - ay; return ex * ex + ey * ey }
            val t = ((px - ax) * dx + (py - ay) * dy).div(lenSq).coerceIn(0f, 1f)
            val cx = ax + t * dx; val cy = ay + t * dy
            val ex = px - cx; val ey = py - cy
            return ex * ex + ey * ey
        }
    }
}
