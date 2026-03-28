package com.tabletnoteapp.canvas.engine

import android.graphics.Color
import com.tabletnoteapp.canvas.models.Point
import com.tabletnoteapp.canvas.models.Stroke
import com.tabletnoteapp.canvas.models.StrokeStyle
import com.tabletnoteapp.canvas.models.ToolType
import com.tabletnoteapp.canvas.models.UndoAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared stroke management: committed strokes list, undo/redo stacks,
 * stroke-eraser hit-testing, and JSON serialization.
 *
 * Used by both DrawingCanvas (blank notes) and PdfDrawingView (PDF annotations).
 */
class StrokeEngine {

    val committedStrokes = mutableListOf<Stroke>()
    val undoStack        = mutableListOf<UndoAction>()
    val redoStack        = mutableListOf<UndoAction>()

    // Stroke-eraser state (active during a single eraser gesture)
    val eraserBuffer         = mutableListOf<Pair<Int, Stroke>>()
    var originalIndexMap: Map<Stroke, Int> = emptyMap()

    var onUndoRedoStateChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    // ── Undo / Redo ──────────────────────────────────────────────────────────

    fun undo(): Boolean {
        val action = undoStack.removeLastOrNull() ?: return false
        when (action) {
            is UndoAction.AddStroke -> committedStrokes.remove(action.stroke)
            is UndoAction.EraseStrokes -> {
                for ((idx, stroke) in action.entries.sortedByDescending { it.first }) {
                    committedStrokes.add(idx.coerceIn(0, committedStrokes.size), stroke)
                }
            }
        }
        redoStack.add(action)
        notifyUndoRedoState()
        return true
    }

    fun redo(): Boolean {
        val action = redoStack.removeLastOrNull() ?: return false
        when (action) {
            is UndoAction.AddStroke    -> committedStrokes.add(action.stroke)
            is UndoAction.EraseStrokes -> committedStrokes.removeAll(action.entries.map { it.second }.toSet())
        }
        undoStack.add(action)
        notifyUndoRedoState()
        return true
    }

    fun commitStroke(stroke: Stroke) {
        if (stroke.isEmpty) return
        committedStrokes.add(stroke)
        undoStack.add(UndoAction.AddStroke(stroke))
        notifyUndoRedoState()
    }

    fun clear() {
        committedStrokes.clear()
        undoStack.clear()
        redoStack.clear()
        notifyUndoRedoState()
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun notifyUndoRedoState() {
        onUndoRedoStateChanged?.invoke(canUndo(), canRedo())
    }

    // ── Stroke-eraser gesture ────────────────────────────────────────────────

    fun beginStrokeErase() {
        redoStack.clear()
        eraserBuffer.clear()
        originalIndexMap = committedStrokes.mapIndexed { i, s -> s to i }.toMap()
    }

    fun eraseStrokesAtPoint(x: Float, y: Float, thresholdSq: Float) {
        val toRemove = committedStrokes.filter {
            it.style.tool != ToolType.ERASER && strokeHitsPoint(it, x, y, thresholdSq)
        }
        if (toRemove.isNotEmpty()) {
            committedStrokes.removeAll(toRemove.toSet())
            toRemove.forEach { stroke ->
                val origIdx = originalIndexMap[stroke] ?: 0
                eraserBuffer.add(origIdx to stroke)
            }
        }
    }

    fun finishStrokeErase() {
        if (eraserBuffer.isNotEmpty()) {
            undoStack.add(UndoAction.EraseStrokes(eraserBuffer.toList()))
            eraserBuffer.clear()
            originalIndexMap = emptyMap()
        }
        notifyUndoRedoState()
    }

    // ── Geometry / hit-testing ────────────────────────────────────────────────

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

    // ── Serialization ────────────────────────────────────────────────────────

    fun toJson(): String {
        val arr = JSONArray()
        for (s in committedStrokes) {
            val obj = JSONObject()
            obj.put("tool",      s.style.tool.name)
            obj.put("color",     s.style.color)
            obj.put("thickness", s.style.thickness.toDouble())
            val pts = JSONArray()
            for (p in s.points) pts.put(JSONObject().apply {
                put("x", p.x.toDouble()); put("y", p.y.toDouble()); put("pressure", p.pressure.toDouble())
            })
            obj.put("points", pts); arr.put(obj)
        }
        return arr.toString()
    }

    fun loadJson(json: String) {
        clear()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj  = arr.getJSONObject(i)
            val tool = try { ToolType.valueOf(obj.getString("tool")) } catch (_: Exception) { ToolType.PEN }
            val s    = Stroke(style = StrokeStyle(
                color     = obj.getInt("color"),
                thickness = obj.getDouble("thickness").toFloat(),
                tool      = tool,
            ))
            val pts = obj.getJSONArray("points")
            for (j in 0 until pts.length()) {
                val p = pts.getJSONObject(j)
                s.addPoint(Point(p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), p.getDouble("pressure").toFloat()))
            }
            committedStrokes.add(s)
        }
        notifyUndoRedoState()
    }
}
