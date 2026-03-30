package com.drafty.core.domain.usecase.notebook

import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.repository.NotebookRepository

/**
 * Use case for creating a new notebook.
 */
class CreateNotebookUseCase(
    private val notebookRepository: NotebookRepository
) {
    suspend operator fun invoke(notebook: Notebook) {
        notebookRepository.createNotebook(notebook)
    }
}
