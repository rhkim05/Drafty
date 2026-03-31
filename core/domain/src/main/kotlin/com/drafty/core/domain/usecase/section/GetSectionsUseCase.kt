package com.drafty.core.domain.usecase.section

import com.drafty.core.domain.model.Section
import com.drafty.core.domain.repository.SectionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving sections for a notebook.
 */
class GetSectionsUseCase(
    private val sectionRepository: SectionRepository
) {
    operator fun invoke(notebookId: String): Flow<List<Section>> {
        return sectionRepository.getSectionsByNotebookId(notebookId)
    }
}
