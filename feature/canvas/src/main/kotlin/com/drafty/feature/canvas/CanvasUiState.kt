package com.drafty.feature.canvas

import com.drafty.core.domain.model.CanvasMode
import com.drafty.core.domain.model.Page
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.model.Tool

/**
 * UI state for the drawing canvas screen.
 */
data class CanvasUiState(
    val currentPage: Page? = null,
    val strokes: List<Stroke> = emptyList(),
    val activeStroke: Stroke? = null,
    val selectedTool: Tool = Tool.PEN,
    val penColor: Long = 0xFF000000,
    val penThickness: Float = 4f,
    val highlighterColor: Long = 0x80FFFF00,
    val highlighterThickness: Float = 20f,
    val canvasMode: CanvasMode = CanvasMode.PAGINATED,
    val zoomLevel: Float = 1f,
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val selectedStrokes: List<Stroke> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
)
