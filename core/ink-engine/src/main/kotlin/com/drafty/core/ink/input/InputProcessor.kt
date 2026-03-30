package com.drafty.core.ink.input

import android.view.MotionEvent
import com.drafty.core.domain.model.InkPoint

/**
 * Converts [MotionEvent] streams into [InkPoint] sequences.
 *
 * Handles batched historical events to capture all stylus samples between
 * frames — critical for smooth, gap-free strokes.
 *
 * See: docs/research.md §1 (MotionEvent for Stylus Input)
 */
class InputProcessor {

    /**
     * Extracts all [InkPoint]s from a move event, including historical samples.
     *
     * @param event The MotionEvent (expected ACTION_MOVE)
     * @param pointerIndex The pointer index to extract from
     * @return Ordered list of InkPoints (historical first, then current)
     */
    fun extractPoints(event: MotionEvent, pointerIndex: Int): List<InkPoint> {
        val points = mutableListOf<InkPoint>()

        // Process all historical (batched) samples first
        val historySize = event.historySize
        for (h in 0 until historySize) {
            points.add(
                InkPoint(
                    x = event.getHistoricalX(pointerIndex, h),
                    y = event.getHistoricalY(pointerIndex, h),
                    pressure = event.getHistoricalPressure(pointerIndex, h)
                        .coerceIn(0f, 1f),
                    tilt = getHistoricalTilt(event, pointerIndex, h),
                    timestamp = event.getHistoricalEventTime(h),
                )
            )
        }

        // Then the current sample
        points.add(extractCurrentPoint(event, pointerIndex))

        return points
    }

    /**
     * Extracts a single [InkPoint] from the current event position.
     * Used for ACTION_DOWN and ACTION_UP.
     */
    fun extractCurrentPoint(event: MotionEvent, pointerIndex: Int): InkPoint {
        return InkPoint(
            x = event.getX(pointerIndex),
            y = event.getY(pointerIndex),
            pressure = event.getPressure(pointerIndex).coerceIn(0f, 1f),
            tilt = getTilt(event, pointerIndex),
            timestamp = event.eventTime,
        )
    }

    /**
     * Returns the tilt angle in radians from the MotionEvent.
     * Returns 0 if tilt is not available.
     */
    private fun getTilt(event: MotionEvent, pointerIndex: Int): Float {
        return event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
    }

    private fun getHistoricalTilt(
        event: MotionEvent,
        pointerIndex: Int,
        historicalIndex: Int,
    ): Float {
        return event.getHistoricalAxisValue(
            MotionEvent.AXIS_TILT,
            pointerIndex,
            historicalIndex,
        )
    }
}
