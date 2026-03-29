package com.drafty.persistence

import com.drafty.model.ToolType

data class StrokeMetadata(
    val id: String,
    val canvasId: String,
    val sortOrder: Int,
    val toolType: ToolType,
    val color: Long,
    val size: Float,
)
