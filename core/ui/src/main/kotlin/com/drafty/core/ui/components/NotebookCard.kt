package com.drafty.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Composable card for displaying a notebook item in a grid.
 * Shows a cover color swatch, title, page count, and last modified date.
 * Supports long-press for context menu actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotebookCard(
    title: String = "",
    coverColor: Long = 0xFF4A6FA5,
    pageCount: Int = 0,
    lastModified: String = "",
    isFavorite: Boolean = false,
    onClick: () -> Unit = {},
    onRename: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true },
            ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Cover color swatch
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color(coverColor)),
            )

            // Text content
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$pageCount pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lastModified.isNotEmpty()) {
                    Text(
                        text = lastModified,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Context menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "Remove favorite" else "Favorite") },
                    onClick = {
                        showMenu = false
                        onToggleFavorite()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        showMenu = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    onClick = {
                        showMenu = false
                        onDuplicate()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}
