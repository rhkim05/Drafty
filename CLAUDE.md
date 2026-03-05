# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A tablet note-taking app ("Goodnotes + Parallel Pages"). Built with a **hybrid React Native + Kotlin architecture**: React Native handles UI/navigation, while the drawing engine runs natively in Kotlin to bypass the JS bridge for low-latency stylus input.

## Commands

```bash
# Install dependencies (use --legacy-peer-deps — required due to version conflicts)
npm install --legacy-peer-deps

# Start Metro bundler (Terminal 1)
npx react-native start

# Build and run on Android emulator (Terminal 2)
npx react-native run-android

# TypeScript check
npx tsc --noEmit
```

### Android build requirements

- **JDK 17** is required (JDK 21+ breaks Gradle 8.3). Path is pinned in `android/gradle.properties` via `org.gradle.java.home`.
- `android/gradle.properties` sets `org.gradle.java.home=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home` — update this path if JDK 17 is installed elsewhere.
- Gradle wrapper: 8.3, AGP: 8.1.1. Do not upgrade these — RN 0.73's gradle plugin has Kotlin warnings that become errors under Gradle 8.11+.
- `androidx.core` and `androidx.transition` are pinned to older versions in `android/build.gradle` via `resolutionStrategy` to stay within `compileSdk 34`.
- The `native_modules.gradle` path in `android/settings.gradle` and `android/app/build.gradle` points to `node_modules/react-native/node_modules/@react-native-community/cli-platform-android/` (not the root `node_modules/`) due to npm hoisting.

## Project Structure

```
tablet-note-app/
├── index.js                       # App entry point, registers TabletNoteApp component
├── App.tsx                        # Entry point, mounts <Navigation />
├── metro.config.js
├── android/
│   ├── build.gradle               # AGP 8.1.1, compileSdk 34, androidx pins
│   ├── gradle.properties          # JDK 17 path, Hermes, new arch flags
│   ├── settings.gradle
│   └── app/
│       ├── build.gradle
│       └── src/main/java/com/tabletnoteapp/
│           ├── MainActivity.kt
│           ├── MainApplication.kt
│           ├── canvas/                    # Pure drawing engine (no RN dependency)
│           │   ├── DrawingCanvas.kt       # Custom View: touch events + rendering
│           │   ├── models/Stroke.kt
│           │   ├── models/Point.kt        # Point (x, y, pressure)
│           │   └── utils/BezierSmoother.kt
│           └── reactbridge/               # RN <-> Kotlin bridge
│               ├── CanvasViewManager.kt
│               ├── CanvasModule.kt
│               └── CanvasPackage.kt
├── src/
│   ├── screens/
│   │   ├── HomeScreen.tsx         # Note grid, PDF import button
│   │   ├── PdfViewerScreen.tsx    # PDF viewer + canvas overlay for drawing on PDFs
│   │   └── NoteEditorScreen.tsx   # Blank drawing canvas screen
│   ├── store/
│   │   ├── useNotebookStore.ts    # Notes list with AsyncStorage persistence (addNote, deleteNote, updateNote)
│   │   ├── useToolStore.ts        # Active tool state (pen/eraser/select), undo/redo availability
│   │   └── useEditorStore.ts      # (stub) Current page, zoom
│   ├── navigation/
│   │   └── index.tsx              # NavigationContainer + RootStackParamList (Home, PdfViewer, NoteEditor)
│   ├── native/                    # Bridge wrappers
│   │   ├── CanvasView.tsx         # requireNativeComponent wrapper with forwardRef
│   │   └── CanvasModule.ts        # undo/redo/clear/getStrokes/loadStrokes imperative commands
│   ├── components/
│   │   ├── Toolbar.tsx            # (stub)
│   │   └── ColorPicker.tsx        # (stub)
│   ├── utils/
│   │   ├── canvas.ts              # (stub)
│   │   ├── geometry.ts            # (stub)
│   │   └── performance.ts         # (stub)
│   └── types/
│       ├── noteTypes.ts           # Note, NoteType
│       └── canvasTypes.ts         # PenColor, ToolMode, StrokeStyle (RN <-> Kotlin contract)
```

## Architecture

### Navigation (`src/navigation/index.tsx`)

Uses `@react-navigation/native-stack`. `RootStackParamList` and the `NavigationContainer` live in `src/navigation/index.tsx`. `App.tsx` just mounts `<Navigation />`. Current routes: `Home` and `PdfViewer: { note: Note }`. `NoteEditorScreen` is not yet wired in.

### PDF Import Flow

1. User taps "Import PDF" → `react-native-document-picker` opens system file picker
2. Selected file is copied to `DocumentDirectoryPath/pdfs/` via `react-native-fs`
3. A `Note` object (`type: 'pdf'`, `pdfUri: <internal path>`) is saved to `useNotebookStore`
4. Tapping the PDF card navigates to `PdfViewerScreen` with the note passed as a route param

### State Management

- `useNotebookStore` — the only implemented store; holds `Note[]` in memory (no persistence yet)
- `Note` type is defined in `src/types/noteTypes.ts` and is the shared contract across screens and the store

### Hybrid Drawing Engine (not yet active)

Drawing will be handled entirely in Kotlin (`canvas/`) using Android Canvas API, bypassing the RN bridge for rendering. The bridge (`reactbridge/`) is only for commands (tool changes, save, undo) and events. The `src/native/` files are stubs waiting for the Kotlin implementation.

### Known Dependency Quirks

- `npm install` requires `--legacy-peer-deps` due to conflicts between installed library versions and RN 0.73
- `react-native-pdf` requires `react-native-blob-util` as a peer dependency
- `@react-native/metro-config` must be installed explicitly (not bundled in this setup)
- **Do not upgrade these packages** — they are pinned to the last versions compatible with RN 0.73 (which lacks `BaseReactPackage`):
  - `react-native-screens@3.35.0` (3.36+ breaks)
  - `react-native-safe-area-context@4.10.0` (4.11+ breaks)
  - `react-native-blob-util@0.19.11` (0.21+ breaks)
  - `@react-navigation/native@6.x` + `@react-navigation/native-stack@6.x` (v7 requires screens 4.x)
  - `@react-native-async-storage/async-storage@1.23.1` (v2+ requires Kotlin 2.1.0 via KSP; project uses Kotlin 1.8.0)

### Git / GitHub

- `android/app/build/` is in `.gitignore` — never commit build outputs. The debug APK is 164MB and will be rejected by GitHub.
