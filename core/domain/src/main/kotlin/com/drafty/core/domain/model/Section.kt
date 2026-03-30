package com.drafty.core.domain.model

/**
 * A section within a notebook, grouping pages like tabs.
 */
data class Section(
    val id: String,
    val notebookId: String,
    val title: String,
    val sortOrder: Int = 0,
)
