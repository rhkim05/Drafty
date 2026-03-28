package com.tabletnoteapp.canvas.pdf

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller

/**
 * Handles touch routing for PdfDrawingView: scrollbar dragging, scroll/fling/pinch,
 * and delegates draw events back to the view.
 */
class PdfTouchRouter(
    context: Context,
    private val transform: PdfTransformManager,
    private val onInvalidate: () -> Unit,
    private val onPageChanged: () -> Unit,
) {
    // ── Scroll / fling ────────────────────────────────────────────────────────

    val scroller       = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var lastTouchX     = 0f
    private var lastTouchY     = 0f
    private var isDragging     = false
    private val touchSlop      = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // ── Scrollbar drag ────────────────────────────────────────────────────────

    var isScrollbarDragging = false
        private set
    private var scrollbarThumbOffset = 0f
    private var scrollbarTouchZoneW  = 32f * context.resources.displayMetrics.density

    // ── Pinch zoom ────────────────────────────────────────────────────────────

    val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                if (transform.applyPinchZoom(d.focusX, d.focusY, d.scaleFactor)) {
                    onPageChanged()
                    onInvalidate()
                }
                return true
            }
        })

    var requestDisallowIntercept: ((Boolean) -> Unit)? = null

    // ── Scrollbar ─────────────────────────────────────────────────────────────

    fun isOnScrollbar(x: Float, y: Float): Boolean {
        val totalH = transform.totalDocHeight * transform.scale
        if (totalH <= transform.viewHeight) return false
        if (x < transform.viewWidth - scrollbarTouchZoneW) return false
        if (y < 0f) return true
        val thumbH = (transform.viewHeight.toFloat() * transform.viewHeight / totalH).coerceIn(32f, transform.viewHeight.toFloat())
        val thumbY = transform.scrollY / (totalH - transform.viewHeight) * (transform.viewHeight - thumbH)
        return y in thumbY..(thumbY + thumbH)
    }

    fun handleScrollbar(event: MotionEvent): Boolean {
        val totalH = transform.totalDocHeight * transform.scale
        if (totalH <= transform.viewHeight) return false
        val thumbH = (transform.viewHeight.toFloat() * transform.viewHeight / totalH).coerceIn(32f, transform.viewHeight.toFloat())

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                isScrollbarDragging = true
                requestDisallowIntercept?.invoke(true)
                val thumbY = transform.scrollY / (totalH - transform.viewHeight) * (transform.viewHeight - thumbH)
                scrollbarThumbOffset = event.y - thumbY
                updateScrollFromThumbY(event.y - scrollbarThumbOffset, thumbH, totalH)
            }
            MotionEvent.ACTION_MOVE -> {
                updateScrollFromThumbY(event.y - scrollbarThumbOffset, thumbH, totalH)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScrollbarDragging = false
                requestDisallowIntercept?.invoke(false)
            }
        }
        onInvalidate()
        return true
    }

    private fun updateScrollFromThumbY(thumbY: Float, thumbH: Float, totalH: Float) {
        val maxThumbY = transform.viewHeight - thumbH
        val ratio = (thumbY / maxThumbY).coerceIn(0f, 1f)
        transform.scrollY = ratio * (totalH - transform.viewHeight)
        onPageChanged()
    }

    // ── Scroll / fling ────────────────────────────────────────────────────────

    fun handleScroll(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                lastTouchX = event.x; lastTouchY = event.y; isDragging = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress || event.pointerCount > 1) return true
                velocityTracker?.addMovement(event)
                val dx = lastTouchX - event.x
                val dy = lastTouchY - event.y
                if (!isDragging && (Math.abs(dy) > touchSlop || Math.abs(dx) > touchSlop)) {
                    isDragging = true; requestDisallowIntercept?.invoke(true)
                }
                if (isDragging) {
                    transform.scrollY    += dy
                    transform.translateX -= dx
                    transform.constrain()
                    lastTouchX = event.x; lastTouchY = event.y
                    onPageChanged(); onInvalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging && !scaleDetector.isInProgress) {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = -(velocityTracker?.yVelocity ?: 0f)
                    scroller.fling(0, transform.scrollY.toInt(), 0, vy.toInt(), 0, 0, 0, transform.maxScrollY().toInt())
                    onInvalidate()
                }
                velocityTracker?.recycle(); velocityTracker = null
                isDragging = false; requestDisallowIntercept?.invoke(false)
            }
        }
        return true
    }

    fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            transform.scrollY = scroller.currY.toFloat().coerceIn(0f, transform.maxScrollY())
            onPageChanged(); onInvalidate()
        }
    }
}
