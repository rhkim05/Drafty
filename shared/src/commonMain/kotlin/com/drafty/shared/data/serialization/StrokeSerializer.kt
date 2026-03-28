package com.drafty.shared.data.serialization

import com.drafty.shared.model.StrokePoint

expect class StrokeSerializer() {
    fun encode(points: List<StrokePoint>): ByteArray
    fun decode(data: ByteArray): List<StrokePoint>
}
