package com.drafty.core.ink.command

import com.drafty.core.ink.renderer.InkSurfaceView

/**
 * Sealed interface for reversible canvas operations (undo/redo).
 *
 * Each command knows how to execute and undo itself against an
 * [InkSurfaceView]. The [UndoManager] orchestrates the stack.
 */
sealed interface CanvasCommand {
    /** Executes the command (applies the change). */
    fun execute(surface: InkSurfaceView)

    /** Reverses the command (undoes the change). */
    fun undo(surface: InkSurfaceView)

    /** Human-readable description for debugging. */
    val description: String
}
