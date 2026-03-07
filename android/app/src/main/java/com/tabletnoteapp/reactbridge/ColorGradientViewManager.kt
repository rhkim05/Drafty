package com.tabletnoteapp.reactbridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.tabletnoteapp.canvas.ColorGradientView

class ColorGradientViewManager : SimpleViewManager<ColorGradientView>() {

    override fun getName() = "ColorGradientView"

    override fun createViewInstance(context: ThemedReactContext): ColorGradientView {
        val view = ColorGradientView(context)

        view.onSVChange = { s, v ->
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("colorPickerSVChange", Arguments.createMap().apply {
                    putDouble("sat", s.toDouble())
                    putDouble("val", v.toDouble())
                })
        }
        view.onHueChange = { h ->
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("colorPickerHueChange", Arguments.createMap().apply {
                    putDouble("hue", h.toDouble())
                })
        }
        return view
    }

    @ReactProp(name = "hue",        defaultFloat = 0f)
    fun setHue(view: ColorGradientView, hue: Float) { view.setHueProp(hue) }

    @ReactProp(name = "sat",        defaultFloat = 1f)
    fun setSat(view: ColorGradientView, sat: Float) { view.setSatProp(sat) }

    @ReactProp(name = "brightness", defaultFloat = 1f)
    fun setBrightness(view: ColorGradientView, v: Float) { view.setBriProp(v) }
}
