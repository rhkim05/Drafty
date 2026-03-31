package com.tabletnoteapp.reactbridge

import android.graphics.Color
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.tabletnoteapp.canvas.UnifiedCanvasView
import com.tabletnoteapp.canvas.models.ToolType

class UnifiedCanvasViewManager : SimpleViewManager<UnifiedCanvasView>() {

    override fun getName() = "UnifiedCanvasView"

    override fun createViewInstance(context: ThemedReactContext): UnifiedCanvasView {
        val view = UnifiedCanvasView(context)

        view.onPageChanged = { page ->
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("canvasPageChanged", Arguments.createMap().apply { putInt("page", page) })
        }
        view.onLoadComplete = { totalPages ->
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("canvasLoadComplete", Arguments.createMap().apply { putInt("totalPages", totalPages) })
        }
        view.onUndoRedoStateChanged = { canUndo, canRedo ->
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("canvasUndoRedoState", Arguments.createMap().apply {
                    putBoolean("canUndo", canUndo)
                    putBoolean("canRedo", canRedo)
                })
        }
        view.onEraserLift = {
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("canvasEraserLift", null)
        }
        return view
    }

    @ReactProp(name = "pdfUri")
    fun setPdfUri(view: UnifiedCanvasView, uri: String?) {
        if (uri != null) {
            view.openPdf(uri)
        } else if (!view.isPdf && view.scrollEngine.getPageCount() == 0) {
            view.initBlankPages(1)
        }
    }

    @ReactProp(name = "tool")
    fun setTool(view: UnifiedCanvasView, tool: String?) {
        view.currentTool = when (tool) {
            "eraser" -> ToolType.ERASER
            "select" -> ToolType.SELECT
            else -> ToolType.PEN
        }
    }

    @ReactProp(name = "penColor")
    fun setPenColor(view: UnifiedCanvasView, color: String?) {
        if (color != null) view.penColor = Color.parseColor(color)
    }

    @ReactProp(name = "penThickness", defaultFloat = 4f)
    fun setPenThickness(view: UnifiedCanvasView, thickness: Float) {
        view.penThickness = thickness
    }

    @ReactProp(name = "eraserThickness", defaultFloat = 24f)
    fun setEraserThickness(view: UnifiedCanvasView, thickness: Float) {
        view.eraserThickness = thickness
    }

    @ReactProp(name = "eraserMode")
    fun setEraserMode(view: UnifiedCanvasView, mode: String?) {
        view.eraserMode = mode ?: "pixel"
    }
}
