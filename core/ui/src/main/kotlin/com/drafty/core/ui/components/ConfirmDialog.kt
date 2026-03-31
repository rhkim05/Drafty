package com.drafty.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Composable confirmation dialog for destructive or important actions.
 * Uses Material 3 AlertDialog styling.
 */
@Composable
fun ConfirmDialog(
    title: String = "",
    message: String = "",
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    isVisible: Boolean = false,
) {
    if (!isVisible) return

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(cancelText)
            }
        },
    )
}
