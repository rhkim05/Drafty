package com.drafty.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.drafty.app.navigation.DraftyNavGraph
import com.drafty.core.ui.theme.DraftyTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity that hosts the Compose navigation graph.
 * All screens are composable destinations within DraftyNavGraph.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DraftyTheme {
                DraftyNavGraph()
            }
        }
    }
}
