# TODO — Drafty

> Master task tracking. Links: [plan.md](./plan.md) | [issues.md](./issues.md)
>
> **Legend**: ✅ Done | 🔧 In Progress | ⬜ Not Started

---

## Phase 0: Project Scaffold

- ✅ Write planning documents (idea.md, plan.md, tech-stack.md, architecture.md, project-structure.md)
- ✅ Create multi-module Gradle project structure
- ✅ Set up version catalog (libs.versions.toml)
- ✅ Create :app module with Hilt, Compose, and Navigation
- ✅ Create :core:domain module with models, repository interfaces, and use cases
- ✅ Create :core:data module with Room database, entities, DAOs, mappers, and repository stubs
- ✅ Create :core:ink-engine module with renderer, input, stroke, spatial, and command stubs
- ✅ Create :core:pdf-engine module with renderer, exporter, and importer stubs
- ✅ Create :core:ui module with Material 3 theme, shared components, and icons
- ✅ Create :feature:canvas module with screen, viewmodel, and component stubs
- ✅ Create :feature:notebooks module with home/detail screens and viewmodels
- ✅ Create :feature:pdf-viewer module with annotation screen and viewmodel
- ✅ Create protobuf schema (strokes.proto)
- ✅ Create GitHub Actions CI workflow
- ✅ Create .gitignore

---

## Phase 1: Ink Engine (Core Complete)

> See [plan.md — Phase 1](./plan.md) | [research.md](./research.md) for details.

### 1A. Input Pipeline
- ✅ Implement `InputProcessor` — extract x, y, pressure, tilt, timestamp from MotionEvent; consume all historical batched samples; emit List<InkPoint>
- ✅ Implement `PalmRejectionFilter` — multi-pointer state machine; accept TOOL_TYPE_STYLUS, reject TOOL_TYPE_PALM; track pointer IDs; handle FLAG_CANCELED on Android 13+

### 1B. Stroke Math
- ✅ Implement `BezierSmoother` — Catmull-Rom spline interpolation through input points; convert to cubic Bézier control points using tangent formula: B1 = p1 + (p2-p0)/6, B2 = p2 - (p3-p1)/6; handle edge cases (first/last segment phantom points)
- ✅ Implement `PressureMapper` — power curve (exp=1.8) for pen, sigmoid (k=5) for highlighter; configurable min/max width; coerce pressure to [0,1]
- ✅ Implement `StrokePath` — generate variable-width Path from smoothed points; circle strip approach with Bézier sampling and deduplication
- ✅ Implement `StrokeBuilder` — orchestrate: raw InkPoints → smoothing → pressure mapping → path generation; finalize Stroke object on ACTION_UP

### 1C. Rendering
- ✅ Implement `StrokeRenderer` — render completed strokes to Canvas using cached Paint; circle strip with anti-aliasing; separate active stroke fast path
- ✅ Implement `InkSurfaceView` — custom SurfaceView with dedicated render thread; double-buffer strategy (completed strokes bitmap + active stroke overlay); lockHardwareCanvas on API 26+; thread-safe input/render synchronization

### 1D. Undo/Redo & Tool Integration
- ✅ Implement `UndoManager` — command pattern stack (depth=50); AddStrokeCommand, DeleteStrokeCommand, MoveStrokesCommand
- ✅ Wire pen, highlighter, eraser tools into InkSurfaceView (tool switching changes color/thickness/behavior)
- ✅ Integrate `CanvasScreen` composable with InkSurfaceView via AndroidView; wire CanvasViewModel to tool state; floating DrawingToolbar

### 1E. Verification
- ⬜ Test ink latency (target: <20ms pen-to-screen) — requires device testing
- ⬜ Verify smooth 60fps with 500+ strokes on cached bitmap — requires device testing

---

## Phase 2: Canvas & Page Management (Not Started)

> See [plan.md — Phase 2](./plan.md) | [research.md — Phase 2](./research.md) for details.

### 2A. Spatial Index & Hit Testing
- ⬜ Add `BoundingBox` data class to `:core:domain` model; add `computeBoundingBox()` extension on `Stroke`
- ⬜ Implement `RTree` as grid-based spatial index (256px cells, `HashMap<Pair<Int,Int>, MutableList<String>>`); support insert, remove, query(rect), clear
- ⬜ Implement `HitTester` — point hit test (eraser: coarse phase via RTree → fine phase point distance), rect intersection (viewport culling), path intersection (lasso polygon via ray-casting)
- ⬜ Wire eraser tool in `InkSurfaceView` to use `HitTester` for whole-stroke deletion on touch

### 2B. ViewportManager & Zoom/Pan
- ⬜ Implement `ViewportManager` — store zoom level + pan offset; expose canvas↔screen coordinate transforms (forward and inverse); clamp zoom to [0.25, 5.0] paginated / [0.1, 10.0] whiteboard; compute visible viewport rect in canvas coords
- ⬜ Implement focal-point zoom — adjust pan offset on zoom change so pinch center stays fixed: `newPan = focal - (focal - pan) × (z₁ / z₀)`
- ⬜ Implement pan clamping for paginated mode (page must stay partially visible); free pan for whiteboard mode
- ⬜ Integrate `ViewportManager` into `InkSurfaceView` — apply `canvas.translate/scale` before drawing; inverse-transform touch input before feeding to `InputProcessor`
- ⬜ Implement zoom-aware bitmap caching — scale existing bitmap during pinch, debounced re-rasterize (300ms) at new zoom level in background

### 2C. GestureDetector — Pinch & Pan
- ⬜ Implement `GestureDetector` using Android `ScaleGestureDetector` for pinch zoom + manual two-pointer tracking for pan; `OverScroller` for fling momentum
- ⬜ Implement gesture arbitration in `InkSurfaceView.onTouchEvent()` — stylus → draw; 2-finger → zoom/pan; 1-finger → pan (stylus-only mode default)
- ⬜ Implement double-tap to reset zoom — `ValueAnimator` to animate zoom → 1.0 and re-center page

### 2D. Paper Templates & Paginated Mode
- ⬜ Add `TemplateConfig` data class to `:core:domain` (type, lineColor, lineSpacing, marginX, marginColor)
- ⬜ Implement template rendering in `InkSurfaceView` — draw BLANK, LINED, GRID, DOTTED, CORNELL patterns as first layer; hairline stroke width (`1f / zoom`) for zoom-independent crispness
- ⬜ Implement page bounds clipping in paginated mode — `canvas.clipRect(0, 0, page.width, page.height)` during stroke rendering
- ⬜ Wire `CanvasViewModel` canvas mode switching (PAGINATED ↔ WHITEBOARD); toggle ViewportManager bounds, template visibility, and page navigation UI

### 2E. Stroke Persistence (StrokeFileManager)
- ⬜ Implement `StrokeFileManager` — protobuf serialization/deserialization using generated classes; file path: `{internal_storage}/strokes/{pageId}.pb`
- ⬜ Wire `StrokeRepositoryImpl` to delegate to `StrokeFileManager`
- ⬜ Inject `LoadStrokesUseCase` and `SaveStrokesUseCase` into `CanvasViewModel`; load strokes on page open, auto-save with 500ms debounce after each stroke completion
- ⬜ Implement save on page navigation and app background (`Lifecycle.Event.ON_STOP`)

### 2F. Page Navigation & Management
- ⬜ Implement `PageNavigator` composable — forward/back buttons + page indicator ("3 / 12")
- ⬜ Implement `CanvasViewModel` page navigation — `nextPage()`, `previousPage()`, `goToPage(index)` with auto-save current → clear → load new
- ⬜ Wire `AddPageUseCase` and `DeletePageUseCase` into CanvasViewModel for add/delete page from canvas screen

### 2G. Lasso Selection Tool
- ⬜ Implement lasso path capture — when tool=LASSO, collect touch points into a closed polygon
- ⬜ Implement lasso selection via `HitTester.intersectsPath()` — select strokes whose bounding box center is inside polygon
- ⬜ Implement selection UI overlay — highlight selected strokes, show bounding box with drag handles
- ⬜ Implement selection actions — move (offset all points by drag delta), copy (duplicate with new IDs), delete (remove + record command); all through `UndoManager`
- ⬜ Add `MoveStrokesCommand` and `DeleteStrokesCommand` (batch) to undo system

### 2H. Verification
- ⬜ Verify pinch-to-zoom is smooth (no frame stutter) with 200+ strokes
- ⬜ Verify viewport culling — only visible strokes render (log stroke count per frame)
- ⬜ Verify eraser correctly deletes touched strokes via spatial index
- ⬜ Verify page navigation saves/loads strokes correctly (round-trip protobuf)
- ⬜ Verify lasso select → move → undo restores original positions
- ⬜ Verify paper templates render at correct spacing and remain crisp across zoom levels

---

## Phase 3: Notebook Organization & UI (Not Started)

> See [plan.md — Phase 3](./plan.md) for details.

- ⬜ Implement `HomeScreen` — notebook grid/list
- ⬜ Implement notebook CRUD (create, rename, delete, duplicate)
- ⬜ Implement `NotebookDetailScreen` — section tabs + page thumbnails
- ⬜ Implement section management (add, rename, reorder, delete)
- ⬜ Implement page management within sections
- ⬜ Implement tagging system
- ⬜ Implement search (notebooks/sections/pages by name)
- ⬜ Implement favorites and sorting
- ⬜ Implement Navigation graph with all routes
- ⬜ Implement Settings screen

---

## Phase 4: PDF Import & Annotation (Not Started)

> See [plan.md — Phase 4](./plan.md) for details.

- ⬜ Implement `PdfImporter` — import PDF from file picker
- ⬜ Implement `PdfPageRenderer` — render pages to Bitmap
- ⬜ Implement `PdfPageCache` — LRU bitmap cache
- ⬜ Implement `PdfAnnotateScreen` — PDF display with ink overlay
- ⬜ Implement `PdfAnnotationCompositor` — composites strokes onto PDF
- ⬜ Implement `PdfExporter` — export annotated PDF
- ⬜ Implement PDF page navigation with thumbnails

---

## Phase 5: Export, Search & Polish (Not Started)

> See [plan.md — Phase 5](./plan.md) for details.

- ⬜ Implement page export as PNG/JPEG
- ⬜ Implement notebook/section export as multi-page PDF
- ⬜ Implement Android share sheet integration
- ⬜ Implement full-text search with results
- ⬜ Add smooth animations and transitions
- ⬜ Add loading/empty states
- ⬜ Add onboarding flow
- ⬜ Implement settings & preferences (DataStore)

---

## Phase 6: Testing, Optimization & Release (Not Started)

> See [plan.md — Phase 6](./plan.md) for details.

- ⬜ Write unit tests for data layer
- ⬜ Write unit tests for ink engine math
- ⬜ Write integration tests for ViewModels
- ⬜ Write UI tests for critical flows
- ⬜ Profile and optimize rendering pipeline (<20ms latency)
- ⬜ Profile memory usage with large PDFs
- ⬜ App icon and branding
- ⬜ ProGuard/R8 configuration
- ⬜ Firebase Crashlytics integration
- ⬜ Play Store listing preparation
