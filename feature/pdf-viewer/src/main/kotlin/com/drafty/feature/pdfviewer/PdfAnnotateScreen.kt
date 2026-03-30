package com.drafty.feature.pdfviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * PDF annotation screen — displays PDF pages with an ink overlay.
 */
@Composable
fun PdfAnnotateScreen(
    notebookId: String,
    onBack: () -> Unit = {},
) {
    // TODO: Implement PDF page display with annotation layer
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("PDF Viewer — $notebookId")
    }
}
