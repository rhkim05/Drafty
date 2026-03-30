package com.drafty.feature.notebooks.detail

import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.model.Page
import com.drafty.core.domain.model.Section

/**
 * UI state for the notebook detail screen (sections + page thumbnails).
 */
data class NotebookDetailUiState(
    val notebook: Notebook? = null,
    val sections: List<Section> = emptyList(),
    val selectedSectionId: String? = null,
    val pages: List<Page> = emptyList(),
    val isLoading: Boolean = true,
)
