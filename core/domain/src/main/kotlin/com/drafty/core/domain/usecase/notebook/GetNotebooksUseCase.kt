package com.drafty.core.domain.usecase.notebook

import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.repository.NotebookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving all notebooks.
 * Returns a flow of notebook lists for reactive updates.
 */
class GetNotebooksUseCase @Inject constructor(
    private val notebookRepository: NotebookRepository
) {

    /**
     * Retrieves all notebooks as a flow.
     *
     * @return A Flow emitting lists of notebooks
     */
    operator fun invoke(): Flow<List<Notebook>> {
        return notebookRepository.getAll()
    }
}
