package com.drafty.core.domain.model

import kotlin.math.max
import kotlin.math.min

/**
 * Axis-aligned bounding box for a stroke.
 * Used by the spatial index for fast intersection queries.
 */
data class BoundingBox(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f

    /**
     * Tests if this bounding box intersects another.
     */
    fun intersects(other: BoundingBox): Boolean {
        return minX <= other.maxX && maxX >= other.minX &&
            minY <= other.maxY && maxY >= other.minY
    }

    /**
     * Tests if this bounding box contains a point.
     */
    fun contains(x: Float, y: Float): Boolean {
        return x in minX..maxX && y in minY..maxY
    }

    /**
     * Expands this bounding box by the given padding in all directions.
     */
    fun expand(padding: Float): BoundingBox {
        return BoundingBox(
            minX = minX - padding,
            minY = minY - padding,
            maxX = maxX + padding,
            maxY = maxY + padding,
        )
    }

    companion object {
        /** An empty bounding box at the origin. */
        val EMPTY = BoundingBox(0f, 0f, 0f, 0f)
    }
}

/**
 * Computes the axis-aligned bounding box for this stroke.
 * Expands by half the base thickness to account for rendered stroke width.
 *
 * See: docs/research.md — Phase 2 §3 (R-Tree Spatial Index)
 */
fun Stroke.computeBoundingBox(): BoundingBox {
    if (points.isEmpty()) return BoundingBox.EMPTY

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE

    for (point in points) {
        minX = min(minX, point.x)
        minY = min(minY, point.y)
        maxX = max(maxX, point.x)
        maxY = max(maxY, point.y)
    }

    // Expand by half of max possible stroke width to account for rendering
    val padding = baseThickness * 1.25f // max pressure mapping is ~2.5x base
    return BoundingBox(
        minX = minX - padding,
        minY = minY - padding,
        maxX = maxX + padding,
        maxY = maxY + padding,
    )
}
