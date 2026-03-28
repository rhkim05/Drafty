package com.drafty.shared.data.repository

import com.drafty.shared.data.db.DraftyDatabase
import com.drafty.shared.data.serialization.StrokeSerializer
import com.drafty.shared.model.BoundingBox
import com.drafty.shared.model.BrushConfig
import com.drafty.shared.model.BrushType
import com.drafty.shared.model.StrokeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StrokeRepository(
    private val database: DraftyDatabase,
    private val serializer: StrokeSerializer
) {
    suspend fun getStrokesForPage(pageId: String): List<StrokeData> =
        withContext(Dispatchers.Default) {
            database.draftyDatabaseQueries.getStrokesForPage(pageId)
                .executeAsList()
                .map { row ->
                    StrokeData(
                        id = row.id,
                        pageId = row.pageId,
                        points = serializer.decode(row.inputData),
                        brush = BrushConfig(
                            type = BrushType.fromKey(row.brushType),
                            size = row.brushSize.toFloat(),
                            colorArgb = row.colorArgb
                        ),
                        strokeOrder = row.strokeOrder.toInt(),
                        boundingBox = BoundingBox(
                            x = row.boundingBoxX.toFloat(),
                            y = row.boundingBoxY.toFloat(),
                            width = row.boundingBoxW.toFloat(),
                            height = row.boundingBoxH.toFloat()
                        ),
                        createdAt = row.createdAt
                    )
                }
        }

    suspend fun insertStroke(stroke: StrokeData) = withContext(Dispatchers.Default) {
        database.draftyDatabaseQueries.insertStroke(
            id = stroke.id,
            pageId = stroke.pageId,
            brushType = stroke.brush.type.key,
            brushSize = stroke.brush.size.toDouble(),
            colorArgb = stroke.brush.colorArgb,
            strokeOrder = stroke.strokeOrder.toLong(),
            inputData = serializer.encode(stroke.points),
            boundingBoxX = stroke.boundingBox.x.toDouble(),
            boundingBoxY = stroke.boundingBox.y.toDouble(),
            boundingBoxW = stroke.boundingBox.width.toDouble(),
            boundingBoxH = stroke.boundingBox.height.toDouble(),
            createdAt = stroke.createdAt
        )
    }

    suspend fun deleteStroke(id: String) = withContext(Dispatchers.Default) {
        database.draftyDatabaseQueries.deleteStroke(id)
    }

    suspend fun getNextStrokeOrder(pageId: String): Int =
        withContext(Dispatchers.Default) {
            val max = database.draftyDatabaseQueries.getMaxStrokeOrder(pageId)
                .executeAsOneOrNull()?.MAX ?: 0L
            (max + 1).toInt()
        }
}
