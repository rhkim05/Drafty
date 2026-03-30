package com.drafty.core.domain.usecase.page

import com.drafty.core.domain.model.Page
import com.drafty.core.domain.repository.PageRepository

/**
 * Use case for adding a new page.
 */
class AddPageUseCase(
    private val pageRepository: PageRepository
) {
    suspend operator fun invoke(page: Page) {
        pageRepository.addPage(page)
    }
}
