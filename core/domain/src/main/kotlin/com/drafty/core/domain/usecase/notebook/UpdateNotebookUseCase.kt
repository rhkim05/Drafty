package com.drafty.core.domain.usecase.notebook

import com.drafty.core.domain.model.Notebook
import com.drafty.core.domain.repository.NotebookRepository

/**
 * Use case for updating an existing notebook.
 */
class UpdateNotebookUseCase(
    private val notebookRepository: NotebookRepository
) {
    suspend operator fun invoke(notebook: Notebook) {
        notebookRepository.updateNotebook(notebook)
    }
}
