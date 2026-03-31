package com.drafty.core.domain.usecase.section

import com.drafty.core.domain.model.Section
import com.drafty.core.domain.repository.SectionRepository

/**
 * Use case for reordering sections within a notebook.
 */
class ReorderSectionsUseCase(
    private val sectionRepository: SectionRepository
) {
    suspend operator fun invoke(sections: List<Section>) {
        sectionRepository.reorderSections(sections)
    }
}
