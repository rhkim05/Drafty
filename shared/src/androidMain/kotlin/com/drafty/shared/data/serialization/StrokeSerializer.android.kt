package com.drafty.shared.data.serialization

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch
import androidx.ink.storage.encode
import androidx.ink.storage.decode
import com.drafty.shared.model.StrokePoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

actual class StrokeSerializer actual constructor() {

    actual fun encode(points: List<StrokePoint>): ByteArray {
        val batch = MutableStrokeInputBatch()
        points.forEach { point ->
            batch.add(
                InputToolType.STYLUS,
                point.x,
                point.y,
                point.timestampMs,
                point.pressure,
                point.tiltRadians,
                point.orientationRadians
            )
        }
        return ByteArrayOutputStream().use { stream ->
            batch.encode(stream)
            stream.toByteArray()
        }
    }

    actual fun decode(data: ByteArray): List<StrokePoint> {
        val batch = ByteArrayInputStream(data).use { stream ->
            StrokeInputBatch.decode(stream)
        }
        val input = StrokeInput()
        return (0 until batch.size).map { i ->
            batch.populate(i, input)
            StrokePoint(
                x = input.x,
                y = input.y,
                pressure = input.pressure,
                tiltRadians = input.tiltRadians,
                orientationRadians = input.orientationRadians,
                timestampMs = input.elapsedTimeMillis
            )
        }
    }
}
