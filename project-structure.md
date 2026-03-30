# Project Structure

This document describes every directory in the Drafty project and what goes in each one.

```
Drafty/
├── build.gradle.kts              Root build — plugin version declarations only (apply false)
├── settings.gradle.kts           Module includes (:shared, :androidApp) and repository config
├── gradle.properties             JVM args, KMP flags, AndroidX opt-in
├── gradlew / gradlew.bat         Gradle wrapper scripts (generated)
├── gradle/wrapper/               Gradle wrapper JAR and properties (generated)
│
├── shared/                       KMP shared module — all business logic lives here
│   ├── build.gradle.kts          KMP targets, Compose, SQLDelight, Wire plugins and dependencies
│   └── src/
│       │
│       ├── commonMain/           Platform-independent code (runs on Android + iOS)
│       │   ├── kotlin/com/drafty/
│       │   │   ├── model/        Data classes and enums: Stroke, Canvas, Folder, ToolType,
│       │   │   │                 Template, FolderColor, StrokeId, InputPoint, BoundingBox
│       │   │   │
│       │   │   ├── state/        State management: CanvasViewModel, CanvasState, ActiveToolState,
│       │   │   │                 DrawCommand interface, all command implementations (AddStroke,
│       │   │   │                 EraseStroke, PartialErase, LassoMove, LassoRecolor, LassoDelete),
│       │   │   │                 UndoRedoManager
│       │   │   │
│       │   │   ├── spatial/      StrokeSpatialIndex — grid-based spatial index for off-screen
│       │   │   │                 culling and eraser hit testing
│       │   │   │
│       │   │   ├── persistence/  Storage layer: repository interfaces (FolderRepository,
│       │   │   │                 CanvasRepository, StrokeRepository), StrokeSerializer (protobuf),
│       │   │   │                 AutosaveManager, StrokeMetadata, DeserializedStroke,
│       │   │   │                 expect DatabaseDriverFactory
│       │   │   │
│       │   │   ├── export/       Import/export: DraftyExporter, DraftyImporter (.drafty ZIP format),
│       │   │   │                 PdfImportUseCase, expect PlatformPdfRenderer
│       │   │   │
│       │   │   ├── ui/
│       │   │   │   ├── library/  Library screen: LibraryScreen (folder grid, canvas thumbnails),
│       │   │   │   │             LibraryViewModel
│       │   │   │   ├── canvas/   Canvas screen: CanvasScreen (toolbar, drawing area layout)
│       │   │   │   └── theme/    DraftyTheme — custom CompositionLocal theme (dark neon)
│       │   │   │
│       │   │   └── util/         Cross-platform utilities: expect generateUuid(), expect
│       │   │                     currentTimeMillis()
│       │   │
│       │   ├── proto/com/drafty/storage/
│       │   │                     stroke.proto — Wire protobuf schema for stroke point serialization.
│       │   │                     Wire generates Kotlin code from this into build/generated/.
│       │   │
│       │   └── sqldelight/com/drafty/db/
│       │                         Drafty.sq — SQLDelight schema defining folder, canvas, stroke
│       │                         tables and all queries. SQLDelight generates type-safe Kotlin
│       │                         DAOs from this into build/generated/.
│       │
│       ├── commonTest/           Unit tests for all shared logic. Uses kotlin.test, coroutines-test.
│       │   └── kotlin/com/drafty/
│       │       ├── model/        BoundingBox, Stroke tests
│       │       ├── state/        UndoRedoManager, command, CanvasViewModel tests
│       │       ├── spatial/      StrokeSpatialIndex tests
│       │       ├── persistence/  StrokeSerializer tests
│       │       └── export/       Exporter/Importer tests
│       │
│       ├── androidMain/          Android-specific implementations
│       │   ├── AndroidManifest.xml   Minimal library manifest
│       │   └── kotlin/com/drafty/
│       │       ├── input/        Stylus input: StrokeInputHandler (MotionEvent → InProgressStrokesView),
│       │       │                 GestureHandler (pan/zoom), EraserHandler (stroke/partial erase)
│       │       │
│       │       ├── rendering/    Android rendering: BrushProvider (tool state → androidx.ink Brush),
│       │       │                 CommittedStrokeView (back-buffer View compositing template +
│       │       │                 highlighter + ink layers), StrokeCommitHandler (bridge from
│       │       │                 InProgressStrokesView to CanvasViewModel), TemplateRenderer
│       │       │                 (lined/grid/dot patterns), PdfPageRenderer (cached PDF rasterization),
│       │       │                 RenderableStroke + RenderableStrokeCache (shared→ink conversion cache)
│       │       │
│       │       ├── adapter/      StrokeAdapter — converts between androidx.ink.strokes.Stroke and
│       │       │                 the shared Stroke model (bidirectional)
│       │       │
│       │       ├── composable/   DrawingCanvas — @Composable that uses AndroidView interop to host
│       │       │                 CommittedStrokeView + InProgressStrokesView in a FrameLayout
│       │       │
│       │       ├── persistence/  actual DatabaseDriverFactory (AndroidSqliteDriver), repository
│       │       │                 implementations: SqlDelightFolderRepository,
│       │       │                 SqlDelightCanvasRepository, SqlDelightStrokeRepository
│       │       │
│       │       ├── export/       actual PlatformPdfRenderer (android.graphics.pdf.PdfRenderer),
│       │       │                 CanvasExportRenderer (PDF/PNG export via PdfDocument + Bitmap)
│       │       │
│       │       └── util/         actual generateUuid() (java.util.UUID), actual currentTimeMillis()
│       │
│       ├── androidInstrumentedTest/  Instrumented tests (run on device/emulator)
│       │   └── kotlin/com/drafty/
│       │       ├── persistence/      SQLDelight repository tests with real SQLite
│       │       └── adapter/          StrokeAdapter round-trip tests
│       │
│       └── iosMain/              Future iOS implementations (stubs for now)
│           └── kotlin/com/drafty/
│               ├── input/        PencilInputHandler (UITouch → PencilKit)
│               ├── rendering/    PencilKitCanvasView
│               ├── adapter/      StrokeAdapter (PencilKit ↔ shared)
│               ├── persistence/  actual DatabaseDriverFactory (NativeSqliteDriver)
│               ├── export/       actual PlatformPdfRenderer (CoreGraphics)
│               └── util/         actual generateUuid(), actual currentTimeMillis()
│
└── androidApp/                   Thin Android application shell
    ├── build.gradle.kts          Android application plugin, depends on :shared
    └── src/main/
        ├── AndroidManifest.xml   Application entry point, activity, permissions
        ├── kotlin/com/drafty/android/
        │   ├── MainActivity.kt       Single Activity — sets Compose content
        │   ├── DraftyApplication.kt   Application subclass — initializes DB driver, DI
        │   └── DraftyApp.kt          Root @Composable — navigation host for Library ↔ Canvas
        └── res/values/
            ├── strings.xml        App name and UI strings
            ├── themes.xml         Dark theme for the app shell (splash/system bars)
            └── colors.xml         Color resources
```

## Module Boundaries

**shared/** contains all business logic, data models, state management, persistence, and even most UI (Compose Multiplatform). The only things that live in platform source sets are:
- Rendering (too different between androidx.ink and PencilKit to abstract)
- Stylus input capture (MotionEvent vs UITouch)
- Platform ↔ shared stroke conversion
- Database driver creation
- PDF rendering/export

**androidApp/** is intentionally thin — it provides the `Application`, `Activity`, navigation wiring, and Android resources. No business logic.

## Code Generation

Two build plugins generate source code into `build/generated/`:
- **SQLDelight** reads `Drafty.sq` and generates type-safe Kotlin query wrappers
- **Wire** reads `stroke.proto` and generates Kotlin protobuf data classes

These generated sources are not checked into version control.
