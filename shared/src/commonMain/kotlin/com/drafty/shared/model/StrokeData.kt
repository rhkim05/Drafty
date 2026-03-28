package com.drafty.shared.model

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltRadians: Float,
    val orientationRadians: Float,
    val timestampMs: Long
)

data class BrushConfig(
    val type: BrushType,
    val size: Float,
    val colorArgb: Long
)

enum class BrushType(val key: String) {
    PRESSURE_PEN("pressure_pen");

    companion object {
        fun fromKey(key: String): BrushType =
            entries.firstOrNull { it.key == key } ?: PRESSURE_PEN
    }
}

data class StrokeData(
    val id: String,
    val pageId: String,
    val points: List<StrokePoint>,
    val brush: BrushConfig,
    val strokeOrder: Int,
    val boundingBox: BoundingBox,
    val createdAt: Long
)

data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
