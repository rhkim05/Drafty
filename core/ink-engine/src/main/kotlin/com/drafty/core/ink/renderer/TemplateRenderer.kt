package com.drafty.core.ink.renderer

import android.graphics.Canvas
import android.graphics.Paint
import com.drafty.core.domain.model.PaperTemplate
import com.drafty.core.domain.model.TemplateConfig

/**
 * Renders paper template backgrounds (lines, grid, dots, Cornell layout)
 * directly onto a [Canvas].
 *
 * Lines are drawn with hairline stroke width (`1f / zoom`) so they remain
 * crisp and 1px wide on screen regardless of the current zoom level.
 *
 * See: docs/research.md — Phase 2 §5 (Paper Templates)
 */
class TemplateRenderer {

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val dotPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    /**
     * Renders the template onto the canvas.
     *
     * @param canvas Canvas to draw on (already has viewport transform applied)
     * @param config Template configuration
     * @param pageWidth Page width in canvas coordinates
     * @param pageHeight Page height in canvas coordinates
     * @param zoom Current zoom level (for hairline width calculation)
     */
    fun render(
        canvas: Canvas,
        config: TemplateConfig,
        pageWidth: Float,
        pageHeight: Float,
        zoom: Float,
    ) {
        val hairlineWidth = (1f / zoom).coerceAtLeast(0.25f)

        when (config.type) {
            PaperTemplate.BLANK -> { /* Nothing to draw */ }
            PaperTemplate.LINED -> renderLined(canvas, config, pageWidth, pageHeight, hairlineWidth)
            PaperTemplate.GRID -> renderGrid(canvas, config, pageWidth, pageHeight, hairlineWidth)
            PaperTemplate.DOTTED -> renderDotted(canvas, config, pageWidth, pageHeight, hairlineWidth, zoom)
            PaperTemplate.CORNELL -> renderCornell(canvas, config, pageWidth, pageHeight, hairlineWidth)
        }
    }

    // ==================== Template renderers ====================

    private fun renderLined(
        canvas: Canvas,
        config: TemplateConfig,
        pageWidth: Float,
        pageHeight: Float,
        hairlineWidth: Float,
    ) {
        linePaint.color = config.lineColor.toInt()
        linePaint.strokeWidth = hairlineWidth

        // Horizontal rules
        var y = config.lineSpacing
        while (y < pageHeight) {
            canvas.drawLine(0f, y, pageWidth, y, linePaint)
            y += config.lineSpacing
        }

        // Optional margin line (red vertical line on the left)
        if (config.marginX > 0) {
            linePaint.color = config.marginColor.toInt()
            canvas.drawLine(config.marginX, 0f, config.marginX, pageHeight, linePaint)
        }
    }

    private fun renderGrid(
        canvas: Canvas,
        config: TemplateConfig,
        pageWidth: Float,
        pageHeight: Float,
        hairlineWidth: Float,
    ) {
        linePaint.color = config.lineColor.toInt()
        linePaint.strokeWidth = hairlineWidth

        // Horizontal lines
        var y = config.lineSpacing
        while (y < pageHeight) {
            canvas.drawLine(0f, y, pageWidth, y, linePaint)
            y += config.lineSpacing
        }

        // Vertical lines
        var x = config.lineSpacing
        while (x < pageWidth) {
            canvas.drawLine(x, 0f, x, pageHeight, linePaint)
            x += config.lineSpacing
        }
    }

    private fun renderDotted(
        canvas: Canvas,
        config: TemplateConfig,
        pageWidth: Float,
        pageHeight: Float,
        hairlineWidth: Float,
        zoom: Float,
    ) {
        dotPaint.color = config.lineColor.toInt()
        // Dot radius scales inversely with zoom to stay visually consistent
        val dotRadius = (config.dotRadius / zoom).coerceAtLeast(0.5f)

        var y = config.lineSpacing
        while (y < pageHeight) {
            var x = config.lineSpacing
            while (x < pageWidth) {
                canvas.drawCircle(x, y, dotRadius, dotPaint)
                x += config.lineSpacing
            }
            y += config.lineSpacing
        }
    }

    private fun renderCornell(
        canvas: Canvas,
        config: TemplateConfig,
        pageWidth: Float,
        pageHeight: Float,
        hairlineWidth: Float,
    ) {
        linePaint.color = config.lineColor.toInt()
        linePaint.strokeWidth = hairlineWidth * 1.5f // slightly thicker for region dividers

        val headerY = pageHeight * TemplateConfig.CORNELL_HEADER_FRACTION
        val cueX = pageWidth * TemplateConfig.CORNELL_CUE_FRACTION
        val summaryY = pageHeight * (1f - TemplateConfig.CORNELL_SUMMARY_FRACTION)

        // Header divider (horizontal line at top)
        canvas.drawLine(0f, headerY, pageWidth, headerY, linePaint)

        // Cue column divider (vertical line on the left, below header)
        canvas.drawLine(cueX, headerY, cueX, summaryY, linePaint)

        // Summary divider (horizontal line near bottom)
        canvas.drawLine(0f, summaryY, pageWidth, summaryY, linePaint)

        // Add ruled lines in the notes area (between header and summary, right of cue)
        linePaint.strokeWidth = hairlineWidth
        var y = headerY + config.lineSpacing
        while (y < summaryY) {
            canvas.drawLine(cueX, y, pageWidth, y, linePaint)
            y += config.lineSpacing
        }
    }
}
