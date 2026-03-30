package com.drafty.core.domain.repository

import com.drafty.core.domain.model.Stroke

/**
 * Repository interface for reading and writing stroke data per page.
 * Strokes are stored as protobuf files on the local filesystem.
 */
interface StrokeRepository {
    suspend fun loadStrokes(pageId: String): List<Stroke>
    suspend fun saveStrokes(pageId: String, strokes: List<Stroke>)
    suspend fun deleteStrokes(pageId: String)
}
