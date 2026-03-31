package com.tabletnoteapp.reactbridge

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class CanvasPackage : ReactPackage {

    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        listOf(UnifiedCanvasViewManager(), ColorGradientViewManager())

    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(UnifiedCanvasModule(context))
}
