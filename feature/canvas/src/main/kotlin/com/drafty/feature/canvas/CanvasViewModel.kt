package com.drafty.feature.canvas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drafty.core.domain.model.CanvasMode
import com.drafty.core.domain.model.Page
import com.drafty.core.domain.model.PageType
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.model.TemplateConfig
import com.drafty.core.domain.model.Tool
import com.drafty.core.domain.usecase.page.GetPagesUseCase
import com.drafty.core.domain.usecase.section.GetSectionsUseCase
import com.drafty.core.domain.usecase.stroke.LoadStrokesUseCase
import com.drafty.core.domain.usecase.stroke.SaveStrokesUseCase
import com.drafty.core.ink.command.AddStrokeCommand
import com.drafty.core.ink.command.DeleteStrokeCommand
import com.drafty.core.ink.command.MoveStrokesCommand
import com.drafty.core.ink.command.UndoManager
import com.drafty.core.ink.renderer.InkSurfaceView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing canvas drawing state.
 *
 * Coordinates tool selection, undo/redo, stroke persistence, and
 * page navigation. The [InkSurfaceView] is the source of truth for
 * stroke data during a drawing session; the ViewModel manages UI
 * state around it.
 *
 * See: docs/research.md — Phase 2 §9 (CanvasViewModel Integration)
 */
@HiltViewModel
class CanvasViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadStrokesUseCase: LoadStrokesUseCase,
    private val saveStrokesUseCase: SaveStrokesUseCase,
    private val getPagesUseCase: GetPagesUseCase,
    private val getSectionsUseCase: GetSectionsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    val undoManager = UndoManager()

    /** Reference to the InkSurfaceView — set by the Compose screen. */
    private var surfaceView: InkSurfaceView? = null

    /** Debounced auto-save job. */
    private var autoSaveJob: Job? = null

    /** Pages in the current section (for navigation). */
    private var sectionPages: List<Page> = emptyList()
    private var currentPageIndex: Int = -1

    init {
        // Auto-load from navigation arguments.
        // Accepts notebookId (finds first section) or sectionId directly.
        val notebookId = savedStateHandle.get<String>("notebookId")
        val sectionId = savedStateHandle.get<String>("sectionId")
        val pageId = savedStateHandle.get<String>("pageId")
        if (notebookId != null) {
            loadNotebook(notebookId)
        } else if (sectionId != null) {
            loadSection(sectionId, pageId)
        }
    }

    /**
     * Loads the first section of a notebook (simplified flow).
     */
    fun loadNotebook(notebookId: String) {
        viewModelScope.launch {
            val sections = getSectionsUseCase(notebookId).first()
            val firstSection = sections.firstOrNull() ?: return@launch
            loadSection(firstSection.id, null)
        }
    }

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
            scheduleAutoSave()
        }

        // Listen for erased strokes (for undo recording)
        surface.onStrokesErased = { erasedStrokes ->
            undoManager.record(DeleteStrokeCommand(erasedStrokes))
            updateUndoRedoState()
            scheduleAutoSave()
        }

        // Listen for lasso selection
        surface.onStrokesSelected = { selectedStrokes ->
            _uiState.update { it.copy(selectedStrokes = selectedStrokes) }
        }

        // Listen for lasso drag-to-move (records undo command)
        surface.onStrokesMoved = { movedIds, totalDx, totalDy ->
            undoManager.record(MoveStrokesCommand(movedIds, totalDx, totalDy))
            updateUndoRedoState()
            scheduleAutoSave()
        }
    }

    fun unbindSurface() {
        // Force save before unbinding
        forceSave()
        surfaceView?.onStrokeCompleted = null
        surfaceView?.onStrokesErased = null
        surfaceView?.onStrokesSelected = null
        surfaceView?.onStrokesMoved = null
        surfaceView = null
    }

    // ==================== Page loading ====================

    /**
     * Loads a page and its strokes into the canvas.
     */
    fun loadPage(page: Page) {
        // Save current page first
        forceSave()

        viewModelScope.launch {
            // Update state
            _uiState.update { it.copy(currentPage = page, isSaving = false) }

            // Configure canvas mode from page type
            val canvasMode = when (page.type) {
                PageType.WHITEBOARD -> CanvasMode.WHITEBOARD
                else -> CanvasMode.PAGINATED
            }
            _uiState.update { it.copy(canvasMode = canvasMode) }

            // Configure surface view
            surfaceView?.let { surface ->
                surface.viewportManager.canvasMode = canvasMode
                surface.viewportManager.pageWidth = page.width
                surface.viewportManager.pageHeight = page.height
                surface.viewportManager.resetZoom()
                surface.templateConfig = TemplateConfig.forTemplate(page.template)
            }

            // Load strokes
            val strokes = loadStrokesUseCase(page.id)
            _uiState.update { it.copy(strokes = strokes) }
            surfaceView?.loadStrokes(strokes)

            // Clear undo history for new page
            undoManager.clear()
            updateUndoRedoState()
        }
    }

    /**
     * Loads all pages for a section (for page navigation).
     */
    fun loadSection(sectionId: String, startPageId: String? = null) {
        viewModelScope.launch {
            sectionPages = getPagesUseCase(sectionId).first()
            currentPageIndex = if (startPageId != null) {
                sectionPages.indexOfFirst { it.id == startPageId }.coerceAtLeast(0)
            } else {
                0
            }

            if (sectionPages.isNotEmpty()) {
                loadPage(sectionPages[currentPageIndex])
            }

            updatePageNavigationState()
        }
    }

    // ==================== Page navigation ====================

    fun nextPage() {
        if (currentPageIndex < sectionPages.size - 1) {
            currentPageIndex++
            loadPage(sectionPages[currentPageIndex])
            updatePageNavigationState()
        }
    }

    fun previousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            loadPage(sectionPages[currentPageIndex])
            updatePageNavigationState()
        }
    }

    fun goToPage(index: Int) {
        if (index in sectionPages.indices && index != currentPageIndex) {
            currentPageIndex = index
            loadPage(sectionPages[currentPageIndex])
            updatePageNavigationState()
        }
    }

    val hasNextPage: Boolean get() = currentPageIndex < sectionPages.size - 1
    val hasPreviousPage: Boolean get() = currentPageIndex > 0
    val pageCount: Int get() = sectionPages.size
    val currentPageNumber: Int get() = currentPageIndex + 1

    private fun updatePageNavigationState() {
        _uiState.update {
            it.copy(
                currentPage = sectionPages.getOrNull(currentPageIndex),
            )
        }
    }

    // ==================== Canvas mode ====================

    fun setCanvasMode(mode: CanvasMode) {
        _uiState.update { it.copy(canvasMode = mode) }
        surfaceView?.viewportManager?.canvasMode = mode
    }

    // ==================== Tool actions ====================

    fun selectTool(tool: Tool) {
        _uiState.update { it.copy(selectedTool = tool) }
        surfaceView?.currentTool = tool

        // Apply the correct color and thickness for the new tool
        val state = _uiState.value
        surfaceView?.let { surface ->
            when (tool) {
                Tool.PEN -> {
                    surface.currentColor = state.penColor
                    surface.currentThickness = state.penThickness
                }
                Tool.HIGHLIGHTER -> {
                    surface.currentColor = state.highlighterColor
                    surface.currentThickness = state.highlighterThickness
                }
                else -> { /* Eraser/Lasso don't use color/thickness */ }
            }
        }
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

    // ==================== Selection actions ====================

    /**
     * Moves currently selected strokes by a delta.
     * Records a MoveStrokesCommand for undo.
     */
    fun moveSelectedStrokes(deltaX: Float, deltaY: Float) {
        val surface = surfaceView ?: return
        val selectedIds = surface.getSelectedStrokeIds()
        if (selectedIds.isEmpty()) return

        surface.moveSelectedStrokes(deltaX, deltaY)
        undoManager.record(MoveStrokesCommand(selectedIds, deltaX, deltaY))
        updateUndoRedoState()
        scheduleAutoSave()
    }

    /**
     * Deletes currently selected strokes.
     * Records a DeleteStrokeCommand for undo.
     */
    fun deleteSelectedStrokes() {
        val surface = surfaceView ?: return
        val deleted = surface.deleteSelectedStrokes()
        if (deleted.isNotEmpty()) {
            undoManager.record(DeleteStrokeCommand(deleted))
            updateUndoRedoState()
            scheduleAutoSave()
        }
        _uiState.update { it.copy(selectedStrokes = emptyList()) }
    }

    /**
     * Clears the current lasso selection.
     */
    fun clearSelection() {
        surfaceView?.clearSelection()
        _uiState.update { it.copy(selectedStrokes = emptyList()) }
    }

    // ==================== Undo / Redo ====================

    fun undo() {
        val surface = surfaceView ?: return
        undoManager.undo(surface)
        updateUndoRedoState()
        scheduleAutoSave()
    }

    fun redo() {
        val surface = surfaceView ?: return
        undoManager.redo(surface)
        updateUndoRedoState()
        scheduleAutoSave()
    }

    private fun updateUndoRedoState() {
        _uiState.update {
            it.copy(
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo,
            )
        }
    }

    // ==================== Persistence ====================

    /**
     * Schedules an auto-save with debounce.
     * Cancels any pending save and starts a new one with [AUTO_SAVE_DELAY_MS].
     */
    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            performSave()
        }
    }

    /**
     * Forces an immediate save (on page navigation, app background, etc.).
     */
    fun forceSave() {
        autoSaveJob?.cancel()
        val pageId = _uiState.value.currentPage?.id ?: return
        val strokes = surfaceView?.getStrokes() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                saveStrokesUseCase(pageId, strokes)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun performSave() {
        val pageId = _uiState.value.currentPage?.id ?: return
        val strokes = surfaceView?.getStrokes() ?: return
        _uiState.update { it.copy(isSaving = true) }
        try {
            saveStrokesUseCase(pageId, strokes)
        } finally {
            _uiState.update { it.copy(isSaving = false) }
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
        forceSave()
        unbindSurface()
    }

    companion object {
        /** Auto-save debounce delay in milliseconds. */
        const val AUTO_SAVE_DELAY_MS = 500L
    }
}
