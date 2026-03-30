package com.drafty.core.domain.usecase.page

import com.drafty.core.domain.repository.PageRepository
import javax.inject.Inject

/**
 * Use case for deleting a page.
 * Handles the business logic of removing a page from the repository.
 */
class DeletePageUseCase @Inject constructor(
    private val pageRepository: PageRepository
) {

    /**
     * Deletes a page by its ID.
     *
     * @param id The ID of the page to delete
     */
    suspend operator fun invoke(id: String) {
        pageRepository.delete(id)
    }
}
