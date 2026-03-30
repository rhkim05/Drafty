package com.drafty.core.domain.usecase.notebook

import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.repository.NotebookRepository
import javax.inject.Inject

/**
 * Use case for creating a new notebook.
 * Handles the business logic of creating and persisting a notebook to the repository.
 */
class CreateNotebookUseCase @Inject constructor(
    private val notebookRepository: NotebookRepository
) {

    /**
     * Creates a new notebook.
     *
     * @param notebook The notebook to create
     */
    suspend operator fun invoke(notebook: Notebook) {
        notebookRepository.create(notebook)
    }
}
