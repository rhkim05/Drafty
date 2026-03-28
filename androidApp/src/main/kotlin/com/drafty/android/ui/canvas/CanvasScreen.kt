package com.drafty.android.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Matrix
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke as InkStroke
import com.drafty.android.ink.StrokeMapper
import com.drafty.shared.store.CanvasIntent
import com.drafty.shared.store.CanvasStore
import java.util.UUID
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    notebookId: String,
    pageId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val store: CanvasStore = koinInject { parametersOf(scope) }
    val state by store.state.collectAsState()

    val renderer = remember { CanvasStrokeRenderer.create() }
    val identityMatrix = remember { Matrix() }
    var finishedInkStrokes by remember { mutableStateOf<List<InkStroke>>(emptyList()) }

    LaunchedEffect(pageId) {
        store.dispatch(CanvasIntent.LoadPage(pageId))
    }

    LaunchedEffect(state.strokes) {
        finishedInkStrokes = state.strokes.mapNotNull { strokeData ->
            if (strokeData.points.isEmpty()) null
            else StrokeMapper.toInkStroke(strokeData)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Canvas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            CanvasToolbar(
                state = state,
                onColorSelected = { store.dispatch(CanvasIntent.ColorSelected(it)) },
                onBrushSizeChanged = { store.dispatch(CanvasIntent.BrushSizeChanged(it)) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    finishedInkStrokes.forEach { stroke ->
                        renderer.draw(nativeCanvas, stroke, identityMatrix)
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        InProgressStrokesView(context).apply {
                            setOnTouchListener(
                                buildTouchListener(
                                    inProgressStrokesView = this,
                                    brushProvider = { StrokeMapper.createBrush(state.activeBrush) },
                                    onStrokeFinished = { inkStroke ->
                                        val nextOrder = state.strokes.size
                                        val strokeData = StrokeMapper.toStrokeData(
                                            inkStroke = inkStroke,
                                            pageId = pageId,
                                            strokeId = UUID.randomUUID().toString(),
                                            strokeOrder = nextOrder
                                        )
                                        store.dispatch(CanvasIntent.StrokeCompleted(strokeData))
                                        finishedInkStrokes = finishedInkStrokes + inkStroke
                                    }
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}
