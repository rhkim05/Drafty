package com.drafty.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Drafty application entry point.
 * Initializes Hilt dependency injection.
 */
@HiltAndroidApp
class DraftyApplication : Application()
