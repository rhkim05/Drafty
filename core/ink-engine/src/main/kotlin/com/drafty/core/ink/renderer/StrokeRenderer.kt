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
     * Used when rebuilding the completed-strokes bitmap cache.
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
     * @param canvas Target canvas
     * @param points Current raw input points
     * @param color Stroke color
     * @param thickness Base stroke thickness
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

        // Circle strip for active stroke — use raw points with pressure mapping
        val paint = getPaintForTool(tool)
        paint.color = color.toInt()

        for (point in points) {
            val radius = pressureMapper.map(point.pressure) / 2f
            canvas.drawCircle(point.x, point.y, radius, paint)
        }
    }

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
