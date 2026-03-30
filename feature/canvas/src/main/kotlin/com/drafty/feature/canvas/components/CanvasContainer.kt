package com.drafty.feature.canvas.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.drafty.core.ink.renderer.InkSurfaceView
import com.drafty.feature.canvas.CanvasViewModel

/**
 * Compose container that hosts the [InkSurfaceView] via AndroidView interop.
 *
 * Creates the SurfaceView, binds it to the [CanvasViewModel], and
 * handles cleanup on disposal.
 */
@Composable
fun CanvasContainer(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            InkSurfaceView(context).also { surface ->
                viewModel.bindSurface(surface)
            }
        },
        onRelease = {
            viewModel.unbindSurface()
        },
    )
}
