# Research — Drafty Codebase

> **Revision note:** This document has been edited to fix factual errors, fill gaps, and resolve contradictions identified in [`feedback.md`](feedback.md).

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
- ~~`uuid:0.8.4` (com.benasher44)~~ **Removed** — see Section 11 for UUID resolution

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
value class StrokeId(val value: String)
```

Zero-cost type wrapper for type safety. Prevents accidentally passing a canvas ID where a stroke ID is expected. Note: `@JvmInline` is not needed in `commonMain` — the Kotlin compiler handles JVM inlining automatically for `value class` declarations.

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

**`colorArgb: Long`** — Avoids sign-extension when high-alpha ARGB values cross the JVM/KMP boundary (e.g., `0xFFFF0000` overflows a signed `Int` to negative). `Long` sidesteps this. Compose Multiplatform's `Color` inline class (backed by `ULong`) is the idiomatic alternative but adds conversion overhead at the rendering boundary.

**Color type unification:** All color values must use `Long` consistently across the codebase:
- `Stroke.colorArgb: Long` — already correct
- `StrokeMetadata.color: Long` — already correct
- SQL `stroke.color INTEGER` — maps to Kotlin `Long` via SQLDelight, already correct
- `ActiveToolState.colorArgb` — **must be changed from `Int` to `Long`** during implementation
- `CanvasViewModel.setColor()` — remove the `toInt()` conversion once `ActiveToolState` is unified

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
    - Sub-frame latency (Google claims sub-8ms on supported hardware)
    |
ACTION_UP (stroke finished)
    |
    v
Stroke Commit Flow:
    1. InProgressStrokesView emits finished androidx.ink.strokes.Stroke
    2. StrokeAdapter converts to shared Stroke model
    3. viewModel.commitStroke() → pushes AddStrokeCommand to UndoRedoManager
       → command.execute() updates CanvasState (strokes list + spatial index)
    4. CommittedStrokeView.invalidate() → triggers onDraw
    5. Finished stroke removed from InProgressStrokesView
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

**Lifecycle requirement:** `DrawingCanvas` must use `collectAsStateWithLifecycle()` (not `collectAsState()`) to observe the ViewModel's StateFlow. Without this, flow collection continues when the app is backgrounded, causing unnecessary state updates and potential inconsistency when the Compose tree isn't active. Since `DrawingCanvas` is in `androidMain`, the AndroidX lifecycle dependency is already available.

### Performance Optimizations

1. **Spatial index culling** — `StrokeSpatialIndex.query(visibleRegion)` returns only strokes overlapping the viewport. O(visible) not O(all).
2. **RenderableStrokeCache** — Avoids converting shared `Stroke` → `androidx.ink.strokes.Stroke` every frame. Cache keyed by `StrokeId`, entries created on first render. **Risk:** Currently an unbounded `HashMap` — heavy canvases (thousands of strokes) could cause OOM. Should add viewport-based eviction or an LRU cap before production.
3. **Template culling** — `TemplateRenderer` only draws lines/grid/dots within the visible viewport region.
4. **PDF caching** — `PdfPageRenderer` rasterizes to a cached `Bitmap`, re-renders only when zoom changes by >2x. **Limitation:** Single-bitmap approach — at 400% zoom on a 2048x2732 canvas, the rasterized bitmap would be ~8192x10928 pixels = ~340MB ARGB_8888, which will OOM. **v1 mitigation:** Cap max zoom at 200% for PDF-backed canvases, limiting bitmaps to ~85MB. Tiled rendering for v2.
5. **Front-buffered rendering** — `InProgressStrokesView` bypasses double-buffered pipeline for the live stroke, achieving sub-frame latency.

### BrushProvider

Maps shared `ActiveToolState` → `androidx.ink.brush.Brush`. Only used for Pen and Highlighter — the eraser does not draw strokes.

| ToolType | BrushFamily | Behavior |
|----------|-------------|----------|
| Pen | `StockBrushes.pressurePenLatest` | Pressure-sensitive, variable width |
| Highlighter | `StockBrushes.highlighterLatest` | Semi-transparent, pressure affects opacity |

The eraser works entirely through `EraserHandler` (see Section 6) — it hit-tests and removes/splits existing strokes via commands. It does not draw anything through `InProgressStrokesView`. Visual feedback during erasing: render a circular cursor overlay at the touch point (radius = eraser width) and redraw the back buffer immediately as strokes are removed/split, so erased portions disappear in real-time.

Pressure and velocity curves are handled internally by the `StockBrushes` families. Custom `BrushFamily` with explicit `BrushBehavior` nodes is the escape hatch for fine-tuning pen feel.

### TemplateRenderer

Interface with per-template implementations. All spacing values are in canvas-space units (see Section 5a), not screen pixels — the zoom/pan transform applied by `CommittedStrokeView` handles the screen mapping.
- **LinedTemplateRenderer** — Horizontal lines at 32-unit spacing, `#D0D0D0` color, 1-unit stroke. Only draws lines within `visibleRegion.minY..maxY`.
- **GridTemplateRenderer** — Square grid pattern (similar approach).
- **DottedTemplateRenderer** — Dot grid.
- **BlankTemplateRenderer** — No-op.

### PdfPageRenderer

Implements `TemplateRenderer`. Uses `android.graphics.pdf.PdfRenderer` to rasterize PDF pages to `Bitmap`. Caching strategy:
- Stores a `cachedBitmap` and the `cachedZoomLevel` at which it was rendered.
- Only re-renders if `currentZoom / cachedZoomLevel > 2` or `cachedZoomLevel / currentZoom > 2`.
- This avoids re-rasterizing on every minor zoom change while keeping text crisp at different zoom levels.

### Canvas Coordinate System

All stroke coordinates (`InputPoint.x`, `InputPoint.y`), bounding boxes, template spacing, and spatial index cells operate in **canvas-space units**, not screen pixels. The coordinate system:

- **Origin:** Top-left corner of the canvas at (0, 0).
- **Units:** 1 canvas-unit ≈ 1dp at 100% zoom. This provides DPI-independence: a stroke drawn at the same canvas coordinates renders at the same physical position regardless of screen density.
- **Canvas dimensions:** Infinite scroll in both axes (no fixed page boundary). Templates repeat as needed. PDF-backed canvases have a logical page boundary matching the PDF page dimensions (in points at 72dpi), but the user can draw outside it.
- **Zoom range:** 50% to 400% (200% max for PDF-backed canvases, see Performance section).

**Touch-to-canvas coordinate mapping:** The `CommittedStrokeView` maintains a `Matrix` (the view transform) that maps between screen coordinates and canvas coordinates. This matrix encodes the current pan offset and zoom level. When `InProgressStrokesView.startStroke()` is called, this transform matrix is passed so the ink library renders in canvas space. The `EraserHandler` receives coordinates already transformed to canvas space — the `StrokeInputHandler` applies the inverse transform before passing points to the eraser.

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
- Generates a new `StrokeId` via UUID generation (see Section 11 for which UUID approach to use).
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
2. **Partial erase** — Scrub to erase portions. This is the highest-risk implementation item due to the alpha status of `ink-geometry` and the complexity of splitting pressure-sensitive strokes.

**Partial erase pipeline:**
1. Build an eraser shape from the touch path + eraser width.
2. Query spatial index for candidate strokes overlapping the eraser path bounding box.
3. For each candidate, use `ink-geometry` intersection APIs to find where the eraser crosses the stroke.
4. Split the original stroke at intersection points, producing N fragment strokes. Each fragment:
   - Gets a new `StrokeId` (via UUID generation)
   - Inherits the original stroke's `canvasId`, `toolType`, `colorArgb`, `baseWidth`
   - Contains a subset of the original `InputPoint` list (timestamps preserved as-is)
   - Gets a freshly computed `BoundingBox`
   - Gets a new `sortOrder` (via `nextSortOrder` query)
5. Produce a `PartialEraseCommand` containing the original stroke and all fragments.
6. On execute: remove original from state/index, add all fragments. On undo: reverse.

**Open risk:** The `ink-geometry` intersection API is undocumented alpha. A spike test is needed early in implementation to verify it handles: single intersection (2 fragments), multiple intersections (3+ fragments), near-endpoint intersections, and strokes with varying pressure. If the API proves unreliable, the fallback is point-by-point distance checking against the eraser path — slower but deterministic.

### GestureHandler

Handles multi-touch gestures when not drawing:
- Two-finger pinch → zoom (50%-400%)
- Two-finger drag → pan
- Two-finger tap → undo
- Double-tap → toggle zoom-to-fit / 100%

Finger events that pass through palm rejection (when `stylusOnly = true`) are routed to the gesture detector instead of the stroke input handler.

**Tool switching during active input:** Tool changes (pen/highlighter/eraser) are ignored while a stroke is in progress (`ACTION_DOWN` received but `ACTION_UP` not yet). The active tool is read at `ACTION_DOWN` time and locked for that stroke's duration. This prevents mid-stroke tool changes from corrupting state.

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

**Implementation warning:** Because `StrokeSpatialIndex` is mutable, `data class` semantics break — `equals()`, `hashCode()`, and `copy()` will shallow-copy the index, meaning two "different" states share the same mutable instance. This is acceptable as long as the spatial index is always accessed via the current `StateFlow.value` and never compared across state snapshots. Do not use `CanvasState` in collections or comparisons that rely on structural equality. If this becomes a problem, move the spatial index out of `CanvasState` and into the `CanvasViewModel` as a separate field.

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

### Threading Model

All state mutations (stroke commit, erase, undo/redo, lasso operations) happen on the **main thread** via `StateFlow.update {}`. This is intentional:
- `StrokeSpatialIndex` is not thread-safe — single-threaded access eliminates the need for synchronization.
- `CanvasState` updates must be immediately visible to the rendering pipeline (also main thread).
- `CommittedStrokeView.onDraw()` reads from `CanvasState` on the main thread.

Background work:
- `AutosaveManager` debounces and flushes on `Dispatchers.IO` for DB writes. Pending insert/delete lists are guarded by a `Mutex` (not `synchronized` — see KMP note below).
- `StrokeRepository` read operations (canvas load) run on `Dispatchers.IO`.
- `DraftyExporter`/`DraftyImporter` run on `Dispatchers.IO`.

**KMP concurrency note:** `synchronized` is JVM-only and does not compile for Kotlin/Native (iOS). Since `AutosaveManager` lives in `commonMain`, it must use `kotlinx.coroutines.sync.Mutex` or `kotlinx.atomicfu` for thread safety instead.

**SQLite WAL mode:** Must be enabled on the `AndroidSqliteDriver` to allow concurrent reads (rendering thread querying metadata) while autosave writes are in progress. SQLDelight's Android driver supports this via `AndroidSqliteDriver(schema, context, name, callback, properties = Properties().apply { put("journal_mode", "WAL") })` or equivalent configuration.

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

**ViewModel lifecycle note:** `CanvasViewModel` is a plain Kotlin class in `commonMain` — it does not extend `androidx.lifecycle.ViewModel`. This means it won't automatically survive Android configuration changes (screen rotation). Resolution: lock the canvas screen to landscape orientation (see Section 22 Assumptions). The library screen may use the KMP multiplatform lifecycle library if rotation support is desired.

**Constructor injection:** All dependencies (`CanvasRepository`, `StrokeRepository`, `AutosaveManager`) must be passed via the constructor — not via `lateinit var`. The architecture.md code sample shows `lateinit var autosaveManager` which creates a crash-prone temporal coupling. Use DI (see Section 15) to provide all dependencies at construction time.

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
- **Thread safety:** Pending insert/delete lists must be guarded by `kotlinx.coroutines.sync.Mutex`, not `synchronized` (which is JVM-only and won't compile for iOS). See Section 7 Threading Model.

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

`CanvasExportRenderer` is a platform-only class (not `expect/actual`) because export rendering depends entirely on platform graphics APIs that have no meaningful shared interface:

```kotlin
// androidMain/export/CanvasExportRenderer.kt
class CanvasExportRenderer {
    suspend fun renderToPdf(canvas: Canvas, strokes: List<Stroke>, outputPath: String)
    suspend fun renderToPng(canvas: Canvas, strokes: List<Stroke>, outputPath: String, scale: Float)
}
```

Android implementation uses `android.graphics.pdf.PdfDocument` for PDF and `Bitmap` + PNG compression for images. iOS will use Core Graphics / UIGraphicsPDFRenderer with a completely different API surface. Both composite the PDF backing (if any), template, and all strokes in the correct layer order.

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

**Cell size rationale:** 256 canvas-units is roughly 1cm at 240dpi — approximately the width of a wide pen stroke. At typical handwriting density (~50 strokes visible in the viewport), this produces ~20-40 occupied cells. A large diagonal stroke spanning the full canvas (~2000 units) registers in ~64 cells, which is acceptable overhead. If profiling shows large strokes bloating the index, the cell size can be increased or a secondary "large stroke" list can bypass the grid.

**Performance impact:** On a canvas with 2000 strokes, if only 200 are visible, the renderer draws 200 instead of 2000. This is the difference between smooth and janky scrolling on large canvases.

---

## 11. Platform Abstractions (expect/actual)

| Abstraction | commonMain (expect) | androidMain (actual) | iosMain (actual) |
|---|---|---|---|
| `currentTimeMillis(): Long` | expect fun | `System.currentTimeMillis()` | stub |
| `DatabaseDriverFactory` | expect class | `AndroidSqliteDriver` | stub (`NativeSqliteDriver`) |
| `PlatformPdfRenderer` | expect class | `android.graphics.pdf.PdfRenderer` | stub (CoreGraphics) |

These are the only `expect/actual` declarations. Everything else is either fully shared or only exists in one platform source set (like rendering classes).

**UUID generation — resolved:** The original design had both `com.benasher44:uuid` as a dependency AND `expect fun generateUuid()` as a platform abstraction. These are redundant.

**Option A (recommended): Use `kotlin.uuid.Uuid` (stdlib).** Kotlin 2.0+ includes `kotlin.uuid.Uuid` as an experimental stdlib API. Since this project uses Kotlin 2.1.10, use `Uuid.random().toString()` directly in `commonMain`. No external dependency, no expect/actual needed. Requires `@OptIn(ExperimentalUuidApi::class)`.

**Option B: Keep `com.benasher44:uuid`.** The library is already KMP-multiplatform — it works in `commonMain` directly. No expect/actual needed. Use `uuid4().toString()`. More stable API (not experimental), but adds an external dependency for something the stdlib now provides.

Either way, remove the `expect fun generateUuid()` / `actual fun` pattern and the corresponding `UuidGeneratorAndroid` / `UuidGeneratorIos` files.

**`CanvasExportRenderer`** is intentionally NOT an expect/actual — it's a platform-only class (see Section 9). Each platform will have its own export renderer with a platform-appropriate API.

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

**Note on document roles:** This `research.md` focuses on findings, analysis, constraints, risks, and decisions. `architecture.md` contains the canonical design with full code samples. Where both documents describe the same system (rendering pipeline, state management, persistence), `architecture.md` is the source of truth for implementation details. This document provides the *analysis* (why decisions were made, what risks exist, what alternatives were considered).

---

## 13. Known Risks & Limitations

| Risk | Severity | Detail | Mitigation |
|------|----------|--------|------------|
| **androidx.ink is alpha01** | High | The entire rendering pipeline depends on `1.0.0-alpha01`. API will change, documentation is sparse, native crashes are likely. | Pin version, isolate behind `StrokeAdapter`/`RenderableStroke` abstractions so API changes don't propagate. Monitor release notes. |
| **Partial erase via ink-geometry** | High | The `ink-geometry` intersection API is undocumented alpha. Splitting pressure-sensitive strokes is the hardest feature in the app. | Spike test early. Fallback: point-by-point distance checking (see Section 6). |
| **Unbounded RenderableStrokeCache** | Medium | HashMap grows with every stroke ever rendered. No eviction. OOM risk on heavy canvases. | Add LRU eviction or viewport-based cleanup before production. |
| **PDF single-bitmap rasterization** | Medium | At 400% zoom on tablet-class canvas, bitmap would be ~340MB ARGB. OOM guaranteed. | Cap max zoom at 200% for PDF canvases in v1 (~85MB max). Tiled rendering for v2. |
| **ViewModel doesn't survive config changes** | Medium | `CanvasViewModel` is a plain class, not `androidx.lifecycle.ViewModel`. Screen rotation destroys state. | Use KMP multiplatform lifecycle ViewModel or lock orientation on canvas screen. |
| **collectAsState() lifecycle leak** | Medium | Flow collection continues when backgrounded, causing unnecessary state updates and potential inconsistency with inactive Compose tree. | Use `collectAsStateWithLifecycle()` in `DrawingCanvas` from day one. |
| **Long/Int colorArgb inconsistency** | Low | `ActiveToolState.colorArgb` is `Int` while all other color representations use `Long`. | Unify `ActiveToolState.colorArgb` to `Long` during implementation. |

---

## 14. Key Design Decisions

| Decision | Rationale | Escape Hatch |
|----------|-----------|--------------|
| `Stroke.colorArgb: Long` not `Int` | Avoids sign-extension bugs with high-alpha ARGB across KMP boundary | — |
| Bounding box in protobuf blob, not SQL columns | Schema simplicity, no extra migrations | Promote to SQL columns if >5000 strokes causes culling bottleneck |
| Full stroke load on canvas open | Typical canvas 100-2000 strokes (~6-12MB protobuf, see estimate below) — fast on modern tablets | Lazy load via metadata + on-demand point data queries |
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

### Stroke data size estimate

A single `InputPoint` serialized as `StrokePointProto` contains 4 floats (x, y, pressure, tilt) + 1 float (orientation) + 1 sint64 (timestamp delta) ≈ 24-28 bytes in protobuf encoding. Average handwriting stroke ≈ 100-200 points ≈ 3-6KB per stroke. 2000 strokes ≈ **6-12MB** of protobuf point data. This is still fast to load on modern tablets (sequential read from SQLite), but the original "~1MB" estimate was off by an order of magnitude. Full-load-on-open remains the correct strategy; lazy loading adds complexity without meaningful benefit at this scale.

---

## 15. Dependency Injection

`DraftyApplication.kt` is described as "placeholder for DI init" but no DI strategy is defined. The ViewModel requires `CanvasRepository` and `StrokeRepository` as constructor parameters, and those require a `DatabaseDriverFactory`.

**Option A (recommended): Koin.** Lightweight, KMP-native DI framework. Define a `commonMain` module with repository interfaces and a platform module with actual implementations. Minimal boilerplate, good Compose integration via `koinViewModel()`.

**Option B: Manual DI.** Create a simple `AppModule` object in `commonMain` that holds lazy singletons. Platform code initializes it with the `DatabaseDriverFactory`. No external dependency but becomes unwieldy as the dependency graph grows.

Either way, the wiring pattern is:
1. `DraftyApplication.onCreate()` creates the `DatabaseDriverFactory` and initializes DI.
2. `DraftyApp` composable retrieves `LibraryViewModel` and `CanvasViewModel` from DI.
3. ViewModels receive repository implementations via constructor injection (never `lateinit var`).

---

## 16. Navigation

The Library-to-Canvas navigation and back-stack management need a concrete implementation.

**Option A (recommended): Compose Navigation (KMP).** `navigation-compose` 2.8+ supports KMP. Define a nav graph with two destinations: `Library` and `Canvas(canvasId: String)`. Handles back-stack, deep links (for `.drafty` file open intents), and argument passing. Most standard approach for Compose-based apps.

**Option B: Voyager.** KMP-first navigation library, simpler API than Compose Navigation, good ViewModel integration. Less ecosystem support than the official library.

**Option C: Manual navigation.** A simple `sealed class Screen` with a `MutableStateFlow<Screen>` in `DraftyApp`. Sufficient for two screens, but doesn't handle deep links or complex back-stack scenarios.

**Deep link requirement:** When the user opens a `.drafty` file via Android's share sheet or file manager, the app should navigate directly to the imported canvas. This requires intent handling in `MainActivity` and a nav deep link definition.

---

## 17. Thumbnail Generation

Thumbnails are PNG byte arrays stored in `canvas.thumbnail`. Generation strategy:

- **When:** On canvas close (when the user navigates back to the library) and on app background (`Lifecycle.ON_STOP`). Not after every stroke — thumbnail generation involves rendering all strokes and is too expensive for real-time updates.
- **Resolution:** 400x560 pixels (matching the PDF import thumbnail size). This gives a crisp preview at typical library grid card sizes without excessive memory.
- **How:** `CanvasExportRenderer.renderToPng()` at a reduced scale, then store via `CanvasRepository.updateThumbnail()`.
- **Empty canvas:** No thumbnail generated (uses a placeholder in the library UI).

---

## 18. Error Handling & Recovery

**Corrupted protobuf blob:** If `StrokeSerializer.deserialize()` throws on a malformed blob, skip that stroke and log a warning. The canvas loads with the remaining strokes intact. Do not crash.

**Missing backing PDF:** If a PDF-backed canvas references a `pdfBackingPath` that no longer exists (file deleted, storage cleared), render the canvas without the PDF background. Show a non-blocking toast/snackbar: "PDF background missing — annotations preserved." The user's strokes are never lost.

**SQLite write failure:** `AutosaveManager.flush()` should catch write exceptions, log them, and retry once on the next flush cycle. If the retry also fails, surface an error to the user via a snackbar. Do not silently discard pending writes.

**App process kill during drawing:** Accepted trade-off — up to 300ms of strokes may be lost (the autosave debounce window). The `flush()` on `ON_STOP` covers normal backgrounding. Only a hard crash during active drawing loses data.

---

## 19. `StrokeRepository` — No `update()` Method (Intentional)

The `StrokeRepository` interface has `insert()` and `delete()` but no `update()`. This is a deliberate design choice:

- **Modify = delete + insert** is handled by `AutosaveManager.onStrokeModified()`, which queues a delete of the old stroke and an insert of the updated stroke within the same flush transaction.
- **Atomicity:** Both operations execute in a single SQLDelight transaction, so there's no window where the stroke is missing.
- **Simplicity:** No partial-update SQL queries needed. Stroke data includes a BLOB (point_data) that would need to be fully replaced anyway, so an UPDATE offers no efficiency advantage over DELETE + INSERT.

---

## 20. `CanvasClipboard` (Deferred to v2)

The `architecture.md` system diagram includes `CanvasClipboard` in the `CanvasViewModel` box. This feature (lasso select → copy → paste strokes) is **deferred to v2**. The v1 lasso tools support move, recolor, and delete only. The `CanvasClipboard` reference should be removed from `architecture.md`'s v1 diagram.

---

## 21. `CanvasState.templateRenderer` Ownership

`architecture.md`'s `CommittedStrokeView.onDraw()` code references `state.templateRenderer?.draw(canvas, visibleRegion)`, implying `CanvasState` owns a `TemplateRenderer` instance. This is incorrect — `CanvasState` is a `commonMain` data class and must not hold platform-specific renderer references.

**Resolution:** `CommittedStrokeView` owns the `TemplateRenderer` instance. It reads `canvasState.template` (the `Template` enum) and selects the appropriate renderer implementation (Lined, Grid, Dotted, Blank, or PdfPageRenderer). The `bind()` method on `CommittedStrokeView` updates the renderer when the template changes. This must be corrected in `architecture.md`.

---

## 22. Assumptions

These assumptions are not stated elsewhere but underpin the architecture. Violations will require design changes.

| Assumption | Implication |
|---|---|
| **Single canvas open at a time** | One `CanvasViewModel` instance. No concurrent canvas state management. |
| **Canvas screen locks to landscape orientation** | Resolves the ViewModel lifecycle issue (no configuration changes to survive). Must be declared in `AndroidManifest.xml` via `android:screenOrientation="landscape"`. The library screen may support both orientations. |
| **English-only for v1** | No RTL layout support, no string localization infrastructure. Must be addressed before international release. |
| **No accessibility features for v1** | No content descriptions on canvas elements, no screen reader support for stroke content. Required for Play Store accessibility compliance before broad release. |
| **Single-window mode only** | No split-screen, freeform multi-window, or Samsung DeX support. The drawing canvas assumes it owns the full screen. |
| **All state mutations are main-thread-sequential** | Enables unsynchronized `StrokeSpatialIndex`. If any mutation moves off the main thread, synchronization must be added. |
| **SQLite WAL mode enabled** | Required for concurrent read (rendering) during autosave write. Must be configured in `DatabaseDriverFactory`. |

---

## 23. Theming — Neon/Cyberpunk Minimalism

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
