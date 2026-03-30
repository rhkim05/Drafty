package com.drafty.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

/**
 * Top-level navigation graph for Drafty.
 * Routes to feature screens: Home (notebooks), Canvas, PDF viewer.
 */
@Composable
fun DraftyNavGraph() {
    val navController = rememberNavController()

    // TODO: Define navigation destinations for each feature module
    // NavHost(navController = navController, startDestination = "home") { ... }
}
