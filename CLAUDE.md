# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Issue Tracking (MANDATORY)

**Every issue, error, or problem encountered while working on this project MUST be logged in [`docs/issues.md`](docs/issues.md).** This applies even after `/clear` — always check and update `issues.md`.

For each issue, document:
1. **What happened** — the exact error or unexpected behavior
2. **Why it happened** — root cause analysis
3. **How to fix** — solution applied, or proposed fix if still open
4. **Files/architecture affected** — which files were changed or need changing

Use the format: `## ISSUE-NNN: Title`, with `Status` (Resolved/Open), `Severity`, and `Date discovered` fields. Number issues sequentially — read the file first to find the next available number.

## Project Overview

A tablet note-taking app ("Goodnotes + Parallel Pages"), Android package `com.tabletnoteapp`. Built with a **hybrid React Native 0.73 + Kotlin architecture**: React Native handles UI/navigation/state, while the drawing engine runs natively in Kotlin using Android Canvas API to bypass the JS bridge for low-latency stylus input.

Three screens: `HomeScreen` (note grid + PDF import), `NoteEditorScreen` (blank canvas), `PdfViewerScreen` (PDF + annotation overlay). Navigation via `@react-navigation/native-stack` v6.

## Commands

```bash
npm install --legacy-peer-deps       # --legacy-peer-deps is REQUIRED
npx react-native start               # Terminal 1 — Metro bundler
npx react-native run-android         # Terminal 2 — build & deploy to device/emulator
npx tsc --noEmit                     # TypeScript type check (no test framework configured)
cd android && ./gradlew build         # Direct Gradle build (if needed)
```

On Windows, use `.\node_modules\.bin\react-native` instead of `npx` if `npx` is unavailable.

TypeScript is in strict mode (`tsconfig.json`). There is no test framework, linter, or formatter configured.

## Critical Build Constraints

- **JDK 17 required** — JDK 21+ breaks Gradle 8.3. JDK path goes in `~/.gradle/gradle.properties` (never in `android/gradle.properties`).
- **Gradle 8.3 + AGP 8.1.1** — do not upgrade. RN 0.73's Gradle plugin has Kotlin warnings that become errors under Gradle 8.11+.
- **`android/local.properties`** must contain `sdk.dir=...` pointing to your Android SDK (file is gitignored, create if missing).
- **`android/gradle.properties`** is committed (shared settings) — never put machine-specific paths there.
- **Do not upgrade these pinned packages** (last versions compatible with RN 0.73):
  - `react-native-screens@3.35.0`, `react-native-safe-area-context@4.10.0`, `react-native-blob-util@0.19.11`
  - `@react-navigation/native@6.x` + `native-stack@6.x`, `@react-native-async-storage/async-storage@1.23.1`
  - `react-native-gesture-handler@~2.14.0`, `react-native-reanimated@~3.6.0`
- `androidx.core` and `androidx.transition` are pinned in `android/build.gradle` via `resolutionStrategy` (compileSdk 34).

See [docs/build-requirements.md](docs/build-requirements.md) for full setup details.

## Architecture

### Dual Native Drawing Engine (Kotlin)

Two separate custom Android Views in `android/app/src/main/java/com/tabletnoteapp/canvas/`, no RN dependency:

- **`DrawingCanvas.kt`** — blank note canvas, exposed as `"CanvasView"` via `CanvasViewManager.kt`
- **`PdfDrawingView.kt`** — PDF rendering (Android `PdfRenderer`) + drawing overlay, exposed as `"PdfCanvasView"` via `PdfCanvasViewManager.kt`. Adds `scrollToPage` command.

Shared engine patterns:
- Off-screen `Bitmap` caches committed strokes; only the active in-progress stroke is replayed each `onDraw`.
- Eraser uses `PorterDuff.Mode.CLEAR` inside `canvas.saveLayer()`, which **requires `LAYER_TYPE_SOFTWARE`** (hardware accel breaks it). This forces all rendering through CPU.
- Undo replays ALL committed strokes onto a fresh bitmap (O(n) — degrades with stroke count, see ISSUE-007).
- Bezier smoothing via `BezierSmoother.kt`. Data models in `canvas/models/` (`Point.kt`, `Stroke.kt`).

### RN-Kotlin Bridge

Communication flows through two layers in `android/.../reactbridge/`:

- **ViewManagers** (`CanvasViewManager`, `PdfCanvasViewManager`) — expose native views as React components. Props: `tool`, `penColor`, `penThickness`, `eraserThickness` (+ `pdfUri` for PDF).
- **Modules** (`CanvasModule`, `PdfCanvasModule`) — imperative commands via `findNodeHandle` view tag: `undo`, `redo`, `clear`, `getStrokes` (async, returns JSON), `loadStrokes`.
- **Kotlin → JS events** via `DeviceEventEmitter`: `canvasUndoRedoState` (after each stroke/undo/redo), `canvasEraserLift` (eraser ACTION_UP), `spenButtonPress` (S-Pen hardware button).
- **`ColorGradientViewManager.kt`** — native HSV picker, emits `colorPickerSVChange`/`colorPickerHueChange`.

React-side wrappers live in `src/native/` (`CanvasView.tsx`, `PdfCanvasView.tsx`, `ColorGradientView.tsx`). These wrap `requireNativeComponent` with `forwardRef` and own the `DeviceEventEmitter` subscriptions for undo/redo state sync.

### State Management (Zustand)

- **`useNotebookStore`** — `Note[]` + `Category[]`, persisted via AsyncStorage (key: `notebook-store`). Notes have optional `drawingUri` and `categoryId`.
- **`useToolStore`** — in-memory only. Active tool (pen/eraser/select), canUndo/canRedo, color, thickness. Updated by native `DeviceEventEmitter` listeners.
- **`useSettingsStore`** — persisted via AsyncStorage (key: `settings-store`). Dark mode, S-Pen button mappings, auto-switch-to-pen toggle.
- **`useEditorStore`** — stub (current page, zoom — not fully wired).

### Stroke Persistence

Both editor screens use the same pattern:
- **Save**: on `beforeRemove` navigation event only — `getStrokes(tag)` → write JSON to `DocumentDirectoryPath/drawings/<noteId>.json`. No periodic auto-save, no `AppState` listener (data loss risk on app kill — see ISSUE-005).
- **Load**: on canvas layout callback, read file and call `loadStrokes(tag, json)`.
- **PDF annotations**: flat JSON array with no page field — page association is implicit via Y-coordinate (see ISSUE-006).

### Theming

`src/styles/theme.ts` defines `lightTheme`/`darkTheme` (12 color tokens). `useTheme()` hook reads `isDarkMode` from settings store. Canvas drawing surface is always white (not themed). PDF viewer UI is always dark.

## Detailed Documentation

- [docs/architecture.md](docs/architecture.md) — full architecture details (navigation, bridge, S-Pen, sidebar)
- [docs/build-requirements.md](docs/build-requirements.md) — JDK/SDK setup, device/emulator config, dependency quirks
- [docs/project-structure.md](docs/project-structure.md) — annotated file tree
- [docs/research.md](docs/research.md) — comprehensive deep-dive report
- [docs/issues.md](docs/issues.md) — issue tracker (always check before starting work)
