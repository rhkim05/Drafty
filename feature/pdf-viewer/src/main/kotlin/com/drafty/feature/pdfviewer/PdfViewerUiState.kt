package com.drafty.feature.pdfviewer

import com.drafty.core.domain.model.Stroke

/**
 * UI state for the PDF annotation screen.
 */
data class PdfViewerUiState(
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0,
    val annotations: List<Stroke> = emptyList(),
    val isLoading: Boolean = true,
    val zoomLevel: Float = 1f,
)
