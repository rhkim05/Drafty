package com.drafty.core.ink.stroke

import android.graphics.Path
import com.drafty.core.domain.model.InkPoint

/**
 * Generates rendering data from smoothed Bézier segments.
 *
 * Uses a circle-strip approach: for each point along the curve, compute
 * the position and the pressure-mapped radius. The renderer then draws
 * filled circles at each sample point.
 *
 * This is simpler and more compatible than quad tessellation, with
 * sufficient quality for a note-taking app.
 *
 * See: docs/research.md §3 (Variable-Width Stroke Rendering)
 */
class StrokePath {

    /**
     * A single sample point along the rendered stroke, with its
     * position and radius (half of the mapped width).
     */
    data class RenderPoint(
        val x: Float,
        val y: Float,
        val radius: Float,
    )

    /**
     * Samples the Bézier segments at a fixed step size to produce
     * [RenderPoint]s for circle-strip drawing.
     *
     * @param segments Smooth Bézier segments from [BezierSmoother]
     * @param pressureMapper Maps pressure values to stroke width
     * @param samplesPerSegment Number of sample points per segment
     * @return List of render points for the circle strip
     */
    fun sampleSegments(
        segments: List<BezierSmoother.BezierSegment>,
        pressureMapper: PressureMapper,
        samplesPerSegment: Int = SAMPLES_PER_SEGMENT,
    ): List<RenderPoint> {
        if (segments.isEmpty()) return emptyList()

        val points = mutableListOf<RenderPoint>()

        for (segment in segments) {
            // Adaptive sampling: compute chord length and use enough
            // samples so each step is at most half the average radius.
            val chordDx = segment.b3x - segment.b0x
            val chordDy = segment.b3y - segment.b0y
            val chordLen = Math.sqrt((chordDx * chordDx + chordDy * chordDy).toDouble()).toFloat()
            val avgPressure = (segment.startPressure + segment.endPressure) / 2f
            val avgWidth = pressureMapper.map(avgPressure)
            val step = (avgWidth * 0.4f).coerceAtLeast(0.5f)
            val adaptiveSamples = maxOf(
                samplesPerSegment,
                (chordLen / step).toInt().coerceAtMost(500),
            )

            for (i in 0..adaptiveSamples) {
                val t = i.toFloat() / adaptiveSamples

                // Cubic Bézier evaluation: B(t) = (1-t)³B0 + 3(1-t)²tB1 + 3(1-t)t²B2 + t³B3
                val oneMinusT = 1f - t
                val omt2 = oneMinusT * oneMinusT
                val omt3 = omt2 * oneMinusT
                val t2 = t * t
                val t3 = t2 * t

                val x = omt3 * segment.b0x +
                    3f * omt2 * t * segment.b1x +
                    3f * oneMinusT * t2 * segment.b2x +
                    t3 * segment.b3x

                val y = omt3 * segment.b0y +
                    3f * omt2 * t * segment.b1y +
                    3f * oneMinusT * t2 * segment.b2y +
                    t3 * segment.b3y

                // Linearly interpolate pressure along the segment
                val pressure = lerp(segment.startPressure, segment.endPressure, t)
                val width = pressureMapper.map(pressure)

                points.add(RenderPoint(x, y, width / 2f))
            }
        }

        // De-duplicate adjacent points that are too close together
        return deduplicatePoints(points)
    }

    /**
     * Generates a [Path] from raw ink points (no Bézier smoothing).
     * Used as a fast path for the active stroke being drawn in real-time.
     */
    fun generateQuickPath(points: List<InkPoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path

        path.moveTo(points[0].x, points[0].y)

        if (points.size == 1) {
            // Single point — draw a tiny line so it's visible
            path.lineTo(points[0].x + 0.1f, points[0].y)
            return path
        }

        // Use quadratic Bézier through midpoints for quick smoothing
        for (i in 0 until points.size - 1) {
            val midX = (points[i].x + points[i + 1].x) / 2f
            val midY = (points[i].y + points[i + 1].y) / 2f
            path.quadTo(points[i].x, points[i].y, midX, midY)
        }

        // Line to the last point
        val last = points.last()
        path.lineTo(last.x, last.y)

        return path
    }

    /**
     * Removes adjacent render points that are closer than the minimum
     * distance to avoid overdraw.
     */
    private fun deduplicatePoints(points: List<RenderPoint>): List<RenderPoint> {
        if (points.size <= 1) return points

        val result = mutableListOf(points[0])
        for (i in 1 until points.size) {
            val prev = result.last()
            val curr = points[i]
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            val dist = dx * dx + dy * dy
            // Keep if distance exceeds half the average radius (avoid gaps)
            val minDist = (prev.radius + curr.radius) * 0.25f
            if (dist > minDist * minDist) {
                result.add(curr)
            }
        }
        // Always include the last point
        if (result.last() != points.last()) {
            result.add(points.last())
        }
        return result
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    companion object {
        /** Default number of sample points per Bézier segment. */
        const val SAMPLES_PER_SEGMENT = 8
    }
}
