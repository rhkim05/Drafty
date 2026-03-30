package com.drafty.core.ink.stroke

import com.drafty.core.domain.model.InkPoint

/**
 * Smooths raw stylus input points using Catmull-Rom spline interpolation,
 * then converts to cubic Bézier control points for rendering.
 *
 * The conversion formula (tangent-based):
 * For Catmull-Rom points p0, p1, p2, p3, the segment p1→p2 becomes
 * a cubic Bézier with control points:
 *   B0 = p1
 *   B1 = p1 + (p2 - p0) / 6
 *   B2 = p2 - (p3 - p1) / 6
 *   B3 = p2
 *
 * See: docs/research.md §2 (Catmull-Rom to Cubic Bézier Conversion)
 */
class BezierSmoother {

    /**
     * A cubic Bézier segment with four control points, plus interpolated
     * pressure/tilt at the start and end.
     */
    data class BezierSegment(
        val b0x: Float, val b0y: Float,
        val b1x: Float, val b1y: Float,
        val b2x: Float, val b2y: Float,
        val b3x: Float, val b3y: Float,
        val startPressure: Float,
        val endPressure: Float,
        val startTilt: Float = 0f,
        val endTilt: Float = 0f,
    )

    /**
     * Converts a list of raw [InkPoint]s into smooth [BezierSegment]s.
     *
     * Uses Catmull-Rom → Bézier conversion. Handles edge cases with
     * phantom points at the start and end.
     *
     * @param points Raw input points (minimum 2 for any output)
     * @return List of Bézier segments connecting consecutive points
     */
    fun smooth(points: List<InkPoint>): List<BezierSegment> {
        if (points.size < 2) return emptyList()

        // For exactly 2 points, produce a linear segment
        if (points.size == 2) {
            val p0 = points[0]
            val p1 = points[1]
            return listOf(
                BezierSegment(
                    b0x = p0.x, b0y = p0.y,
                    b1x = lerp(p0.x, p1.x, 1f / 3f),
                    b1y = lerp(p0.y, p1.y, 1f / 3f),
                    b2x = lerp(p0.x, p1.x, 2f / 3f),
                    b2y = lerp(p0.y, p1.y, 2f / 3f),
                    b3x = p1.x, b3y = p1.y,
                    startPressure = p0.pressure,
                    endPressure = p1.pressure,
                    startTilt = p0.tilt,
                    endTilt = p1.tilt,
                )
            )
        }

        val segments = mutableListOf<BezierSegment>()

        for (i in 0 until points.size - 1) {
            // Catmull-Rom requires 4 points: p0, p1, p2, p3
            // The segment goes from p1 to p2.
            // Edge cases: phantom points at start and end.
            val p0 = points[maxOf(0, i - 1)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[minOf(points.size - 1, i + 2)]

            // Tangent vectors
            val t1x = (p2.x - p0.x) / 2f
            val t1y = (p2.y - p0.y) / 2f
            val t2x = (p3.x - p1.x) / 2f
            val t2y = (p3.y - p1.y) / 2f

            // Bézier control points
            val b1x = p1.x + t1x / 3f
            val b1y = p1.y + t1y / 3f
            val b2x = p2.x - t2x / 3f
            val b2y = p2.y - t2y / 3f

            segments.add(
                BezierSegment(
                    b0x = p1.x, b0y = p1.y,
                    b1x = b1x, b1y = b1y,
                    b2x = b2x, b2y = b2y,
                    b3x = p2.x, b3y = p2.y,
                    startPressure = p1.pressure,
                    endPressure = p2.pressure,
                    startTilt = p1.tilt,
                    endTilt = p2.tilt,
                )
            )
        }

        return segments
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
