package com.drafty.feature.notebooks.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for the home screen (notebook grid/list).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    // TODO: Inject GetNotebooksUseCase, SearchNotebooksUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // TODO: Implement notebook loading, search, create, delete actions
}
