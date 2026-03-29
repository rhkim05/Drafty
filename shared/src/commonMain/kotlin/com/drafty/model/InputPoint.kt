package com.drafty.model

data class InputPoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltRadians: Float,
    val orientationRadians: Float,
    val timestampMs: Long,
)
