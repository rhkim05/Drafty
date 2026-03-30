package com.drafty.core.domain.usecase.notebook

import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.repository.NotebookRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving all notebooks.
 */
class GetNotebooksUseCase(
    private val notebookRepository: NotebookRepository
) {
    operator fun invoke(): Flow<List<Notebook>> {
        return notebookRepository.getNotebooks()
    }
}
