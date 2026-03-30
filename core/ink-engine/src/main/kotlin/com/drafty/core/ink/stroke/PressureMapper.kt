package com.drafty.core.ink.stroke

import kotlin.math.exp
import kotlin.math.pow

/**
 * Maps stylus pressure [0..1] to stroke width using configurable curves.
 *
 * Supported curves:
 * - **Power**: width = min + pressure^exp × (max - min) — natural for pens
 * - **Sigmoid**: width = min + σ(k·(p-0.5)) × (max - min) — marker-like threshold
 * - **Linear**: width = min + pressure × (max - min)
 *
 * See: docs/research.md §6 (Pressure-to-Width Mapping)
 */
class PressureMapper(
    val minWidth: Float,
    val maxWidth: Float,
    val curveType: CurveType = CurveType.POWER,
    val curveParameter: Float = DEFAULT_PEN_EXPONENT,
) {

    enum class CurveType {
        /** width = min + p^exp × range. Good for pen/brush. */
        LINEAR,
        /** width = min + p^exp × range. Good for pen/brush. */
        POWER,
        /** width = min + σ(k·(p-0.5)) × range. Good for highlighter/marker. */
        SIGMOID,
    }

    private val widthRange = maxWidth - minWidth

    /**
     * Maps a pressure value to a stroke width (diameter).
     *
     * @param pressure Normalized pressure [0..1]
     * @return Stroke width in pixels
     */
    fun map(pressure: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        val normalized = when (curveType) {
            CurveType.LINEAR -> p
            CurveType.POWER -> p.pow(curveParameter)
            CurveType.SIGMOID -> sigmoid(curveParameter * (p - 0.5f))
        }
        return minWidth + normalized * widthRange
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    companion object {
        /** Default exponent for pen tool — biased toward thin strokes. */
        const val DEFAULT_PEN_EXPONENT = 1.8f

        /** Default sigmoid steepness for highlighter. */
        const val DEFAULT_HIGHLIGHTER_STEEPNESS = 5f

        /** Creates a mapper configured for pen/brush drawing. */
        fun pen(
            minWidth: Float = 1.5f,
            maxWidth: Float = 18f,
            exponent: Float = DEFAULT_PEN_EXPONENT,
        ) = PressureMapper(minWidth, maxWidth, CurveType.POWER, exponent)

        /** Creates a mapper configured for highlighter/marker. */
        fun highlighter(
            minWidth: Float = 10f,
            maxWidth: Float = 35f,
            steepness: Float = DEFAULT_HIGHLIGHTER_STEEPNESS,
        ) = PressureMapper(minWidth, maxWidth, CurveType.SIGMOID, steepness)

        /** Creates a mapper configured for eraser. */
        fun eraser(
            minWidth: Float = 20f,
            maxWidth: Float = 60f,
        ) = PressureMapper(minWidth, maxWidth, CurveType.LINEAR, 1f)
    }
}
