package com.drafty.feature.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drafty.core.domain.model.CanvasMode
import com.drafty.feature.canvas.components.CanvasContainer
import com.drafty.feature.canvas.components.DrawingToolbar
import com.drafty.feature.canvas.components.PageNavigator

/**
 * Main drawing canvas screen.
 *
 * Hosts the [InkSurfaceView] (via [CanvasContainer]) with a floating
 * [DrawingToolbar] for tool selection and undo/redo, a [PageNavigator]
 * for paginated mode, and a back button to return to the home screen.
 */
@Composable
fun CanvasScreen(
    onBack: () -> Unit = {},
    viewModel: CanvasViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen drawing canvas
        CanvasContainer(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
        )

        // Back button (top-left)
        Surface(
            onClick = {
                viewModel.forceSave()
                onBack()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .size(40.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.padding(8.dp),
            )
        }

        // Page navigator (top-center, only in paginated mode with multiple pages)
        if (uiState.canvasMode == CanvasMode.PAGINATED && viewModel.pageCount > 1) {
            PageNavigator(
                currentPage = viewModel.currentPageNumber,
                totalPages = viewModel.pageCount,
                hasPrevious = viewModel.hasPreviousPage,
                hasNext = viewModel.hasNextPage,
                onPrevious = { viewModel.previousPage() },
                onNext = { viewModel.nextPage() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = 8.dp),
            )
        }

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
