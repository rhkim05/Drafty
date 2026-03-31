package com.drafty.core.data.repository

import com.drafty.core.data.file.StrokeFileManager
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.repository.StrokeRepository
import javax.inject.Inject

class StrokeRepositoryImpl @Inject constructor(
    private val strokeFileManager: StrokeFileManager,
) : StrokeRepository {

    override suspend fun loadStrokes(pageId: String): List<Stroke> =
        strokeFileManager.loadStrokes(pageId)

    override suspend fun saveStrokes(pageId: String, strokes: List<Stroke>) =
        strokeFileManager.saveStrokes(pageId, strokes)

    override suspend fun deleteStrokes(pageId: String) =
        strokeFileManager.deleteStrokes(pageId)
}
