package com.drafty.android.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.Stroke as InkStroke
import com.drafty.shared.model.BoundingBox
import com.drafty.shared.model.BrushConfig
import com.drafty.shared.model.BrushType
import com.drafty.shared.model.StrokeData
import com.drafty.shared.model.StrokePoint

object StrokeMapper {

    fun toStrokeData(
        inkStroke: InkStroke,
        pageId: String,
        strokeId: String,
        strokeOrder: Int
    ): StrokeData {
        val inputs = inkStroke.inputs
        val input = StrokeInput()
        val points = (0 until inputs.size).map { i ->
            inputs.populate(i, input)
            StrokePoint(
                x = input.x,
                y = input.y,
                pressure = input.pressure,
                tiltRadians = input.tiltRadians,
                orientationRadians = input.orientationRadians,
                timestampMs = input.elapsedTimeMillis
            )
        }
        val box = inkStroke.shape.computeBoundingBox()
        return StrokeData(
            id = strokeId,
            pageId = pageId,
            points = points,
            brush = BrushConfig(
                type = BrushType.PRESSURE_PEN,
                size = inkStroke.brush.size,
                colorArgb = inkStroke.brush.colorIntArgb.toLong() or (0xFF000000L)
            ),
            strokeOrder = strokeOrder,
            boundingBox = BoundingBox(
                x = box?.xMin ?: 0f,
                y = box?.yMin ?: 0f,
                width = box?.width ?: 0f,
                height = box?.height ?: 0f
            ),
            createdAt = System.currentTimeMillis()
        )
    }

    fun toInkStroke(strokeData: StrokeData): InkStroke {
        val brush = createBrush(strokeData.brush)
        val batch = MutableStrokeInputBatch()
        strokeData.points.forEach { point ->
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
        return InkStroke(brush, batch)
    }

    fun createBrush(config: BrushConfig): Brush {
        return Brush.createWithColorIntArgb(
            family = StockBrushes.pressurePen(),
            colorIntArgb = config.colorArgb.toInt(),
            size = config.size,
            epsilon = 0.1f
        )
    }
}
