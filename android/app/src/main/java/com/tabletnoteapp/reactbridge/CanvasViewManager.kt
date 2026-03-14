package com.tabletnoteapp.reactbridge

import android.graphics.Color
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.tabletnoteapp.canvas.DrawingCanvas
import com.tabletnoteapp.canvas.models.ToolType

/**
 * Exposes DrawingCanvas to React Native as a native view component named "CanvasView".
 * Props set from JS: tool, penColor, penThickness, eraserThickness, eraserMode,
 *                    highlighterColor, highlighterThickness
 */
class CanvasViewManager : SimpleViewManager<DrawingCanvas>() {

    override fun getName() = "CanvasView"

    override fun createViewInstance(context: ThemedReactContext): DrawingCanvas {
        val canvas = DrawingCanvas(context)

        canvas.onUndoRedoStateChanged = { canUndo, canRedo ->
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("canvasUndoRedoState", Arguments.createMap().apply {
                    putBoolean("canUndo", canUndo)
                    putBoolean("canRedo", canRedo)
                })
        }

        canvas.onEraserLift = {
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("canvasEraserLift", null)
        }

        canvas.onSelectionChanged = { hasSelection, count, bounds ->
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("canvasSelectionChanged", Arguments.createMap().apply {
                    putBoolean("hasSelection", hasSelection)
                    putInt("count", count)
                    putMap("bounds", Arguments.createMap().apply {
                        putDouble("x", bounds.left.toDouble())
                        putDouble("y", bounds.top.toDouble())
                        putDouble("width", bounds.width().toDouble())
                        putDouble("height", bounds.height().toDouble())
                    })
                })
        }

        canvas.onPageChanged = { page ->
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("canvasPageChanged", Arguments.createMap().apply { putInt("page", page) })
        }

        canvas.onPageCountChanged = { total ->
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("canvasPageCountChanged", Arguments.createMap().apply { putInt("total", total) })
        }

        return canvas
    }

    @ReactProp(name = "tool")
    fun setTool(view: DrawingCanvas, tool: String?) {
        view.currentTool = when (tool) {
            "eraser"      -> ToolType.ERASER
            "select"      -> ToolType.SELECT
            "highlighter" -> ToolType.HIGHLIGHTER
            "scroll"      -> ToolType.SCROLL
            else          -> ToolType.PEN
        }
    }

    @ReactProp(name = "penColor")
    fun setPenColor(view: DrawingCanvas, color: String?) {
        if (color != null) view.penColor = Color.parseColor(color)
    }

    @ReactProp(name = "penThickness", defaultFloat = 4f)
    fun setPenThickness(view: DrawingCanvas, thickness: Float) {
        view.penThickness = thickness
    }

    @ReactProp(name = "eraserThickness", defaultFloat = 24f)
    fun setEraserThickness(view: DrawingCanvas, thickness: Float) {
        view.eraserThickness = thickness
    }

    @ReactProp(name = "eraserMode")
    fun setEraserMode(view: DrawingCanvas, mode: String?) {
        view.eraserMode = mode ?: "pixel"
    }

    @ReactProp(name = "highlighterColor")
    fun setHighlighterColor(view: DrawingCanvas, color: String?) {
        if (color != null) view.highlighterColor = Color.parseColor(color)
    }

    @ReactProp(name = "highlighterThickness", defaultFloat = 16f)
    fun setHighlighterThickness(view: DrawingCanvas, thickness: Float) {
        view.highlighterThickness = thickness
    }
}
