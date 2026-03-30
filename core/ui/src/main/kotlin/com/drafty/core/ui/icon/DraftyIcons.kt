package com.drafty.core.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon definitions for the Drafty application using Material Icons.
 */
object DraftyIcons {
    val Pen: ImageVector = Icons.Outlined.Draw
    val Highlighter: ImageVector = Icons.Outlined.BorderColor
    val Eraser: ImageVector = Icons.Filled.Delete
    val Lasso: ImageVector = Icons.Outlined.HighlightAlt
    val Undo: ImageVector = Icons.AutoMirrored.Filled.Undo
    val Redo: ImageVector = Icons.AutoMirrored.Filled.Redo
    val Add: ImageVector = Icons.Filled.Add
    val Delete: ImageVector = Icons.Filled.Delete
    val Export: ImageVector = Icons.Outlined.FileDownload
    val Search: ImageVector = Icons.Filled.Search
    val Info: ImageVector = Icons.Filled.Info
}
