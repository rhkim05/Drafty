package com.drafty.core.ink.renderer

import com.drafty.core.domain.model.BoundingBox
import com.drafty.core.domain.model.CanvasMode

/**
 * Snapshot of the current viewport state.
 */
data class ViewportState(
    val zoomLevel: Float = 1.0f,
    val panOffsetX: Float = 0.0f,
    val panOffsetY: Float = 0.0f,
)

/**
 * Manages zoom level, pan offset, and coordinate transforms for the canvas.
 *
 * Owns the affine transform between canvas coordinates (where strokes live)
 * and screen coordinates (where pixels are drawn).
 *
 * Transform: `screen = (canvas - panOffset) × zoom`  (conceptually)
 * Rendering: `canvas.translate(panOffsetX, panOffsetY); canvas.scale(zoom, zoom)`
 *
 * See: docs/research.md — Phase 2 §1 (ViewportManager)
 */
class ViewportManager {

    // --- Current state ---
    var zoom: Float = 1.0f
        private set
    var panOffsetX: Float = 0.0f
        private set
    var panOffsetY: Float = 0.0f
        private set

    // --- Canvas mode (affects clamping behavior) ---
    var canvasMode: CanvasMode = CanvasMode.PAGINATED
    var pageWidth: Float = DEFAULT_PAGE_WIDTH
    var pageHeight: Float = DEFAULT_PAGE_HEIGHT

    // --- Screen dimensions (set by InkSurfaceView on surface change) ---
    var screenWidth: Float = 0f
    var screenHeight: Float = 0f

    /** Snapshot of the current viewport state. */
    val state: ViewportState
        get() = ViewportState(zoom, panOffsetX, panOffsetY)

    // ==================== Zoom ====================

    /**
     * Sets the zoom level, clamped to mode-appropriate bounds.
     */
    fun setZoomLevel(zoomLevel: Float) {
        zoom = zoomLevel.coerceIn(minZoom, maxZoom)
    }

    /**
     * Applies a scale factor at a focal point (for pinch-to-zoom).
     *
     * Adjusts pan offset so that the focal point remains fixed on screen.
     * Formula: `newPan = focal - (focal - pan) × (newZoom / oldZoom)`
     *
     * @param scaleFactor Multiplicative scale factor (e.g., 1.05 for 5% zoom in)
     * @param focalScreenX Focal point x in screen coordinates
     * @param focalScreenY Focal point y in screen coordinates
     */
    fun scaleAtFocalPoint(scaleFactor: Float, focalScreenX: Float, focalScreenY: Float) {
        val oldZoom = zoom
        val newZoom = (oldZoom * scaleFactor).coerceIn(minZoom, maxZoom)
        if (newZoom == oldZoom) return

        val ratio = newZoom / oldZoom
        panOffsetX = focalScreenX - (focalScreenX - panOffsetX) * ratio
        panOffsetY = focalScreenY - (focalScreenY - panOffsetY) * ratio

        zoom = newZoom
        clampPan()
    }

    /**
     * Resets zoom to 1.0 and centers the page on screen.
     */
    fun resetZoom() {
        zoom = 1.0f
        centerPage()
    }

    // ==================== Pan ====================

    /**
     * Sets the pan offset directly.
     */
    fun setPanOffset(offsetX: Float, offsetY: Float) {
        panOffsetX = offsetX
        panOffsetY = offsetY
        clampPan()
    }

    /**
     * Applies a pan delta (for drag gestures).
     */
    fun panBy(deltaX: Float, deltaY: Float) {
        panOffsetX += deltaX
        panOffsetY += deltaY
        clampPan()
    }

    /**
     * Centers the page on screen.
     */
    fun centerPage() {
        if (screenWidth <= 0 || screenHeight <= 0) return
        panOffsetX = (screenWidth - pageWidth * zoom) / 2f
        panOffsetY = (screenHeight - pageHeight * zoom) / 2f
        clampPan()
    }

    // ==================== Coordinate transforms ====================

    /**
     * Converts screen coordinates to canvas coordinates.
     * Used for mapping touch input to stroke positions.
     */
    fun screenToCanvas(screenX: Float, screenY: Float): Pair<Float, Float> {
        val canvasX = (screenX - panOffsetX) / zoom
        val canvasY = (screenY - panOffsetY) / zoom
        return canvasX to canvasY
    }

    /**
     * Converts canvas coordinates to screen coordinates.
     */
    fun canvasToScreen(canvasX: Float, canvasY: Float): Pair<Float, Float> {
        val screenX = canvasX * zoom + panOffsetX
        val screenY = canvasY * zoom + panOffsetY
        return screenX to screenY
    }

    // ==================== Viewport rect ====================

    /**
     * Returns the visible area in canvas coordinates.
     * Used for viewport culling — only strokes inside this rect need rendering.
     */
    fun getVisibleRect(): BoundingBox {
        if (screenWidth <= 0 || screenHeight <= 0 || zoom <= 0f) {
            return BoundingBox.EMPTY
        }
        val left = -panOffsetX / zoom
        val top = -panOffsetY / zoom
        val right = left + screenWidth / zoom
        val bottom = top + screenHeight / zoom
        return BoundingBox(left, top, right, bottom)
    }

    // ==================== Internal ====================

    private val minZoom: Float
        get() = when (canvasMode) {
            CanvasMode.PAGINATED -> MIN_ZOOM_PAGINATED
            CanvasMode.WHITEBOARD -> MIN_ZOOM_WHITEBOARD
        }

    private val maxZoom: Float
        get() = when (canvasMode) {
            CanvasMode.PAGINATED -> MAX_ZOOM_PAGINATED
            CanvasMode.WHITEBOARD -> MAX_ZOOM_WHITEBOARD
        }

    /**
     * Clamps pan offset so the page stays partially visible in paginated mode.
     * In whiteboard mode, no clamping.
     */
    private fun clampPan() {
        if (canvasMode != CanvasMode.PAGINATED) return
        if (screenWidth <= 0 || screenHeight <= 0) return

        val scaledWidth = pageWidth * zoom
        val scaledHeight = pageHeight * zoom

        // Allow overscroll up to half the screen, but ensure at least part
        // of the page is always visible
        val marginX = screenWidth * OVERSCROLL_FRACTION
        val marginY = screenHeight * OVERSCROLL_FRACTION

        panOffsetX = panOffsetX.coerceIn(
            -(scaledWidth - marginX),
            screenWidth - marginX,
        )
        panOffsetY = panOffsetY.coerceIn(
            -(scaledHeight - marginY),
            screenHeight - marginY,
        )
    }

    companion object {
        // Zoom bounds per mode
        const val MIN_ZOOM_PAGINATED = 0.25f
        const val MAX_ZOOM_PAGINATED = 5.0f
        const val MIN_ZOOM_WHITEBOARD = 0.1f
        const val MAX_ZOOM_WHITEBOARD = 10.0f

        // Default A4 page dimensions (canvas coordinates, ~300 DPI)
        const val DEFAULT_PAGE_WIDTH = 2480f
        const val DEFAULT_PAGE_HEIGHT = 3508f

        // How much overscroll before clamping (fraction of screen size)
        const val OVERSCROLL_FRACTION = 0.25f
    }
}
