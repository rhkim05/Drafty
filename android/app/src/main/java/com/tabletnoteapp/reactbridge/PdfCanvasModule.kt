package com.tabletnoteapp.reactbridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.UIManagerModule
import com.tabletnoteapp.canvas.PdfDrawingView

class PdfCanvasModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "PdfCanvasModule"

    @ReactMethod fun undo(viewTag: Int)  { withView(viewTag) { it.undo() } }
    @ReactMethod fun redo(viewTag: Int)  { withView(viewTag) { it.redo() } }
    @ReactMethod fun clear(viewTag: Int) { withView(viewTag) { it.clearCanvas() } }

    @ReactMethod
    fun getStrokes(viewTag: Int, promise: Promise) {
        reactContext.runOnUiQueueThread {
            val view = resolveView(viewTag)
            if (view != null) promise.resolve(view.getStrokesJson())
            else promise.reject("ERR_NO_VIEW", "PdfCanvasView not found for tag $viewTag")
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

    private fun withView(viewTag: Int, block: (PdfDrawingView) -> Unit) {
        reactContext.runOnUiQueueThread {
            resolveView(viewTag)?.let(block)
        }
    }

    private fun resolveView(viewTag: Int): PdfDrawingView? =
        reactContext.getNativeModule(UIManagerModule::class.java)
            ?.resolveView(viewTag) as? PdfDrawingView
}
