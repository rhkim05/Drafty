package com.drafty.core.domain.usecase.section

import com.drafty.core.domain.model.Section
import com.drafty.core.domain.repository.SectionRepository

/**
 * Use case for renaming a section (delegates to update).
 */
class RenameSectionUseCase(
    private val sectionRepository: SectionRepository
) {
    suspend operator fun invoke(section: Section) {
        sectionRepository.updateSection(section)
    }
}
