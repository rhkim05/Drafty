package com.drafty.core.data.file

import com.drafty.core.model.Stroke
import javax.inject.Inject

class StrokeFileManager @Inject constructor() {

    /**
     * Load strokes for a page from protobuf file storage.
     *
     * @param pageId The ID of the page
     * @return List of strokes for the page
     */
    suspend fun loadStrokes(pageId: String): List<Stroke> {
        // TODO: Implement protobuf file loading
        return emptyList()
    }

    /**
     * Save strokes for a page to protobuf file storage.
     *
     * @param pageId The ID of the page
     * @param strokes The strokes to save
     */
    suspend fun saveStrokes(pageId: String, strokes: List<Stroke>) {
        // TODO: Implement protobuf file saving
    }
}
