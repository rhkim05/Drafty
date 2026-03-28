package com.tabletnoteapp.canvas.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.Executors

/**
 * Manages PDF page rendering, caching, and lifecycle.
 * Renders pages off the UI thread and notifies the caller when ready.
 */
class PdfPageCache(
    private val renderWidth: Int,
    private val onPageReady: () -> Unit,
) {
    private val pdfLock = Any()
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private val renderExecutor = Executors.newSingleThreadExecutor()

    private val cache          = HashMap<Int, Bitmap>()
    private val renderingPages = HashSet<Int>()
    private val maxCachedPages = 6

    val pageAspects  = mutableListOf<Float>()   // height/width ratio per page

    val pageCount get() = pageAspects.size

    /** Post a block to the UI thread — must be set by the owning View. */
    var postToUi: (Runnable) -> Unit = {}

    fun open(filePath: String, viewWidth: Int, onLoaded: (pageCount: Int, aspects: List<Float>) -> Unit) {
        cache.values.forEach { it.recycle() }
        cache.clear()
        renderingPages.clear()
        pageAspects.clear()

        renderExecutor.submit {
            synchronized(pdfLock) {
                pdfRenderer?.close()
                fileDescriptor?.close()
                pdfRenderer = null
                fileDescriptor = null
            }
            try {
                val fd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                synchronized(pdfLock) { fileDescriptor = fd; pdfRenderer = renderer }

                val aspects = mutableListOf<Float>()
                for (i in 0 until renderer.pageCount) {
                    synchronized(pdfLock) {
                        val page = renderer.openPage(i)
                        aspects.add(page.height.toFloat() / page.width.toFloat())
                        page.close()
                    }
                }
                postToUi.invoke(Runnable {
                    pageAspects.clear()
                    pageAspects.addAll(aspects)
                    onLoaded(renderer.pageCount, aspects)
                })
            } catch (_: Exception) {}
        }
    }

    operator fun get(index: Int): Bitmap? = cache[index]

    fun ensureRendered(index: Int) {
        if (cache.containsKey(index) || renderingPages.contains(index)) return
        val aspect = pageAspects.getOrNull(index) ?: return
        renderingPages.add(index)
        val rw = renderWidth
        val rh = (rw * aspect).toInt()
        renderExecutor.submit {
            try {
                val bm = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                bm.eraseColor(Color.WHITE)
                synchronized(pdfLock) {
                    val r = pdfRenderer ?: return@submit
                    val p = r.openPage(index)
                    p.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    p.close()
                }
                postToUi.invoke(Runnable {
                    cache[index] = bm
                    renderingPages.remove(index)
                    onPageReady()
                })
            } catch (_: Exception) { renderingPages.remove(index) }
        }
    }

    fun evictDistantPages(currentPage: Int) {
        if (cache.size <= maxCachedPages) return
        cache.keys.sortedBy { Math.abs(it - currentPage) }
            .drop(maxCachedPages)
            .forEach { cache.remove(it)?.recycle() }
    }

    fun shutdown() {
        renderExecutor.submit {
            synchronized(pdfLock) {
                pdfRenderer?.close()
                fileDescriptor?.close()
            }
        }
        renderExecutor.shutdown()
    }
}
