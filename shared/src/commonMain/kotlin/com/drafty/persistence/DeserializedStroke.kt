package com.drafty.persistence

import com.drafty.model.BoundingBox
import com.drafty.model.InputPoint

data class DeserializedStroke(
    val points: List<InputPoint>,
    val bounds: BoundingBox,
)
