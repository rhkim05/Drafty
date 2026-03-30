package com.drafty.core.data.file

import com.drafty.core.domain.model.Stroke
import javax.inject.Inject

class StrokeFileManager @Inject constructor() {

    /**
     * Load strokes for a page from protobuf file storage.
     */
    suspend fun loadStrokes(pageId: String): List<Stroke> {
        // TODO: Implement protobuf file loading
        return emptyList()
    }

    /**
     * Save strokes for a page to protobuf file storage.
     */
    suspend fun saveStrokes(pageId: String, strokes: List<Stroke>) {
        // TODO: Implement protobuf file saving
    }
}
