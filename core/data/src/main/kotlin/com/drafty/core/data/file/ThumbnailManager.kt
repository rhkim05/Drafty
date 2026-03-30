package com.drafty.core.data.file

import javax.inject.Inject

class ThumbnailManager @Inject constructor() {

    /**
     * Generate a thumbnail for a page.
     *
     * @param pageId The ID of the page
     */
    suspend fun generateThumbnail(pageId: String) {
        // TODO: Implement thumbnail generation
    }

    /**
     * Get the path to a page's thumbnail.
     *
     * @param pageId The ID of the page
     * @return The path to the thumbnail file
     */
    fun getThumbnailPath(pageId: String): String {
        // TODO: Implement thumbnail path retrieval
        return ""
    }
}
