# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

**Read [`workflow.md`](workflow.md) before starting any task.** This project uses a strict multi-phase pipeline. Never write code without an approved plan.

Pipeline: `research.md` → `plan.md` & `TODO.md` → Annotate → Implementation

- **`research.md`** — Deep reading of relevant code/folders. Write detailed findings here before planning. Review surface for verifying understanding.
- **`plan.md`** — Detailed approach, code snippets, trade-offs. Iterated via annotation cycles.
- **`TODO.md`** — Granular task checklist and progress tracking. Linked from `plan.md`, not duplicated there. **Always include a "Manual Testing Checklist (Emulator)" section** with testable items for the current phase. This checklist is for the user to fill in manually — do not mark these items as complete.

During implementation, mark tasks complete in `TODO.md` and run typecheck continuously.

## Project Context

Drafty is a handwriting/note-taking app targeting Android (primary) with a future iOS port planned. Built with Kotlin Multiplatform (KMP). Full technical research and architecture details are in [`research.md`](research.md).

## Build & Development Commands

```bash
# Build
./gradlew build                              # Full build (all modules + tests)
./gradlew :androidApp:assembleDebug          # Debug APK only

# Typecheck (run continuously during implementation)
./gradlew :shared:compileDebugKotlinAndroid  # Typecheck shared module
./gradlew :androidApp:compileDebugKotlin     # Typecheck Android app

# Tests
./gradlew test                               # All unit tests
./gradlew :shared:testDebugUnitTest          # Shared module unit tests only
./gradlew :shared:connectedDebugAndroidTest  # Instrumentation tests (requires emulator)

# Lint
./gradlew lint                               # Android lint (all modules)
./gradlew :shared:lintDebug                  # Shared module only

# Install & Run
./gradlew :androidApp:installDebug           # Install on connected emulator/device
```

### Manual Testing on Emulator

```bash
# 1. List available AVDs
emulator -list-avds

# 2. Launch the emulator (use the Pixel_Tablet AVD)
emulator -avd Pixel_Tablet &

# 3. Wait for the emulator to boot, then install and run
./gradlew :androidApp:installDebug

# 4. Launch the app
~/Library/Android/sdk/platform-tools/adb shell am start -n com.drafty.android/.MainActivity
```

Note: `adb` is not on PATH by default — use the full path `~/Library/Android/sdk/platform-tools/adb`.

## Architecture

### Module Structure

Two Gradle modules (`:shared` and `:androidApp`). Almost all code lives in `:shared`:

- **`shared/commonMain`** — Platform-independent: domain models, state management, persistence interfaces, spatial indexing, export/import logic, all Compose UI
- **`shared/androidMain`** — Android-specific: rendering (androidx.ink), touch input handling, SQLDelight repository implementations, PDF rendering
- **`shared/iosMain`** — iOS stubs for future port
- **`androidApp`** — Thin shell: `MainActivity` → `DraftyApp` composable → shared screens

### Android SDK & Toolchain

- minSdk 29, targetSdk 35, compileSdk 35
- JVM target: 17
- Gradle 8.11, AGP 8.7.3

### Key Dependencies

- Kotlin 2.1.10, Jetbrains Compose 1.7.3
- SQLDelight 2.0.2, Wire Protobuf 5.1.0
- androidx.ink 1.0.0-alpha01 (authoring, rendering, strokes, brush, geometry)
- kotlinx-coroutines 1.9.0

### Navigation

Two-screen model: **LibraryScreen** (folder/canvas grid) ↔ **CanvasScreen** (drawing surface). Wired in `DraftyApp` composable, which is the root navigation host called from `MainActivity`.

### Dependency Wiring

No DI framework. Dependencies are manually constructed and passed via ViewModel constructors. Platform abstractions use Kotlin `expect`/`actual` (e.g., `DatabaseDriverFactory`, `PlatformPdfRenderer`, `UuidGenerator`, `TimeProvider`).

### Key Patterns

**MVVM + Command Pattern:** `CanvasViewModel` exposes `StateFlow<CanvasState>`. All canvas mutations go through `DrawCommand` implementations (`AddStrokeCommand`, `EraseStrokeCommand`, `PartialEraseCommand`, `LassoMoveCommand`, `LassoDeleteCommand`, `LassoRecolorCommand`) managed by `UndoRedoManager`.

**Dual-Buffer Rendering (Android):** Live strokes render via `InProgressStrokesView` (androidx.ink front-buffered SurfaceView for sub-frame latency). On stroke completion, `StrokeCommitHandler` converts to the shared `Stroke` model and `CommittedStrokeView` composites template + highlighter + ink layers in its `onDraw`. Both views are hosted in `DrawingCanvas` composable via `AndroidView` interop. Layer order in `CommittedStrokeView.onDraw`: template → highlighter strokes → pen ink strokes.

**Spatial Indexing:** Grid-based `StrokeSpatialIndex` (256px cells) provides O(visible) culling for rendering and eraser hit-testing via `query(BoundingBox) → Set<StrokeId>`.

**No shared renderer abstraction** — Android uses androidx.ink, future iOS will use PencilKit. Both consume the shared `Stroke` data model via platform-specific adapters (`StrokeAdapter`).

### Stroke Data Flow

Touch → `StrokeInputHandler` → `InProgressStrokesView` (live render) → on `ACTION_UP` → `StrokeCommitHandler` converts `androidx.ink.strokes.Stroke` → shared `Stroke` via `StrokeAdapter` → `CanvasViewModel.commitStroke()` → `AddStrokeCommand.execute()` → `CanvasState` updated → `CommittedStrokeView.invalidate()` → `UndoRedoManager.push()` → `AutosaveManager.debounce()` → `StrokeRepository.save()`.

### Persistence

- **SQLDelight** generates `DraftyDatabase` (package `com.drafty.db`) from `shared/src/commonMain/sqldelight/com/drafty/db/Drafty.sq` (tables: `folder`, `canvas`, `stroke`)
- **Wire Protobuf** generates Kotlin classes from `shared/src/commonMain/proto/com/drafty/storage/stroke.proto` for compact stroke point serialization
- Repository interfaces in `commonMain/persistence/`, implementations in `androidMain/persistence/`
- `AutosaveManager` debounces writes at 300ms

### Code Generation

SQLDelight and Wire both generate code at build time into `build/generated/`. If you modify `.sq` or `.proto` files, run a build to regenerate before typechecking.
