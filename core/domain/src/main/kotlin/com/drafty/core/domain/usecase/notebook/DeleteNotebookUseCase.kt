package com.drafty.core.domain.usecase.notebook

import com.drafty.core.domain.repository.NotebookRepository

/**
 * Use case for deleting a notebook.
 */
class DeleteNotebookUseCase(
    private val notebookRepository: NotebookRepository
) {
    suspend operator fun invoke(id: String) {
        notebookRepository.deleteNotebook(id)
    }
}
