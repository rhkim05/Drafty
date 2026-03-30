package com.drafty.core.domain.model

/**
 * Represents a notebook containing sections and pages.
 */
data class Notebook(
    val id: String,
    val title: String,
    val coverColor: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0,
)
