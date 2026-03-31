package com.drafty.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.drafty.feature.canvas.CanvasScreen
import com.drafty.feature.notebooks.home.HomeScreen
import com.drafty.feature.notebooks.settings.SettingsScreen

/**
 * Top-level navigation graph for Drafty.
 * Routes: Home → Canvas (direct), Settings.
 */
@Composable
fun DraftyNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        // Home — canvas grid
        composable("home") {
            HomeScreen(
                onNotebookClick = { notebookId ->
                    navController.navigate("canvas/$notebookId")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
            )
        }

        // Canvas — drawing surface (loads first section of notebook automatically)
        composable(
            route = "canvas/{notebookId}",
            arguments = listOf(
                navArgument("notebookId") { type = NavType.StringType },
            ),
        ) {
            CanvasScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // Settings
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
