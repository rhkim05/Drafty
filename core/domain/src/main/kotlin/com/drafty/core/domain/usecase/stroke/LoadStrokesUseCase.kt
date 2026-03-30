package com.drafty.core.domain.usecase.stroke

import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.repository.StrokeRepository
import javax.inject.Inject

/**
 * Use case for loading strokes from a page.
 * Handles the business logic of retrieving drawing strokes from the repository.
 */
class LoadStrokesUseCase @Inject constructor(
    private val strokeRepository: StrokeRepository
) {

    /**
     * Loads strokes for a page.
     *
     * @param pageId The ID of the page
     * @return A list of strokes for the page
     */
    suspend operator fun invoke(pageId: String): List<Stroke> {
        return strokeRepository.load(pageId)
    }
}
