# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

**Read [`workflow.md`](workflow.md) before starting any task.** This project uses a strict Research → Plan → Annotate → Implement pipeline. Never write code without an approved plan. Write research into `research.md`, plans into `plan.md`, iterate via inline annotation cycles, and only implement when explicitly told to. During implementation, mark tasks complete in the plan and run typecheck continuously.

## Build & Development Commands

```bash
# Full build (all modules)
./gradlew build

# Shared module tests
./gradlew :shared:allTests

# Android lint
./gradlew :androidApp:lint

# Compile shared module per target
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinIosArm64

# Generate SQLDelight sources
./gradlew :shared:generateCommonMainDraftyDatabaseInterface
```

CI runs: `build` → `:shared:allTests` → `:androidApp:lint` (see `.github/workflows/build.yml`).

## Architecture

**Kotlin Multiplatform (KMP)** app targeting Android (primary) and iOS (planned). Two Gradle modules:

- **`:shared`** — KMP library with business logic, data layer, and database. Compiles to Android library + iOS framework (iosX64, iosArm64, iosSimulatorArm64).
- **`:androidApp`** — Android app using Jetpack Compose + Material 3. Depends on `:shared`.

### Layers (Clean Architecture + MVI)

```
androidApp (UI, platform DI, navigation)
    ↓
shared/commonMain (stores, use cases, models, repositories, SQLDelight)
    ↓
shared/{androidMain,iosMain} (expect/actual: DB drivers, platform utils)
```

**MVI State Management**: Stores (`CanvasStore`, `NotebookStore`, `LibraryStore`) handle intents and emit state. Screens observe state via Compose.

### Navigation

Three Compose routes in `DraftyNavHost`:
- `library` — notebook list
- `notebook/{notebookId}` — page navigator
- `canvas/{notebookId}/{pageId}` — drawing canvas

Type-safe route builders: `Routes.notebook(id)`, `Routes.canvas(notebookId, pageId)`.

### Database

**SQLDelight 2.0.2** with schema in `shared/src/commonMain/sqldelight/.../DraftyDatabase.sq`. Five tables: `Notebook`, `Page`, `PdfDocument`, `Stroke`, `TextBox`. Platform drivers via `expect/actual` `DatabaseDriverFactory`.

### Dependency Injection

**Koin** with two modules: `SharedModule` (database, shared services) and `AppModule` (Android-specific, e.g. `DatabaseDriverFactory`). Initialized in `DraftyApp.onCreate()`.

## Key Libraries

- **androidx.ink 1.0.0** — 9 modules for stylus drawing/rendering (core to the app's purpose)
- **SQLDelight 2.0.2** — multiplatform database
- **Koin 4.0.2** — multiplatform DI
- **Napier 2.7.1** — multiplatform logging
- **kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime, Okio**

## Project Context

Drafty is a handwriting/note-taking app. Phase 0 (scaffolding) is complete. Implementation follows the phased plan in `plan.md`, with research context in `research.md` and progress tracked in `TODO.md`.

Android min SDK 26, compile/target SDK 35, JVM target 17, Kotlin 2.1.10.
