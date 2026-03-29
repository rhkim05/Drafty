package com.drafty.state

import com.drafty.model.ToolType

data class ActiveToolState(
    val canvasId: String = "",
    val toolType: ToolType = ToolType.Pen,
    val colorArgb: Int = 0xFF000000.toInt(),
    val width: Float = 1.5f,
)
