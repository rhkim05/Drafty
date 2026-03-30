package com.drafty.core.domain.usecase.page

import com.drafty.core.domain.repository.PageRepository

/**
 * Use case for deleting a page.
 */
class DeletePageUseCase(
    private val pageRepository: PageRepository
) {
    suspend operator fun invoke(id: String) {
        pageRepository.deletePage(id)
    }
}
