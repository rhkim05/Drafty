package com.drafty.core.ink.spatial

import com.drafty.core.domain.model.BoundingBox
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.model.computeBoundingBox

/**
 * Performs stroke hit testing for eraser, selection, and viewport culling.
 *
 * Uses a two-phase approach:
 * 1. **Coarse phase**: Query the [RTree] spatial index for candidate strokes
 *    whose bounding box overlaps the query region.
 * 2. **Fine phase**: Check individual stroke points against the precise
 *    query geometry (point distance, polygon containment, etc.).
 *
 * See: docs/research.md — Phase 2 §4 (HitTester)
 */
class HitTester(private val spatialIndex: RTree) {

    /**
     * Tests if a point hits any strokes within a tolerance distance.
     *
     * Used by the eraser tool (whole-stroke deletion mode):
     * - Coarse: query spatial index for strokes near the point
     * - Fine: check if any stroke point is within [tolerance] of (x, y)
     *
     * @param x Touch x in canvas coordinates
     * @param y Touch y in canvas coordinates
     * @param tolerance Hit radius in canvas coordinates
     * @return Strokes hit by this point
     */
    fun hitTest(x: Float, y: Float, tolerance: Float = DEFAULT_TOLERANCE): List<Stroke> {
        // Coarse phase: query a small rectangle around the touch point
        val queryBox = BoundingBox(
            minX = x - tolerance,
            minY = y - tolerance,
            maxX = x + tolerance,
            maxY = y + tolerance,
        )
        val candidates = spatialIndex.query(queryBox)

        // Fine phase: check point-to-stroke distance
        val toleranceSq = tolerance * tolerance
        return candidates.filter { stroke ->
            stroke.points.any { point ->
                val dx = point.x - x
                val dy = point.y - y
                dx * dx + dy * dy <= toleranceSq
            }
        }
    }

    /**
     * Tests if a swept eraser path hits any strokes.
     *
     * For continuous eraser dragging, checks against the line segment
     * between consecutive eraser positions to prevent "skipping over"
     * strokes at fast eraser speeds.
     *
     * @param x0 Previous eraser x (canvas coordinates)
     * @param y0 Previous eraser y
     * @param x1 Current eraser x
     * @param y1 Current eraser y
     * @param tolerance Hit radius
     * @return Strokes hit by the swept eraser path
     */
    fun hitTestSwept(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        tolerance: Float = DEFAULT_TOLERANCE,
    ): List<Stroke> {
        // Build a bounding box around the swept segment + tolerance
        val queryBox = BoundingBox(
            minX = minOf(x0, x1) - tolerance,
            minY = minOf(y0, y1) - tolerance,
            maxX = maxOf(x0, x1) + tolerance,
            maxY = maxOf(y0, y1) + tolerance,
        )
        val candidates = spatialIndex.query(queryBox)

        val toleranceSq = tolerance * tolerance
        return candidates.filter { stroke ->
            stroke.points.any { point ->
                pointToSegmentDistanceSq(point.x, point.y, x0, y0, x1, y1) <= toleranceSq
            }
        }
    }

    /**
     * Queries all strokes whose bounding box intersects a rectangular region.
     * Used for viewport culling — only render visible strokes.
     *
     * @param x Left edge (canvas coordinates)
     * @param y Top edge
     * @param width Rectangle width
     * @param height Rectangle height
     * @return Strokes visible in the rectangle
     */
    fun intersectsRect(x: Float, y: Float, width: Float, height: Float): List<Stroke> {
        return spatialIndex.query(x, y, width, height)
    }

    /**
     * Tests if strokes intersect a closed lasso polygon.
     *
     * A stroke is considered "selected" if ANY of its points fall inside
     * the lasso polygon. This means partially enclosed strokes are selected
     * in their entirety — matching user expectation from apps like GoodNotes.
     *
     * @param pathPoints Ordered vertices of the closed lasso polygon (canvas coords)
     * @param tolerance Expansion around the polygon for the coarse query
     * @return Strokes selected by the lasso
     */
    fun intersectsPath(
        pathPoints: List<Pair<Float, Float>>,
        tolerance: Float = DEFAULT_TOLERANCE,
    ): List<Stroke> {
        if (pathPoints.size < 3) return emptyList()

        // Compute bounding box of the lasso polygon for coarse query
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((px, py) in pathPoints) {
            if (px < minX) minX = px
            if (py < minY) minY = py
            if (px > maxX) maxX = px
            if (py > maxY) maxY = py
        }

        val candidates = spatialIndex.query(
            minX - tolerance,
            minY - tolerance,
            (maxX - minX) + tolerance * 2,
            (maxY - minY) + tolerance * 2,
        )

        // Fine phase: select stroke if ANY of its points are inside the polygon
        return candidates.filter { stroke ->
            stroke.points.any { point ->
                isPointInPolygon(point.x, point.y, pathPoints)
            }
        }
    }

    // ==================== Geometry helpers ====================

    /**
     * Ray-casting point-in-polygon test.
     *
     * Casts a horizontal ray from (px, py) to the right and counts
     * how many polygon edges it crosses. Odd count = inside.
     *
     * Internal visibility so [InkSurfaceView] can reuse it for
     * detecting drag-inside-lasso gestures.
     */
    internal fun isPointInPolygon(
        px: Float, py: Float,
        polygon: List<Pair<Float, Float>>,
    ): Boolean {
        var inside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            val (xi, yi) = polygon[i]
            val (xj, yj) = polygon[j]

            if ((yi > py) != (yj > py) &&
                px < (xj - xi) * (py - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Squared distance from a point (px, py) to the line segment (x0,y0)→(x1,y1).
     */
    private fun pointToSegmentDistanceSq(
        px: Float, py: Float,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
    ): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSq = dx * dx + dy * dy

        if (lengthSq == 0f) {
            // Degenerate segment (point)
            val ex = px - x0
            val ey = py - y0
            return ex * ex + ey * ey
        }

        // Parameter t of the closest point on the segment
        val t = ((px - x0) * dx + (py - y0) * dy) / lengthSq
        val clampedT = t.coerceIn(0f, 1f)

        val closestX = x0 + clampedT * dx
        val closestY = y0 + clampedT * dy
        val ex = px - closestX
        val ey = py - closestY
        return ex * ex + ey * ey
    }

    companion object {
        /** Default eraser hit tolerance in canvas coordinates. */
        const val DEFAULT_TOLERANCE = 15f
    }
}
