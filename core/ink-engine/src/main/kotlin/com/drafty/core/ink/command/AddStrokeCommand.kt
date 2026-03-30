package com.drafty.core.ink.command

import com.drafty.core.domain.model.Stroke
import com.drafty.core.ink.renderer.InkSurfaceView

/**
 * Command that adds a stroke to the canvas.
 * Undo removes it; redo adds it back.
 */
data class AddStrokeCommand(
    val stroke: Stroke,
) : CanvasCommand {

    override fun execute(surface: InkSurfaceView) {
        surface.addStroke(stroke)
    }

    override fun undo(surface: InkSurfaceView) {
        surface.removeLastStroke()
    }

    override val description: String
        get() = "Add stroke ${stroke.id.take(8)}"
}
