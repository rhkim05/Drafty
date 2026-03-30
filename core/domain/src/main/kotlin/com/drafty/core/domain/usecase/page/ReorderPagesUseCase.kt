package com.drafty.core.domain.usecase.page

import com.drafty.core.domain.model.Page
import com.drafty.core.domain.repository.PageRepository

/**
 * Use case for reordering pages.
 */
class ReorderPagesUseCase(
    private val pageRepository: PageRepository
) {
    suspend operator fun invoke(pages: List<Page>) {
        pageRepository.reorderPages(pages)
    }
}
