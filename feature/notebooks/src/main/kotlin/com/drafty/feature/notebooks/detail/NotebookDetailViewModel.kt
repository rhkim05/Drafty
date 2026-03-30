package com.drafty.feature.notebooks.detail

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for notebook detail (sections and page thumbnails).
 */
@HiltViewModel
class NotebookDetailViewModel @Inject constructor(
    // TODO: Inject use cases for sections and pages
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotebookDetailUiState())
    val uiState: StateFlow<NotebookDetailUiState> = _uiState.asStateFlow()

    // TODO: Implement section/page loading and management
}
