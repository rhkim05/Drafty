package com.drafty.core.domain.model

/**
 * Configuration for a paper template's visual appearance.
 *
 * All dimensions are in canvas coordinates. Colors are ARGB packed longs.
 * Kept as pure data in `:core:domain` so rendering is deterministic and
 * the config is serializable.
 *
 * See: docs/research.md — Phase 2 §5 (Paper Templates)
 */
data class TemplateConfig(
    val type: PaperTemplate,
    val lineColor: Long = DEFAULT_LINE_COLOR,
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    val marginX: Float = DEFAULT_MARGIN_X,
    val marginColor: Long = DEFAULT_MARGIN_COLOR,
    val dotRadius: Float = DEFAULT_DOT_RADIUS,
) {
    companion object {
        const val DEFAULT_LINE_COLOR = 0xFFCCCCCC.toLong()
        const val DEFAULT_MARGIN_COLOR = 0xFFFF9999.toLong()
        const val DEFAULT_LINE_SPACING = 32f
        const val DEFAULT_MARGIN_X = 100f
        const val DEFAULT_DOT_RADIUS = 1.5f

        /** Cornell note regions as fractions of page dimensions. */
        const val CORNELL_HEADER_FRACTION = 0.08f
        const val CORNELL_CUE_FRACTION = 0.30f
        const val CORNELL_SUMMARY_FRACTION = 0.15f

        /** Returns the default config for a given template type. */
        fun forTemplate(type: PaperTemplate): TemplateConfig = TemplateConfig(type = type)
    }
}
