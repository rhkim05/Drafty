package com.drafty.core.domain.usecase.search

import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.repository.NotebookRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for searching notebooks.
 */
class SearchNotebooksUseCase(
    private val notebookRepository: NotebookRepository
) {
    operator fun invoke(query: String): Flow<List<Notebook>> {
        return notebookRepository.searchNotebooks(query)
    }
}
