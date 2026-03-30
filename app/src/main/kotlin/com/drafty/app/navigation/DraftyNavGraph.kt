package com.drafty.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.drafty.feature.canvas.CanvasScreen

/**
 * Top-level navigation graph for Drafty.
 * Routes to feature screens: Home (notebooks), Canvas, PDF viewer.
 */
@Composable
fun DraftyNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "canvas") {
        composable("canvas") {
            CanvasScreen()
        }
        // TODO: Add "home" (notebook list) and "pdf-viewer" destinations
    }
}
