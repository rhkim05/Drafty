package com.drafty.core.domain.usecase.notebook

import com.drafty.core.domain.repository.NotebookRepository
import javax.inject.Inject

/**
 * Use case for deleting a notebook.
 * Handles the business logic of removing a notebook from the repository.
 */
class DeleteNotebookUseCase @Inject constructor(
    private val notebookRepository: NotebookRepository
) {

    /**
     * Deletes a notebook by its ID.
     *
     * @param id The ID of the notebook to delete
     */
    suspend operator fun invoke(id: String) {
        notebookRepository.delete(id)
    }
}
