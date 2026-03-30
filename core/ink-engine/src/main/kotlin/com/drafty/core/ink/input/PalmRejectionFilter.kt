package com.drafty.core.ink.input

import android.os.Build
import android.view.MotionEvent

/**
 * Filters palm and finger touches when a stylus is active.
 *
 * State machine that tracks pointer IDs and their tool types. Only
 * stylus pointers are accepted for drawing. Palm and finger pointers
 * are rejected.
 *
 * See: docs/research.md §5 (Palm Rejection)
 */
class PalmRejectionFilter {

    /** Tool types for tracked pointers. */
    private val pointerToolTypes = mutableMapOf<Int, Int>()

    /** Active stylus pointer IDs that are drawing. */
    private val activeStylusPointers = mutableSetOf<Int>()

    /** Whether any stylus is currently active (down). */
    val isStylusActive: Boolean
        get() = activeStylusPointers.isNotEmpty()

    /** The primary active stylus pointer ID, or -1 if none. */
    val activeStylusPointerId: Int
        get() = activeStylusPointers.firstOrNull() ?: -1

    /**
     * Filters a [MotionEvent] and returns the result.
     *
     * @return [FilterResult] indicating whether the event should be
     *         processed for drawing, used for gestures, or ignored.
     */
    fun filter(event: MotionEvent): FilterResult {
        // Check FLAG_CANCELED on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (event.flags and MotionEvent.FLAG_CANCELED != 0) {
                reset()
                return FilterResult.CANCELED
            }
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event)

            MotionEvent.ACTION_MOVE -> handleMove(event)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)

            MotionEvent.ACTION_CANCEL -> {
                reset()
                FilterResult.CANCELED
            }

            else -> FilterResult.IGNORED
        }
    }

    private fun handlePointerDown(event: MotionEvent): FilterResult {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        val toolType = event.getToolType(actionIndex)

        pointerToolTypes[pointerId] = toolType

        return when (toolType) {
            MotionEvent.TOOL_TYPE_STYLUS -> {
                activeStylusPointers.add(pointerId)
                FilterResult.ACCEPT_DRAW
            }
            MotionEvent.TOOL_TYPE_FINGER -> {
                if (isStylusActive) FilterResult.IGNORED
                else FilterResult.ACCEPT_GESTURE
            }
            MotionEvent.TOOL_TYPE_ERASER -> {
                activeStylusPointers.add(pointerId)
                FilterResult.ACCEPT_ERASE
            }
            else -> FilterResult.IGNORED
        }
    }

    private fun handleMove(event: MotionEvent): FilterResult {
        if (activeStylusPointers.isNotEmpty()) {
            return FilterResult.ACCEPT_DRAW
        }

        for (i in 0 until event.pointerCount) {
            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER) {
                return FilterResult.ACCEPT_GESTURE
            }
        }

        return FilterResult.IGNORED
    }

    private fun handlePointerUp(event: MotionEvent): FilterResult {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        val wasStylusActive = activeStylusPointers.remove(pointerId)
        pointerToolTypes.remove(pointerId)

        return if (wasStylusActive) FilterResult.ACCEPT_DRAW else FilterResult.IGNORED
    }

    /** Clears all tracked state. */
    fun reset() {
        pointerToolTypes.clear()
        activeStylusPointers.clear()
    }

    /** Result of filtering a [MotionEvent]. */
    enum class FilterResult {
        /** Event is from a stylus — process for drawing. */
        ACCEPT_DRAW,
        /** Event is from the stylus eraser end. */
        ACCEPT_ERASE,
        /** Event is a finger gesture (pan/zoom) — not drawing. */
        ACCEPT_GESTURE,
        /** Event was canceled (palm detected, FLAG_CANCELED). */
        CANCELED,
        /** Event should be ignored (palm touch, etc.). */
        IGNORED,
    }
}
