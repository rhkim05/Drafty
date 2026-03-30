package com.drafty.feature.pdfviewer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for PDF viewing and annotation.
 */
@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    // TODO: Inject PDF use cases and ink engine
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    // TODO: Implement PDF loading, page navigation, annotation management
}
