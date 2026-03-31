package com.drafty.feature.notebooks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drafty.core.domain.model.Notebook
import com.drafty.core.ui.components.NotebookCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Grid layout for displaying notebooks. Adapts column count based on [columns].
 */
@Composable
fun NotebookGrid(
    notebooks: List<Notebook>,
    columns: Int = 2,
    onNotebookClick: (String) -> Unit = {},
    onRename: (Notebook) -> Unit = {},
    onDuplicate: (Notebook) -> Unit = {},
    onDelete: (Notebook) -> Unit = {},
    onToggleFavorite: (Notebook) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(
            items = notebooks,
            key = { it.id },
        ) { notebook ->
            NotebookCard(
                title = notebook.title,
                coverColor = notebook.coverColor,
                lastModified = formatDate(notebook.modifiedAt),
                isFavorite = notebook.isFavorite,
                onClick = { onNotebookClick(notebook.id) },
                onRename = { onRename(notebook) },
                onDuplicate = { onDuplicate(notebook) },
                onDelete = { onDelete(notebook) },
                onToggleFavorite = { onToggleFavorite(notebook) },
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

private fun formatDate(timestamp: Long): String =
    dateFormat.format(Date(timestamp))
