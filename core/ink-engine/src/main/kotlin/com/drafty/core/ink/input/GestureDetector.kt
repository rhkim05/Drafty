package com.drafty.core.ink.input

import android.content.Context
import android.view.GestureDetector as AndroidGestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.OverScroller
import com.drafty.core.ink.renderer.ViewportManager

/**
 * Detects two-finger zoom and pan gestures for canvas navigation.
 *
 * Composes Android's [ScaleGestureDetector] for pinch-to-zoom with
 * manual two-pointer tracking for panning, and [OverScroller] for
 * fling momentum.
 *
 * See: docs/research.md — Phase 2 §2 (GestureDetector)
 */
class GestureDetector(
    context: Context,
    private val viewportManager: ViewportManager,
    private val onViewportChanged: () -> Unit,
) {
    // --- Scale gesture (pinch-to-zoom) ---
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    // --- Tap gesture (double-tap to reset zoom) ---
    private val tapDetector = AndroidGestureDetector(context, TapListener())

    // --- Fling scroller ---
    private val scroller = OverScroller(context)

    // --- Pan tracking ---
    private var isPanning = false
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var activePointerCount = 0

    /** Whether a gesture is currently in progress. */
    val isGestureActive: Boolean
        get() = isPanning || scaleDetector.isInProgress

    /**
     * Processes a motion event for zoom/pan gestures.
     *
     * Should only receive events where pointerCount >= 2 or
     * single-finger events in pan mode (when stylus-only drawing is active).
     *
     * @return true if the event was consumed
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = false

        // Feed to scale detector
        handled = scaleDetector.onTouchEvent(event) || handled

        // Feed to tap detector (double-tap to reset)
        handled = tapDetector.onTouchEvent(event) || handled

        // Manual pan tracking
        handled = handlePan(event) || handled

        return handled
    }

    /**
     * Should be called each frame to apply fling momentum.
     * @return true if the fling is still active and another frame is needed
     */
    fun computeFling(): Boolean {
        if (scroller.computeScrollOffset()) {
            viewportManager.setPanOffset(
                scroller.currX.toFloat(),
                scroller.currY.toFloat(),
            )
            onViewportChanged()
            return !scroller.isFinished
        }
        return false
    }

    /** Stops any active fling animation. */
    fun stopFling() {
        scroller.forceFinished(true)
    }

    // ==================== Pan handling ====================

    private fun handlePan(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    isPanning = true
                    stopFling()
                    updatePanAnchor(event)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isPanning && event.pointerCount >= 2) {
                    val midX = getMidpointX(event)
                    val midY = getMidpointY(event)
                    val dx = midX - lastPanX
                    val dy = midY - lastPanY

                    viewportManager.panBy(dx, dy)
                    onViewportChanged()

                    lastPanX = midX
                    lastPanY = midY
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    // Transitioning to single-pointer — end pan
                    isPanning = false

                    // TODO: Compute fling velocity and start fling if fast enough
                    // For now, just stop
                    return true
                } else {
                    // Still multi-touch, update anchor
                    updatePanAnchor(event)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isPanning = false
            }
        }
        return false
    }

    private fun updatePanAnchor(event: MotionEvent) {
        lastPanX = getMidpointX(event)
        lastPanY = getMidpointY(event)
        activePointerCount = event.pointerCount
    }

    // ==================== Scale listener ====================

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            stopFling()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            viewportManager.scaleAtFocalPoint(
                scaleFactor = detector.scaleFactor,
                focalScreenX = detector.focusX,
                focalScreenY = detector.focusY,
            )
            onViewportChanged()
            return true
        }
    }

    // ==================== Tap listener (double-tap) ====================

    private inner class TapListener : AndroidGestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            stopFling()
            viewportManager.resetZoom()
            onViewportChanged()
            return true
        }
    }

    // ==================== Helpers ====================

    private fun getMidpointX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getX(i)
        }
        return sum / event.pointerCount
    }

    private fun getMidpointY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getY(i)
        }
        return sum / event.pointerCount
    }
}
