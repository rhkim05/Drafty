package com.drafty.feature.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drafty.feature.canvas.components.CanvasContainer
import com.drafty.feature.canvas.components.DrawingToolbar

/**
 * Main drawing canvas screen.
 *
 * Hosts the [InkSurfaceView] (via [CanvasContainer]) with a floating
 * [DrawingToolbar] for tool selection and undo/redo.
 */
@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen drawing canvas
        CanvasContainer(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
        )

        // Floating toolbar at the bottom
        DrawingToolbar(
            selectedTool = uiState.selectedTool,
            canUndo = uiState.canUndo,
            canRedo = uiState.canRedo,
            onToolSelected = { viewModel.selectTool(it) },
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 16.dp),
        )
    }
}
