package com.drafty.core.ink.spatial

import com.drafty.core.ink.stroke.Stroke

/**
 * Performs stroke hit testing for eraser, selection, and interaction detection.
 */
class HitTester {

    /**
     * Tests if a point hits any strokes within a tolerance distance.
     * TODO: Implement point-to-stroke hit testing
     */
    fun hitTest(x: Float, y: Float, tolerance: Float = 10.0f): List<Stroke> {
        // TODO: Implementation
        return emptyList()
    }

    /**
     * Tests if a rectangular region intersects with strokes.
     * TODO: Implement rect-to-stroke intersection testing
     */
    fun intersectsRect(x: Float, y: Float, width: Float, height: Float): List<Stroke> {
        // TODO: Implementation
        return emptyList()
    }

    /**
     * Tests if a line path intersects with strokes (for eraser).
     * TODO: Implement line-to-stroke intersection testing
     */
    fun intersectsPath(pathPoints: List<Pair<Float, Float>>, tolerance: Float): List<Stroke> {
        // TODO: Implementation
        return emptyList()
    }
}
