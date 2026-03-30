package com.drafty.feature.canvas.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drafty.core.domain.model.Tool
import com.drafty.core.ui.icon.DraftyIcons

/**
 * Toolbar for selecting drawing tools, undo/redo, and color/thickness.
 */
@Composable
fun DrawingToolbar(
    selectedTool: Tool,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolSelected: (Tool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drawing tools
            ToolIconButton(
                icon = DraftyIcons.Pen,
                contentDescription = "Pen",
                isSelected = selectedTool == Tool.PEN,
                onClick = { onToolSelected(Tool.PEN) },
            )
            ToolIconButton(
                icon = DraftyIcons.Highlighter,
                contentDescription = "Highlighter",
                isSelected = selectedTool == Tool.HIGHLIGHTER,
                onClick = { onToolSelected(Tool.HIGHLIGHTER) },
            )
            ToolIconButton(
                icon = DraftyIcons.Eraser,
                contentDescription = "Eraser",
                isSelected = selectedTool == Tool.ERASER,
                onClick = { onToolSelected(Tool.ERASER) },
            )
            ToolIconButton(
                icon = DraftyIcons.Lasso,
                contentDescription = "Lasso Select",
                isSelected = selectedTool == Tool.LASSO,
                onClick = { onToolSelected(Tool.LASSO) },
            )

            // Divider space
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Undo / Redo
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
            ) {
                Icon(
                    imageVector = DraftyIcons.Undo,
                    contentDescription = "Undo",
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
            ) {
                Icon(
                    imageVector = DraftyIcons.Redo,
                    contentDescription = "Redo",
                )
            }
        }
    }
}

@Composable
private fun ToolIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    if (isSelected) {
        FilledIconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}
