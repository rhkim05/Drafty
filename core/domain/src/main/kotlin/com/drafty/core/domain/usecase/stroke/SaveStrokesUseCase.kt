package com.drafty.core.domain.usecase.stroke

import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.repository.StrokeRepository

/**
 * Use case for saving strokes to a page.
 */
class SaveStrokesUseCase(
    private val strokeRepository: StrokeRepository
) {
    suspend operator fun invoke(pageId: String, strokes: List<Stroke>) {
        strokeRepository.saveStrokes(pageId, strokes)
    }
}
