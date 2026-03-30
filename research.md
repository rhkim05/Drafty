# Research — Drafty Codebase

Deep reading of the entire Drafty project. This document captures how every layer works, what each file does, design decisions and their rationale, and the current implementation state.

---

## 1. Project Overview

Drafty is a handwriting/note-taking app for tablets. Android is the primary target; a native iOS port is planned after the Android release. The defining feature is **sub-frame ink latency** — writing must feel instant. The app is deliberately minimal: no AI features, no collaboration, no template marketplaces. Three tools (pen, highlighter, eraser), four templates (blank, lined, grid, dotted), folders and canvases in a simple hierarchy.

**Tech stack summary:**

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin Multiplatform | 2.1.10 |
| UI framework | Compose Multiplatform | 1.7.3 |
| Android rendering | androidx.ink | 1.0.0-alpha01 |
| iOS rendering (future) | PencilKit | — |
| Database | SQLDelight | 2.0.2 |
| Stroke serialization | Wire Protobuf | 5.1.0 |
| State management | StateFlow + Command pattern | stdlib |
| Android Gradle Plugin | AGP | 8.7.3 |
| Target/Compile SDK | Android 15 | 35 |
| Min SDK | Android 10 | 29 |
| JVM target | | 17 |
| App version | | 0.1.0 |

---

## 2. Module Structure

Two Gradle modules: `:shared` and `:androidApp`. Almost all code lives in `:shared`.

### shared/commonMain — Platform-independent (~90% of the codebase)

```
kotlin/com/drafty/
  model/          Stroke, Canvas, Folder, InputPoint, BoundingBox, StrokeId,
                  ToolType, Template, FolderColor
  state/          CanvasViewModel, CanvasState, ActiveToolState, DrawCommand,
                  AddStrokeCommand, EraseStrokeCommand, PartialEraseCommand,
                  LassoMoveCommand, LassoRecolorCommand, LassoDeleteCommand,
                  UndoRedoManager
  spatial/        StrokeSpatialIndex
  persistence/    FolderRepository, CanvasRepository, StrokeRepository (interfaces),
                  DatabaseDriverFactory (expect), StrokeSerializer, AutosaveManager,
                  StrokeMetadata, DeserializedStroke
  export/         DraftyExporter, DraftyImporter, PdfImportUseCase,
                  PlatformPdfRenderer (expect)
  ui/             LibraryScreen, LibraryViewModel, CanvasScreen, DraftyTheme
  util/           generateUuid() (expect), currentTimeMillis() (expect)

proto/com/drafty/storage/
  stroke.proto

sqldelight/com/drafty/db/
  Drafty.sq
```

### shared/androidMain — Android-specific implementations

```
kotlin/com/drafty/
  input/          StrokeInputHandler, GestureHandler, EraserHandler
  rendering/      BrushProvider, CommittedStrokeView, StrokeCommitHandler,
                  RenderableStroke, RenderableStrokeCache,
                  TemplateRenderer, PdfPageRenderer
  composable/     DrawingCanvas
  adapter/        StrokeAdapter
  persistence/    DatabaseDriverFactoryAndroid, SqlDelightCanvasRepository,
                  SqlDelightFolderRepository, SqlDelightStrokeRepository
  export/         PlatformPdfRendererAndroid, CanvasExportRenderer
  util/           UuidGeneratorAndroid, TimeProviderAndroid
```

### shared/iosMain — iOS stubs (all empty, future port)

```
kotlin/com/drafty/
  input/          PencilInputHandler
  rendering/      PencilKitCanvasView
  adapter/        StrokeAdapter
  persistence/    DatabaseDriverFactoryIos
  export/         PlatformPdfRendererIos
  util/           UuidGeneratorIos, TimeProviderIos
```

### androidApp — Thin application shell

```
kotlin/com/drafty/android/
  MainActivity.kt          Single Activity, sets Compose content to DraftyApp()
  DraftyApplication.kt     Application subclass (placeholder for DI init)
  DraftyApp.kt             Root @Composable — navigation host (stub)

res/values/
  strings.xml              App name only
  themes.xml               Dark theme (splash/system bars), no action bar
  colors.xml               Empty
```

### Why this structure

- **commonMain** holds everything that doesn't touch platform rendering or I/O APIs. This includes all UI screens (Compose Multiplatform), all state management, all persistence interfaces, all export/import logic.
- **androidMain/iosMain** only contain what *must* be platform-specific: rendering (APIs are too different to abstract), stylus input capture, DB driver creation, PDF rendering.
- **androidApp** is intentionally minimal — just the Activity, Application, and navigation wiring. No business logic.

---

## 3. Build System & Dependencies

### Root build.gradle.kts

Plugin declarations only (all `apply false`):
- `org.jetbrains.kotlin.multiplatform` 2.1.10
- `org.jetbrains.kotlin.plugin.compose` 2.1.10
- `org.jetbrains.compose` 1.7.3
- `com.android.application` 8.7.3
- `com.android.library` 8.7.3
- `app.cash.sqldelight` 2.0.2
- `com.squareup.wire` 5.1.0

### gradle.properties

- `kotlin.code.style=official`
- `android.useAndroidX=true`
- `org.gradle.jvmargs=-Xmx2048m`
- `android.nonTransitiveRClass=true`

### shared/build.gradle.kts — Dependencies

**commonMain:**
- `compose.runtime`, `compose.foundation`, `compose.material3`, `compose.ui` — Compose Multiplatform UI (material3 used for structural components only — Scaffold, FAB, Card, etc. — not for MaterialTheme color scheme)
- `kotlinx-coroutines-core:1.9.0` — Coroutines for async operations
- `wire-runtime:5.1.0` — Protobuf runtime for stroke serialization
- `uuid:0.8.4` (com.benasher44) — Cross-platform UUID generation

**androidMain:**
- `androidx.ink:ink-authoring:1.0.0-alpha01` — `InProgressStrokesView` for front-buffered live stroke capture
- `androidx.ink:ink-rendering:1.0.0-alpha01` — `CanvasStrokeRenderer` for drawing committed strokes
- `androidx.ink:ink-strokes:1.0.0-alpha01` — Stroke data structures
- `androidx.ink:ink-brush:1.0.0-alpha01` — Brush definitions (`StockBrushes`, `BrushFamily`)
- `androidx.ink:ink-geometry:1.0.0-alpha01` — Hit testing, geometry operations for eraser
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7` — ViewModel Compose integration
- `app.cash.sqldelight:android-driver:2.0.2` — SQLite driver for Android
- `kotlinx-coroutines-android:1.9.0` — Android dispatchers

**androidApp/build.gradle.kts:**
- Depends on `:shared`
- `androidx.activity:activity-compose:1.9.3`
- `androidx.core:core-ktx:1.15.0`
- compileSdk 35, minSdk 29, targetSdk 35, JVM 17

### Code generation

- **SQLDelight** reads `Drafty.sq` → generates type-safe Kotlin DAOs into `build/generated/sqldelight/`
- **Wire** reads `stroke.proto` → generates Kotlin protobuf classes into `build/generated/source/wire/`
- Generated sources are **not** checked into version control
- Must run `./gradlew build` after modifying `.sq` or `.proto` files to regenerate

---

## 4. Data Model

### Enums

```kotlin
enum class ToolType { Pen, Highlighter, Eraser }
enum class Template { Blank, Lined, Grid, Dotted }
enum class FolderColor { Red, Orange, Yellow, Green, Blue, Purple, Pink, Gray }
```

### StrokeId

```kotlin
@JvmInline value class StrokeId(val value: String)
```

Zero-cost type wrapper for type safety. Prevents accidentally passing a canvas ID where a stroke ID is expected.

### InputPoint

```kotlin
data class InputPoint(
    val x: Float, val y: Float,
    val pressure: Float,            // 0.0..1.0 normalized
    val tiltRadians: Float,         // 0 = perpendicular, pi/2 = flat
    val orientationRadians: Float,
    val timestampMs: Long,
)
```

Captures full stylus data: position, pressure, tilt, orientation, timestamp. Preserves all data needed for both Android (androidx.ink) and iOS (Apple Pencil) rendering. Note: Apple Pencil uses azimuth/altitude rather than tilt/orientation — mathematically translatable but the adapter will need coordinate system conversion, not a direct pass-through.

### BoundingBox

```kotlin
data class BoundingBox(
    val minX: Float, val minY: Float,
    val maxX: Float, val maxY: Float,
) {
    fun intersects(other: BoundingBox): Boolean = ...
}
```

Pre-computed on stroke creation. Used by the spatial index for viewport culling and by the eraser for hit testing.

### Stroke

```kotlin
data class Stroke(
    val id: StrokeId,
    val canvasId: String,
    val sortOrder: Int,           // z-order (monotonically increasing)
    val toolType: ToolType,
    val colorArgb: Long,          // Long, not Int — avoids sign-extension bugs
    val baseWidth: Float,
    val points: List<InputPoint>,
    val boundingBox: BoundingBox, // pre-computed
)
```

**`colorArgb: Long`** — Avoids sign-extension when high-alpha ARGB values cross the JVM/KMP boundary (e.g., `0xFFFF0000` overflows a signed `Int` to negative). `Long` sidesteps this. Note: `ActiveToolState.colorArgb` is `Int`, and `CanvasViewModel.setColor()` does a `toInt()` conversion — this Long/Int inconsistency should be unified in implementation. Compose Multiplatform's `Color` inline class (backed by `ULong`) is the idiomatic alternative but adds conversion overhead at the rendering boundary.

### Canvas

```kotlin
data class Canvas(
    val id: String,
    val folderId: String?,        // null = root level (unfiled)
    val title: String,
    val template: Template,
    val created: Long,            // epoch millis
    val modified: Long,
    val thumbnail: ByteArray?,    // PNG bytes for library grid
    val pdfBackingPath: String?,  // non-null if created from PDF import
    val pdfPageIndex: Int?,       // which page of the backing PDF (0-based)
) {
    data class Summary(
        val id: String, val folderId: String?, val title: String,
        val template: Template, val created: Long, val modified: Long,
        val hasThumbnail: Boolean,  // flag, not actual bytes
    )
}
```

`Canvas.Summary` is a lightweight projection for the library grid — excludes the thumbnail `ByteArray` to reduce memory pressure when listing many canvases.

### Folder

```kotlin
data class Folder(
    val id: String,
    val parentId: String?,  // null = root level
    val title: String,
    val color: FolderColor,
    val created: Long,
    val modified: Long,
    val sortOrder: Int,
)
```

Self-referencing hierarchy. Folders can be nested arbitrarily deep. 8 color options for visual differentiation.

---

## 5. Rendering Pipeline

### Layer Composition (back to front)

The canvas composites four logical layers:

| Layer | Content | Renderer |
|-------|---------|----------|
| 1 (back) | Template background (lines/grid/dots) or PDF page | `TemplateRenderer` / `PdfPageRenderer` |
| 2 | Highlighter strokes | `CanvasStrokeRenderer` via `CommittedStrokeView` |
| 3 | Ink strokes (pen) | `CanvasStrokeRenderer` via `CommittedStrokeView` |
| 4 (front) | Live in-progress stroke | `InProgressStrokesView` (front-buffered) |

These are **not** four separate GPU surfaces. Two actual views exist:

1. **`CommittedStrokeView`** — a custom `View` underneath that composites layers 1-3 in a single `onDraw()` pass.
2. **`InProgressStrokesView`** — a `FrameLayout` from `androidx.ink:ink-authoring` that manages a front-buffered `SurfaceView` overlay. Only renders the currently-being-drawn stroke.

### Dual-Buffer Architecture

```
ACTION_DOWN / ACTION_MOVE
    |
    v
InProgressStrokesView (FRONT BUFFER)
    - Draws partial stroke directly to display
    - Motion prediction adds ~1 frame of look-ahead
    - Sub-frame latency (~4ms on Samsung Tab S8)
    |
ACTION_UP (stroke finished)
    |
    v
Stroke Commit Flow:
    1. InProgressStrokesView emits finished androidx.ink.strokes.Stroke
    2. StrokeAdapter converts to shared Stroke model
    3. AddStrokeCommand pushed to UndoRedoManager
    4. CanvasState updated (strokes list + spatial index)
    5. CommittedStrokeView.invalidate() → triggers onDraw
    6. Finished stroke removed from InProgressStrokesView
    |
    v
CommittedStrokeView (BACK BUFFER)
    onDraw(canvas):
      1. Draw template/PDF (only visible region)
      2. Draw highlighter strokes (visible only, via spatial index)
      3. Draw ink strokes (visible only, via spatial index)
```

### DrawingCanvas Composable

`DrawingCanvas` hosts both views via `AndroidView` interop in a `FrameLayout`:

```kotlin
@Composable
fun DrawingCanvas(viewModel: CanvasViewModel, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                addView(CommittedStrokeView(context))    // back
                addView(InProgressStrokesView(context))  // front overlay
                // Wire up: BrushProvider, StrokeInputHandler, StrokeCommitHandler
                setOnTouchListener { _, event -> inputHandler.onTouchEvent(event) }
            }
        },
        update = { root -> committedView.bind(canvasState) }
    )
}
```

The `update` block fires on every recomposition (i.e., every state change), rebinding the latest `CanvasState` to the committed view.

**Lifecycle note:** The current code uses `collectAsState()` to observe the ViewModel's StateFlow. On Android, `collectAsStateWithLifecycle()` is the recommended alternative — it stops collection when the app is backgrounded, avoiding unnecessary work and potential resource leakage. Since `DrawingCanvas` is in `androidMain`, switching is straightforward.

### Performance Optimizations

1. **Spatial index culling** — `StrokeSpatialIndex.query(visibleRegion)` returns only strokes overlapping the viewport. O(visible) not O(all).
2. **RenderableStrokeCache** — Avoids converting shared `Stroke` → `androidx.ink.strokes.Stroke` every frame. Cache keyed by `StrokeId`, entries created on first render. **Risk:** Currently an unbounded `HashMap` — heavy canvases (thousands of strokes) could cause OOM. Should add viewport-based eviction or an LRU cap before production.
3. **Template culling** — `TemplateRenderer` only draws lines/grid/dots within the visible viewport region.
4. **PDF caching** — `PdfPageRenderer` rasterizes to a cached `Bitmap`, re-renders only when zoom changes by >2x. **Limitation:** Single-bitmap approach — rasterizing at high zoom on high-res tablets (e.g., Galaxy Tab S9 Ultra at 400%) will allocate a very large bitmap. Tiled rendering (à la SubsamplingScaleImageView) is the standard approach for large documents but is a significant engineering effort. Acceptable for v1 with a reasonable max zoom cap.
5. **Front-buffered rendering** — `InProgressStrokesView` bypasses double-buffered pipeline for the live stroke, achieving sub-frame latency.

### BrushProvider

Maps shared `ActiveToolState` → `androidx.ink.brush.Brush`:

| ToolType | BrushFamily | Behavior |
|----------|-------------|----------|
| Pen | `StockBrushes.pressurePenLatest` | Pressure-sensitive, variable width |
| Highlighter | `StockBrushes.highlighterLatest` | Semi-transparent, pressure affects opacity |
| Eraser | `StockBrushes.pressurePenLatest` + `Color.TRANSPARENT` | Transparent stroke (visual only) |

Pressure and velocity curves are handled internally by the `StockBrushes` families. Custom `BrushFamily` with explicit `BrushBehavior` nodes is the escape hatch for fine-tuning pen feel.

### TemplateRenderer

Interface with per-template implementations:
- **LinedTemplateRenderer** — Horizontal lines at 32px spacing, `#D0D0D0` color, 1px stroke. Only draws lines within `visibleRegion.minY..maxY`.
- **GridTemplateRenderer** — Square grid pattern (similar approach).
- **DottedTemplateRenderer** — Dot grid.
- **BlankTemplateRenderer** — No-op.

### PdfPageRenderer

Implements `TemplateRenderer`. Uses `android.graphics.pdf.PdfRenderer` to rasterize PDF pages to `Bitmap`. Caching strategy:
- Stores a `cachedBitmap` and the `cachedZoomLevel` at which it was rendered.
- Only re-renders if `currentZoom / cachedZoomLevel > 2` or `cachedZoomLevel / currentZoom > 2`.
- This avoids re-rasterizing on every minor zoom change while keeping text crisp at different zoom levels.

---

## 6. Stylus Input Pipeline

### Pipeline Stages

```
MotionEvent (system)
    |
    v
1. Palm Rejection
   - If stylusOnly mode: reject TOOL_TYPE_FINGER events (fall through to gesture detector)
   - Check FLAG_CANCELED for system-level palm rejection
    |
    v
2. InProgressStrokesView (androidx.ink)
   - ACTION_DOWN → startStroke(event, pointerId, brush, transform)
   - ACTION_MOVE → addToStroke(event, pointerId)
   - ACTION_UP → finishStroke(event, pointerId)
   - ACTION_CANCEL → cancelStroke(event, pointerId)
   - Internally: batched MotionEvent unpacking, Kalman filter prediction, front-buffer rendering
   - requestUnbufferedDispatch() on ACTION_DOWN for lowest latency
    |
    v
3. Stroke Commit (StrokeCommitHandler)
   - Convert finished androidx.ink.Stroke → shared Stroke model (via StrokeAdapter)
   - Push AddStrokeCommand to UndoRedoManager
   - Invalidate CommittedStrokeView
   - Remove finished stroke from InProgressStrokesView
    |
    v
4. Autosave (AutosaveManager)
   - 300ms debounced
   - Serialize via Wire Protobuf → write BLOB to SQLDelight
```

### StrokeInputHandler

```kotlin
class StrokeInputHandler(
    private val inProgressStrokesView: InProgressStrokesView,
    private val brushProvider: BrushProvider,
    private val canvasTransform: () -> Matrix,
    private val inputConfig: () -> InputConfig,
)
```

- `InputConfig(stylusOnly: Boolean = true)` — controls palm rejection. When `false`, finger input draws too (for emulator testing / stylus-free devices).
- `requestUnbufferedDispatch(event)` on `ACTION_DOWN` tells the system to deliver touch events at the hardware scan rate rather than batching them per frame.
- Canvas transform matrix is passed to `startStroke()` so the ink library can account for pan/zoom.

### StrokeAdapter (Bidirectional Conversion)

**androidx.ink → shared model** (`toSharedStroke`):
- Iterates `inputs` (StrokeInputBatch), extracting x, y, pressure, tilt, orientation, timestamp per point.
- Generates a new `StrokeId` via `uuid4()`.
- Computes `BoundingBox` from the points.
- Gets toolType/color/width from the current `ActiveToolState`.

**shared model → androidx.ink** (`toInkStroke`):
- Reconstructs `StrokeInputBatch` from `List<InputPoint>`.
- Creates `Brush` from toolType/color/width.
- Returns new `androidx.ink.strokes.Stroke(brush, inputs)`.
- Used by `RenderableStrokeCache` when loading existing strokes for rendering.

### EraserHandler

Two modes:
1. **Stroke erase** — Tap to remove entire stroke. Queries spatial index for candidates in a `HIT_RADIUS` (8px) bounding box around the touch point, then does precise hit testing via `ink-geometry`.
2. **Partial erase** — Scrub to erase portions. Builds an eraser shape from the path + width, uses `ink-geometry` to find intersections and compute split points. Produces a `PartialEraseCommand` with the original stroke and split fragments.

### GestureHandler

Handles multi-touch gestures when not drawing:
- Two-finger pinch → zoom (50%-400%)
- Two-finger drag → pan
- Two-finger tap → undo
- Double-tap → toggle zoom-to-fit / 100%

Finger events that pass through palm rejection (when `stylusOnly = true`) are routed to the gesture detector instead of the stroke input handler.

---

## 7. State Management

### Architecture: MVVM + Command Pattern

`CanvasViewModel` is the central coordinator. It exposes immutable state via `StateFlow` and mutates state exclusively through `DrawCommand` implementations managed by `UndoRedoManager`.

### CanvasState

```kotlin
data class CanvasState(
    val canvasId: String? = null,
    val strokes: List<Stroke> = emptyList(),
    val spatialIndex: StrokeSpatialIndex = StrokeSpatialIndex(),
    val template: Template = Template.Blank,
    val pdfPageIndex: Int? = null,
) {
    val highlighterStrokes: List<Stroke> get() = strokes.filter { it.toolType == ToolType.Highlighter }
    val inkStrokes: List<Stroke> get() = strokes.filter { it.toolType == ToolType.Pen }
    fun strokeById(id: StrokeId): Stroke? = strokes.find { it.id == id }
}
```

Immutable data class. Every mutation produces a new instance via `copy()`. The `spatialIndex` is mutable internally but travels with the state for convenience.

### ActiveToolState

```kotlin
data class ActiveToolState(
    val canvasId: String = "",
    val toolType: ToolType = ToolType.Pen,
    val colorArgb: Int = 0xFF000000.toInt(),  // default black
    val width: Float = 1.5f,
)
```

Separate from `CanvasState` because tool selection doesn't affect the canvas content and shouldn't trigger canvas re-renders.

### CanvasViewModel

```kotlin
class CanvasViewModel(
    private val canvasRepository: CanvasRepository,
    private val strokeRepository: StrokeRepository,
) {
    val state: StateFlow<CanvasState>
    val toolState: StateFlow<ActiveToolState>
    val canUndo: StateFlow<Boolean>
    val canRedo: StateFlow<Boolean>

    fun commitStroke(stroke: Stroke)              // → AddStrokeCommand
    fun eraseStroke(strokeId: StrokeId)            // → EraseStrokeCommand
    fun erasePartial(strokeId: StrokeId, splits: List<Stroke>)  // → PartialEraseCommand
    fun undo()
    fun redo()
    fun selectTool(toolType: ToolType)
    fun setColor(argb: Long)
    fun setWidth(width: Float)
}
```

All canvas mutations go through the `UndoRedoManager` → `DrawCommand` pipeline. Tool state changes are direct `_toolState.update` calls (not undoable).

**ViewModel lifecycle note:** `CanvasViewModel` is a plain Kotlin class in `commonMain` — it does not extend `androidx.lifecycle.ViewModel`. This means it won't automatically survive Android configuration changes (screen rotation). The implementation must either: (a) retain it via a `ViewModelStoreOwner` wrapper in `androidMain`, (b) use the KMP multiplatform lifecycle library (`androidx.lifecycle:lifecycle-viewmodel` for commonMain, available since KMP 1.6+), or (c) lock screen orientation for the canvas screen. This needs to be addressed during implementation.

### DrawCommand Interface

```kotlin
interface DrawCommand {
    fun execute(state: MutableStateFlow<CanvasState>)
    fun undo(state: MutableStateFlow<CanvasState>)
}
```

### Command Implementations (6 total)

| Command | Execute | Undo | Autosave |
|---------|---------|------|----------|
| **AddStrokeCommand** | Appends stroke to list, inserts into spatial index | Removes stroke from list and index | `onStrokeAdded` / `onStrokeDeleted` |
| **EraseStrokeCommand** | Removes stroke from list and index | Re-adds stroke to list and index | `onStrokeDeleted` / `onStrokeAdded` |
| **PartialEraseCommand** | Removes original, adds split fragments | Removes fragments, restores original | Deletes original + adds fragments / reverse |
| **LassoMoveCommand** | Translates selected strokes by (deltaX, deltaY), recomputes bounding boxes, updates spatial index | Translates by (-deltaX, -deltaY), updates spatial index | `onStrokeModified` per moved stroke |
| **LassoRecolorCommand** | Sets new color on selected strokes | Restores previous colors from saved map | `onStrokeModified` per recolored stroke |
| **LassoDeleteCommand** | Removes selected strokes from list and index | Re-adds all removed strokes | `onStrokeDeleted` / `onStrokeAdded` |

All commands that modify strokes update the spatial index and trigger autosave. Lasso move/recolor call `onStrokeModified` per affected stroke, which internally handles the delete + re-insert in the autosave pipeline.

### UndoRedoManager

```kotlin
class UndoRedoManager {
    private val undoStack = ArrayDeque<DrawCommand>()
    private val redoStack = ArrayDeque<DrawCommand>()
    val canUndo: StateFlow<Boolean>
    val canRedo: StateFlow<Boolean>

    fun execute(command, state)  // execute + push to undo stack, clear redo
    fun undo(state)              // pop undo → undo() → push to redo
    fun redo(state)              // pop redo → execute() → push to undo
}
```

- In-memory, per-session. Closing a canvas clears stacks.
- `execute()` clears the redo stack (standard undo/redo behavior — new action after undo discards the redo branch).
- The persisted state is always the result of all executed (non-undone) commands.

---

## 8. Persistence Layer

### SQLDelight Schema

Three tables with indexes and foreign keys:

**folder**
```sql
id TEXT PRIMARY KEY, parent_id TEXT REFERENCES folder(id) ON DELETE CASCADE,
title TEXT, color TEXT DEFAULT 'Gray', created INTEGER, modified INTEGER, sort_order INTEGER DEFAULT 0
```
- Index: `folder_parent(parent_id)` — efficient hierarchy traversal
- `ON DELETE CASCADE` on parent_id — deleting a parent folder removes subfolders

**canvas**
```sql
id TEXT PRIMARY KEY, folder_id TEXT REFERENCES folder(id) ON DELETE SET NULL,
title TEXT, template TEXT DEFAULT 'Blank', created INTEGER, modified INTEGER,
thumbnail BLOB, pdf_backing_path TEXT, pdf_page_index INTEGER
```
- Indexes: `canvas_folder(folder_id)`, `canvas_modified(modified)`
- `ON DELETE SET NULL` on folder_id — **never destroys user data when deleting folders**. Canvases become "unfiled" (root level).
- This means: delete a folder → its subfolders cascade-delete → canvases in those subfolders become unfiled.

**stroke**
```sql
id TEXT PRIMARY KEY, canvas_id TEXT REFERENCES canvas(id) ON DELETE CASCADE,
sort_order INTEGER, tool_type TEXT, color INTEGER, size REAL, point_data BLOB
```
- Composite index: `stroke_canvas(canvas_id, sort_order)` — efficient layered retrieval
- `ON DELETE CASCADE` on canvas_id — deleting a canvas removes all its strokes
- `point_data` is a Wire Protobuf-serialized `StrokePointListProto` blob

### Queries (19 total)

**Folder (6):** selectRootFolders, selectFoldersByParent, selectFolderById, insertFolder, updateFolder, deleteFolder

**Canvas (8):** selectCanvasSummariesByFolder, selectRootCanvasSummaries, selectCanvasById, selectCanvasThumbnail, insertCanvas, updateCanvasMetadata, updateCanvasThumbnail, deleteCanvas

**Stroke (7):** selectStrokesByCanvas, selectStrokeMetadataByCanvas, selectStrokePointData, insertStroke, deleteStroke, deleteStrokesByCanvas, nextSortOrder

Notable queries:
- `selectCanvasSummariesByFolder` returns `(thumbnail IS NOT NULL) AS has_thumbnail` — a boolean flag instead of the actual BLOB, for memory-efficient library listing.
- `selectStrokeMetadataByCanvas` returns everything except `point_data` — lightweight metadata for spatial index population without full deserialization.
- `nextSortOrder` returns `COALESCE(MAX(sort_order), 0) + 1` — monotonically increasing z-order.

### Wire Protobuf Schema

```protobuf
message StrokePointProto {
    float x = 1, y = 2, pressure = 3, tilt_radians = 4, orientation_radians = 5;
    sint64 timestamp_ms = 6;  // delta from first point (compact encoding)
}

message StrokePointListProto {
    repeated StrokePointProto points = 1;
    float bounds_min_x = 2, bounds_min_y = 3, bounds_max_x = 4, bounds_max_y = 5;
    sint64 base_timestamp_ms = 6;  // absolute timestamp of first point, for restoring deltas
}

message StrokeExportProto {
    string id = 1; int32 sort_order = 2; string tool_type = 3;
    int64 color = 4; float size = 5; StrokePointListProto point_data = 6;
}
```

**Design decision:** Bounding box is embedded in the protobuf blob (`StrokePointListProto`), not stored as SQL columns. This keeps the schema simple. Escape hatch: promote `bounds_*` to SQL columns if >5000 strokes causes a culling bottleneck from having to deserialize blobs just to read bounds.

`StrokeExportProto` is used only for `.drafty` file export, not for DB storage. DB stores `StrokePointListProto` in `stroke.point_data` and keeps metadata (id, tool_type, color, size) as regular SQL columns.

### StrokeSerializer

```kotlin
object StrokeSerializer {
    fun serialize(points: List<InputPoint>, bounds: BoundingBox): ByteArray
    fun deserialize(data: ByteArray): DeserializedStroke
    fun deserializeBounds(data: ByteArray): BoundingBox  // fast path: bounds only
}
```

- `serialize`: Converts `List<InputPoint>` → `StrokePointListProto` → `ByteArray`. Timestamps are stored as deltas from the first point (compact `sint64` encoding). The absolute timestamp of the first point is stored in `base_timestamp_ms` for reconstruction.
- `deserialize`: Full deserialization — reconstructs absolute timestamps by adding `base_timestamp_ms` back to each delta. Returns points + bounds.
- `deserializeBounds`: Partial deserialization — reads only the bounding box fields without allocating `InputPoint` objects. Useful if bounds are needed without loading all point data.

### AutosaveManager

```kotlin
class AutosaveManager(
    private val strokeRepository: StrokeRepository,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val debounceMs = 300L

    fun onStrokeAdded(stroke: Stroke)
    fun onStrokeDeleted(strokeId: StrokeId)
    fun onStrokeModified(stroke: Stroke)  // for lasso move/recolor: delete old + insert updated
    suspend fun flush()  // force-flush on canvas exit / app background
}
```

Key behaviors:
- **300ms debounce** — Coalesces rapid strokes into one DB transaction. A burst of 5 strokes in 200ms produces one write, not five.
- **Cancel optimization** — If a stroke is added then deleted within the debounce window (e.g., rapid undo), the add is cancelled from pending inserts and no delete is queued. Net DB writes: zero.
- **Force flush** — Called on `Lifecycle.ON_STOP` and `ViewModel.onCleared()`. Guarantees no data loss even if the app is killed.
- **In-memory is source of truth** — DB writes never block the UI. The rendering always reads from `CanvasState`, not from the database.
- **Crash window trade-off** — Between a stroke being committed to in-memory state and the debounced DB write (up to 300ms), a process-kill loses that stroke. The `flush()` on `ON_STOP` covers normal backgrounding; only a hard crash during active drawing loses data. This is an accepted trade-off for write coalescing performance.

### Repository Interfaces

```kotlin
interface FolderRepository {
    fun getRootFolders(): Flow<List<Folder>>
    fun getFoldersByParent(parentId: String): Flow<List<Folder>>
    suspend fun getFolderById(id: String): Folder?
    suspend fun insert(folder: Folder)
    suspend fun update(folder: Folder)
    suspend fun delete(id: String)
}

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

interface StrokeRepository {
    suspend fun getStrokesByCanvas(canvasId: String): List<Stroke>
    suspend fun getStrokeMetadataByCanvas(canvasId: String): List<StrokeMetadata>
    suspend fun getStrokePointData(strokeId: String): ByteArray?
    suspend fun insert(stroke: Stroke)
    suspend fun delete(strokeId: String)
    suspend fun deleteByCanvas(canvasId: String)
    suspend fun getNextSortOrder(canvasId: String): Int
}
```

- `FolderRepository` and `CanvasRepository` list queries return `Flow` (reactive, re-emit on changes).
- `StrokeRepository` queries return plain lists/values (loaded once on canvas open, then kept in memory).
- Android implementations: `SqlDelightFolderRepository`, `SqlDelightCanvasRepository`, `SqlDelightStrokeRepository`.

---

## 9. Export/Import

### .drafty File Format

A `.drafty` file is a ZIP archive:

```
canvas_<id>.drafty (ZIP)
  manifest.json          Version, export date, stroke count
  canvas.json            Canvas metadata (title, template, dates)
  strokes/
    00001.bin            Protobuf StrokeExportProto
    00002.bin
    ...
  thumbnail.png          Optional
  pdf/
    backing.pdf          Only if canvas was created from PDF import
```

- JSON for manifest/metadata (human-readable, debuggable).
- Protobuf for stroke data (compact binary).

### DraftyExporter / DraftyImporter

- `DraftyExporter` — Packages a canvas + strokes into the ZIP format above.
- `DraftyImporter` — Unpacks the ZIP, inserts canvas and strokes into the database.

### PDF Import (PdfImportUseCase)

Workflow:
1. Copy PDF to app-local storage.
2. Count pages via `PlatformPdfRenderer.getPageCount()`.
3. Create a folder named after the PDF file.
4. Create one canvas per page, each referencing the stored PDF path and its page index.
5. Render a 400x560 thumbnail for each page.

All canvases share the same backing PDF file. The `pdfBackingPath` and `pdfPageIndex` fields on `Canvas` control which page renders as the background.

### PDF/PNG Export (CanvasExportRenderer)

```kotlin
expect class CanvasExportRenderer {
    suspend fun renderToPdf(canvas: Canvas, strokes: List<Stroke>, outputPath: String)
    suspend fun renderToPng(canvas: Canvas, strokes: List<Stroke>, outputPath: String, scale: Float)
}
```

Android implementation uses `android.graphics.pdf.PdfDocument` for PDF and `Bitmap` + PNG compression for images. Both composite the PDF backing (if any), template, and all strokes in the correct layer order.

**Implementation note:** `DraftyExporter` and `DraftyImporter` involve ZIP compression and file I/O. Implementations must run on `Dispatchers.IO` — running synchronously on the main thread will cause ANR on large canvases.

---

## 10. Spatial Indexing

### StrokeSpatialIndex

Grid-based spatial index with 256px cell size:

```kotlin
class StrokeSpatialIndex(private val cellSize: Float = 256f) {
    private val cells = HashMap<Long, MutableList<StrokeId>>()
    private val strokeBoxes = HashMap<StrokeId, BoundingBox>()

    fun insert(stroke: Stroke)           // register in all overlapping cells
    fun remove(strokeId: StrokeId)       // remove from all cells
    fun query(visible: BoundingBox): Set<StrokeId>  // return all strokes in visible cells
}
```

- Cell keys are packed `(col << 32) | (row & 0xFFFFFFFFL)` — a single `Long` per cell.
- `forEachCell(box)` iterates all grid cells touched by a bounding box.
- `query()` collects all stroke IDs from cells overlapping the visible region. Returns a `Set` (natural dedup for strokes spanning multiple cells).

**Performance impact:** On a canvas with 2000 strokes, if only 200 are visible, the renderer draws 200 instead of 2000. This is the difference between smooth and janky scrolling on large canvases.

---

## 11. Platform Abstractions (expect/actual)

| Abstraction | commonMain (expect) | androidMain (actual) | iosMain (actual) |
|---|---|---|---|
| `generateUuid(): String` | expect fun | `java.util.UUID.randomUUID().toString()` | stub |
| `currentTimeMillis(): Long` | expect fun | `System.currentTimeMillis()` | stub |
| `DatabaseDriverFactory` | expect class | `AndroidSqliteDriver` | stub (`NativeSqliteDriver`) |
| `PlatformPdfRenderer` | expect class | `android.graphics.pdf.PdfRenderer` | stub (CoreGraphics) |

These are the only `expect/actual` declarations. Everything else is either fully shared or only exists in one platform source set (like rendering classes).

---

## 12. Current Implementation State

### Fully implemented

- All data model classes and enums (model/)
- SQLDelight schema with all 19 queries (Drafty.sq)
- Wire Protobuf schema (stroke.proto)
- KMP project structure with all source sets
- Build system with all plugins configured
- `UuidGeneratorAndroid` and `TimeProviderAndroid` (actual implementations)
- `DatabaseDriverFactoryAndroid` (actual, but body is stub)
- Android app shell (MainActivity, DraftyApplication, DraftyApp composable)
- Android resources (strings, themes)

### Stubbed (architecture designed, code skeleton only)

Everything else — the files exist with correct package declarations but empty or minimal bodies:

**State management:** CanvasViewModel, CanvasState, ActiveToolState, all 6 DrawCommand implementations, UndoRedoManager

**Persistence:** All 3 repository implementations, StrokeSerializer, AutosaveManager

**Rendering:** CommittedStrokeView, BrushProvider, RenderableStroke, RenderableStrokeCache, StrokeCommitHandler, TemplateRenderer, PdfPageRenderer

**Input:** StrokeInputHandler, GestureHandler, EraserHandler

**Composable:** DrawingCanvas

**Adapter:** StrokeAdapter

**Export/Import:** DraftyExporter, DraftyImporter, PdfImportUseCase, CanvasExportRenderer, PlatformPdfRendererAndroid

**UI:** LibraryScreen, LibraryViewModel, CanvasScreen, DraftyTheme

**Spatial:** StrokeSpatialIndex

**iOS:** All files (stubs for future port)

### What this means

The project has a complete architecture design (documented in `architecture.md` with full code samples) and a complete file scaffold, but no functional implementation yet. The `.sq` and `.proto` schemas are the only code that produces real output (via code generation). All Kotlin source files are package-declaration stubs waiting to be filled with the implementations documented in `architecture.md`.

---

## 13. Known Risks & Limitations

| Risk | Severity | Detail | Mitigation |
|------|----------|--------|------------|
| **androidx.ink is alpha01** | High | The entire rendering pipeline depends on `1.0.0-alpha01`. API will change, documentation is sparse, native crashes are likely. | Pin version, isolate behind `StrokeAdapter`/`RenderableStroke` abstractions so API changes don't propagate. Monitor release notes. |
| **Unbounded RenderableStrokeCache** | Medium | HashMap grows with every stroke ever rendered. No eviction. OOM risk on heavy canvases. | Add LRU eviction or viewport-based cleanup before production. |
| **PDF single-bitmap rasterization** | Medium | High-zoom rendering on high-res tablets allocates oversized bitmaps. | Cap max zoom for PDF canvases in v1. Tiled rendering for v2. |
| **ViewModel doesn't survive config changes** | Medium | `CanvasViewModel` is a plain class, not `androidx.lifecycle.ViewModel`. Screen rotation destroys state. | Use KMP multiplatform lifecycle ViewModel or lock orientation on canvas screen. |
| **collectAsState() lifecycle leak** | Low | Flow collection continues when app is backgrounded. | Switch to `collectAsStateWithLifecycle()` in `DrawingCanvas`. |
| **Long/Int colorArgb inconsistency** | Low | `Stroke.colorArgb` is `Long`, `ActiveToolState.colorArgb` is `Int`. Conversion in ViewModel. | Unify to one type during implementation. |

---

## 14. Key Design Decisions

| Decision | Rationale | Escape Hatch |
|----------|-----------|--------------|
| `Stroke.colorArgb: Long` not `Int` | Avoids sign-extension bugs with high-alpha ARGB across KMP boundary | — |
| Bounding box in protobuf blob, not SQL columns | Schema simplicity, no extra migrations | Promote to SQL columns if >5000 strokes causes culling bottleneck |
| Full stroke load on canvas open | Typical canvas 100-2000 strokes (~1MB) — instant on tablet | Lazy load via metadata + on-demand point data queries |
| 300ms autosave debounce | Coalesces rapid strokes into one fsync | Reduce to 100ms if data loss observed |
| Undo/redo in-memory, per-session | Simple, no command serialization | Persist to DB if cross-session undo requested |
| `ON DELETE SET NULL` for canvas.folder_id | **Never destroy user data** on folder delete | Change to CASCADE if explicit "delete folder and contents" added |
| `ON DELETE CASCADE` for folder.parent_id | Subfolder tree deletion is expected behavior | — |
| No cross-platform renderer abstraction | APIs too different (InProgressStrokesView vs PencilKit); forced abstraction would be leaky | — |
| `.drafty` = ZIP with JSON manifest + protobuf strokes | Human-readable metadata, compact stroke data, native ZIP support on both platforms | — |
| Grid-based spatial index (256px cells) | Simple, fast, good enough for handwriting density | Switch to R-tree if spatial query patterns change |
| `Canvas.Summary` separate from `Canvas` | Avoids loading thumbnail bytes for library grid listings | — |
| `StrokeMetadata` separate from full Stroke | Avoids deserializing point data when only metadata is needed | — |
| Wire Protobuf (not FlatBuffers) | Mature tooling, good KMP support, schema evolution. FlatBuffers would be faster but less mature for KMP. | Switch to FlatBuffers if deserialization profiled as bottleneck |
| SQLDelight (not Room) | KMP-first design, more battle-tested for multiplatform | Room 2.7+ is viable alternative |
| v1 cloud sync = manual export/import via share sheet | Zero infrastructure. User saves `.drafty` files to Drive/email/files app. | Supabase for real sync in v2+ |

---

## 15. Theming — Neon/Cyberpunk Minimalism

### Design Direction

Dark-first UI with vibrant neon accent colors on near-black surfaces. No Material Design theming — custom `CompositionLocal`-based theme system for cross-platform consistency (Android + future iOS).

### Color Palette

| Role | Hex | Usage |
|------|-----|-------|
| `background` | `#0D0D11` | App background, canvas list |
| `surface` | `#1A1A2E` | Cards, panels, toolbar |
| `surfaceVariant` | `#13131A` | Sidebar, secondary surfaces |
| `primary` | `#7C3AED` | FAB, selected/active states, primary interactive elements |
| `accentPink` | `#E040A0` | Folder/canvas color tag, category indicator |
| `accentBlue` | `#3B82F6` | Folder/canvas color tag, category indicator |
| `accentGreen` | `#22C55E` | Folder/canvas color tag, category indicator |
| `accentRed` | `#EF4444` | Folder/canvas color tag, destructive actions |
| `textPrimary` | `#E8E8EE` | Headings, titles, primary text |
| `textSecondary` | `#6B6B80` | Dates, metadata, muted labels |
| `border` | `#2A2A3C` | Card borders, dividers |

### Design Characteristics

- **Dark-only** for v1 (no light mode variant)
- **Neon accent strips** — thin colored bars at card tops for visual category/folder markers
- **Purple as brand color** — used for all primary interactive elements (FAB, active states, selections)
- **Minimal chrome** — no shadows, no gradients, flat dark surfaces with subtle borders
- **High contrast text** — off-white on near-black for readability

### Architecture: Custom CompositionLocal Theme

No `MaterialTheme` — define a custom `DraftyTheme` with a `DraftyColors` data class provided via `CompositionLocalProvider`. This approach:

1. **Cross-platform portable** — works identically on Android and iOS Compose Multiplatform
2. **Easy to change** — all colors in one data class; swap the instance to change the entire theme
3. **Extensible** — add dark/light variants later by providing different `DraftyColors` instances
4. **No Material baggage** — no need to fight Material component defaults or override dozens of color slots

```kotlin
// DraftyColors — single source of truth for all colors
data class DraftyColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val accentPink: Color,
    val accentBlue: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
)

// Provided via CompositionLocal
val LocalDraftyColors = staticCompositionLocalOf { DarkColors }

// Accessed via DraftyTheme.colors.background, etc.
object DraftyTheme {
    val colors: DraftyColors
        @Composable get() = LocalDraftyColors.current
}
```

### FolderColor Mapping (updated for dark theme)

The existing `FolderColor` enum values map to neon accent colors that pop against the dark background:

| FolderColor | Hex | Note |
|-------------|-----|------|
| Red | `#EF4444` | Matches `accentRed` |
| Orange | `#F97316` | Warm neon orange |
| Yellow | `#EAB308` | Gold/amber — brighter than typical yellow for dark bg |
| Green | `#22C55E` | Matches `accentGreen` |
| Blue | `#3B82F6` | Matches `accentBlue` |
| Purple | `#7C3AED` | Matches `primary` |
| Pink | `#E040A0` | Matches `accentPink` |
| Gray | `#6B7280` | Neutral, slightly lighter than `textSecondary` |

### Structural Components from material3 Library

The `compose.material3` library is still a dependency for its structural components (`Scaffold`, `FloatingActionButton`, `AlertDialog`, `Card`, etc.), but `MaterialTheme` is **never applied**. Components are individually styled using `DraftyTheme.colors` via explicit `colors` parameters (e.g., `CardDefaults.cardColors(containerColor = DraftyTheme.colors.surface)`). This avoids Material's built-in color propagation while still leveraging the layout/interaction patterns.
