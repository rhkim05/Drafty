package com.drafty.core.pdf.exporter

import android.graphics.Bitmap

/**
 * Composites stroke bitmaps onto PDF page backgrounds for annotation blending.
 */
class PdfAnnotationCompositor {

    /**
     * Composites a stroke bitmap over a PDF page bitmap with blending.
     * TODO: Implement bitmap compositing with alpha blending
     */
    fun compositeBitmap(
        pageBackground: Bitmap,
        annotationBitmap: Bitmap,
        blendMode: BlendMode = BlendMode.NORMAL,
    ): Bitmap {
        // TODO: Implementation
        return pageBackground
    }
}

/**
 * Blending modes for annotation compositing.
 */
enum class BlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
}
