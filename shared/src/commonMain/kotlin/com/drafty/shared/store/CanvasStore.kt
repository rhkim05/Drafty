package com.drafty.shared.store

import com.drafty.shared.data.repository.StrokeRepository
import com.drafty.shared.model.BrushConfig
import com.drafty.shared.model.BrushType
import com.drafty.shared.model.StrokeData
import com.drafty.shared.model.Tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CanvasState(
    val pageId: String = "",
    val strokes: List<StrokeData> = emptyList(),
    val activeTool: Tool = Tool.PEN,
    val activeBrush: BrushConfig = BrushConfig(
        type = BrushType.PRESSURE_PEN,
        size = 3.0f,
        colorArgb = 0xFF000000
    ),
    val isLoading: Boolean = true
)

sealed interface CanvasIntent {
    data class LoadPage(val pageId: String) : CanvasIntent
    data class StrokeCompleted(val stroke: StrokeData) : CanvasIntent
    data class ToolSelected(val tool: Tool) : CanvasIntent
    data class ColorSelected(val colorArgb: Long) : CanvasIntent
    data class BrushSizeChanged(val size: Float) : CanvasIntent
}

class CanvasStore(
    private val strokeRepository: StrokeRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(CanvasState())
    val state: StateFlow<CanvasState> = _state.asStateFlow()

    fun dispatch(intent: CanvasIntent) {
        when (intent) {
            is CanvasIntent.LoadPage -> loadPage(intent.pageId)
            is CanvasIntent.StrokeCompleted -> onStrokeCompleted(intent.stroke)
            is CanvasIntent.ToolSelected -> _state.update {
                it.copy(activeTool = intent.tool)
            }
            is CanvasIntent.ColorSelected -> _state.update {
                it.copy(activeBrush = it.activeBrush.copy(colorArgb = intent.colorArgb))
            }
            is CanvasIntent.BrushSizeChanged -> _state.update {
                it.copy(activeBrush = it.activeBrush.copy(size = intent.size))
            }
        }
    }

    private fun loadPage(pageId: String) {
        _state.update { it.copy(pageId = pageId, isLoading = true) }
        scope.launch {
            val strokes = strokeRepository.getStrokesForPage(pageId)
            _state.update { it.copy(strokes = strokes, isLoading = false) }
        }
    }

    private fun onStrokeCompleted(stroke: StrokeData) {
        _state.update { it.copy(strokes = it.strokes + stroke) }
        scope.launch {
            strokeRepository.insertStroke(stroke)
        }
    }
}
