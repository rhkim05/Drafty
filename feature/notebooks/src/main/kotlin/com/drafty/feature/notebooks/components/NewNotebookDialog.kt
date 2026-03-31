package com.drafty.feature.notebooks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Preset cover color swatches. */
private val coverColors = listOf(
    0xFF4A6FA5, // Blue (default)
    0xFF5B8C5A, // Green
    0xFFD4724E, // Orange
    0xFF8B5E83, // Purple
    0xFFCB6B6B, // Red
    0xFF4EADD4, // Teal
    0xFFD4A84E, // Gold
    0xFF6B6B6B, // Gray
)

/**
 * Dialog for creating a new notebook with title and cover color selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewNotebookDialog(
    onDismiss: () -> Unit = {},
    onCreate: (title: String, coverColor: Long) -> Unit = { _, _ -> },
) {
    var title by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(coverColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Notebook",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Notebook title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Cover color",
                    style = MaterialTheme.typography.labelMedium,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    coverColors.forEach { color ->
                        val isSelected = color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColor = color },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title.trim(), selectedColor)
                    }
                },
                enabled = title.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
