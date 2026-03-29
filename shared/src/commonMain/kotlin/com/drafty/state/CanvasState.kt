package com.drafty.state

import com.drafty.model.Stroke
import com.drafty.model.Template

data class CanvasState(
    val canvasId: String? = null,
    val strokes: List<Stroke> = emptyList(),
    val template: Template = Template.Blank,
    val pdfPageIndex: Int? = null,
)
