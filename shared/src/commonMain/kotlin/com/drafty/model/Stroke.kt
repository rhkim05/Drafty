package com.drafty.model

data class Stroke(
    val id: StrokeId,
    val canvasId: String,
    val sortOrder: Int,
    val toolType: ToolType,
    val colorArgb: Long,
    val baseWidth: Float,
    val points: List<InputPoint>,
    val boundingBox: BoundingBox,
)
