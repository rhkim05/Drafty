package com.drafty.core.domain.usecase.section

import com.drafty.core.domain.model.Section
import com.drafty.core.domain.repository.SectionRepository

/**
 * Use case for creating a new section within a notebook.
 */
class CreateSectionUseCase(
    private val sectionRepository: SectionRepository
) {
    suspend operator fun invoke(section: Section) {
        sectionRepository.createSection(section)
    }
}
