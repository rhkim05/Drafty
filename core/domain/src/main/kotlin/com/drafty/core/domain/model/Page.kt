package com.drafty.core.domain.model

/**
 * A single page within a section. Can be a notebook page, whiteboard, or PDF page.
 */
data class Page(
    val id: String,
    val sectionId: String,
    val type: PageType,
    val template: PaperTemplate = PaperTemplate.BLANK,
    val width: Float,
    val height: Float,
    val sortOrder: Int = 0,
)

enum class PageType {
    NOTEBOOK,
    WHITEBOARD,
    PDF,
}
