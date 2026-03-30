package com.drafty.core.ink.stroke

import com.drafty.core.domain.model.InkPoint
import com.drafty.core.domain.model.Stroke
import com.drafty.core.domain.model.Tool
import java.util.UUID

/**
 * Builds [Stroke] objects from raw input points.
 *
 * Orchestrates the full pipeline: collects InkPoints → applies Bézier
 * smoothing → maps pressure to width → generates render data.
 *
 * Usage:
 * 1. Call [begin] on ACTION_DOWN
 * 2. Call [addPoints] on each ACTION_MOVE
 * 3. Call [finish] on ACTION_UP to get the completed Stroke
 *
 * See: docs/research.md §2-§3
 */
class StrokeBuilder(
    private val smoother: BezierSmoother = BezierSmoother(),
    private val strokePath: StrokePath = StrokePath(),
) {

    private val rawPoints = mutableListOf<InkPoint>()
    private var currentColor: Long = 0xFF000000
    private var currentThickness: Float = 4f
    private var currentTool: Tool = Tool.PEN
    private var isBuilding = false

    /** Whether a stroke is currently being built. */
    val isActive: Boolean get() = isBuilding

    /** Current raw point count for the in-progress stroke. */
    val pointCount: Int get() = rawPoints.size

    /** Read-only access to current raw points (for active stroke rendering). */
    val currentPoints: List<InkPoint> get() = rawPoints

    /**
     * Begins a new stroke with the given parameters.
     *
     * @param firstPoint The initial contact point (ACTION_DOWN)
     * @param color ARGB color for this stroke
     * @param thickness Base thickness (before pressure mapping)
     * @param tool The active drawing tool
     */
    fun begin(
        firstPoint: InkPoint,
        color: Long,
        thickness: Float,
        tool: Tool,
    ) {
        rawPoints.clear()
        rawPoints.add(firstPoint)
        currentColor = color
        currentThickness = thickness
        currentTool = tool
        isBuilding = true
    }

    /**
     * Adds points from a batched move event.
     *
     * @param points Points extracted by [InputProcessor.extractPoints]
     */
    fun addPoints(points: List<InkPoint>) {
        if (!isBuilding) return
        rawPoints.addAll(points)
    }

    /**
     * Finalizes the stroke and returns the completed [Stroke] domain object.
     *
     * @return The finished Stroke, or null if no points were collected
     */
    fun finish(): Stroke? {
        if (!isBuilding || rawPoints.isEmpty()) {
            isBuilding = false
            return null
        }

        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            points = rawPoints.toList(),
            color = currentColor,
            baseThickness = currentThickness,
            tool = currentTool,
            timestamp = rawPoints.last().timestamp,
        )

        rawPoints.clear()
        isBuilding = false
        return stroke
    }

    /**
     * Discards the current in-progress stroke without producing output.
     */
    fun cancel() {
        rawPoints.clear()
        isBuilding = false
    }

    /**
     * Generates smooth Bézier segments from the current raw points.
     * Can be called during drawing for preview rendering.
     */
    fun smoothCurrentPoints(): List<BezierSmoother.BezierSegment> {
        return smoother.smooth(rawPoints)
    }

    /**
     * Generates render points for the current in-progress stroke.
     *
     * @param pressureMapper The pressure mapper matching the current tool
     */
    fun generateRenderPoints(pressureMapper: PressureMapper): List<StrokePath.RenderPoint> {
        val segments = smoother.smooth(rawPoints)
        return strokePath.sampleSegments(segments, pressureMapper)
    }
}
