package com.drafty.core.domain.usecase.stroke

import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.repository.StrokeRepository

/**
 * Use case for loading strokes from a page.
 */
class LoadStrokesUseCase(
    private val strokeRepository: StrokeRepository
) {
    suspend operator fun invoke(pageId: String): List<Stroke> {
        return strokeRepository.loadStrokes(pageId)
    }
}
