package com.drafty.core.ui.components

import androidx.compose.runtime.Composable

/**
 * Composable button for tool selection (pen, eraser, highlighter, etc).
 */
@Composable
fun ToolButton(
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    // TODO: Implement tool selection button with visual feedback for selected state
}
