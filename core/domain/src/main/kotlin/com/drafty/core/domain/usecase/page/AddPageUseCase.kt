package com.drafty.core.domain.usecase.page

import com.drafty.core.domain.model.Page
import com.drafty.core.domain.repository.PageRepository
import javax.inject.Inject

/**
 * Use case for adding a new page.
 * Handles the business logic of creating and persisting a page to the repository.
 */
class AddPageUseCase @Inject constructor(
    private val pageRepository: PageRepository
) {

    /**
     * Adds a new page.
     *
     * @param page The page to add
     */
    suspend operator fun invoke(page: Page) {
        pageRepository.add(page)
    }
}
