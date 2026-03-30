package com.drafty.feature.canvas

import androidx.lifecycle.ViewModel
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.model.Tool
import com.drafty.core.ink.command.AddStrokeCommand
import com.drafty.core.ink.command.UndoManager
import com.drafty.core.ink.renderer.InkSurfaceView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel managing canvas drawing state.
 *
 * Coordinates tool selection, undo/redo, and stroke persistence.
 * The [InkSurfaceView] is the source of truth for stroke data during
 * a drawing session; the ViewModel manages UI state around it.
 */
@HiltViewModel
class CanvasViewModel @Inject constructor(
    // TODO: Inject SaveStrokesUseCase, LoadStrokesUseCase for persistence
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    val undoManager = UndoManager()

    /** Reference to the InkSurfaceView — set by the Compose screen. */
    private var surfaceView: InkSurfaceView? = null

    // ==================== Surface binding ====================

    /**
     * Binds the InkSurfaceView to this ViewModel.
     * Called when the Compose CanvasContainer creates the view.
     */
    fun bindSurface(surface: InkSurfaceView) {
        surfaceView = surface

        // Apply current tool state to the surface
        applySurfaceState(surface)

        // Listen for completed strokes
        surface.onStrokeCompleted = { stroke ->
            undoManager.record(AddStrokeCommand(stroke))
            updateUndoRedoState()
        }
    }

    fun unbindSurface() {
        surfaceView?.onStrokeCompleted = null
        surfaceView = null
    }

    // ==================== Tool actions ====================

    fun selectTool(tool: Tool) {
        _uiState.update { it.copy(selectedTool = tool) }
        surfaceView?.currentTool = tool
    }

    fun setPenColor(color: Long) {
        _uiState.update { it.copy(penColor = color) }
        val state = _uiState.value
        if (state.selectedTool == Tool.PEN) {
            surfaceView?.currentColor = color
        }
    }

    fun setPenThickness(thickness: Float) {
        _uiState.update { it.copy(penThickness = thickness) }
        val state = _uiState.value
        if (state.selectedTool == Tool.PEN) {
            surfaceView?.currentThickness = thickness
        }
    }

    fun setHighlighterColor(color: Long) {
        _uiState.update { it.copy(highlighterColor = color) }
        val state = _uiState.value
        if (state.selectedTool == Tool.HIGHLIGHTER) {
            surfaceView?.currentColor = color
        }
    }

    fun setHighlighterThickness(thickness: Float) {
        _uiState.update { it.copy(highlighterThickness = thickness) }
        val state = _uiState.value
        if (state.selectedTool == Tool.HIGHLIGHTER) {
            surfaceView?.currentThickness = thickness
        }
    }

    // ==================== Undo / Redo ====================

    fun undo() {
        val surface = surfaceView ?: return
        undoManager.undo(surface)
        updateUndoRedoState()
    }

    fun redo() {
        val surface = surfaceView ?: return
        undoManager.redo(surface)
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _uiState.update {
            it.copy(
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo,
            )
        }
    }

    // ==================== Helpers ====================

    private fun applySurfaceState(surface: InkSurfaceView) {
        val state = _uiState.value
        surface.currentTool = state.selectedTool
        when (state.selectedTool) {
            Tool.PEN -> {
                surface.currentColor = state.penColor
                surface.currentThickness = state.penThickness
            }
            Tool.HIGHLIGHTER -> {
                surface.currentColor = state.highlighterColor
                surface.currentThickness = state.highlighterThickness
            }
            else -> {
                surface.currentColor = state.penColor
                surface.currentThickness = state.penThickness
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindSurface()
    }
}
