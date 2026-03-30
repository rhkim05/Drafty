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

> See [plan.md — Phase 2](./plan.md) for details.

- ⬜ Implement paginated canvas mode with paper templates
- ⬜ Implement infinite whiteboard mode
- ⬜ Implement `ViewportManager` — zoom/pan/viewport culling
- ⬜ Implement `GestureDetector` — pinch-to-zoom, two-finger pan
- ⬜ Implement `UndoManager` with command pattern
- ⬜ Implement `RTree` spatial index
- ⬜ Implement `HitTester` for eraser and lasso selection
- ⬜ Implement lasso select tool (move, resize, copy, delete strokes)
- ⬜ Implement page navigation (swipe, thumbnails sidebar)

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
