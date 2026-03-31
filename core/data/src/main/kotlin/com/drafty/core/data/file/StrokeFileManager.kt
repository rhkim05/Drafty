package com.drafty.core.data.file

import android.content.Context
import com.drafty.core.domain.model.InkPoint
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.model.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Manages reading and writing stroke data to page-level binary files.
 *
 * File format: a compact binary format matching the protobuf schema in
 * `strokes.proto`. Each page's strokes are stored at:
 * `{app_internal_storage}/strokes/{pageId}.pb`
 *
 * Format layout:
 * - int32 version (currently 1)
 * - int32 strokeCount
 * - for each stroke:
 *   - UTF strokeId
 *   - int32 pointCount
 *   - for each point: float x, float y, float pressure, float tilt, long timestamp
 *   - long color (ARGB)
 *   - float baseThickness
 *   - int32 toolOrdinal
 *   - long timestamp
 *
 * See: docs/research.md — Phase 2 §8 (Stroke Persistence)
 */
class StrokeFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val strokesDir: File by lazy {
        File(context.filesDir, STROKES_DIR_NAME).also { it.mkdirs() }
    }

    /**
     * Loads strokes for a page from binary file storage.
     * Returns an empty list if the file doesn't exist.
     */
    suspend fun loadStrokes(pageId: String): List<Stroke> = withContext(Dispatchers.IO) {
        val file = File(strokesDir, "$pageId.pb")
        if (!file.exists()) return@withContext emptyList()

        try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val version = input.readInt()
                if (version != FILE_VERSION) {
                    // Unknown version — return empty rather than crash
                    return@withContext emptyList()
                }

                val strokeCount = input.readInt()
                val strokes = ArrayList<Stroke>(strokeCount)

                repeat(strokeCount) {
                    val id = input.readUTF()
                    val pointCount = input.readInt()
                    val points = ArrayList<InkPoint>(pointCount)

                    repeat(pointCount) {
                        points.add(
                            InkPoint(
                                x = input.readFloat(),
                                y = input.readFloat(),
                                pressure = input.readFloat(),
                                tilt = input.readFloat(),
                                timestamp = input.readLong(),
                            )
                        )
                    }

                    val color = input.readLong()
                    val baseThickness = input.readFloat()
                    val toolOrdinal = input.readInt()
                    val timestamp = input.readLong()

                    strokes.add(
                        Stroke(
                            id = id,
                            points = points,
                            color = color,
                            baseThickness = baseThickness,
                            tool = Tool.entries.getOrElse(toolOrdinal) { Tool.PEN },
                            timestamp = timestamp,
                        )
                    )
                }

                strokes
            }
        } catch (e: Exception) {
            // Corrupted file — return empty
            emptyList()
        }
    }

    /**
     * Saves strokes for a page to binary file storage.
     * Writes to a temporary file first, then atomically renames to avoid
     * partial writes on crash.
     */
    suspend fun saveStrokes(pageId: String, strokes: List<Stroke>): Unit = withContext(Dispatchers.IO) {
        val file = File(strokesDir, "$pageId.pb")
        val tmpFile = File(strokesDir, "$pageId.pb.tmp")

        try {
            DataOutputStream(tmpFile.outputStream().buffered()).use { output ->
                output.writeInt(FILE_VERSION)
                output.writeInt(strokes.size)

                for (stroke in strokes) {
                    output.writeUTF(stroke.id)
                    output.writeInt(stroke.points.size)

                    for (point in stroke.points) {
                        output.writeFloat(point.x)
                        output.writeFloat(point.y)
                        output.writeFloat(point.pressure)
                        output.writeFloat(point.tilt)
                        output.writeLong(point.timestamp)
                    }

                    output.writeLong(stroke.color)
                    output.writeFloat(stroke.baseThickness)
                    output.writeInt(stroke.tool.ordinal)
                    output.writeLong(stroke.timestamp)
                }
            }

            // Atomic rename
            tmpFile.renameTo(file)
            Unit
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
    }

    /**
     * Deletes the stroke file for a page.
     */
    suspend fun deleteStrokes(pageId: String): Unit = withContext(Dispatchers.IO) {
        File(strokesDir, "$pageId.pb").delete()
        Unit
    }

    companion object {
        const val STROKES_DIR_NAME = "strokes"
        const val FILE_VERSION = 1
    }
}
