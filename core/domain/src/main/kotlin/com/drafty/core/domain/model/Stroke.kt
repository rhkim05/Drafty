package com.drafty.core.domain.model

/**
 * A single stroke drawn on the canvas.
 * Contains a list of points capturing the stylus path.
 */
data class Stroke(
    val id: String,
    val points: List<InkPoint>,
    val color: Long,
    val baseThickness: Float,
    val tool: Tool,
    val timestamp: Long,
)
