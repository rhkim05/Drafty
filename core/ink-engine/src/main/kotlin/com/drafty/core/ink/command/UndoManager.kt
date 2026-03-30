package com.drafty.core.ink.command

import com.drafty.core.ink.renderer.InkSurfaceView

/**
 * Manages an undo/redo stack of [CanvasCommand]s.
 *
 * Stack depth is bounded to [maxDepth] to limit memory usage.
 * Executing a new command clears the redo stack.
 */
class UndoManager(private val maxDepth: Int = DEFAULT_MAX_DEPTH) {

    private val undoStack = ArrayDeque<CanvasCommand>()
    private val redoStack = ArrayDeque<CanvasCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    /**
     * Executes a command and pushes it onto the undo stack.
     * Clears the redo stack (new action invalidates redo history).
     */
    fun execute(command: CanvasCommand, surface: InkSurfaceView) {
        command.execute(surface)
        undoStack.addLast(command)
        redoStack.clear()

        // Trim to max depth
        while (undoStack.size > maxDepth) {
            undoStack.removeFirst()
        }
    }

    /**
     * Records a command without executing it (the action already happened).
     * Used when InkSurfaceView directly adds a stroke, and we just need
     * to record it for undo.
     */
    fun record(command: CanvasCommand) {
        undoStack.addLast(command)
        redoStack.clear()

        while (undoStack.size > maxDepth) {
            undoStack.removeFirst()
        }
    }

    /**
     * Undoes the last command.
     *
     * @return true if an undo was performed
     */
    fun undo(surface: InkSurfaceView): Boolean {
        val command = undoStack.removeLastOrNull() ?: return false
        command.undo(surface)
        redoStack.addLast(command)
        return true
    }

    /**
     * Redoes the last undone command.
     *
     * @return true if a redo was performed
     */
    fun redo(surface: InkSurfaceView): Boolean {
        val command = redoStack.removeLastOrNull() ?: return false
        command.execute(surface)
        undoStack.addLast(command)
        return true
    }

    /** Clears all undo and redo history. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 50
    }
}
