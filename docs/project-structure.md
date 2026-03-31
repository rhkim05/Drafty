# Project Structure: Drafty

> References: [idea.md](./idea.md) | [plan.md](./plan.md) | [tech-stack.md](./tech-stack.md) | [architecture.md](./architecture.md)
>
> **Last updated**: Phase 3 — Notebook Organization & UI (code complete)

---

## Top-Level Structure

```
Drafty/
├── app/                          # Main application module
├── core/
│   ├── data/                     # Data layer — Room, repositories, file storage
│   ├── domain/                   # Domain layer — models, use cases, repo interfaces
│   ├── ink-engine/               # Ink rendering engine — custom canvas, stroke math
│   ├── pdf-engine/               # PDF rendering and export
│   └── ui/                       # Shared UI — theme, components, icons
├── feature/
│   ├── canvas/                   # Drawing canvas screen
│   ├── notebooks/                # Notebook management screens
│   └── pdf-viewer/               # PDF annotation screen
├── docs/                         # Project documentation (this folder)
│   ├── idea.md
│   ├── plan.md
│   ├── tech-stack.md
│   ├── architecture.md
│   ├── project-structure.md      ← you are here
│   ├── research.md               # Created per-feature during implementation
│   ├── TODO.md                   # Master task tracking
│   └── issues.md                 # Issue log
├── build.gradle.kts              # Root build file
├── settings.gradle.kts           # Module declarations
├── gradle.properties
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml        # Version catalog
├── .github/
│   └── workflows/
│       └── ci.yml                # GitHub Actions CI
├── .gitignore
└── README.md
```

---

## Module Details

### `app/`

```
app/
├── build.gradle.kts
└── src/
    └── main/
        ├── AndroidManifest.xml
        ├── kotlin/com/drafty/app/
        │   ├── DraftyApplication.kt       # Application class + Hilt entry point
        │   ├── MainActivity.kt              # Single activity, hosts Compose nav
        │   ├── navigation/
        │   │   └── DraftyNavGraph.kt       # Top-level navigation graph
        │   └── di/
        │       └── AppModule.kt             # App-wide Hilt module
        └── res/
            ├── values/
            │   ├── strings.xml
            │   └── themes.xml
            └── mipmap-*/                    # App icon
```

### `core/domain/`

```
core/domain/
├── build.gradle.kts                         # Pure Kotlin module (no Android deps)
└── src/main/kotlin/com/drafty/core/domain/
    ├── model/
    │   ├── Notebook.kt
    │   ├── Section.kt
    │   ├── Page.kt
    │   ├── Stroke.kt
    │   ├── InkPoint.kt
    │   ├── Tag.kt
    │   ├── Tool.kt                          # enum: PEN, HIGHLIGHTER, ERASER, LASSO
    │   ├── CanvasMode.kt                    # enum: PAGINATED, WHITEBOARD
    │   └── PaperTemplate.kt                 # enum: BLANK, LINED, GRID, DOTTED, CORNELL
    ├── repository/
    │   ├── NotebookRepository.kt            # interface
    │   ├── SectionRepository.kt             # interface (Phase 3)
    │   ├── PageRepository.kt                # interface
    │   ├── StrokeRepository.kt              # interface
    │   └── PdfRepository.kt                 # interface
    └── usecase/
        ├── notebook/
        │   ├── CreateNotebookUseCase.kt     # auto-creates default section + page
        │   ├── GetNotebooksUseCase.kt
        │   ├── DeleteNotebookUseCase.kt
        │   └── UpdateNotebookUseCase.kt
        ├── section/                          # Phase 3
        │   ├── GetSectionsUseCase.kt
        │   ├── CreateSectionUseCase.kt
        │   ├── RenameSectionUseCase.kt
        │   ├── DeleteSectionUseCase.kt
        │   └── ReorderSectionsUseCase.kt
        ├── page/
        │   ├── AddPageUseCase.kt
        │   ├── DeletePageUseCase.kt
        │   ├── ReorderPagesUseCase.kt
        │   └── GetPagesUseCase.kt
        ├── stroke/
        │   ├── SaveStrokesUseCase.kt
        │   └── LoadStrokesUseCase.kt
        ├── pdf/
        │   ├── ImportPdfUseCase.kt
        │   └── ExportPdfUseCase.kt
        └── search/
            └── SearchNotebooksUseCase.kt
```

### `core/data/`

```
core/data/
├── build.gradle.kts
└── src/main/kotlin/com/drafty/core/data/
    ├── db/
    │   ├── DraftyDatabase.kt              # Room database definition
    │   ├── dao/
    │   │   ├── NotebookDao.kt
    │   │   ├── SectionDao.kt
    │   │   ├── PageDao.kt
    │   │   └── TagDao.kt
    │   ├── entity/
    │   │   ├── NotebookEntity.kt
    │   │   ├── SectionEntity.kt
    │   │   ├── PageEntity.kt
    │   │   ├── TagEntity.kt
    │   │   └── NotebookTagCrossRef.kt
    │   └── mapper/
    │       ├── NotebookMapper.kt            # Entity ↔ Domain model
    │       ├── SectionMapper.kt
    │       └── PageMapper.kt
    ├── file/
    │   ├── StrokeFileManager.kt             # Read/write protobuf stroke files
    │   └── ThumbnailManager.kt              # Generate and cache page thumbnails
    ├── repository/
    │   ├── NotebookRepositoryImpl.kt
    │   ├── SectionRepositoryImpl.kt         # Phase 3
    │   ├── PageRepositoryImpl.kt
    │   ├── StrokeRepositoryImpl.kt
    │   └── PdfRepositoryImpl.kt
    ├── preferences/                          # Phase 3
    │   └── UserPreferencesRepository.kt     # DataStore-backed settings
    └── di/
        └── DataModule.kt                   # Hilt module for data layer
```

### `core/ink-engine/`

```
core/ink-engine/
├── build.gradle.kts
└── src/main/kotlin/com/drafty/core/ink/
    ├── renderer/
    │   ├── InkSurfaceView.kt               # Custom SurfaceView for drawing
    │   ├── StrokeRenderer.kt               # Renders strokes to Canvas
    │   └── ViewportManager.kt              # Handles zoom/pan/viewport culling
    ├── input/
    │   ├── InputProcessor.kt               # MotionEvent → InkPoint conversion
    │   ├── PalmRejectionFilter.kt          # Stylus vs. finger/palm detection
    │   └── GestureDetector.kt              # Two-finger zoom/pan gestures
    ├── stroke/
    │   ├── StrokeBuilder.kt                # Builds strokes from input points
    │   ├── BezierSmoother.kt               # Catmull-Rom / cubic Bézier smoothing
    │   ├── PressureMapper.kt               # Pressure → stroke width mapping
    │   └── StrokePath.kt                   # Generates variable-width Path
    ├── spatial/
    │   ├── RTree.kt                        # R-tree spatial index for strokes
    │   └── HitTester.kt                    # Stroke hit testing (eraser, selection)
    ├── command/
    │   ├── CanvasCommand.kt                # sealed interface for undo/redo
    │   ├── AddStrokeCommand.kt
    │   ├── DeleteStrokeCommand.kt
    │   ├── MoveStrokesCommand.kt
    │   └── UndoManager.kt                  # Undo/redo stack
    └── di/
        └── InkEngineModule.kt              # Hilt module
```

### `core/pdf-engine/`

```
core/pdf-engine/
├── build.gradle.kts
└── src/main/kotlin/com/drafty/core/pdf/
    ├── renderer/
    │   ├── PdfPageRenderer.kt              # Wraps PdfRenderer API
    │   └── PdfPageCache.kt                 # LRU bitmap cache
    ├── exporter/
    │   ├── PdfExporter.kt                  # Flatten annotations → export PDF
    │   └── PdfAnnotationCompositor.kt      # Composites strokes onto PDF pages
    ├── importer/
    │   └── PdfImporter.kt                  # Copies PDF to internal storage, extracts page info
    └── di/
        └── PdfEngineModule.kt              # Hilt module
```

### `core/ui/`

```
core/ui/
├── build.gradle.kts
└── src/main/kotlin/com/drafty/core/ui/
    ├── theme/
    │   ├── DraftyTheme.kt                # Material 3 theme
    │   ├── Color.kt
    │   ├── Typography.kt
    │   └── Shape.kt
    ├── components/
    │   ├── ColorPicker.kt                  # Color selection composable
    │   ├── ThicknessSlider.kt              # Stroke thickness selector
    │   ├── ToolButton.kt                   # Tool selection button
    │   ├── NotebookCard.kt                 # Notebook grid item
    │   ├── PageThumbnail.kt                # Page thumbnail composable
    │   └── ConfirmDialog.kt                # Reusable confirmation dialog
    └── icon/
        └── DraftyIcons.kt                # Custom icon definitions
```

### `feature/canvas/`

```
feature/canvas/
├── build.gradle.kts
└── src/main/kotlin/com/drafty/feature/canvas/
    ├── CanvasScreen.kt                     # Main drawing screen composable
    ├── CanvasViewModel.kt                  # Canvas state management
    ├── CanvasUiState.kt                    # UI state data class
    ├── components/
    │   ├── DrawingToolbar.kt               # Top/side toolbar with tools
    │   ├── CanvasContainer.kt              # Hosts InkSurfaceView in Compose
    │   └── PageNavigator.kt               # Page forward/back controls
    └── di/
        └── CanvasModule.kt
```

### `feature/notebooks/`

```
feature/notebooks/
├── build.gradle.kts
└── src/main/kotlin/com/drafty/feature/notebooks/
    ├── home/
    │   ├── HomeScreen.kt                   # Notebook grid with search, sort, FAB
    │   ├── HomeViewModel.kt                # Reactive Flow-based state
    │   └── HomeUiState.kt
    ├── detail/
    │   ├── NotebookDetailScreen.kt         # Sections tabs + page thumbnail grid
    │   ├── NotebookDetailViewModel.kt      # SavedStateHandle for notebookId
    │   └── NotebookDetailUiState.kt
    ├── settings/                            # Phase 3
    │   ├── SettingsScreen.kt               # App preferences UI
    │   └── SettingsViewModel.kt            # Reads/writes DataStore
    ├── components/
    │   ├── NotebookGrid.kt                 # LazyVerticalGrid of NotebookCards
    │   ├── NewNotebookDialog.kt            # Title + color picker dialog
    │   └── SectionTabs.kt                  # ScrollableTabRow + add/context menu
    └── di/
        └── NotebooksModule.kt
```

### `feature/pdf-viewer/`

```
feature/pdf-viewer/
├── build.gradle.kts
└── src/main/kotlin/com/drafty/feature/pdfviewer/
    ├── PdfAnnotateScreen.kt                # PDF + ink overlay screen
    ├── PdfViewerViewModel.kt
    ├── PdfViewerUiState.kt
    ├── components/
    │   ├── PdfPageView.kt                  # Single PDF page with annotation layer
    │   └── PdfThumbnailSidebar.kt          # PDF page thumbnails
    └── di/
        └── PdfViewerModule.kt
```

---

## Protobuf Schema File

```
proto/
└── strokes.proto                           # Stroke data protobuf schema
```

---

## Key Config Files

```
gradle/libs.versions.toml                   # Centralized dependency versions
build.gradle.kts                            # Root: plugins, common config
settings.gradle.kts                         # Module includes
app/build.gradle.kts                        # App-specific: applicationId, signing
.github/workflows/ci.yml                    # Build + lint + test on PR
.gitignore                                  # Standard Android .gitignore
```

---

## Revision Log

| Date | Change | Related Document |
|------|--------|-----------------|
| (Initial) | Created initial project structure | [architecture.md](./architecture.md) |
| Phase 0 | Scaffold created — all modules, build files, domain models, Room entities/DAOs, repository stubs, UI theme, feature screens, protobuf schema, CI workflow | [TODO.md](./TODO.md) |
| Phase 2 | Added `BoundingBox.kt`, `TemplateConfig.kt` to `:core:domain`; Implemented `RTree`, `HitTester`, `ViewportManager`, `GestureDetector`, `TemplateRenderer` in `:core:ink-engine`; Implemented `StrokeFileManager` binary I/O in `:core:data`; Updated `CanvasViewModel` with persistence, page nav, lasso; Implemented `PageNavigator` composable; Added Hilt bindings for use cases | [TODO.md](./TODO.md), [research.md](./research.md) |
| Phase 3 | Added `SectionRepository` + 5 section use cases to `:core:domain`; Added `SectionRepositoryImpl`, `UserPreferencesRepository` to `:core:data`; Updated `DataModule` with all missing bindings; Enhanced `CreateNotebookUseCase` with defaults; Implemented `HomeScreen`, `NotebookDetailScreen`, `SettingsScreen` + ViewModels; Implemented `NotebookCard`, `PageThumbnail`, `ConfirmDialog` components; Updated `DraftyNavGraph` with full navigation (home → detail → canvas → settings); Added `SavedStateHandle` to `CanvasViewModel` | [TODO.md](./TODO.md), [research-phase3.md](./research-phase3.md) |

---

*This document will be updated each time the project structure changes.*
