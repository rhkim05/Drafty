package com.tabletnoteapp.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller

enum class OverscrollState { NONE, TOP_READY, BOTTOM_READY }

class PagedScrollEngine(context: Context, private val host: View) {

    val pageHeights = mutableListOf<Int>()
    val pageYOffsets = mutableListOf<Float>()
    val pageAspects = mutableListOf<Float>()
    var totalDocHeight = 0f

    var pageGapPx = (20 * context.resources.displayMetrics.density).toInt()
        private set

    var scale = 1f
    var scrollY = 0f
    var translateX = 0f
    private val minScale = 0.85f

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var isScrollbarDragging = false
    private var scrollbarThumbOffset = 0f
    private val scrollbarTouchZoneW = 32f * context.resources.displayMetrics.density
    private val dp = context.resources.displayMetrics.density

    private val scrollbarActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 120, 120, 120)
    }
    private val scrollbarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 200, 200, 200)
    }

    private val overscrollLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 150, 150, 150)
        textSize = 14f * dp
        textAlign = Paint.Align.CENTER
    }

    private val overscrollThresholdPx = 80f * dp
    private var overscrollDragDist = 0f
    private var isOverscrolling = false
    private var overscrollFromTop = false

    private var lastReportedPage = -1
    var onPageChanged: ((Int) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val newScale = (scale * d.scaleFactor).coerceIn(minScale, 4f)
                val f = newScale / scale
                if (f == 1f) return true
                translateX = (d.focusX * (1 - f) + translateX * f)
                scrollY = (d.focusY * (f - 1) + scrollY * f)
                scale = newScale
                constrain()
                notifyPageChanged()
                host.invalidate()
                return true
            }
        })

    fun maxScrollY() = (totalDocHeight * scale - host.height).coerceAtLeast(0f)

    private fun centeredTranslateX() = host.width * (1f - scale) / 2f

    fun constrain() {
        scale = scale.coerceIn(minScale, 4f)
        scrollY = scrollY.coerceIn(0f, maxScrollY())
        translateX = if (scale <= 1f) {
            centeredTranslateX()
        } else {
            val minTx = -(host.width * (scale - 1f))
            translateX.coerceIn(minTx, 0f)
        }
    }

    fun toLogicalX(sx: Float) = (sx - translateX) / scale
    fun toLogicalY(sy: Float) = (sy + scrollY) / scale
    fun currentScale() = scale

    fun initPages(count: Int, aspects: List<Float>) {
        pageAspects.clear(); pageAspects.addAll(aspects)
        reLayoutPages(host.width)
    }

    fun reLayoutPages(viewWidth: Int) {
        if (pageAspects.isEmpty()) return
        val gap = pageGapPx
        pageHeights.clear(); pageYOffsets.clear()
        var y = 0f
        for (aspect in pageAspects) {
            val h = (viewWidth * aspect).toInt()
            pageHeights.add(h); pageYOffsets.add(y); y += h + gap
        }
        totalDocHeight = y
        scrollY = scrollY.coerceIn(0f, maxScrollY())
        constrain()
    }

    fun addPage(aspectRatio: Float) {
        pageAspects.add(aspectRatio)
        val h = (host.width * aspectRatio).toInt()
        pageHeights.add(h)
        pageYOffsets.add(totalDocHeight)
        totalDocHeight += h + pageGapPx
    }

    fun prependPage(aspectRatio: Float): Float {
        val h = (host.width * aspectRatio).toInt()
        val offset = h.toFloat() + pageGapPx
        pageAspects.add(0, aspectRatio)
        pageHeights.add(0, h)
        for (i in pageYOffsets.indices) {
            pageYOffsets[i] += offset
        }
        pageYOffsets.add(0, 0f)
        totalDocHeight += offset
        scrollY += offset * scale
        constrain()
        return offset
    }

    fun applyTransform(canvas: Canvas) {
        canvas.translate(translateX, -scrollY)
        canvas.scale(scale, scale)
    }

    fun drawScrollbar(canvas: Canvas) {
        val totalH = totalDocHeight * scale
        if (totalH <= host.height) return
        val thumbH = (host.height.toFloat() * host.height / totalH).coerceIn(32f, host.height.toFloat())
        val thumbY = scrollY / (totalH - host.height) * (host.height - thumbH)
        val barW = if (isScrollbarDragging) 7f * dp else 4f * dp
        val barX = host.width - barW - 3f * dp
        val paint = if (isScrollbarDragging) scrollbarActivePaint else scrollbarPaint
        canvas.drawRoundRect(barX, thumbY, barX + barW, thumbY + thumbH, barW / 2, barW / 2, paint)
    }

    fun drawOverscrollIndicator(canvas: Canvas) {
        if (!isOverscrolling) return
        val alpha = (overscrollDragDist / overscrollThresholdPx).coerceIn(0f, 1f)
        overscrollLabelPaint.alpha = (alpha * 220).toInt()
        val label = if (overscrollFromTop) "\u2191 New page" else "\u2193 New page"
        val x = host.width / 2f
        val y = if (overscrollFromTop) 30f * dp else host.height - 16f * dp
        canvas.drawText(label, x, y, overscrollLabelPaint)
    }

    fun overscrollState(): OverscrollState {
        if (!isOverscrolling || overscrollDragDist < overscrollThresholdPx) return OverscrollState.NONE
        return if (overscrollFromTop) OverscrollState.TOP_READY else OverscrollState.BOTTOM_READY
    }

    fun isOnScrollbar(x: Float, y: Float = -1f): Boolean {
        val totalH = totalDocHeight * scale
        if (totalH <= host.height) return false
        if (x < host.width - scrollbarTouchZoneW) return false
        if (y < 0f) return true
        val thumbH = (host.height.toFloat() * host.height / totalH).coerceIn(32f, host.height.toFloat())
        val thumbY = scrollY / (totalH - host.height) * (host.height - thumbH)
        return y in thumbY..(thumbY + thumbH)
    }

    fun handleScrollbar(event: MotionEvent): Boolean {
        val totalH = totalDocHeight * scale
        if (totalH <= host.height) return false
        val thumbH = (host.height.toFloat() * host.height / totalH).coerceIn(32f, host.height.toFloat())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                isScrollbarDragging = true
                host.parent?.requestDisallowInterceptTouchEvent(true)
                val thumbY = scrollY / (totalH - host.height) * (host.height - thumbH)
                scrollbarThumbOffset = event.y - thumbY
                updateScrollFromThumbY(event.y - scrollbarThumbOffset, thumbH, totalH)
            }
            MotionEvent.ACTION_MOVE -> {
                updateScrollFromThumbY(event.y - scrollbarThumbOffset, thumbH, totalH)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScrollbarDragging = false
                host.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        host.invalidate()
        return true
    }

    private fun updateScrollFromThumbY(thumbY: Float, thumbH: Float, totalH: Float) {
        val maxThumbY = host.height - thumbH
        val ratio = (thumbY / maxThumbY).coerceIn(0f, 1f)
        scrollY = ratio * (totalH - host.height)
        notifyPageChanged()
    }

    fun handleScroll(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                lastTouchX = event.x; lastTouchY = event.y; isDragging = false
                isOverscrolling = false; overscrollDragDist = 0f
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress || event.pointerCount > 1) {
                    isOverscrolling = false; overscrollDragDist = 0f
                    return true
                }
                velocityTracker?.addMovement(event)
                val dx = lastTouchX - event.x
                val dy = lastTouchY - event.y
                if (!isDragging && (Math.abs(dy) > touchSlop || Math.abs(dx) > touchSlop)) {
                    isDragging = true; host.parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (isDragging) {
                    val prevScrollY = scrollY
                    scrollY += dy
                    translateX -= dx
                    val maxSY = maxScrollY()

                    if (scrollY < 0f) {
                        isOverscrolling = true; overscrollFromTop = true
                        overscrollDragDist = -scrollY
                        scrollY = 0f
                    } else if (scrollY > maxSY) {
                        isOverscrolling = true; overscrollFromTop = false
                        overscrollDragDist = scrollY - maxSY
                        scrollY = maxSY
                    } else {
                        isOverscrolling = false; overscrollDragDist = 0f
                    }
                    constrain()
                    lastTouchX = event.x; lastTouchY = event.y
                    notifyPageChanged(); host.invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val state = overscrollState()
                isOverscrolling = false; overscrollDragDist = 0f

                if (isDragging && !scaleDetector.isInProgress && state == OverscrollState.NONE) {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = -(velocityTracker?.yVelocity ?: 0f)
                    scroller.fling(0, scrollY.toInt(), 0, vy.toInt(), 0, 0, 0, maxScrollY().toInt())
                    host.postInvalidateOnAnimation()
                }
                velocityTracker?.recycle(); velocityTracker = null
                isDragging = false; host.parent?.requestDisallowInterceptTouchEvent(false)
                host.invalidate()
                return when (state) {
                    OverscrollState.TOP_READY, OverscrollState.BOTTOM_READY -> {
                        true // caller checks overscrollState before we reset
                    }
                    else -> true
                }
            }
        }
        return true
    }

    fun computeScroll(): Boolean {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.currY.toFloat().coerceIn(0f, maxScrollY())
            notifyPageChanged()
            return true
        }
        return false
    }

    fun scrollToPage(page: Int) {
        if (pageYOffsets.isEmpty()) return
        val idx = (page - 1).coerceIn(0, pageYOffsets.size - 1)
        scrollY = (pageYOffsets[idx] * scale).coerceIn(0f, maxScrollY())
        notifyPageChanged(); host.invalidate()
    }

    fun getCurrentPage(): Int {
        val logMidY = (scrollY + host.height / 2f) / scale
        for (i in pageYOffsets.indices.reversed()) { if (logMidY >= pageYOffsets[i]) return i }
        return 0
    }

    fun getPageCount(): Int = pageAspects.size

    private fun notifyPageChanged() {
        val page = getCurrentPage() + 1
        if (page != lastReportedPage) { lastReportedPage = page; onPageChanged?.invoke(page) }
    }

    fun lastOverscrollState(): OverscrollState {
        return overscrollState()
    }

    fun abortAnimation() {
        scroller.abortAnimation()
    }
}
