# Phase 1 — Core Drawing Canvas

## Goal

Implement a functional drawing canvas: stylus input with pressure sensitivity, stroke persistence via SQLDelight, and low-latency rendering using `androidx.ink`.

---

## Current State

Phase 0 (scaffolding) is complete. We have:
- SQLDelight schema with a `Stroke` table (`inputData BLOB`, `brushType`, `brushSize`, `colorArgb`, bounding box fields)
- Generated queries: `getStrokesForPage`, `insertNotebook` (but no stroke insert/delete queries yet)
- Placeholder `CanvasScreen` with a crosshair Canvas
- All 8 `androidx.ink` modules declared in `androidApp/build.gradle.kts` (v1.0.0)
- Koin DI with `DraftyDatabase` singleton
- Navigation: `library` -> `notebook/{id}` -> `canvas/{notebookId}/{pageId}`

**What's missing**: models, repositories, stores, stroke capture, rendering, serialization, and the actual canvas implementation.

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│ androidApp                                                       │
│                                                                  │
│  CanvasScreen (Compose)                                          │
│    ├── InProgressStrokesView (AndroidView interop)               │
│    │     └── Captures MotionEvents, renders in-progress strokes  │
│    ├── CanvasStrokeRenderer (renders finalized strokes)          │
│    └── ToolBar (pen settings, color, brush size)                 │
│                                                                  │
│  StrokeMapper                                                    │
│    └── Converts InkStroke <-> StrokeData                         │
│                                                                  │
│  StrokeSerializerImpl (actual)                                   │
│    └── ink-storage encode/decode for StrokeInputBatch            │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│ shared (commonMain)                                              │
│                                                                  │
│  Models: StrokeData, StrokePoint, BrushConfig, Tool              │
│  CanvasStore (MVI): state + intents + side effects               │
│  StrokeRepository: CRUD via DraftyDatabase                       │
│  StrokeSerializer (expect): encode/decode StrokePoints           │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Approach

### Why `InProgressStrokesView` (View-based) instead of Compose-native `InProgressStrokes`

The Compose `InProgressStrokes` API requires manual `drawStroke` calls on a `DrawScope` and doesn't integrate with the front-buffered rendering pipeline that gives sub-frame latency. `InProgressStrokesView` handles front-buffered rendering internally and has been stable since ink 1.0.0. We wrap it via `AndroidView` composable — this is the approach Google recommends for production apps.

### Why `expect/actual` for serialization

The `ink-storage` module uses Android-specific protobuf encoding. The shared module must remain platform-agnostic, so we use `expect/actual` to keep the serialization boundary clean. The shared module deals with `List<StrokePoint>`, and each platform provides its own encoder.

### Why separate `StrokeData` from SQLDelight-generated `Stroke`

The generated `Stroke` data class maps 1:1 to the DB schema (with `ByteArray inputData`). `StrokeData` is the domain model with deserialized `List<StrokePoint>` and a typed `BrushConfig`. This separation keeps the domain layer clean and testable.

---

## Detailed Plan

### 1. Add Missing SQLDelight Queries

**File**: `shared/src/commonMain/sqldelight/com/drafty/shared/data/db/DraftyDatabase.sq`

Add stroke and page CRUD queries needed for canvas operations:

```sql
insertStroke:
INSERT INTO Stroke(id, pageId, brushType, brushSize, colorArgb, strokeOrder, inputData,
                   boundingBoxX, boundingBoxY, boundingBoxW, boundingBoxH, createdAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

deleteStroke:
DELETE FROM Stroke WHERE id = ?;

deleteAllStrokesForPage:
DELETE FROM Stroke WHERE pageId = ?;

getMaxStrokeOrder:
SELECT MAX(strokeOrder) FROM Stroke WHERE pageId = ?;

getPageById:
SELECT * FROM Page WHERE id = ?;

insertPage:
INSERT INTO Page(id, notebookId, pageIndex, pageType, width, height, templateType,
                 pdfDocumentId, pdfPageIndex, createdAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

---

### 2. Domain Models in Shared Module

**New file**: `shared/src/commonMain/kotlin/com/drafty/shared/model/StrokeData.kt`

```kotlin
package com.drafty.shared.model

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltRadians: Float,
    val orientationRadians: Float,
    val timestampMs: Long
)

data class BrushConfig(
    val type: BrushType,
    val size: Float,
    val colorArgb: Long
)

enum class BrushType(val key: String) {
    PRESSURE_PEN("pressure_pen");

    companion object {
        fun fromKey(key: String): BrushType =
            entries.firstOrNull { it.key == key } ?: PRESSURE_PEN
    }
}

data class StrokeData(
    val id: String,
    val pageId: String,
    val points: List<StrokePoint>,
    val brush: BrushConfig,
    val strokeOrder: Int,
    val boundingBox: BoundingBox,
    val createdAt: Long
)

data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
```

**New file**: `shared/src/commonMain/kotlin/com/drafty/shared/model/Tool.kt`

```kotlin
package com.drafty.shared.model

enum class Tool {
    PEN,
    // ERASER, HIGHLIGHTER — future phases
}
```

---

### 3. StrokeSerializer (expect/actual)

**New file**: `shared/src/commonMain/kotlin/com/drafty/shared/data/serialization/StrokeSerializer.kt`

```kotlin
package com.drafty.shared.data.serialization

import com.drafty.shared.model.StrokePoint

expect class StrokeSerializer() {
    fun encode(points: List<StrokePoint>): ByteArray
    fun decode(data: ByteArray): List<StrokePoint>
}
```

**New file**: `shared/src/androidMain/kotlin/com/drafty/shared/data/serialization/StrokeSerializer.android.kt`

```kotlin
package com.drafty.shared.data.serialization

import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch
import com.drafty.shared.model.StrokePoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

actual class StrokeSerializer actual constructor() {

    actual fun encode(points: List<StrokePoint>): ByteArray {
        val batch = MutableStrokeInputBatch()
        points.forEachIndexed { index, point ->
            val input = StrokeInput.create(
                x = point.x,
                y = point.y,
                elapsedTimeMillis = point.timestampMs,
                pressure = point.pressure,
                tiltRadians = point.tiltRadians,
                orientationRadians = point.orientationRadians,
                toolType = StrokeInput.ToolType.STYLUS
            )
            if (index == 0) {
                batch.addOrThrow(input)
            } else {
                batch.addOrThrow(input)
            }
        }
        return ByteArrayOutputStream().use { stream ->
            batch.encode(stream)
            stream.toByteArray()
        }
    }

    actual fun decode(data: ByteArray): List<StrokePoint> {
        val batch = ByteArrayInputStream(data).use { stream ->
            StrokeInputBatch.decode(stream)
        }
        return (0 until batch.size).map { i ->
            StrokePoint(
                x = batch.getX(i),
                y = batch.getY(i),
                pressure = batch.getPressure(i),
                tiltRadians = batch.getTiltRadians(i),
                orientationRadians = batch.getOrientationRadians(i),
                timestampMs = batch.getElapsedTimeMillis(i)
            )
        }
    }
}
```

**New file**: `shared/src/iosMain/kotlin/com/drafty/shared/data/serialization/StrokeSerializer.ios.kt`

Stub for now — iOS canvas is not in scope for Phase 1:

```kotlin
package com.drafty.shared.data.serialization

import com.drafty.shared.model.StrokePoint

actual class StrokeSerializer actual constructor() {
    actual fun encode(points: List<StrokePoint>): ByteArray = byteArrayOf()
    actual fun decode(data: ByteArray): List<StrokePoint> = emptyList()
}
```

---

### 4. StrokeRepository

**New file**: `shared/src/commonMain/kotlin/com/drafty/shared/data/repository/StrokeRepository.kt`

```kotlin
package com.drafty.shared.data.repository

import com.drafty.shared.data.db.DraftyDatabase
import com.drafty.shared.data.serialization.StrokeSerializer
import com.drafty.shared.model.BoundingBox
import com.drafty.shared.model.BrushConfig
import com.drafty.shared.model.BrushType
import com.drafty.shared.model.StrokeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StrokeRepository(
    private val database: DraftyDatabase,
    private val serializer: StrokeSerializer
) {
    suspend fun getStrokesForPage(pageId: String): List<StrokeData> =
        withContext(Dispatchers.Default) {
            database.draftyDatabaseQueries.getStrokesForPage(pageId)
                .executeAsList()
                .map { row ->
                    StrokeData(
                        id = row.id,
                        pageId = row.pageId,
                        points = serializer.decode(row.inputData),
                        brush = BrushConfig(
                            type = BrushType.fromKey(row.brushType),
                            size = row.brushSize.toFloat(),
                            colorArgb = row.colorArgb
                        ),
                        strokeOrder = row.strokeOrder.toInt(),
                        boundingBox = BoundingBox(
                            x = row.boundingBoxX.toFloat(),
                            y = row.boundingBoxY.toFloat(),
                            width = row.boundingBoxW.toFloat(),
                            height = row.boundingBoxH.toFloat()
                        ),
                        createdAt = row.createdAt
                    )
                }
        }

    suspend fun insertStroke(stroke: StrokeData) = withContext(Dispatchers.Default) {
        database.draftyDatabaseQueries.insertStroke(
            id = stroke.id,
            pageId = stroke.pageId,
            brushType = stroke.brush.type.key,
            brushSize = stroke.brush.size.toDouble(),
            colorArgb = stroke.brush.colorArgb,
            strokeOrder = stroke.strokeOrder.toLong(),
            inputData = serializer.encode(stroke.points),
            boundingBoxX = stroke.boundingBox.x.toDouble(),
            boundingBoxY = stroke.boundingBox.y.toDouble(),
            boundingBoxW = stroke.boundingBox.width.toDouble(),
            boundingBoxH = stroke.boundingBox.height.toDouble(),
            createdAt = stroke.createdAt
        )
    }

    suspend fun deleteStroke(id: String) = withContext(Dispatchers.Default) {
        database.draftyDatabaseQueries.deleteStroke(id)
    }

    suspend fun getNextStrokeOrder(pageId: String): Int =
        withContext(Dispatchers.Default) {
            val max = database.draftyDatabaseQueries.getMaxStrokeOrder(pageId)
                .executeAsOneOrNull()?.MAX ?: 0L
            (max + 1).toInt()
        }
}
```

---

### 5. CanvasStore (MVI)

**New file**: `shared/src/commonMain/kotlin/com/drafty/shared/store/CanvasStore.kt`

```kotlin
package com.drafty.shared.store

import com.drafty.shared.data.repository.StrokeRepository
import com.drafty.shared.model.BrushConfig
import com.drafty.shared.model.BrushType
import com.drafty.shared.model.StrokeData
import com.drafty.shared.model.Tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CanvasState(
    val pageId: String = "",
    val strokes: List<StrokeData> = emptyList(),
    val activeTool: Tool = Tool.PEN,
    val activeBrush: BrushConfig = BrushConfig(
        type = BrushType.PRESSURE_PEN,
        size = 3.0f,
        colorArgb = 0xFF000000
    ),
    val isLoading: Boolean = true
)

sealed interface CanvasIntent {
    data class LoadPage(val pageId: String) : CanvasIntent
    data class StrokeCompleted(val stroke: StrokeData) : CanvasIntent
    data class ToolSelected(val tool: Tool) : CanvasIntent
    data class ColorSelected(val colorArgb: Long) : CanvasIntent
    data class BrushSizeChanged(val size: Float) : CanvasIntent
}

class CanvasStore(
    private val strokeRepository: StrokeRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(CanvasState())
    val state: StateFlow<CanvasState> = _state.asStateFlow()

    fun dispatch(intent: CanvasIntent) {
        when (intent) {
            is CanvasIntent.LoadPage -> loadPage(intent.pageId)
            is CanvasIntent.StrokeCompleted -> onStrokeCompleted(intent.stroke)
            is CanvasIntent.ToolSelected -> _state.update {
                it.copy(activeTool = intent.tool)
            }
            is CanvasIntent.ColorSelected -> _state.update {
                it.copy(activeBrush = it.activeBrush.copy(colorArgb = intent.colorArgb))
            }
            is CanvasIntent.BrushSizeChanged -> _state.update {
                it.copy(activeBrush = it.activeBrush.copy(size = intent.size))
            }
        }
    }

    private fun loadPage(pageId: String) {
        _state.update { it.copy(pageId = pageId, isLoading = true) }
        scope.launch {
            val strokes = strokeRepository.getStrokesForPage(pageId)
            _state.update { it.copy(strokes = strokes, isLoading = false) }
        }
    }

    private fun onStrokeCompleted(stroke: StrokeData) {
        _state.update { it.copy(strokes = it.strokes + stroke) }
        scope.launch {
            strokeRepository.insertStroke(stroke)
        }
    }
}
```

---

### 6. Koin DI Wiring

**Modify**: `shared/src/commonMain/kotlin/com/drafty/shared/di/SharedModule.kt`

```kotlin
val sharedModule: Module = module {
    single<DraftyDatabase> { createDatabase(get()) }
    single { StrokeSerializer() }
    single { StrokeRepository(get(), get()) }
}
```

**Modify**: `androidApp/src/main/kotlin/com/drafty/android/di/AppModule.kt`

```kotlin
val appModule = module {
    single { DatabaseDriverFactory(androidContext()).create() }
    // CanvasStore is scoped per-screen, created in the composable via koinInject
    factory { (scope: CoroutineScope) -> CanvasStore(get(), scope) }
}
```

---

### 7. StrokeMapper (Android-side converter)

**New file**: `androidApp/src/main/kotlin/com/drafty/android/ink/StrokeMapper.kt`

Converts between `androidx.ink.strokes.Stroke` and `StrokeData`:

```kotlin
package com.drafty.android.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushPaint
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke as InkStroke
import androidx.ink.strokes.StrokeInput
import com.drafty.shared.model.BoundingBox
import com.drafty.shared.model.BrushConfig
import com.drafty.shared.model.BrushType
import com.drafty.shared.model.StrokeData
import com.drafty.shared.model.StrokePoint
import kotlinx.datetime.Clock

object StrokeMapper {

    fun toStrokeData(
        inkStroke: InkStroke,
        pageId: String,
        strokeId: String,
        strokeOrder: Int
    ): StrokeData {
        val inputs = inkStroke.inputs
        val points = (0 until inputs.size).map { i ->
            StrokePoint(
                x = inputs.getX(i),
                y = inputs.getY(i),
                pressure = inputs.getPressure(i),
                tiltRadians = inputs.getTiltRadians(i),
                orientationRadians = inputs.getOrientationRadians(i),
                timestampMs = inputs.getElapsedTimeMillis(i)
            )
        }
        val box = inkStroke.renderBounds
        return StrokeData(
            id = strokeId,
            pageId = pageId,
            points = points,
            brush = BrushConfig(
                type = BrushType.PRESSURE_PEN,
                size = inkStroke.brush.size,
                colorArgb = inkStroke.brush.colorLong.toLong()
            ),
            strokeOrder = strokeOrder,
            boundingBox = BoundingBox(
                x = box.xMin,
                y = box.yMin,
                width = box.xMax - box.xMin,
                height = box.yMax - box.yMin
            ),
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
    }

    fun toInkStroke(strokeData: StrokeData): InkStroke {
        val brush = createBrush(strokeData.brush)
        val batch = MutableStrokeInputBatch()
        strokeData.points.forEach { point ->
            batch.addOrThrow(
                StrokeInput.create(
                    x = point.x,
                    y = point.y,
                    elapsedTimeMillis = point.timestampMs,
                    pressure = point.pressure,
                    tiltRadians = point.tiltRadians,
                    orientationRadians = point.orientationRadians,
                    toolType = StrokeInput.ToolType.STYLUS
                )
            )
        }
        return InkStroke(brush, batch)
    }

    fun createBrush(config: BrushConfig): Brush {
        return Brush.createWithColorLong(
            family = BrushFamily.PRESSURE_PEN,
            colorLong = config.colorArgb.toColorLong(),
            size = config.size,
            epsilon = 0.1f
        )
    }

    private fun Long.toColorLong(): Long {
        // Convert ARGB integer color to Android ColorLong format
        val a = ((this shr 24) and 0xFF) / 255f
        val r = ((this shr 16) and 0xFF) / 255f
        val g = ((this shr 8) and 0xFF) / 255f
        val b = (this and 0xFF) / 255f
        return android.graphics.Color.pack(r, g, b, a).toLong()
    }
}
```

> **Note**: The exact `Brush.createWithColorLong` and `colorLong` APIs need verification against ink 1.0.0 stable. If the API differs, we adapt `StrokeMapper` accordingly during implementation. The key contract is: ARGB Long in shared model <-> whatever Color format Brush uses.

---

### 8. CanvasScreen — Full Implementation

**Rewrite**: `androidApp/src/main/kotlin/com/drafty/android/ui/canvas/CanvasScreen.kt`

This is the core of the feature. The screen has three layers:

1. **Bottom layer**: `Canvas` composable rendering finalized strokes via `CanvasStrokeRenderer`
2. **Middle layer**: `InProgressStrokesView` (via `AndroidView`) handling stylus input with front-buffered low-latency rendering
3. **Top layer**: Toolbar overlay for pen settings

```kotlin
package com.drafty.android.ui.canvas

import android.view.MotionEvent
import android.view.View
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
import androidx.compose.runtime.DisposableEffect
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
    var finishedInkStrokes by remember { mutableStateOf<List<InkStroke>>(emptyList()) }

    // Load page on first composition
    LaunchedEffect(pageId) {
        store.dispatch(CanvasIntent.LoadPage(pageId))
    }

    // Convert domain strokes to ink strokes when state changes
    LaunchedEffect(state.strokes) {
        finishedInkStrokes = state.strokes.map { StrokeMapper.toInkStroke(it) }
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
                // Layer 1: Render finalized strokes
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    finishedInkStrokes.forEach { stroke ->
                        renderer.draw(
                            canvas = nativeCanvas,
                            stroke = stroke
                        )
                    }
                }

                // Layer 2: In-progress stroke capture + front-buffered rendering
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        InProgressStrokesView(context).apply {
                            // Configure touch listener for stylus input
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
                                        // Add to local rendered list immediately
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
```

---

### 9. Touch Listener — Stylus Input Handling

**New file**: `androidApp/src/main/kotlin/com/drafty/android/ui/canvas/CanvasTouchListener.kt`

Handles `MotionEvent` dispatch to `InProgressStrokesView`, including pressure, tilt, and palm rejection:

```kotlin
package com.drafty.android.ui.canvas

import android.view.MotionEvent
import android.view.View
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.strokes.Stroke as InkStroke

fun buildTouchListener(
    inProgressStrokesView: InProgressStrokesView,
    brushProvider: () -> Brush,
    onStrokeFinished: (InkStroke) -> Unit
): View.OnTouchListener {
    val activeTouchIds = mutableMapOf<Int, InProgressStrokesView.StrokeId>()

    val finishedStrokesListener =
        InProgressStrokesView.FinishedStrokesListener { finishedStrokes ->
            for (stroke in finishedStrokes) {
                onStrokeFinished(stroke)
            }
            inProgressStrokesView.removeFinishedStrokes(finishedStrokes.map { it to it }.toMap())
        }

    inProgressStrokesView.addFinishedStrokesListener(finishedStrokesListener)

    return View.OnTouchListener { view, event ->
        // Only process stylus input — reject finger touches (palm rejection)
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS &&
            event.getToolType(0) != MotionEvent.TOOL_TYPE_ERASER
        ) {
            return@OnTouchListener false
        }

        val pointerId = event.getPointerId(0)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.requestUnbufferedDispatch(event)
                val strokeId = inProgressStrokesView.startStroke(
                    event = event,
                    pointerId = pointerId,
                    brush = brushProvider()
                )
                activeTouchIds[pointerId] = strokeId
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val strokeId = activeTouchIds[pointerId] ?: return@OnTouchListener false
                inProgressStrokesView.addToStroke(
                    event = event,
                    pointerId = pointerId,
                    strokeId = strokeId,
                    predict = true  // Enable stroke prediction for lower perceived latency
                )
                true
            }

            MotionEvent.ACTION_UP -> {
                val strokeId = activeTouchIds.remove(pointerId) ?: return@OnTouchListener false
                inProgressStrokesView.finishStroke(
                    event = event,
                    pointerId = pointerId,
                    strokeId = strokeId
                )
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                val strokeId = activeTouchIds.remove(pointerId) ?: return@OnTouchListener false
                inProgressStrokesView.cancelStroke(strokeId, event)
                true
            }

            else -> false
        }
    }
}
```

Key behaviors:
- **Palm rejection**: Only `TOOL_TYPE_STYLUS` and `TOOL_TYPE_ERASER` are accepted; finger touches fall through
- **`requestUnbufferedDispatch`**: Ensures `ACTION_MOVE` events arrive at screen refresh rate (120Hz on supported devices) rather than batched
- **`predict = true`**: `InProgressStrokesView` extrapolates the stroke ahead of the actual touch point to reduce perceived latency
- **Pressure sensitivity**: Handled automatically by `BrushFamily.PRESSURE_PEN` — it reads pressure from the `MotionEvent` and varies stroke width

---

### 10. Toolbar — Color Picker and Brush Size

**New file**: `androidApp/src/main/kotlin/com/drafty/android/ui/canvas/CanvasToolbar.kt`

Minimal toolbar for Phase 1 — color presets + brush size slider:

```kotlin
package com.drafty.android.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.drafty.shared.store.CanvasState

val PRESET_COLORS = listOf(
    0xFF000000L, // Black
    0xFF1A1A1AL, // Dark gray
    0xFF0D47A1L, // Blue
    0xFFC62828L, // Red
    0xFF2E7D32L, // Green
    0xFFFF8F00L, // Amber
)

@Composable
fun CanvasToolbar(
    state: CanvasState,
    onColorSelected: (Long) -> Unit,
    onBrushSizeChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Color presets
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PRESET_COLORS.forEach { colorArgb ->
                    val isSelected = state.activeBrush.colorArgb == colorArgb
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(colorArgb.toInt()))
                            .then(
                                if (isSelected) Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ) else Modifier
                            )
                            .clickable { onColorSelected(colorArgb) }
                    )
                }
            }

            // Brush size slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("Size", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = state.activeBrush.size,
                    onValueChange = onBrushSizeChanged,
                    valueRange = 1f..20f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}
```

The toolbar is placed at the bottom of the `CanvasScreen` `Scaffold` (or as an overlay). Integration in `CanvasScreen`:

```kotlin
// Inside Scaffold, after the Canvas + AndroidView Box:
CanvasToolbar(
    state = state,
    onColorSelected = { store.dispatch(CanvasIntent.ColorSelected(it)) },
    onBrushSizeChanged = { store.dispatch(CanvasIntent.BrushSizeChanged(it)) },
    modifier = Modifier.align(Alignment.BottomCenter)
)
```

---

## Dependencies to Add

**`shared/build.gradle.kts`** — add `ink-strokes` and `ink-storage` to androidMain:

```kotlin
// In androidMain dependencies block
implementation(libs.ink.strokes)
implementation(libs.ink.storage)
```

These are needed for the `actual` StrokeSerializer on Android. Verify these are already transitively available from androidApp — if so, this step is unnecessary.

**`gradle/libs.versions.toml`** — verify all ink modules are cataloged (they should already be from Phase 0).

---

## Files Modified / Created

| Action | File |
|--------|------|
| **Modify** | `shared/src/commonMain/sqldelight/.../DraftyDatabase.sq` — add 5 queries |
| **Create** | `shared/src/commonMain/kotlin/.../model/StrokeData.kt` |
| **Create** | `shared/src/commonMain/kotlin/.../model/Tool.kt` |
| **Create** | `shared/src/commonMain/kotlin/.../data/serialization/StrokeSerializer.kt` |
| **Create** | `shared/src/androidMain/kotlin/.../data/serialization/StrokeSerializer.android.kt` |
| **Create** | `shared/src/iosMain/kotlin/.../data/serialization/StrokeSerializer.ios.kt` |
| **Create** | `shared/src/commonMain/kotlin/.../data/repository/StrokeRepository.kt` |
| **Create** | `shared/src/commonMain/kotlin/.../store/CanvasStore.kt` |
| **Modify** | `shared/src/commonMain/kotlin/.../di/SharedModule.kt` — add serializer + repo |
| **Modify** | `androidApp/src/main/kotlin/.../di/AppModule.kt` — add CanvasStore factory |
| **Create** | `androidApp/src/main/kotlin/.../ink/StrokeMapper.kt` |
| **Rewrite** | `androidApp/src/main/kotlin/.../ui/canvas/CanvasScreen.kt` |
| **Create** | `androidApp/src/main/kotlin/.../ui/canvas/CanvasTouchListener.kt` |
| **Create** | `androidApp/src/main/kotlin/.../ui/canvas/CanvasToolbar.kt` |
| **Modify** | `shared/build.gradle.kts` — possibly add ink deps to androidMain |

---

## Pen Tool Spec

| Property | Value |
|---|---|
| Brush family | `BrushFamily.PRESSURE_PEN` |
| Pressure mapping | Variable width based on `MotionEvent` pressure (handled by brush family) |
| Default size | 3.0f |
| Size range | 1.0f - 20.0f |
| Default color | Black (`0xFF000000`) |

---

## Key Risks

| Risk | Mitigation |
|---|---|
| `InProgressStrokesView` requires View-layer touch events | Wrap in `AndroidView` composable with explicit `setOnTouchListener` |
| `Brush.createWithColorLong` API surface may differ | Verify during implementation; adapt StrokeMapper to match 1.0.0 stable API |
| Front-buffered rendering requires hardware support | Degrades gracefully — still renders, just without sub-frame latency |
| Palm rejection doesn't work on all devices | Fallback: accept all touch types on non-stylus devices (phone testing) |
| `StrokeInputBatch.encode/decode` not available in `ink-storage` | Fall back to manual point serialization via `kotlinx.serialization` |

---

## What's Deferred

- **Eraser tool** — Phase 2
- **Undo/redo** — Phase 3
- **Pan/zoom** — Phase 5
- **Multi-touch** — Phase 5
- **Highlighter** — future
- **iOS canvas** — future (StrokeSerializer.ios.kt is a stub)

---

## Task Checklist

See [TODO.md](TODO.md) for granular progress tracking.
