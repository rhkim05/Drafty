package com.drafty.core.domain.usecase.page

import com.drafty.core.domain.model.Page
import com.drafty.core.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving pages for a section.
 */
class GetPagesUseCase(
    private val pageRepository: PageRepository
) {
    operator fun invoke(sectionId: String): Flow<List<Page>> {
        return pageRepository.getPagesBySectionId(sectionId)
    }
}
