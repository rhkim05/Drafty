# Architecture: Drafty

> References: [idea.md](./idea.md) | [plan.md](./plan.md) | [tech-stack.md](./tech-stack.md) | [project-structure.md](./project-structure.md)

---

## Architecture Overview

Drafty follows **Clean Architecture** with an **MVVM presentation layer**, organized into a multi-module Gradle project. The app is split into three architectural layers — Presentation, Domain, and Data — with clear dependency rules:

```
┌─────────────────────────────────────────────────────────────────┐
│                        PRESENTATION                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │  :feature:    │  │  :feature:    │  │  :feature:           │   │
│  │  canvas       │  │  notebooks   │  │  pdf-viewer          │   │
│  │              │  │              │  │                      │   │
│  │  CanvasScreen │  │  HomeScreen  │  │  PdfAnnotateScreen   │   │
│  │  CanvasVM    │  │  NotebooksVM │  │  PdfViewerVM         │   │
│  │  Toolbar     │  │  NotebookList│  │                      │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘   │
│         │                 │                      │               │
├─────────┼─────────────────┼──────────────────────┼───────────────┤
│         │          DOMAIN (Use Cases)            │               │
│         ▼                 ▼                      ▼               │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  :core:domain                                              │  │
│  │                                                            │  │
│  │  CreateNotebookUseCase    SavePageStrokesUseCase           │  │
│  │  GetNotebooksUseCase      LoadPageStrokesUseCase           │  │
│  │  ImportPdfUseCase         ExportPdfUseCase                 │  │
│  │  SearchNotebooksUseCase   DeleteNotebookUseCase            │  │
│  │                                                            │  │
│  │  Repository interfaces    Domain models                    │  │
│  └──────────────────────────┬─────────────────────────────────┘  │
│                             │                                    │
├─────────────────────────────┼────────────────────────────────────┤
│                       DATA  │                                    │
│                             ▼                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │  :core:data   │  │  :core:      │  │  :core:              │   │
│  │              │  │  ink-engine   │  │  pdf-engine           │   │
│  │  Room DB     │  │              │  │                      │   │
│  │  File storage│  │  Renderer    │  │  PdfRenderer         │   │
│  │  Repositories│  │  Input proc  │  │  PdfExporter         │   │
│  │  (impl)      │  │  Stroke math │  │  Page cache          │   │
│  └──────────────┘  └──────────────┘  └──────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  :core:ui  — shared Compose components, theme, icons     │    │
│  └──────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### Dependency Rule
- **Feature modules** depend on `:core:domain` and `:core:ui` only
- `:core:domain` has **zero Android dependencies** (pure Kotlin)
- `:core:data` implements repository interfaces defined in `:core:domain`
- `:core:ink-engine` and `:core:pdf-engine` are platform-specific, exposed to features via interfaces in `:core:domain`
- `:app` module ties everything together with Hilt and navigation

---

## Module Breakdown

### `:app`
- Application class, Hilt setup, navigation graph
- Main activity (single-activity architecture)
- App-level theme and configuration

### `:core:domain`
- **Models**: `Notebook`, `Section`, `Page`, `Stroke`, `InkPoint`, `Tag`, `PaperTemplate`, `CanvasMode`
- **Repository interfaces**: `NotebookRepository`, `PageRepository`, `StrokeRepository`, `PdfRepository`
- **Use cases**: Business logic encapsulated in single-responsibility classes
- **Zero platform dependencies** — pure Kotlin, future KMP candidate

### `:core:data`
- **Room database**: DAOs, entities, type converters
- **File storage**: Stroke protobuf read/write, thumbnail generation
- **Repository implementations**: Concrete implementations of domain repository interfaces
- **Mappers**: Entity ↔ Domain model conversion

### `:core:ink-engine`
- **Renderer**: Custom `SurfaceView` subclass that handles stroke rendering
- **Input processor**: Converts `MotionEvent` streams into `InkPoint` sequences
- **Stroke math**: Bézier curve fitting, smoothing, pressure-to-width mapping
- **Spatial index**: R-tree for efficient hit testing and viewport culling
- **Undo/Redo**: Command pattern stack

### `:core:pdf-engine`
- **PDF renderer**: Wraps `PdfRenderer` API for page-to-bitmap conversion
- **PDF exporter**: Flattens ink annotations onto PDF pages, generates output PDF
- **Page cache**: LRU bitmap cache for rendered PDF pages

### `:core:ui`
- **Theme**: Material 3 color scheme, typography, shapes
- **Shared components**: Toolbar, color picker, thickness selector, dialogs
- **Icons**: Custom icon set for drawing tools

### `:feature:canvas`
- **CanvasScreen**: Main drawing screen composable
- **CanvasViewModel**: Manages canvas state (current page, tool, zoom, strokes)
- **Toolbar composables**: Tool selection, color, thickness, undo/redo buttons

### `:feature:notebooks`
- **HomeScreen**: Notebook grid/list, search, favorites
- **NotebookDetailScreen**: Section tabs, page thumbnails
- **ViewModels**: NotebooksViewModel, NotebookDetailViewModel

### `:feature:pdf-viewer`
- **PdfAnnotateScreen**: PDF page display with ink overlay
- **PdfViewerViewModel**: PDF loading, page navigation, annotation state

---

## Data Flow

### Drawing a Stroke

```
User touches stylus to screen
        │
        ▼
MotionEvent (with historical batched events)
        │
        ▼
InputProcessor
├── Extracts x, y, pressure, tilt, timestamp
├── Applies palm rejection filter
└── Emits List<InkPoint> for current stroke
        │
        ▼
StrokeBuilder
├── Applies Catmull-Rom/Bézier smoothing
├── Calculates variable width from pressure
└── Builds Path for rendering
        │
        ▼
InkRenderer (on SurfaceView)
├── Draws active stroke path (real-time, low-latency)
├── Composites with existing stroke bitmap cache
└── Renders at display refresh rate
        │
        ▼
On ACTION_UP (stroke complete):
├── StrokeBuilder finalizes Stroke object
├── CanvasViewModel adds to stroke list
├── UndoManager records AddStrokeCommand
├── StrokeRepository persists to protobuf file (async)
└── ThumbnailGenerator updates page thumbnail (debounced)
```

### Loading a Page

```
User navigates to a page
        │
        ▼
CanvasViewModel.loadPage(pageId)
        │
        ▼
LoadPageStrokesUseCase
├── PageRepository.getPage(pageId) → Page metadata from Room
├── StrokeRepository.loadStrokes(pageId) → Deserialize protobuf
└── Returns PageData(page, strokes, template)
        │
        ▼
CanvasViewModel updates state
├── Sets current strokes list
├── Sets paper template background
├── If PDF page: PdfEngine renders page bitmap
└── InkRenderer redraws all strokes
```

### Exporting Annotated PDF

```
User taps "Export PDF"
        │
        ▼
ExportPdfUseCase
        │
        ▼
For each page in notebook:
├── PdfEngine.renderPage(pageIndex) → Base PDF bitmap
├── InkRenderer.renderStrokes(strokes) → Ink overlay bitmap
├── Composite: base + overlay → final page bitmap
└── PdfExporter.addPage(bitmap)
        │
        ▼
PdfExporter.save(outputPath)
        │
        ▼
Share via Android ShareSheet or save to Downloads
```

---

## State Management

### CanvasViewModel State

```kotlin
data class CanvasUiState(
    val currentPage: Page?,
    val strokes: List<Stroke>,
    val activeStroke: Stroke?,           // stroke being drawn right now
    val selectedTool: Tool,              // PEN, HIGHLIGHTER, ERASER, LASSO
    val penColor: Color,
    val penThickness: Float,
    val highlighterColor: Color,
    val highlighterThickness: Float,
    val canvasMode: CanvasMode,          // PAGINATED, WHITEBOARD
    val zoomLevel: Float,
    val panOffset: Offset,
    val selectedStrokes: List<Stroke>,   // lasso-selected strokes
    val canUndo: Boolean,
    val canRedo: Boolean,
    val isSaving: Boolean,
    val pdfPageBitmap: Bitmap?,          // if viewing PDF page
)
```

State is exposed as `StateFlow<CanvasUiState>` and collected by Compose UI.

---

## Threading Model

| Operation | Thread | Mechanism |
|-----------|--------|-----------|
| Stylus input capture | Main thread | `View.onTouchEvent` |
| Stroke rendering | Render thread | `SurfaceView` dedicated render thread |
| Stroke smoothing/math | Render thread | Inline with rendering |
| Stroke persistence | IO dispatcher | `Dispatchers.IO` coroutine |
| PDF page rendering | IO dispatcher | Background coroutine, result cached |
| Thumbnail generation | Default dispatcher | CPU-bound, debounced |
| Database queries | IO dispatcher | Room + coroutines |
| UI state updates | Main thread | `StateFlow` collected on main |

---

## Persistence Strategy

### Auto-Save
- Strokes auto-saved to protobuf file after each stroke completion (debounced by 500ms)
- Page metadata updated in Room on navigation away from page
- No explicit "save" button needed — everything is auto-saved

### File Format

Each page's stroke data is stored as a protobuf file:

```protobuf
message StrokesFile {
  int32 version = 1;
  repeated Stroke strokes = 2;
}

message Stroke {
  string id = 1;
  repeated InkPoint points = 2;
  uint32 color = 3;          // ARGB packed int
  float base_thickness = 4;
  Tool tool = 5;
  int64 timestamp = 6;
}

message InkPoint {
  float x = 1;
  float y = 2;
  float pressure = 3;
  float tilt = 4;
  int64 timestamp = 5;
}

enum Tool {
  PEN = 0;
  HIGHLIGHTER = 1;
}
```

### Why Protobuf over JSON/SQLite for strokes?
- **Size**: ~10x smaller than JSON for dense point data
- **Speed**: ~5-10x faster to serialize/deserialize
- **Cross-platform**: Protobuf works identically on iOS (Swift) for future port
- **Schema evolution**: Can add fields without breaking existing files

---

## Error Handling Strategy

- **Repository layer**: Returns `Result<T>` or `sealed class` for success/failure
- **ViewModel layer**: Maps errors to UI-friendly error states
- **Auto-save failures**: Retry with exponential backoff; show non-blocking toast on repeated failure
- **PDF loading failures**: Show error state with retry option
- **OOM on large PDFs**: Reduce bitmap resolution; load pages on-demand

---

## Security Considerations

- All data stored in app-internal storage (not accessible to other apps)
- PDF imports copied to internal storage (original file untouched)
- No network calls in MVP (no data leaves device)
- Future cloud sync: TLS for all API calls, token-based auth, encrypted storage for credentials

---

*Next step: Define the project folder structure → see [project-structure.md](./project-structure.md)*
