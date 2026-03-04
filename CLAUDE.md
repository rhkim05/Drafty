# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A tablet note-taking app ("Goodnotes + Parallel Pages"). Built with a **hybrid React Native + Kotlin architecture**: React Native handles UI/navigation, while the drawing engine runs natively in Kotlin to bypass the JS bridge for low-latency stylus input.

## Commands

No build scripts are defined in `package.json` yet. Standard React Native commands apply:

```bash
# Install dependencies
npm install

# Run on Android
npx react-native run-android

# TypeScript check
npx tsc --noEmit
```

## Project Structure

```
tablet-note-app/
├── android/app/src/main/java/com/tabletnoteapp/  # Kotlin native
│   ├── canvas/                    # Pure drawing engine (no RN dependency)
│   │   ├── DrawingCanvas.kt       # Custom View: touch events + rendering
│   │   ├── models/Stroke.kt       # Stroke data model
│   │   ├── models/Point.kt        # Point (x, y, pressure) data model
│   │   └── utils/BezierSmoother.kt
│   └── reactbridge/               # RN <-> Kotlin bridge
│       ├── CanvasViewManager.kt   # Exposes DrawingCanvas as RN UI component
│       ├── CanvasModule.kt        # Exposes Kotlin methods (save, clear, etc.) to JS
│       └── CanvasPackage.kt       # Registers both with RN engine
├── src/                           # React Native (TypeScript)
│   ├── components/                # Toolbar.tsx, ColorPicker.tsx
│   ├── screens/                   # HomeScreen.tsx, NoteEditorScreen.tsx
│   ├── store/                     # Zustand stores
│   ├── native/                    # CanvasView.tsx, CanvasModule.ts (bridge wrappers)
│   └── types/canvasTypes.ts       # Shared types between RN and Kotlin
└── App.tsx                        # Entry point, navigation setup
```

## Architecture

### Hybrid RN + Native Drawing Engine

The core design decision: drawing is handled entirely in Kotlin (`android/app/src/main/java/com/tabletnoteapp/canvas/`) using Android Canvas API, bypassing the React Native bridge for rendering. The bridge is only used for commands (tool changes, save, undo) and events (stroke completed, etc.).

**Data flow for drawing:**

1. Touch events → `DrawingCanvas.kt` (Kotlin, native speed)
2. Stroke smoothing → `BezierSmoother.kt`
3. Tool state changes (pen type, color, thickness) → Zustand store in RN → `CanvasModule.ts` bridge → Kotlin

### React Native Bridge Layer (`src/native/`)

- `CanvasView.tsx` — Maps the Kotlin `DrawingCanvas` view as a native RN component
- `CanvasModule.ts` — Bridge for imperative commands to the canvas (save, clear, undo/redo, viewport pan/zoom) and receiving native events back

### State Management (`src/store/`)

Three Zustand stores:

- `useToolStore` — Active tool state: pen type, color, thickness, eraser mode
- `useNotebookStore` — Notebook/page list and metadata
- `useEditorStore` — Editor-level state: current page, zoom level, etc.

### Kotlin Native (`android/.../canvas/`)

- `DrawingCanvas.kt` — Main canvas view, handles touch events and rendering
- `BezierSmoother.kt` — Smooths raw touch points into bezier curves
- `models/Point.kt`, `models/Stroke.kt` — Data models

### React Native Bridge (`android/.../reactbridge/`)

- `CanvasViewManager.kt` — Registers `DrawingCanvas` as a native RN view
- `CanvasModule.kt` — Exposes imperative canvas methods to JS
- `CanvasPackage.kt` — Registers both with the React Native package system

### Shared Types (`src/types/canvasTypes.ts`)

TypeScript types that mirror Kotlin data models — defines the contract between RN and Kotlin (pen colors, stroke thickness, tool modes, etc.).

## Planned Stack (from `stack.yaml`)

The following libraries are planned but not yet installed:

- `react-native-skia` — Alternative drawing engine (evaluate vs. current Kotlin approach)
- `react-native-gesture-handler` + `react-native-reanimated` — Gesture and animation
- `WatermelonDB` or `Realm` — Local database for notes
- `react-native-mmkv` — Fast key-value storage
- OpenAI / Anthropic Claude API — AI integration
- `react-native-fast-image` — Image handling
