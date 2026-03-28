package com.tabletnoteapp.canvas.pdf

/**
 * Manages the transform state (scale, scroll, translation) and coordinate conversions
 * for the PDF document view.
 *
 * Coordinate system (logical = scale 1.0 document coordinates):
 *   screen_x = logical_x * scale + translateX
 *   screen_y = logical_y * scale - scrollY        (scrollY in screen pixels)
 *
 * Strokes are stored in logical document coordinates.
 * scrollY and translateX are in screen pixels.
 */
class PdfTransformManager {

    var scale      = 1f
    var scrollY    = 0f
    var translateX = 0f

    private val minScale = 0.85f
    private val maxScale = 4f

    // Set by owning view
    var viewWidth  = 0
    var viewHeight = 0

    var totalDocHeight = 0f

    val pageHeights  = mutableListOf<Int>()
    val pageYOffsets = mutableListOf<Float>()

    fun maxScrollY() = (totalDocHeight * scale - viewHeight).coerceAtLeast(0f)

    fun constrain() {
        scale   = scale.coerceIn(minScale, maxScale)
        scrollY = scrollY.coerceIn(0f, maxScrollY())
        translateX = if (scale <= 1f) {
            viewWidth * (1f - scale) / 2f
        } else {
            val minTx = -(viewWidth * (scale - 1f))
            translateX.coerceIn(minTx, 0f)
        }
    }

    fun toLogicalX(sx: Float) = (sx - translateX) / scale
    fun toLogicalY(sy: Float) = (sy + scrollY) / scale

    fun logVisTop()    = scrollY / scale
    fun logVisBottom() = (scrollY + viewHeight) / scale

    fun layoutPages(viewWidth: Int, aspects: List<Float>, pageGapPx: Int) {
        this.viewWidth = viewWidth
        pageHeights.clear()
        pageYOffsets.clear()
        var y = 0f
        for (aspect in aspects) {
            val h = (viewWidth * aspect).toInt()
            pageHeights.add(h)
            pageYOffsets.add(y)
            y += h + pageGapPx
        }
        totalDocHeight = y
    }

    fun getCurrentPage(): Int {
        val logMidY = (scrollY + viewHeight / 2f) / scale
        for (i in pageYOffsets.indices.reversed()) {
            if (logMidY >= pageYOffsets[i]) return i
        }
        return 0
    }

    fun scrollToPage(page: Int) {
        if (pageYOffsets.isEmpty()) return
        val idx = (page - 1).coerceIn(0, pageYOffsets.size - 1)
        scrollY = (pageYOffsets[idx] * scale).coerceIn(0f, maxScrollY())
    }

    fun reset() {
        scale = 1f
        scrollY = 0f
        translateX = 0f
        totalDocHeight = 0f
        pageHeights.clear()
        pageYOffsets.clear()
    }

    /**
     * Apply a pinch-zoom scale factor around a focal point.
     * Returns true if the scale actually changed.
     */
    fun applyPinchZoom(focusX: Float, focusY: Float, scaleFactor: Float): Boolean {
        val newScale = (scale * scaleFactor).coerceIn(minScale, maxScale)
        val f = newScale / scale
        if (f == 1f) return false
        translateX = (focusX * (1 - f) + translateX * f)
        scrollY    = (focusY * (f - 1) + scrollY * f)
        scale      = newScale
        constrain()
        return true
    }
}
