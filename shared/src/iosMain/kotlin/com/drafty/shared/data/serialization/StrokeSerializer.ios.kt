package com.drafty.shared.data.serialization

import com.drafty.shared.model.StrokePoint

actual class StrokeSerializer actual constructor() {
    actual fun encode(points: List<StrokePoint>): ByteArray = byteArrayOf()
    actual fun decode(data: ByteArray): List<StrokePoint> = emptyList()
}
