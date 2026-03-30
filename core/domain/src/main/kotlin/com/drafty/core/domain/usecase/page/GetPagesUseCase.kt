package com.drafty.core.domain.usecase.page

import com.drafty.core.domain.model.Page
import com.drafty.core.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving pages for a section.
 * Returns a flow of page lists for reactive updates.
 */
class GetPagesUseCase @Inject constructor(
    private val pageRepository: PageRepository
) {

    /**
     * Retrieves all pages for a section as a flow.
     *
     * @param sectionId The ID of the section
     * @return A Flow emitting lists of pages
     */
    operator fun invoke(sectionId: String): Flow<List<Page>> {
        return pageRepository.getBySection(sectionId)
    }
}
