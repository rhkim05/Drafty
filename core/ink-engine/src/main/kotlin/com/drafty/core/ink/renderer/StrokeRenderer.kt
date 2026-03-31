package com.drafty.core.ink.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.drafty.core.domain.model.InkPoint
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.model.Tool
import com.drafty.core.ink.stroke.BezierSmoother
import com.drafty.core.ink.stroke.PressureMapper
import com.drafty.core.ink.stroke.StrokePath

/**
 * Renders [Stroke] objects to an Android [Canvas] using the circle-strip
 * approach for variable-width, pressure-sensitive strokes.
 *
 * Maintains cached [Paint] objects to avoid allocations in the hot path.
 *
 * See: docs/research.md §3 (Variable-Width Stroke Rendering)
 */
class StrokeRenderer {

    private val smoother = BezierSmoother()
    private val strokePath = StrokePath()

    /** Reusable paint for pen strokes. */
    private val penPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    /** Reusable paint for highlighter strokes (semi-transparent). */
    private val highlighterPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    /** Reusable paint for active stroke quick-path rendering. */
    private val quickPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Renders a completed [Stroke] with full Bézier smoothing and
     * pressure-sensitive width.
     *
     * Completed strokes are stored with canvas-space thickness (screen
     * thickness ÷ zoom at draw time). They are rendered inside the
     * viewport transform, so they scale naturally with zoom — zooming
     * in makes them bigger, zooming out makes them smaller.
     * See: issues.md ISS-017, ISS-018.
     */
    fun renderStroke(canvas: Canvas, stroke: Stroke) {
        val pressureMapper = getPressureMapper(stroke.tool, stroke.baseThickness)
        val segments = smoother.smooth(stroke.points)
        val renderPoints = strokePath.sampleSegments(segments, pressureMapper)

        val paint = getPaintForTool(stroke.tool)
        paint.color = stroke.color.toInt()

        for (point in renderPoints) {
            canvas.drawCircle(point.x, point.y, point.radius, paint)
        }
    }

    /**
     * Renders a list of completed strokes onto a canvas.
     */
    fun renderStrokes(canvas: Canvas, strokes: List<Stroke>) {
        for (stroke in strokes) {
            renderStroke(canvas, stroke)
        }
    }

    /**
     * Renders an in-progress stroke using fast path rendering (no full
     * Bézier smoothing — uses quadratic midpoint approximation instead).
     *
     * This is the fast path for real-time rendering during drawing.
     *
     * @param canvas Target canvas (with viewport transform already applied)
     * @param points Current raw input points
     * @param color Stroke color
     * @param thickness Base stroke thickness in canvas space (already divided
     *   by zoom by the caller so it appears at constant screen size while drawing)
     * @param tool Active tool
     */
    fun renderActiveStroke(
        canvas: Canvas,
        points: List<InkPoint>,
        color: Long,
        thickness: Float,
        tool: Tool,
    ) {
        if (points.isEmpty()) return

        val pressureMapper = getPressureMapper(tool, thickness)

        if (points.size == 1) {
            // Single dot
            val paint = getPaintForTool(tool)
            paint.color = color.toInt()
            val radius = pressureMapper.map(points[0].pressure) / 2f
            canvas.drawCircle(points[0].x, points[0].y, radius, paint)
            return
        }

        // Circle strip for active stroke — interpolate between raw points
        // so fast drawing doesn't produce visible gaps between circles.
        val paint = getPaintForTool(tool)
        paint.color = color.toInt()

        var prevPoint = points[0]
        var prevRadius = pressureMapper.map(prevPoint.pressure) / 2f
        canvas.drawCircle(prevPoint.x, prevPoint.y, prevRadius, paint)

        for (i in 1 until points.size) {
            val curr = points[i]
            val currRadius = pressureMapper.map(curr.pressure) / 2f

            // Compute distance between consecutive points
            val dx = curr.x - prevPoint.x
            val dy = curr.y - prevPoint.y
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            // Step size = half the average radius (ensures circle overlap)
            val avgRadius = (prevRadius + currRadius) / 2f
            val step = (avgRadius * 0.5f).coerceAtLeast(0.5f)

            if (dist > step) {
                // Interpolate circles between prev and curr
                val steps = (dist / step).toInt().coerceAtMost(200)
                for (s in 1..steps) {
                    val t = s.toFloat() / (steps + 1)
                    val ix = prevPoint.x + dx * t
                    val iy = prevPoint.y + dy * t
                    val iRadius = lerp(prevRadius, currRadius, t)
                    canvas.drawCircle(ix, iy, iRadius, paint)
                }
            }

            canvas.drawCircle(curr.x, curr.y, currRadius, paint)
            prevPoint = curr
            prevRadius = currRadius
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * Returns the appropriate [PressureMapper] for the given tool.
     */
    private fun getPressureMapper(tool: Tool, baseThickness: Float): PressureMapper {
        return when (tool) {
            Tool.PEN -> PressureMapper.pen(
                minWidth = baseThickness * 0.3f,
                maxWidth = baseThickness * 2.5f,
            )
            Tool.HIGHLIGHTER -> PressureMapper.highlighter(
                minWidth = baseThickness * 0.8f,
                maxWidth = baseThickness * 1.5f,
            )
            Tool.ERASER -> PressureMapper.eraser(
                minWidth = baseThickness,
                maxWidth = baseThickness * 3f,
            )
            Tool.LASSO -> PressureMapper(
                minWidth = 1.5f,
                maxWidth = 1.5f,
                curveType = PressureMapper.CurveType.LINEAR,
            )
        }
    }

    private fun getPaintForTool(tool: Tool): Paint {
        return when (tool) {
            Tool.HIGHLIGHTER -> highlighterPaint
            else -> penPaint
        }
    }
}
