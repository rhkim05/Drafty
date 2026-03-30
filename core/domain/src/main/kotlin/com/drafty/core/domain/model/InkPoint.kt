package com.drafty.core.domain.model

/**
 * A single point in a stroke, capturing position and stylus metadata.
 */
data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tilt: Float = 0f,
    val timestamp: Long,
)
