# Architecture

This document describes the system architecture for Drafty — a handwriting app built with Kotlin Multiplatform (KMP) targeting Android first, with a future iOS port. It covers the rendering pipeline, stylus input processing, data model, storage layer, state management, export/import, and module boundaries.

Tech stack decisions referenced throughout: androidx.ink (Android rendering), PencilKit (iOS rendering, future), Compose Multiplatform (UI framework), SQLDelight (persistence), Wire Protobuf (stroke serialization), StateFlow + Command pattern (state management).

---

## 1. System Overview

```
┌────────────────────────────────────────────────────────────────────┐
│                        Compose Multiplatform UI                    │
│   ┌──────────────────┐              ┌───────────────────────────┐  │
│   │  Library Screen   │              │  Canvas Screen            │  │
│   │  (folders, grid)  │              │  (toolbar, drawing area)  │  │
│   └────────┬─────────┘              └────────────┬──────────────┘  │
└────────────┼─────────────────────────────────────┼─────────────────┘
             │                                     │
             ▼                                     ▼
┌────────────────────────┐     ┌──────────────────────────────────────┐
│   LibraryViewModel     │     │   CanvasViewModel (commonMain)      │
│   (commonMain)         │     │   ┌──────────────────────────────┐  │
│                        │     │   │ StateFlow<CanvasState>        │  │
│                        │     │   │ UndoRedoManager               │  │
│                        │     │   │ AutosaveManager               │  │
│                        │     │   │ CanvasClipboard                │  │
│                        │     │   └──────────────────────────────┘  │
└───────────┬────────────┘     └───────────┬──────────────────────────┘
            │                              │
            ▼                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     Repository Layer (commonMain)                    │
│   FolderRepository    CanvasRepository    StrokeRepository           │
└──────────────┬───────────────┬────────────────┬──────────────────────┘
               │               │                │
               ▼               ▼                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     Storage Layer                                    │
│   ┌─────────────────────────┐   ┌──────────────────┐                │
│   │ SQLDelight              │   │ StrokeSerializer  │                │
│   │ (folder, canvas, stroke │   │ (Wire Protobuf)   │                │
│   │  tables)                │   │                    │                │
│   └─────────────────────────┘   └──────────────────┘                │
│   ┌─────────────────────────┐                                       │
│   │ File Storage            │                                       │
│   │ (PDF backings, exports) │                                       │
│   └─────────────────────────┘                                       │
└──────────────────────────────────────────────────────────────────────┘

               PLATFORM LAYER (below CanvasViewModel)

┌──────────────────────────────┐  ┌──────────────────────────────────┐
│       androidMain            │  │       iosMain (future)           │
│                              │  │                                  │
│  StrokeInputHandler          │  │  PencilInputHandler              │
│  InProgressStrokesView       │  │  PKCanvasView                    │
│  CommittedStrokeView         │  │  PencilKitAdapter                │
│  BrushProvider               │  │                                  │
│  StrokeAdapter               │  │                                  │
│  TemplateRenderer            │  │                                  │
│  PdfPageRenderer             │  │                                  │
└──────────────────────────────┘  └──────────────────────────────────┘
```

### KMP Module Structure

```
shared/
  commonMain/
    model/          Stroke, Canvas, Folder, enums
    state/          CanvasViewModel, CanvasState, DrawCommand, UndoRedoManager
    spatial/        StrokeSpatialIndex
    persistence/    StrokeSerializer, AutosaveManager, repository interfaces
    export/         DraftyExporter, DraftyImporter, PdfImportUseCase
    sqldelight/     .sq schema files

  androidMain/
    input/          StrokeInputHandler, GestureHandler, EraserHandler
    rendering/      BrushProvider, CommittedStrokeView, StrokeCommitHandler,
                    TemplateRenderer, PdfPageRenderer, RenderableStrokeCache
    adapter/        StrokeAdapter (androidx.ink <-> shared Stroke)
    composable/     DrawingCanvas (AndroidView interop)

  iosMain/          (future)
    input/          PencilInputHandler
    rendering/      PencilKitCanvasView
    adapter/        StrokeAdapter (PencilKit <-> shared Stroke)
```

---

## 2. Rendering Pipeline

### Layer Composition

The canvas screen composites four layers from back to front:

```
┌────────────────────────────────────────────────┐
│  Layer 4: FRONT BUFFER (live stroke)           │
│  InProgressStrokesView overlay                 │
│  Only the stroke currently being drawn.        │
│  Renders directly to display, bypasses the     │
│  normal double-buffered pipeline.              │
├────────────────────────────────────────────────┤
│  Layer 3: INK STROKES (back buffer)            │
│  CanvasStrokeRenderer draws committed pen      │
│  strokes to a Canvas/View.                     │
├────────────────────────────────────────────────┤
│  Layer 2: HIGHLIGHTER STROKES                  │
│  Rendered before ink strokes so highlights     │
│  appear behind pen ink.                        │
├────────────────────────────────────────────────┤
│  Layer 1: TEMPLATE / PDF                       │
│  Static background: ruled lines, grid, dot     │
│  pattern, or rendered PDF page.                │
└────────────────────────────────────────────────┘
```

These are not four separate GPU surfaces. The implementation uses two actual rendering surfaces:

1. **InProgressStrokesView** — a `FrameLayout` subclass from `androidx.ink:ink-authoring` that internally manages a `SurfaceView` with front-buffered rendering. This is an overlay view on top of everything.
2. **CommittedStrokeView** — a single custom `View` underneath that composites layers 1-3 during its `onDraw` pass (template/PDF first, then highlighter strokes, then ink strokes).

### Dual-Buffer Architecture

```
                  ACTION_DOWN / ACTION_MOVE
                        │
                        ▼
            ┌──────────────────────┐
            │  InProgressStrokesView│
            │  (FRONT BUFFER)      │
            │                      │
            │  Draws partial stroke │
            │  directly to display │
            │  via front buffer.   │
            │                      │
            │  Motion prediction   │
            │  adds ~1 frame of    │
            │  look-ahead points.  │
            └──────────┬───────────┘
                       │
                  ACTION_UP (stroke finished)
                       │
                       ▼
            ┌──────────────────────────────────────────┐
            │  Stroke Commit Flow                      │
            │                                          │
            │  1. InProgressStrokesView emits finished │
            │     androidx.ink.strokes.Stroke           │
            │  2. Convert to shared Stroke model       │
            │  3. Add to CanvasState.strokes            │
            │  4. Push AddStrokeCommand to undo stack  │
            │  5. Invalidate CommittedStrokeView       │
            │  6. Remove finished stroke from          │
            │     InProgressStrokesView                │
            └──────────────────────────────────────────┘
                       │
                       ▼
            ┌──────────────────────────────────────────┐
            │  CommittedStrokeView (BACK BUFFER)       │
            │                                          │
            │  onDraw(canvas):                         │
            │    1. drawTemplate(canvas, visibleRegion) │
            │    2. for each visible highlighter stroke:│
            │         renderer.draw(canvas, stroke)    │
            │    3. for each visible ink stroke:        │
            │         renderer.draw(canvas, stroke)    │
            └──────────────────────────────────────────┘
```

### Stroke Commit Handler

When `InProgressStrokesView` signals a stroke is finished, the commit handler bridges from the platform stroke to the shared model:

```kotlin
// androidMain/rendering/StrokeCommitHandler.kt

class StrokeCommitHandler(
    private val viewModel: CanvasViewModel,
    private val committedStrokeView: CommittedStrokeView,
) : InProgressStrokesView.FinishedStrokeListener {

    override fun onStrokeFinished(
        inProgressStrokesView: InProgressStrokesView,
        stroke: androidx.ink.strokes.Stroke,
    ) {
        val sharedStroke = stroke.toSharedStroke(viewModel.toolState.value)
        viewModel.commitStroke(sharedStroke)
        inProgressStrokesView.removeFinishedStrokes(setOf(stroke))
        committedStrokeView.invalidate()
    }
}
```

### Back-Buffer Rendering

```kotlin
// androidMain/rendering/CommittedStrokeView.kt

class CommittedStrokeView(context: Context) : View(context) {

    private val renderer = CanvasStrokeRenderer.create()
    private val strokeCache = RenderableStrokeCache()
    private var canvasState: CanvasState? = null
    private var viewTransform = Matrix()
    private var visibleRegion = BoundingBox(0f, 0f, 0f, 0f)

    fun bind(state: CanvasState) {
        canvasState = state
        invalidate()
    }

    fun updateTransform(transform: Matrix, visible: BoundingBox) {
        viewTransform = transform
        visibleRegion = visible
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val state = canvasState ?: return
        canvas.save()
        canvas.concat(viewTransform)

        // Layer 1: Template or PDF
        state.templateRenderer?.draw(canvas, visibleRegion)

        // Query spatial index for visible strokes only
        val visibleIds = state.spatialIndex.query(visibleRegion)

        // Layer 2: Highlighter strokes (drawn first = behind ink)
        for (id in visibleIds) {
            val stroke = state.strokeById(id) ?: continue
            if (stroke.toolType != ToolType.Highlighter) continue
            val renderable = strokeCache.getOrCreate(stroke)
            renderer.draw(canvas, renderable.inkStroke)
        }

        // Layer 3: Ink strokes
        for (id in visibleIds) {
            val stroke = state.strokeById(id) ?: continue
            if (stroke.toolType != ToolType.Pen) continue
            val renderable = strokeCache.getOrCreate(stroke)
            renderer.draw(canvas, renderable.inkStroke)
        }

        canvas.restore()
    }
}
```

### Off-Screen Culling: Spatial Index

With thousands of strokes, drawing only visible strokes is critical. A grid-based spatial index provides O(visible) lookup instead of O(all):

```kotlin
// commonMain/spatial/StrokeSpatialIndex.kt

class StrokeSpatialIndex(private val cellSize: Float = 256f) {

    private val cells = HashMap<Long, MutableList<StrokeId>>()
    private val strokeBoxes = HashMap<StrokeId, BoundingBox>()

    fun insert(stroke: Stroke) {
        strokeBoxes[stroke.id] = stroke.boundingBox
        forEachCell(stroke.boundingBox) { key ->
            cells.getOrPut(key) { mutableListOf() }.add(stroke.id)
        }
    }

    fun remove(strokeId: StrokeId) {
        val box = strokeBoxes.remove(strokeId) ?: return
        forEachCell(box) { key -> cells[key]?.remove(strokeId) }
    }

    fun query(visible: BoundingBox): Set<StrokeId> {
        val result = mutableSetOf<StrokeId>()
        forEachCell(visible) { key ->
            cells[key]?.let { result.addAll(it) }
        }
        return result
    }

    private inline fun forEachCell(box: BoundingBox, action: (Long) -> Unit) {
        val minCol = (box.minX / cellSize).toInt()
        val maxCol = (box.maxX / cellSize).toInt()
        val minRow = (box.minY / cellSize).toInt()
        val maxRow = (box.maxY / cellSize).toInt()
        for (row in minRow..maxRow) {
            for (col in minCol..maxCol) {
                action((col.toLong() shl 32) or (row.toLong() and 0xFFFFFFFFL))
            }
        }
    }
}
```

### RenderableStroke Cache

Avoids converting shared `Stroke` objects to `androidx.ink.strokes.Stroke` on every frame:

```kotlin
// androidMain/rendering/RenderableStrokeCache.kt

class RenderableStrokeCache {
    private val cache = HashMap<StrokeId, RenderableStroke>()

    fun getOrCreate(stroke: Stroke): RenderableStroke {
        return cache.getOrPut(stroke.id) {
            RenderableStroke(
                sharedStroke = stroke,
                inkStroke = stroke.toInkStroke(),
            )
        }
    }

    fun remove(id: StrokeId) { cache.remove(id) }
    fun clear() { cache.clear() }
}

class RenderableStroke(
    val sharedStroke: Stroke,
    val inkStroke: androidx.ink.strokes.Stroke,
)
```

### Template Rendering

Templates only draw elements within the visible viewport:

```kotlin
// androidMain/rendering/TemplateRenderer.kt

interface TemplateRenderer {
    fun draw(canvas: Canvas, visibleRegion: BoundingBox)
}

class LinedTemplateRenderer(
    private val lineSpacing: Float = 32f,
    private val lineColor: Int = 0xFFD0D0D0.toInt(),
) : TemplateRenderer {
    private val paint = Paint().apply {
        color = lineColor; strokeWidth = 1f; style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas, visibleRegion: BoundingBox) {
        val firstLine = ((visibleRegion.minY / lineSpacing).toInt()) * lineSpacing
        var y = firstLine
        while (y <= visibleRegion.maxY) {
            canvas.drawLine(visibleRegion.minX, y, visibleRegion.maxX, y, paint)
            y += lineSpacing
        }
    }
}
```

### PDF Page Rendering

PDF pages are rasterized to a cached bitmap, re-rendered only when zoom changes significantly:

```kotlin
// androidMain/rendering/PdfPageRenderer.kt

class PdfPageRenderer(
    private val pdfRenderer: android.graphics.pdf.PdfRenderer,
    private val pageIndex: Int,
) : TemplateRenderer {
    private var cachedBitmap: Bitmap? = null
    private var cachedZoomLevel: Float = 0f

    override fun draw(canvas: Canvas, visibleRegion: BoundingBox) {
        val currentZoom = /* derived from canvas transform */
        if (cachedBitmap == null || shouldReRender(currentZoom)) {
            cachedBitmap = renderPage(currentZoom)
            cachedZoomLevel = currentZoom
        }
        canvas.drawBitmap(cachedBitmap!!, /* position */)
    }

    private fun shouldReRender(zoom: Float): Boolean {
        return zoom / cachedZoomLevel > 2f || cachedZoomLevel / zoom > 2f
    }
}
```

---

## 3. Stylus Input Pipeline

### Pipeline Stages

```
MotionEvent (system)
      │
      ▼
┌─────────────────────────┐
│  1. Palm Rejection       │  Reject TOOL_TYPE_FINGER when stylus-only
│     Filter               │  mode active. Check FLAG_CANCELED.
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  2. InProgressStrokesView│  startStroke() on ACTION_DOWN
│     (androidx.ink)       │  addToStroke() on ACTION_MOVE
│                          │  finishStroke() on ACTION_UP
│                          │  cancelStroke() on ACTION_CANCEL
│                          │
│  Internal: batched event │  Handles getHistoricalX/Y/Pressure,
│  unpacking, motion       │  Kalman filter prediction,
│  prediction, front-      │  front-buffer rendering.
│  buffer rendering.       │
└────────┬────────────────┘
         │  (stroke finished callback)
         ▼
┌─────────────────────────┐
│  3. Stroke Commit        │  Convert to shared Stroke model.
│                          │  Push AddStrokeCommand.
│                          │  Trigger back-buffer re-render.
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  4. Autosave             │  Serialize via Protobuf (Wire).
│     (commonMain)         │  Write BLOB to SQLDelight.
│                          │  300ms debounced.
└─────────────────────────┘
```

### Input Handler

```kotlin
// androidMain/input/StrokeInputHandler.kt

class StrokeInputHandler(
    private val inProgressStrokesView: InProgressStrokesView,
    private val brushProvider: BrushProvider,
    private val canvasTransform: () -> Matrix,
    private val inputConfig: () -> InputConfig,
) {
    fun onTouchEvent(event: MotionEvent): Boolean {
        val config = inputConfig()

        // Palm rejection
        if (config.stylusOnly && event.getToolType(0) != TOOL_TYPE_STYLUS) {
            return false // finger events fall through to gesture detector
        }
        if ((event.flags and MotionEvent.FLAG_CANCELED) != 0) {
            inProgressStrokesView.cancelStroke(event, event.getPointerId(0))
            return true
        }

        val pointerId = event.getPointerId(event.actionIndex)

        when (event.actionMasked) {
            ACTION_DOWN -> {
                inProgressStrokesView.requestUnbufferedDispatch(event)
                inProgressStrokesView.startStroke(
                    event = event,
                    pointerId = pointerId,
                    brush = brushProvider.currentBrush(),
                    transform = canvasTransform(),
                )
            }
            ACTION_MOVE -> inProgressStrokesView.addToStroke(event, pointerId)
            ACTION_UP -> inProgressStrokesView.finishStroke(event, pointerId)
            ACTION_CANCEL -> inProgressStrokesView.cancelStroke(event, pointerId)
        }
        return true
    }
}

data class InputConfig(val stylusOnly: Boolean = true)
```

### Brush Mapping

Translates the shared tool state into `androidx.ink.brush.Brush` instances:

```kotlin
// androidMain/rendering/BrushProvider.kt

class BrushProvider(private val toolState: StateFlow<ActiveToolState>) {

    fun currentBrush(): Brush {
        val state = toolState.value
        return when (state.toolType) {
            ToolType.Pen -> Brush.createWithColorIntArgb(
                family = StockBrushes.pressurePenLatest,
                colorIntArgb = state.colorArgb,
                size = state.width,
                epsilon = 0.1f,
            )
            ToolType.Highlighter -> Brush.createWithColorIntArgb(
                family = StockBrushes.highlighterLatest,
                colorIntArgb = state.colorArgb,
                size = state.width,
                epsilon = 0.1f,
            )
            ToolType.Eraser -> Brush.createWithColorIntArgb(
                family = StockBrushes.pressurePenLatest,
                colorIntArgb = Color.TRANSPARENT,
                size = state.width,
                epsilon = 0.1f,
            )
        }
    }
}
```

Pressure and velocity curves are handled internally by `StockBrushes.pressurePenLatest` and `highlighterLatest` brush families. If custom tuning is needed, a custom `BrushFamily` can be constructed with explicit `BrushBehavior` nodes controlling pressure-to-width and velocity-to-width mappings.

### Eraser Hit-Testing

The eraser uses the spatial index for efficient stroke lookup, then `androidx.ink:ink-geometry` for precise hit testing:

```kotlin
// androidMain/input/EraserHandler.kt

class EraserHandler(
    private val viewModel: CanvasViewModel,
    private val spatialIndex: StrokeSpatialIndex,
) {
    /** Stroke eraser: tap to remove the entire stroke under the touch point. */
    fun strokeErase(point: InputPoint) {
        val hitBox = BoundingBox(
            point.x - HIT_RADIUS, point.y - HIT_RADIUS,
            point.x + HIT_RADIUS, point.y + HIT_RADIUS,
        )
        val candidates = spatialIndex.query(hitBox)
        for (id in candidates) {
            val stroke = viewModel.state.value.strokeById(id) ?: continue
            if (strokeContainsPoint(stroke, point)) {
                viewModel.eraseStroke(id)
                return
            }
        }
    }

    /** Partial eraser: split strokes where the eraser path crosses them. */
    fun partialErase(eraserPath: List<InputPoint>) {
        // Build eraser shape from path with width.
        // Use ink-geometry to find intersecting strokes and compute splits.
        // For each split: viewModel.erasePartial(strokeId, splitResult)
    }

    companion object { private const val HIT_RADIUS = 8f }
}
```

### Compose Integration Point

The drawing canvas is hosted via `AndroidView` interop in Compose:

```kotlin
// androidMain/composable/DrawingCanvas.kt

@Composable
fun DrawingCanvas(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier,
) {
    val canvasState by viewModel.state.collectAsState()

    AndroidView(
        factory = { context ->
            val root = FrameLayout(context)
            val committedView = CommittedStrokeView(context)
            val inProgressView = InProgressStrokesView(context)

            root.addView(committedView, matchParent())
            root.addView(inProgressView, matchParent())

            val brushProvider = BrushProvider(viewModel.toolState)
            val inputHandler = StrokeInputHandler(
                inProgressStrokesView = inProgressView,
                brushProvider = brushProvider,
                canvasTransform = { committedView.currentTransform },
                inputConfig = { InputConfig(stylusOnly = true) },
            )
            val commitHandler = StrokeCommitHandler(viewModel, committedView)
            inProgressView.addFinishedStrokeListener(commitHandler)

            root.setOnTouchListener { _, event -> inputHandler.onTouchEvent(event) }
            root.tag = RenderingComponents(committedView, inProgressView, inputHandler)
            root
        },
        update = { root ->
            val components = root.tag as RenderingComponents
            components.committedView.bind(canvasState)
        },
        modifier = modifier.fillMaxSize(),
    )
}
```

---

## 4. Data Model

### Enums

```kotlin
// commonMain/model/ToolType.kt
enum class ToolType { Pen, Highlighter, Eraser }

// commonMain/model/Template.kt
enum class Template { Blank, Lined, Grid, Dotted }

// commonMain/model/FolderColor.kt
enum class FolderColor { Red, Orange, Yellow, Green, Blue, Purple, Pink, Gray }
```

### Core Entities

```kotlin
// commonMain/model/StrokeId.kt
@JvmInline
value class StrokeId(val value: String)

// commonMain/model/InputPoint.kt
data class InputPoint(
    val x: Float,
    val y: Float,
    val pressure: Float,        // 0.0..1.0 normalized
    val tiltRadians: Float,     // 0 = perpendicular, π/2 = flat
    val orientationRadians: Float,
    val timestampMs: Long,
)

// commonMain/model/BoundingBox.kt
data class BoundingBox(
    val minX: Float, val minY: Float,
    val maxX: Float, val maxY: Float,
) {
    fun intersects(other: BoundingBox): Boolean =
        minX <= other.maxX && maxX >= other.minX &&
        minY <= other.maxY && maxY >= other.minY
}

// commonMain/model/Stroke.kt
data class Stroke(
    val id: StrokeId,
    val canvasId: String,
    val sortOrder: Int,          // z-order on canvas (monotonically increasing)
    val toolType: ToolType,
    val colorArgb: Long,         // Long avoids sign-extension bugs with high-alpha ARGB
    val baseWidth: Float,
    val points: List<InputPoint>,
    val boundingBox: BoundingBox, // pre-computed for culling
)

// commonMain/model/Folder.kt
data class Folder(
    val id: String,
    val parentId: String?,       // null = root level
    val title: String,
    val color: FolderColor,
    val created: Long,           // epoch millis
    val modified: Long,
    val sortOrder: Int,
)

// commonMain/model/Canvas.kt
data class Canvas(
    val id: String,
    val folderId: String?,       // null = root level (unfiled)
    val title: String,
    val template: Template,
    val created: Long,
    val modified: Long,
    val thumbnail: ByteArray?,   // PNG bytes
    val pdfBackingPath: String?, // non-null if created from PDF import
    val pdfPageIndex: Int?,      // which page of the backing PDF (0-based)
) {
    /** Lightweight projection for library listing (no thumbnail bytes). */
    data class Summary(
        val id: String,
        val folderId: String?,
        val title: String,
        val template: Template,
        val created: Long,
        val modified: Long,
        val hasThumbnail: Boolean,
    )
}
```

### Stroke Adapter (Platform Conversion)

```kotlin
// androidMain/adapter/StrokeAdapter.kt

/** Convert a finished androidx.ink Stroke to the shared model. */
fun androidx.ink.strokes.Stroke.toSharedStroke(toolState: ActiveToolState): Stroke {
    val inputs = this.inputs
    val points = buildList(inputs.size) {
        for (i in 0 until inputs.size) {
            add(InputPoint(
                x = inputs.getX(i),
                y = inputs.getY(i),
                pressure = inputs.getPressure(i),
                tiltRadians = inputs.getTiltRadians(i),
                orientationRadians = inputs.getOrientationRadians(i),
                timestampMs = inputs.getTimestampMillis(i),
            ))
        }
    }
    return Stroke(
        id = StrokeId(uuid4().toString()),
        canvasId = toolState.canvasId,
        sortOrder = toolState.nextSortOrder(),
        toolType = toolState.toolType,
        colorArgb = this.brush.colorIntArgb.toLong(),
        baseWidth = this.brush.size,
        points = points,
        boundingBox = computeBoundingBox(points),
    )
}

/** Convert the shared model back to an androidx.ink Stroke for rendering. */
fun Stroke.toInkStroke(): androidx.ink.strokes.Stroke {
    // Reconstruct StrokeInputBatch from points, create Brush from toolType/color/width
    // Return new androidx.ink.strokes.Stroke(brush, inputs)
}
```

---

## 5. Storage Layer

### SQLDelight Schema

```sql
-- commonMain/sqldelight/com/drafty/db/Drafty.sq

-- ============================================================
-- FOLDER
-- ============================================================
CREATE TABLE folder (
    id          TEXT NOT NULL PRIMARY KEY,
    parent_id   TEXT REFERENCES folder(id) ON DELETE CASCADE,
    title       TEXT NOT NULL,
    color       TEXT NOT NULL DEFAULT 'Gray',
    created     INTEGER NOT NULL,
    modified    INTEGER NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX folder_parent ON folder(parent_id);

-- ============================================================
-- CANVAS
-- ============================================================
CREATE TABLE canvas (
    id               TEXT NOT NULL PRIMARY KEY,
    folder_id        TEXT REFERENCES folder(id) ON DELETE SET NULL,
    title            TEXT NOT NULL,
    template         TEXT NOT NULL DEFAULT 'Blank',
    created          INTEGER NOT NULL,
    modified         INTEGER NOT NULL,
    thumbnail        BLOB,
    pdf_backing_path TEXT,
    pdf_page_index   INTEGER
);

CREATE INDEX canvas_folder   ON canvas(folder_id);
CREATE INDEX canvas_modified ON canvas(modified);

-- ============================================================
-- STROKE
-- ============================================================
CREATE TABLE stroke (
    id          TEXT NOT NULL PRIMARY KEY,
    canvas_id   TEXT NOT NULL REFERENCES canvas(id) ON DELETE CASCADE,
    sort_order  INTEGER NOT NULL,
    tool_type   TEXT NOT NULL,
    color       INTEGER NOT NULL,
    size        REAL NOT NULL,
    point_data  BLOB NOT NULL
);

CREATE INDEX stroke_canvas ON stroke(canvas_id, sort_order);

-- ============================================================
-- QUERIES: Folder
-- ============================================================
selectRootFolders:
SELECT * FROM folder WHERE parent_id IS NULL ORDER BY sort_order, modified DESC;

selectFoldersByParent:
SELECT * FROM folder WHERE parent_id = ? ORDER BY sort_order, modified DESC;

selectFolderById:
SELECT * FROM folder WHERE id = ?;

insertFolder:
INSERT INTO folder (id, parent_id, title, color, created, modified, sort_order)
VALUES (?, ?, ?, ?, ?, ?, ?);

updateFolder:
UPDATE folder SET title = ?, color = ?, modified = ?, parent_id = ?, sort_order = ?
WHERE id = ?;

deleteFolder:
DELETE FROM folder WHERE id = ?;

-- ============================================================
-- QUERIES: Canvas
-- ============================================================
selectCanvasSummariesByFolder:
SELECT id, folder_id, title, template, created, modified,
       (thumbnail IS NOT NULL) AS has_thumbnail
FROM canvas
WHERE folder_id = ?
ORDER BY modified DESC;

selectRootCanvasSummaries:
SELECT id, folder_id, title, template, created, modified,
       (thumbnail IS NOT NULL) AS has_thumbnail
FROM canvas
WHERE folder_id IS NULL
ORDER BY modified DESC;

selectCanvasById:
SELECT * FROM canvas WHERE id = ?;

selectCanvasThumbnail:
SELECT thumbnail FROM canvas WHERE id = ?;

insertCanvas:
INSERT INTO canvas (id, folder_id, title, template, created, modified,
                    thumbnail, pdf_backing_path, pdf_page_index)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);

updateCanvasMetadata:
UPDATE canvas SET title = ?, template = ?, modified = ?, folder_id = ? WHERE id = ?;

updateCanvasThumbnail:
UPDATE canvas SET thumbnail = ?, modified = ? WHERE id = ?;

deleteCanvas:
DELETE FROM canvas WHERE id = ?;

-- ============================================================
-- QUERIES: Stroke
-- ============================================================
selectStrokesByCanvas:
SELECT * FROM stroke WHERE canvas_id = ? ORDER BY sort_order;

selectStrokeMetadataByCanvas:
SELECT id, canvas_id, sort_order, tool_type, color, size
FROM stroke WHERE canvas_id = ? ORDER BY sort_order;

selectStrokePointData:
SELECT point_data FROM stroke WHERE id = ?;

insertStroke:
INSERT INTO stroke (id, canvas_id, sort_order, tool_type, color, size, point_data)
VALUES (?, ?, ?, ?, ?, ?, ?);

deleteStroke:
DELETE FROM stroke WHERE id = ?;

deleteStrokesByCanvas:
DELETE FROM stroke WHERE canvas_id = ?;

nextSortOrder:
SELECT COALESCE(MAX(sort_order), 0) + 1 FROM stroke WHERE canvas_id = ?;
```

**Foreign key behavior:**
- `stroke.canvas_id → ON DELETE CASCADE` — deleting a canvas removes all its strokes.
- `canvas.folder_id → ON DELETE SET NULL` — deleting a folder moves canvases to root, never destroys them.
- `folder.parent_id → ON DELETE CASCADE` — deleting a parent folder removes subfolders. Canvases in deleted subfolders become unfiled (due to the SET NULL above).

### Protobuf Schema (Wire)

```protobuf
// commonMain/proto/stroke.proto

syntax = "proto3";
package com.drafty.storage;
option java_package = "com.drafty.storage.proto";

message StrokePointProto {
    float x = 1;
    float y = 2;
    float pressure = 3;
    float tilt_radians = 4;
    float orientation_radians = 5;
    sint64 timestamp_ms = 6;     // relative to first point (delta-friendly)
}

// Stored in stroke.point_data BLOB column.
message StrokePointListProto {
    repeated StrokePointProto points = 1;
    // Pre-computed bounding box for fast culling without full deserialization.
    float bounds_min_x = 2;
    float bounds_min_y = 3;
    float bounds_max_x = 4;
    float bounds_max_y = 5;
    // Absolute timestamp of first point — used to restore deltas to absolute values.
    sint64 base_timestamp_ms = 6;
}
```

**Design decision:** The bounding box is embedded in the protobuf blob rather than stored as SQL columns. This keeps the schema simple. If profiling on canvases with 5000+ strokes shows that deserializing blobs just to read bounds is a bottleneck, the escape hatch is to promote `bounds_min_x/y/max_x/y` to SQL columns on the `stroke` table and use SQL-side spatial filtering.

### Stroke Serializer

```kotlin
// commonMain/persistence/StrokeSerializer.kt

object StrokeSerializer {

    fun serialize(points: List<InputPoint>, bounds: BoundingBox): ByteArray {
        val baseTimestamp = points.firstOrNull()?.timestampMs ?: 0L
        val protos = points.map { pt ->
            StrokePointProto(
                x = pt.x, y = pt.y,
                pressure = pt.pressure,
                tilt_radians = pt.tiltRadians,
                orientation_radians = pt.orientationRadians,
                timestamp_ms = pt.timestampMs - baseTimestamp,
            )
        }
        return StrokePointListProto(
            points = protos,
            bounds_min_x = bounds.minX, bounds_min_y = bounds.minY,
            bounds_max_x = bounds.maxX, bounds_max_y = bounds.maxY,
            base_timestamp_ms = baseTimestamp,
        ).encode()
    }

    fun deserialize(data: ByteArray): DeserializedStroke {
        val proto = StrokePointListProto.ADAPTER.decode(data)
        val baseTimestamp = proto.base_timestamp_ms
        val points = proto.points.map { pt ->
            InputPoint(
                x = pt.x, y = pt.y,
                pressure = pt.pressure,
                tiltRadians = pt.tilt_radians,
                orientationRadians = pt.orientation_radians,
                timestampMs = pt.timestamp_ms + baseTimestamp,
            )
        }
        val bounds = BoundingBox(
            proto.bounds_min_x, proto.bounds_min_y,
            proto.bounds_max_x, proto.bounds_max_y,
        )
        return DeserializedStroke(points, bounds)
    }

    fun deserializeBounds(data: ByteArray): BoundingBox {
        val proto = StrokePointListProto.ADAPTER.decode(data)
        return BoundingBox(
            proto.bounds_min_x, proto.bounds_min_y,
            proto.bounds_max_x, proto.bounds_max_y,
        )
    }
}

data class DeserializedStroke(val points: List<InputPoint>, val bounds: BoundingBox)
```

### Repository Interfaces

```kotlin
// commonMain/persistence/FolderRepository.kt
interface FolderRepository {
    fun getRootFolders(): Flow<List<Folder>>
    fun getFoldersByParent(parentId: String): Flow<List<Folder>>
    suspend fun getFolderById(id: String): Folder?
    suspend fun insert(folder: Folder)
    suspend fun update(folder: Folder)
    suspend fun delete(id: String)
}

// commonMain/persistence/CanvasRepository.kt
interface CanvasRepository {
    fun getRootCanvasSummaries(): Flow<List<Canvas.Summary>>
    fun getCanvasSummariesByFolder(folderId: String): Flow<List<Canvas.Summary>>
    suspend fun getCanvasById(id: String): Canvas?
    suspend fun getThumbnail(canvasId: String): ByteArray?
    suspend fun insert(canvas: Canvas)
    suspend fun updateMetadata(canvas: Canvas)
    suspend fun updateThumbnail(canvasId: String, thumbnail: ByteArray)
    suspend fun delete(id: String)
}

// commonMain/persistence/StrokeRepository.kt
interface StrokeRepository {
    suspend fun getStrokesByCanvas(canvasId: String): List<Stroke>
    suspend fun getStrokeMetadataByCanvas(canvasId: String): List<StrokeMetadata>
    suspend fun getStrokePointData(strokeId: String): ByteArray?
    suspend fun insert(stroke: Stroke)
    suspend fun delete(strokeId: String)
    suspend fun deleteByCanvas(canvasId: String)
    suspend fun getNextSortOrder(canvasId: String): Int
}

data class StrokeMetadata(
    val id: String,
    val canvasId: String,
    val sortOrder: Int,
    val toolType: ToolType,
    val color: Long,
    val size: Float,
)
```

---

## 6. State Management

### Canvas ViewModel

```kotlin
// commonMain/state/CanvasViewModel.kt

class CanvasViewModel(
    private val canvasRepository: CanvasRepository,
    private val strokeRepository: StrokeRepository,
) {
    private val _state = MutableStateFlow(CanvasState())
    val state: StateFlow<CanvasState> = _state.asStateFlow()

    private val _toolState = MutableStateFlow(ActiveToolState())
    val toolState: StateFlow<ActiveToolState> = _toolState.asStateFlow()

    private val undoRedoManager = UndoRedoManager()
    val canUndo: StateFlow<Boolean> = undoRedoManager.canUndo
    val canRedo: StateFlow<Boolean> = undoRedoManager.canRedo

    lateinit var autosaveManager: AutosaveManager

    fun commitStroke(stroke: Stroke) {
        undoRedoManager.execute(AddStrokeCommand(stroke, autosaveManager), _state)
    }

    fun eraseStroke(strokeId: StrokeId) {
        val stroke = _state.value.strokeById(strokeId) ?: return
        undoRedoManager.execute(EraseStrokeCommand(stroke, autosaveManager), _state)
    }

    fun erasePartial(strokeId: StrokeId, splitResult: List<Stroke>) {
        val original = _state.value.strokeById(strokeId) ?: return
        undoRedoManager.execute(
            PartialEraseCommand(original, splitResult, autosaveManager), _state
        )
    }

    fun undo() { undoRedoManager.undo(_state) }
    fun redo() { undoRedoManager.redo(_state) }

    fun selectTool(toolType: ToolType) {
        _toolState.update { it.copy(toolType = toolType) }
    }
    fun setColor(argb: Long) {
        _toolState.update { it.copy(colorArgb = argb.toInt()) }
    }
    fun setWidth(width: Float) {
        _toolState.update { it.copy(width = width) }
    }
}

// commonMain/state/CanvasState.kt

data class CanvasState(
    val canvasId: String? = null,
    val strokes: List<Stroke> = emptyList(),
    val spatialIndex: StrokeSpatialIndex = StrokeSpatialIndex(),
    val template: Template = Template.Blank,
    val pdfPageIndex: Int? = null,
) {
    val highlighterStrokes: List<Stroke>
        get() = strokes.filter { it.toolType == ToolType.Highlighter }
    val inkStrokes: List<Stroke>
        get() = strokes.filter { it.toolType == ToolType.Pen }

    fun strokeById(id: StrokeId): Stroke? = strokes.find { it.id == id }
}

data class ActiveToolState(
    val canvasId: String = "",
    val toolType: ToolType = ToolType.Pen,
    val colorArgb: Int = 0xFF000000.toInt(),
    val width: Float = 1.5f,
)
```

### Command Pattern

```kotlin
// commonMain/state/DrawCommand.kt

interface DrawCommand {
    fun execute(state: MutableStateFlow<CanvasState>)
    fun undo(state: MutableStateFlow<CanvasState>)
}

class AddStrokeCommand(
    private val stroke: Stroke,
    private val autosave: AutosaveManager,
) : DrawCommand {
    override fun execute(state: MutableStateFlow<CanvasState>) {
        state.update { it.copy(strokes = it.strokes + stroke) }
        state.value.spatialIndex.insert(stroke)
        autosave.onStrokeAdded(stroke)
    }
    override fun undo(state: MutableStateFlow<CanvasState>) {
        state.update { it.copy(strokes = it.strokes.filter { s -> s.id != stroke.id }) }
        state.value.spatialIndex.remove(stroke.id)
        autosave.onStrokeDeleted(stroke.id)
    }
}

class EraseStrokeCommand(
    private val stroke: Stroke,
    private val autosave: AutosaveManager,
) : DrawCommand {
    override fun execute(state: MutableStateFlow<CanvasState>) {
        state.update { it.copy(strokes = it.strokes.filter { s -> s.id != stroke.id }) }
        state.value.spatialIndex.remove(stroke.id)
        autosave.onStrokeDeleted(stroke.id)
    }
    override fun undo(state: MutableStateFlow<CanvasState>) {
        state.update { it.copy(strokes = it.strokes + stroke) }
        state.value.spatialIndex.insert(stroke)
        autosave.onStrokeAdded(stroke)
    }
}

class PartialEraseCommand(
    private val original: Stroke,
    private val splitResult: List<Stroke>,
    private val autosave: AutosaveManager,
) : DrawCommand {
    override fun execute(state: MutableStateFlow<CanvasState>) {
        state.update { s ->
            s.copy(strokes = s.strokes.filter { it.id != original.id } + splitResult)
        }
        state.value.spatialIndex.remove(original.id)
        splitResult.forEach { state.value.spatialIndex.insert(it) }
        autosave.onStrokeDeleted(original.id)
        splitResult.forEach { autosave.onStrokeAdded(it) }
    }
    override fun undo(state: MutableStateFlow<CanvasState>) {
        val splitIds = splitResult.map { it.id }.toSet()
        state.update { s ->
            s.copy(strokes = s.strokes.filter { it.id !in splitIds } + original)
        }
        splitResult.forEach { state.value.spatialIndex.remove(it.id) }
        state.value.spatialIndex.insert(original)
        splitResult.forEach { autosave.onStrokeDeleted(it.id) }
        autosave.onStrokeAdded(original)
    }
}

class LassoMoveCommand(
    private val strokeIds: List<StrokeId>,
    private val deltaX: Float,
    private val deltaY: Float,
    private val autosave: AutosaveManager,
) : DrawCommand {
    override fun execute(state: MutableStateFlow<CanvasState>) {
        translateStrokes(state, deltaX, deltaY)
    }
    override fun undo(state: MutableStateFlow<CanvasState>) {
        translateStrokes(state, -deltaX, -deltaY)
    }
    private fun translateStrokes(state: MutableStateFlow<CanvasState>, dx: Float, dy: Float) {
        val idsSet = strokeIds.toSet()
        state.update { s ->
            s.copy(strokes = s.strokes.map { stroke ->
                if (stroke.id in idsSet) {
                    val translated = stroke.points.map { it.copy(x = it.x + dx, y = it.y + dy) }
                    stroke.copy(
                        points = translated,
                        boundingBox = computeBoundingBox(translated),
                    )
                } else stroke
            })
        }
        // Update spatial index: remove old positions, re-insert with new bounding boxes
        for (id in strokeIds) {
            state.value.spatialIndex.remove(id)
            val updated = state.value.strokeById(id) ?: continue
            state.value.spatialIndex.insert(updated)
            autosave.onStrokeModified(updated)
        }
    }
}

class LassoRecolorCommand(
    private val strokeIds: List<StrokeId>,
    private val newColor: Long,
    private val previousColors: Map<StrokeId, Long>,
    private val autosave: AutosaveManager,
) : DrawCommand {
    override fun execute(state: MutableStateFlow<CanvasState>) {
        state.update { s ->
            s.copy(strokes = s.strokes.map { stroke ->
                if (stroke.id in strokeIds) stroke.copy(colorArgb = newColor) else stroke
            })
        }
        for (id in strokeIds) {
            val updated = state.value.strokeById(id) ?: continue
            autosave.onStrokeModified(updated)
        }
    }
    override fun undo(state: MutableStateFlow<CanvasState>) {
        state.update { s ->
            s.copy(strokes = s.strokes.map { stroke ->
                val original = previousColors[stroke.id]
                if (original != null) stroke.copy(colorArgb = original) else stroke
            })
        }
        for ((id, _) in previousColors) {
            val restored = state.value.strokeById(id) ?: continue
            autosave.onStrokeModified(restored)
        }
    }
}

class LassoDeleteCommand(
    private val strokes: List<Stroke>,
    private val autosave: AutosaveManager,
) : DrawCommand {
    override fun execute(state: MutableStateFlow<CanvasState>) {
        val ids = strokes.map { it.id }.toSet()
        state.update { s -> s.copy(strokes = s.strokes.filter { it.id !in ids }) }
        ids.forEach { state.value.spatialIndex.remove(it) }
        ids.forEach { autosave.onStrokeDeleted(it) }
    }
    override fun undo(state: MutableStateFlow<CanvasState>) {
        state.update { s -> s.copy(strokes = s.strokes + strokes) }
        strokes.forEach { state.value.spatialIndex.insert(it) }
        strokes.forEach { autosave.onStrokeAdded(it) }
    }
}
```

### Undo/Redo Manager

```kotlin
// commonMain/state/UndoRedoManager.kt

class UndoRedoManager {
    private val undoStack = ArrayDeque<DrawCommand>()
    private val redoStack = ArrayDeque<DrawCommand>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    fun execute(command: DrawCommand, state: MutableStateFlow<CanvasState>) {
        command.execute(state)
        undoStack.addLast(command)
        redoStack.clear()
        updateFlags()
    }

    fun undo(state: MutableStateFlow<CanvasState>) {
        val command = undoStack.removeLastOrNull() ?: return
        command.undo(state)
        redoStack.addLast(command)
        updateFlags()
    }

    fun redo(state: MutableStateFlow<CanvasState>) {
        val command = redoStack.removeLastOrNull() ?: return
        command.execute(state)
        undoStack.addLast(command)
        updateFlags()
    }

    private fun updateFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
```

Undo/redo stacks are in-memory and per-session. Closing a canvas clears the stacks. The persisted state is always the result of all executed (non-undone) commands.

### Autosave Manager

```kotlin
// commonMain/persistence/AutosaveManager.kt

class AutosaveManager(
    private val strokeRepository: StrokeRepository,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val pendingInserts = mutableListOf<Stroke>()
    private val pendingDeletes = mutableListOf<StrokeId>()
    private var flushJob: Job? = null
    private val debounceMs = 300L

    fun onStrokeAdded(stroke: Stroke) {
        synchronized(this) { pendingInserts.add(stroke) }
        scheduleFlush()
    }

    fun onStrokeDeleted(strokeId: StrokeId) {
        synchronized(this) {
            val removedFromPending = pendingInserts.removeAll { it.id == strokeId }
            if (!removedFromPending) pendingDeletes.add(strokeId)
        }
        scheduleFlush()
    }

    fun onStrokeModified(stroke: Stroke) {
        synchronized(this) {
            // If stroke is still in pending inserts, just replace it there
            val idx = pendingInserts.indexOfFirst { it.id == stroke.id }
            if (idx >= 0) {
                pendingInserts[idx] = stroke
            } else {
                // Delete old version + insert updated version
                pendingDeletes.add(stroke.id)
                pendingInserts.add(stroke)
            }
        }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = scope.launch(dispatcher) {
            delay(debounceMs)
            flush()
        }
    }

    /** Force-flush. Called on canvas exit and app background. */
    suspend fun flush() {
        val inserts: List<Stroke>
        val deletes: List<StrokeId>
        synchronized(this) {
            inserts = pendingInserts.toList()
            deletes = pendingDeletes.toList()
            pendingInserts.clear()
            pendingDeletes.clear()
        }
        if (inserts.isEmpty() && deletes.isEmpty()) return

        // Single transaction for atomicity and single fsync
        deletes.forEach { strokeRepository.delete(it.value) }
        inserts.forEach { strokeRepository.insert(it) }
    }
}
```

**Autosave behavior:**
- 300ms debounce coalesces rapid strokes into one DB transaction.
- Rapid undo/redo that returns to the same state produces zero DB writes (pending insert cancelled by pending delete).
- Force-flush on `Lifecycle.ON_STOP` and `ViewModel.onCleared()` guarantees no data loss.
- In-memory state is the source of truth for rendering — DB writes never block the UI.

---

## 7. Export/Import

### .drafty File Format

A `.drafty` file is a ZIP archive:

```
canvas_<id>.drafty (ZIP)
├── manifest.json             // version, export date, stroke count
├── canvas.json               // canvas metadata (title, template, dates)
├── strokes/
│   ├── 00001.bin             // protobuf StrokeExportProto
│   ├── 00002.bin
│   └── ...
├── thumbnail.png             // optional
└── pdf/
    └── backing.pdf           // only if canvas was created from PDF import
```

JSON for the manifest and canvas metadata (human-readable, debuggable). Protobuf for stroke data (compact).

```protobuf
// Additional proto definitions for export

message StrokeExportProto {
    string id = 1;
    int32 sort_order = 2;
    string tool_type = 3;
    int64 color = 4;
    float size = 5;
    StrokePointListProto point_data = 6;
}
```

### PDF Import

PDF import creates one canvas per page. All canvases reference the same PDF file stored in app-local storage:

```kotlin
// commonMain/export/PdfImportUseCase.kt

class PdfImportUseCase(
    private val canvasRepository: CanvasRepository,
    private val folderRepository: FolderRepository,
    private val pdfRenderer: PlatformPdfRenderer, // expect/actual
) {
    suspend fun importPdf(pdfSourcePath: String, fileName: String): String {
        val storedPath = copyToAppStorage(pdfSourcePath)
        val pageCount = pdfRenderer.getPageCount(storedPath)
        val now = currentTimeMillis()
        val folderId = generateUuid()

        // Create a folder named after the PDF
        folderRepository.insert(Folder(
            id = folderId, parentId = null, title = fileName.removeSuffix(".pdf"),
            color = FolderColor.Blue, created = now, modified = now, sortOrder = 0,
        ))

        // One canvas per page
        for (i in 0 until pageCount) {
            val thumbnail = pdfRenderer.renderPage(storedPath, i, 400, 560)
            canvasRepository.insert(Canvas(
                id = generateUuid(), folderId = folderId,
                title = "Page ${i + 1}", template = Template.Blank,
                created = now, modified = now, thumbnail = thumbnail,
                pdfBackingPath = storedPath, pdfPageIndex = i,
            ))
        }
        return folderId
    }
}
```

### PDF/PNG Export

Platform-specific renderers handle export:

```kotlin
// expect/actual for each platform
expect class CanvasExportRenderer {
    suspend fun renderToPdf(canvas: Canvas, strokes: List<Stroke>, outputPath: String)
    suspend fun renderToPng(canvas: Canvas, strokes: List<Stroke>, outputPath: String, scale: Float)
}
```

On Android, `renderToPdf` uses `android.graphics.pdf.PdfDocument` and `renderToPng` uses `Bitmap` + PNG compression. Both composite the PDF backing (if any), template, and all strokes in the correct layer order.

---

## 8. Module Boundaries

### Abstraction Boundary

The boundary is at the **Stroke model and CanvasViewModel**. Everything above is shared KMP code; everything below is platform-native:

```
┌────────────────────────────────────────────────────────────┐
│                     commonMain                              │
│                                                             │
│  CanvasViewModel  ←──  UndoRedoManager                     │
│       │                      │                              │
│  StateFlow<CanvasState>     DrawCommand                    │
│       │                                                     │
│  Stroke, InputPoint, BoundingBox, StrokeSpatialIndex       │
│       │                                                     │
│  CanvasRepository, StrokeRepository (interfaces)           │
│  StrokeSerializer (Protobuf via Wire)                      │
│  AutosaveManager, DraftyExporter, DraftyImporter           │
├────────────────────────────────────────────────────────────┤
│         ▲                              ▲                    │
│    androidMain                     iosMain (future)         │
│                                                             │
│  InProgressStrokesView          PKCanvasView               │
│  CanvasStrokeRenderer           PencilKit rendering        │
│  MotionEvent processing         UITouch processing         │
│  StrokeAdapter (ink↔shared)     StrokeAdapter (pk↔shared)  │
│  CommittedStrokeView            PencilKitCanvasView        │
│  BrushProvider                                             │
│  TemplateRenderer                                          │
│  PdfPageRenderer                                           │
└────────────────────────────────────────────────────────────┘
```

### No Cross-Platform Renderer Abstraction

There is intentionally **no** `expect/actual PlatformRenderer` interface. The rendering APIs are too different between `InProgressStrokesView` + `CanvasStrokeRenderer` (Android) and PencilKit (iOS) for a shared interface to be useful. A forced abstraction would be leaky and constrain both platforms.

Instead, the shared code provides **data and state**. Each platform consumes it however its rendering API requires:
- Android: reads `StateFlow<CanvasState>` → converts `Stroke` to `androidx.ink.strokes.Stroke` via `StrokeAdapter` → renders via `CanvasStrokeRenderer`.
- iOS (future): reads `StateFlow<CanvasState>` → converts `Stroke` to `PKStroke` via adapter → renders via PencilKit.

### What IS Shared

- All data models (`Stroke`, `Canvas`, `Folder`, `InputPoint`, `BoundingBox`)
- All state management (`CanvasViewModel`, `UndoRedoManager`, `DrawCommand` implementations)
- All persistence (`StrokeSerializer`, `AutosaveManager`, repository interfaces, SQLDelight schema)
- All export/import logic (`DraftyExporter`, `DraftyImporter`, `PdfImportUseCase`)
- Spatial indexing (`StrokeSpatialIndex`)
- All UI except the drawing canvas (`LibraryScreen`, `CanvasScreen` toolbar, settings — all Compose Multiplatform)

### What IS Platform-Specific

- Stylus input capture and routing
- Front-buffered/back-buffered rendering
- Platform stroke ↔ shared stroke conversion
- Template drawing (Canvas API on Android, Core Graphics on iOS)
- PDF page rasterization
- PDF/PNG export rendering

---

## Key Design Decisions

| Decision | Rationale | Escape Hatch |
|----------|-----------|--------------|
| Bounding box in protobuf blob, not SQL columns | Keeps schema simple; one fewer migration | Promote to SQL columns if >5000 strokes causes culling bottleneck |
| Full stroke load on canvas open | Typical canvas is 100-2000 strokes (~1MB) — instant on tablet | Viewport-based lazy loading via `selectStrokeMetadataByCanvas` + on-demand `selectStrokePointData` |
| 300ms autosave debounce | Coalesces rapid strokes; single fsync per batch | Reduce to 100ms if data loss risk is observed |
| Undo/redo in-memory, per-session | Simple; no command serialization needed | Persist undo stack to DB if cross-session undo is requested |
| `ON DELETE SET NULL` for canvas.folder_id | Never destroy user data when deleting folders | Change to CASCADE if explicit "delete folder and contents" is added |
| No cross-platform renderer abstraction | APIs too different; abstraction would be leaky | n/a |
| `.drafty` = ZIP with JSON + protobuf | Human-readable manifest; compact stroke data; native ZIP support on both platforms | n/a |
| `Stroke.colorArgb` is `Long` not `Int` | Avoids sign-extension bugs with high-alpha ARGB values across KMP boundary | n/a |
