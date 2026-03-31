package com.drafty.core.ink.spatial

import com.drafty.core.domain.model.BoundingBox
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.model.computeBoundingBox
import kotlin.math.floor

/**
 * Grid-based spatial index for efficient stroke lookup and viewport culling.
 *
 * Divides the canvas into a uniform grid of [cellSize]×[cellSize] cells.
 * Each cell stores the IDs of strokes whose bounding box overlaps that cell.
 *
 * Provides O(1) amortized insert/remove and O(k) query where k is the number
 * of cells the query rectangle spans.
 *
 * Named "RTree" to preserve the interface contract from architecture docs —
 * can be upgraded to a proper R-tree later if profiling shows the need.
 *
 * See: docs/research.md — Phase 2 §3 (R-Tree Spatial Index)
 */
class RTree(private val cellSize: Float = DEFAULT_CELL_SIZE) {

    /** Grid cells: packed (cellX, cellY) → set of stroke IDs in that cell. */
    private val grid = HashMap<Long, MutableSet<String>>()

    /** Stroke lookup: stroke ID → Stroke object. */
    private val strokes = HashMap<String, Stroke>()

    /** Cached bounding boxes: stroke ID → BoundingBox. */
    private val boundingBoxes = HashMap<String, BoundingBox>()

    /** Total number of strokes in the index. */
    val size: Int get() = strokes.size

    /**
     * Inserts a stroke into the spatial index.
     */
    fun insert(stroke: Stroke) {
        val bbox = stroke.computeBoundingBox()
        strokes[stroke.id] = stroke
        boundingBoxes[stroke.id] = bbox

        forEachCell(bbox) { cellKey ->
            grid.getOrPut(cellKey) { mutableSetOf() }.add(stroke.id)
        }
    }

    /**
     * Removes a stroke from the spatial index.
     */
    fun remove(stroke: Stroke) {
        val bbox = boundingBoxes[stroke.id] ?: return

        forEachCell(bbox) { cellKey ->
            grid[cellKey]?.let { cell ->
                cell.remove(stroke.id)
                if (cell.isEmpty()) grid.remove(cellKey)
            }
        }

        strokes.remove(stroke.id)
        boundingBoxes.remove(stroke.id)
    }

    /**
     * Removes a stroke by ID.
     */
    fun removeById(strokeId: String) {
        strokes[strokeId]?.let { remove(it) }
    }

    /**
     * Queries all strokes whose bounding box intersects the given rectangle.
     *
     * @param x Left edge of query rectangle (canvas coordinates)
     * @param y Top edge of query rectangle (canvas coordinates)
     * @param width Width of query rectangle
     * @param height Height of query rectangle
     * @return List of strokes intersecting the query rectangle
     */
    fun query(x: Float, y: Float, width: Float, height: Float): List<Stroke> {
        val queryBox = BoundingBox(x, y, x + width, y + height)
        val resultIds = mutableSetOf<String>()

        forEachCell(queryBox) { cellKey ->
            grid[cellKey]?.let { resultIds.addAll(it) }
        }

        // Fine-phase: verify bounding box intersection
        return resultIds.mapNotNull { id ->
            val bbox = boundingBoxes[id] ?: return@mapNotNull null
            if (bbox.intersects(queryBox)) strokes[id] else null
        }
    }

    /**
     * Queries all strokes whose bounding box intersects the given [BoundingBox].
     */
    fun query(queryBox: BoundingBox): List<Stroke> {
        return query(queryBox.minX, queryBox.minY, queryBox.width, queryBox.height)
    }

    /**
     * Returns the cached bounding box for a stroke, if present.
     */
    fun getBoundingBox(strokeId: String): BoundingBox? = boundingBoxes[strokeId]

    /**
     * Returns the stored stroke for the given ID, if present.
     */
    fun getStroke(strokeId: String): Stroke? = strokes[strokeId]

    /**
     * Re-indexes a stroke after its points have changed (e.g., after a move).
     */
    fun update(stroke: Stroke) {
        remove(stroke)
        insert(stroke)
    }

    /**
     * Clears all strokes from the index.
     */
    fun clear() {
        grid.clear()
        strokes.clear()
        boundingBoxes.clear()
    }

    /**
     * Bulk-loads strokes into the index (more efficient than individual inserts).
     */
    fun insertAll(strokeList: List<Stroke>) {
        for (stroke in strokeList) {
            insert(stroke)
        }
    }

    // ==================== Grid helpers ====================

    /**
     * Encodes a cell (x, y) pair into a single Long key for HashMap performance.
     */
    private fun cellKey(cellX: Int, cellY: Int): Long {
        return (cellX.toLong() shl 32) or (cellY.toLong() and 0xFFFFFFFFL)
    }

    /**
     * Iterates over all grid cells that overlap the given bounding box,
     * invoking [action] with each cell's key.
     */
    private inline fun forEachCell(bbox: BoundingBox, action: (Long) -> Unit) {
        val startCellX = floor(bbox.minX / cellSize).toInt()
        val startCellY = floor(bbox.minY / cellSize).toInt()
        val endCellX = floor(bbox.maxX / cellSize).toInt()
        val endCellY = floor(bbox.maxY / cellSize).toInt()

        for (cx in startCellX..endCellX) {
            for (cy in startCellY..endCellY) {
                action(cellKey(cx, cy))
            }
        }
    }

    companion object {
        /**
         * Default cell size in canvas coordinates.
         * For an A4 page (2480×3508), this gives ~10×14 ≈ 140 cells.
         */
        const val DEFAULT_CELL_SIZE = 256f
    }
}
