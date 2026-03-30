package com.drafty.feature.notebooks.home

import com.drafty.core.domain.model.Notebook

/**
 * UI state for the home screen showing all notebooks.
 */
data class HomeUiState(
    val notebooks: List<Notebook> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.RECENT,
)

enum class SortOrder {
    RECENT,
    NAME,
    CREATED,
}
