package com.drafty.feature.notebooks.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Notebook detail screen with section tabs and page thumbnails.
 */
@Composable
fun NotebookDetailScreen(
    notebookId: String,
    onPageClick: (pageId: String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    // TODO: Implement section tabs + page thumbnail grid
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("Notebook Detail — $notebookId")
    }
}
