package com.drafty.core.ui.components

import androidx.compose.runtime.Composable

/**
 * Composable confirmation dialog for destructive or important actions.
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
    // TODO: Implement material 3 confirmation dialog
}
