package com.drafty.core.domain.usecase.section

import com.drafty.core.domain.repository.SectionRepository

/**
 * Use case for deleting a section.
 */
class DeleteSectionUseCase(
    private val sectionRepository: SectionRepository
) {
    suspend operator fun invoke(id: String) {
        sectionRepository.deleteSection(id)
    }
}
