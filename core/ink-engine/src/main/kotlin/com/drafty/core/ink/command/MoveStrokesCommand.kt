package com.drafty.core.ink.command

import com.drafty.core.domain.model.InkPoint
import com.drafty.core.domain.model.Stroke
import com.drafty.core.ink.renderer.InkSurfaceView

/**
 * Command that moves strokes by a delta offset (lasso move).
 * Undo moves them back by the negative offset.
 */
data class MoveStrokesCommand(
    val strokeIds: Set<String>,
    val deltaX: Float,
    val deltaY: Float,
) : CanvasCommand {

    override fun execute(surface: InkSurfaceView) {
        applyOffset(surface, deltaX, deltaY)
    }

    override fun undo(surface: InkSurfaceView) {
        applyOffset(surface, -deltaX, -deltaY)
    }

    private fun applyOffset(surface: InkSurfaceView, dx: Float, dy: Float) {
        val strokes = surface.getStrokes().map { stroke ->
            if (stroke.id in strokeIds) {
                stroke.copy(
                    points = stroke.points.map { point ->
                        point.copy(x = point.x + dx, y = point.y + dy)
                    }
                )
            } else {
                stroke
            }
        }
        surface.loadStrokes(strokes)
    }

    override val description: String
        get() = "Move ${strokeIds.size} stroke(s) by ($deltaX, $deltaY)"
}
