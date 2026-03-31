package com.tabletnoteapp.reactbridge

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.UIManagerModule
import com.tabletnoteapp.canvas.UnifiedCanvasView
import java.io.File

class UnifiedCanvasModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "UnifiedCanvasModule"

    @ReactMethod fun undo(viewTag: Int) { withView(viewTag) { it.undo() } }
    @ReactMethod fun redo(viewTag: Int) { withView(viewTag) { it.redo() } }
    @ReactMethod fun clear(viewTag: Int) { withView(viewTag) { it.clearCanvas() } }

    @ReactMethod
    fun getStrokes(viewTag: Int, promise: Promise) {
        reactContext.runOnUiQueueThread {
            val view = resolveView(viewTag)
            if (view != null) promise.resolve(view.getStrokesJson())
            else promise.reject("ERR_NO_VIEW", "UnifiedCanvasView not found for tag $viewTag")
        }
    }

    @ReactMethod
    fun loadStrokes(viewTag: Int, json: String) {
        withView(viewTag) { it.loadStrokesJson(json) }
    }

    @ReactMethod
    fun scrollToPage(viewTag: Int, page: Int) {
        withView(viewTag) { it.scrollToPage(page) }
    }

    @ReactMethod
    fun getPageCount(viewTag: Int, promise: Promise) {
        reactContext.runOnUiQueueThread {
            val view = resolveView(viewTag)
            if (view != null) promise.resolve(view.getPageCount())
            else promise.reject("ERR_NO_VIEW", "UnifiedCanvasView not found for tag $viewTag")
        }
    }

    @ReactMethod
    fun getPageCountFromFile(filePath: String, promise: Promise) {
        try {
            val fd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val count = renderer.pageCount
            renderer.close()
            fd.close()
            promise.resolve(count)
        } catch (e: Exception) {
            promise.reject("ERR_PDF_PAGE_COUNT", e.message)
        }
    }

    @ReactMethod
    fun addPage(viewTag: Int) {
        withView(viewTag) { it.addPage() }
    }

    @ReactMethod
    fun prependPage(viewTag: Int) {
        withView(viewTag) { it.prependPage() }
    }

    private fun withView(viewTag: Int, block: (UnifiedCanvasView) -> Unit) {
        reactContext.runOnUiQueueThread {
            resolveView(viewTag)?.let(block)
        }
    }

    private fun resolveView(viewTag: Int): UnifiedCanvasView? =
        reactContext.getNativeModule(UIManagerModule::class.java)
            ?.resolveView(viewTag) as? UnifiedCanvasView
}
