package com.drafty.core.ink.command

import com.drafty.core.domain.model.Stroke
import com.drafty.core.ink.renderer.InkSurfaceView

/**
 * Command that removes strokes from the canvas (e.g., eraser).
 * Undo adds them back.
 */
data class DeleteStrokeCommand(
    val strokes: List<Stroke>,
) : CanvasCommand {

    override fun execute(surface: InkSurfaceView) {
        // Strokes already removed by eraser — this is for redo
        // Re-remove by reloading without these strokes
        val current = surface.getStrokes().toMutableList()
        val idsToRemove = strokes.map { it.id }.toSet()
        current.removeAll { it.id in idsToRemove }
        surface.loadStrokes(current)
    }

    override fun undo(surface: InkSurfaceView) {
        // Add the deleted strokes back
        for (stroke in strokes) {
            surface.addStroke(stroke)
        }
    }

    override val description: String
        get() = "Delete ${strokes.size} stroke(s)"
}
