package com.drafty.core.domain.usecase.page

import com.drafty.core.domain.model.Page
import com.drafty.core.domain.repository.PageRepository
import javax.inject.Inject

/**
 * Use case for reordering pages.
 * Handles the business logic of updating page order in the repository.
 */
class ReorderPagesUseCase @Inject constructor(
    private val pageRepository: PageRepository
) {

    /**
     * Reorders pages in the specified order.
     *
     * @param pages The list of pages in the desired order
     */
    suspend operator fun invoke(pages: List<Page>) {
        pageRepository.reorder(pages)
    }
}
