package com.drafty.core.domain.model

/**
 * A tag that can be applied to notebooks for organization.
 */
data class Tag(
    val id: String,
    val name: String,
    val color: Long,
)
