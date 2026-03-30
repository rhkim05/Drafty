package com.drafty.core.domain.usecase.stroke

import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.repository.StrokeRepository
import javax.inject.Inject

/**
 * Use case for saving strokes to a page.
 * Handles the business logic of persisting drawing strokes to the repository.
 */
class SaveStrokesUseCase @Inject constructor(
    private val strokeRepository: StrokeRepository
) {

    /**
     * Saves strokes for a page.
     *
     * @param pageId The ID of the page
     * @param strokes The list of strokes to save
     */
    suspend operator fun invoke(pageId: String, strokes: List<Stroke>) {
        strokeRepository.save(pageId, strokes)
    }
}
