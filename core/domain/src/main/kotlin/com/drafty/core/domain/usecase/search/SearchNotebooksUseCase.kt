package com.drafty.core.domain.usecase.search

import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.repository.NotebookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for searching notebooks.
 * Returns a flow of filtered notebook lists based on a search query.
 */
class SearchNotebooksUseCase @Inject constructor(
    private val notebookRepository: NotebookRepository
) {

    /**
     * Searches notebooks by query.
     *
     * @param query The search query string
     * @return A Flow emitting lists of matching notebooks
     */
    operator fun invoke(query: String): Flow<List<Notebook>> {
        return notebookRepository.search(query)
    }
}
