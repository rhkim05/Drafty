package com.drafty.core.domain.usecase.notebook

import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.repository.NotebookRepository
import javax.inject.Inject

/**
 * Use case for updating an existing notebook.
 * Handles the business logic of modifying and persisting notebook changes to the repository.
 */
class UpdateNotebookUseCase @Inject constructor(
    private val notebookRepository: NotebookRepository
) {

    /**
     * Updates an existing notebook.
     *
     * @param notebook The notebook with updated information
     */
    suspend operator fun invoke(notebook: Notebook) {
        notebookRepository.update(notebook)
    }
}
