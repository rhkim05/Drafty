package com.drafty.feature.notebooks.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Home screen showing notebook grid/list with search and sort.
 */
@Composable
fun HomeScreen(
    onNotebookClick: (notebookId: String) -> Unit = {},
    onCreateNotebook: () -> Unit = {},
) {
    // TODO: Implement notebook grid with FAB
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("Home Screen — Notebooks")
    }
}
