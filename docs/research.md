# Drafty - Comprehensive Codebase Research Report

## Project Overview

**Drafty** is a tablet-optimized note-taking and PDF annotation app built with a **hybrid React Native + Kotlin** architecture. The core innovation is a native Kotlin drawing engine that bypasses the React Native JS bridge entirely, enabling low-latency stylus input — critical for a natural writing/drawing experience on tablets with S-Pen or similar styluses.

### Key Capabilities
- Freehand drawing on blank canvas notes
- PDF import, viewing, and annotation
- Multi-touch gestures (pinch-to-zoom, pan, scroll)
- Samsung S-Pen hardware button customization
- Dark/light theme support
- Auto-save to device storage (offline-first)
- Custom categories and favorites for note organization
- Export notes as PDF/PNG

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    React Native UI (JS/TS)                  │
│  Screens: HomeScreen, NoteEditorScreen, PdfViewerScreen     │
│  Components: Toolbar, Sidebar, ColorPicker, ThumbnailStrip  │
│  Navigation: @react-navigation/native-stack                 │
│  State: Zustand stores (notebook, tool, settings)           │
└──────────────────────┬──────────────────────────────────────┘
                       │  React Native Bridge
                       │  (ViewManagers + NativeModules)
┌──────────────────────┴──────────────────────────────────────┐
│              Native Drawing Engine (Kotlin)                  │
│  DrawingCanvas.kt       — blank note canvas                 │
│  PdfDrawingView.kt      — PDF renderer + annotation overlay │
│  ColorGradientView.kt   — HSV color picker                  │
│  BezierSmoother.kt      — quadratic Bezier curve smoothing  │
│  MainActivity.kt        — S-Pen hardware event capture      │
│                                                              │
│  Drawing is fully offline from the JS bridge:                │
│  touch → native canvas → bitmap, no round-trip to JS        │
└──────────────────────────────────────────────────────────────┘
```

The split is deliberate: React Native handles UI chrome, navigation, state, and persistence, while Kotlin handles everything latency-sensitive (drawing, PDF rendering, color picking).

---

## Navigation & Screens

Three screens managed by `@react-navigation/native-stack`:

### 1. HomeScreen (`src/screens/HomeScreen.tsx`, ~580 lines)
- **3-column grid** of note cards (FlatList)
- Each card shows: title, date, thumbnail, PDF badge, favorite star, options menu
- **Sidebar** (animated slide-in, 240px): category navigation + settings modal
- **Category filtering**: All, Favorites, PDFs, Notes, plus user-created custom categories
- **Modals**: rename note, add to category, export (format + page range), note options popup
- **PDF import**: uses `react-native-document-picker`, copies PDF to app storage, generates cover thumbnail
- Automatic unique title generation to avoid collisions

### 2. NoteEditorScreen (`src/screens/NoteEditorScreen.tsx`, ~130 lines)
- Full-screen `CanvasView` (native Kotlin drawing surface)
- Header with back button and note title
- Toolbar for undo/redo, tool selection, pen/eraser config
- **Lifecycle**: loads saved strokes on layout, auto-saves strokes on navigation exit (`beforeRemove` listener)

### 3. PdfViewerScreen (`src/screens/PdfViewerScreen.tsx`, ~290 lines)
- `PdfCanvasView` renders PDF pages with annotation overlay
- Header with back, title, clickable page indicator ("Go to page" modal)
- Toolbar with hand/draw tool toggle, thumbnail strip toggle
- `pointerEvents` toggled between `'none'` (scroll mode) and `'auto'` (draw mode)
- **ThumbnailStrip**: horizontal scrollable page previews at bottom
- Listens to native events: `pdfCanvasPageChanged`, `pdfCanvasLoadComplete`
- Saves `lastPage` bookmark on unmount for resume

---

## State Management (Zustand)

### useNotebookStore (persisted via AsyncStorage, key: `'notebook-store'`)
```
notes: Note[]           — all user notes
categories: Category[]  — custom categories
addNote / deleteNote / updateNote / addCategory / deleteCategory
```

### useToolStore (in-memory, not persisted)
```
activeTool: 'pen' | 'eraser' | 'select'
canUndo / canRedo: boolean
penThickness: number (default 4)
eraserThickness: number (default 24)
eraserMode: 'pixel' | 'stroke'
penColor: string (hex)
presetColors: string[] (10 editable swatches)
```
Updated by native `DeviceEventEmitter` listeners (`canvasUndoRedoState`, `canvasEraserLift`).

### useSettingsStore (persisted via AsyncStorage, key: `'settings-store'`)
```
penButtonAction: PenAction        — single-press S-Pen action
penButtonDoubleAction: PenAction  — double-press S-Pen action
autoSwitchToPen: boolean          — auto-switch to pen after eraser lift
isDarkMode: boolean
```
PenAction options: `'none'`, `'togglePenEraser'`, `'eraser'`, `'pen'`, `'undo'`

### useEditorStore (stub — reserved for future use)

---

## Data Persistence & Storage

### File System Layout
All files stored in the device's `DocumentDirectoryPath` (`/data/data/com.tabletnoteapp/files/`):

```
DocumentDirectoryPath/
├── drawings/              # Stroke data as JSON
│   └── {noteId}.json
├── pdfs/                  # Imported PDF copies
│   └── {timestamp}_{name}.pdf
└── thumbnails/            # Generated page thumbnails
    └── {noteId}_p{N}.jpg
```

### Note Data Model (`src/types/noteTypes.ts`)
```typescript
interface Note {
  id: string              // Unix timestamp as string
  title: string
  createdAt: number
  updatedAt: number
  type: 'note' | 'pdf'
  pdfUri?: string         // absolute path to PDF file
  drawingUri?: string     // path to strokes JSON file
  lastPage?: number       // PDF bookmark (resume position)
  totalPages?: number
  thumbnailUri?: string   // cover image path
  categoryIds?: string[]
  isFavorite?: boolean
}
```

### Stroke Serialization Format
```json
[
  {
    "tool": "PEN",
    "color": -16777216,
    "thickness": 4.0,
    "points": [
      { "x": 100.5, "y": 200.0, "pressure": 1.0 },
      { "x": 110.2, "y": 205.3, "pressure": 0.95 }
    ]
  }
]
```
Colors stored as Android integer color values. Points include pressure data (captured but currently unused in rendering).

---

## Native Drawing Engine (Kotlin)

### DrawingCanvas.kt (Blank Notes)
- **Off-screen Bitmap + Canvas** for committed strokes
- Touch input via `onTouchEvent()` handling `ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP`
- **Bezier smoothing** via `BezierSmoother.buildPath()` — quadratic Bezier curves with midpoint anchors
- **Eraser modes**:
  - `pixel`: `PorterDuff.Mode.CLEAR` (transparent holes, requires `saveLayer` isolation)
  - `stroke`: hit-test radius-based removal of entire committed strokes
- **Undo/Redo**: stack-based with `UndoAction` enum (`AddStroke`, `EraseStrokes`)
  - Undo replays bitmap from scratch minus the undone action
  - Erased strokes re-inserted at original indices in descending order to avoid offset issues
- **Paint config**: anti-aliased, round cap/join, stroke style
- **Requires** `setLayerType(LAYER_TYPE_SOFTWARE, null)` — hardware acceleration breaks `PorterDuff.CLEAR`
- Serialization: `getStrokesJson()` / `loadStrokesJson()` for persistence

### PdfDrawingView.kt (PDF Annotation)
Everything from DrawingCanvas plus:
- **PDF rendering** via Android `PdfRenderer` API (5.0+)
- **Async page rendering** on background `Executors.newSingleThreadExecutor`
- **LRU page cache** (max 6 pages) to manage memory
- **Coordinate transformation system**:
  - Strokes stored in **logical document coordinates** (zoom-invariant)
  - Screen rendering: `screen_x = logical_x * scale + translateX`
  - Enables safe persistence across zoom/rotation changes
- **Scroll & zoom**: `OverScroller` + `VelocityTracker` for fling, `ScaleGestureDetector` for pinch (0.85x–4x)
- Custom scrollbar with drag interaction
- Focal point preservation during zoom
- Page navigation: `getCurrentPage()` from scrollY, `scrollToPage(n)`
- **Events emitted to JS**: `pdfCanvasPageChanged`, `pdfCanvasLoadComplete`, `canvasUndoRedoState`, `canvasEraserLift`
- **Annotation storage**: strokes are stored as a flat array in logical document coordinates — there is no explicit `pageNumber` field per stroke. Page association is implicit via Y-coordinate position in the continuous vertical document. This means operations like "export annotations for pages 3–5" require geometric calculation rather than a simple filter, and loading a PDF always processes all strokes regardless of which page is being viewed.

### BezierSmoother.kt
Quadratic Bezier curves with midpoint anchors:
```
For each consecutive triplet of points (prev, curr, next):
  midpoint = (curr + next) / 2
  quadTo(curr, midpoint)
Final lineTo(last point)
```
Low computational cost, smooth output from raw touch points, supports incremental updates for in-progress strokes.

### ColorGradientView.kt
Native HSV color picker surface. Renders a saturation-brightness gradient square and hue bar. Emits high-frequency touch events — the JS wrapper (`ColorGradientView.tsx`) coalesces updates using a `latestRef` + busy flag to prevent React update flooding.

---

## React Native Bridge Layer

### View Managers (Kotlin → RN Component)
- **CanvasViewManager**: exposes `DrawingCanvas` as `<CanvasView>`, props for tool/color/thickness/eraserMode
- **PdfCanvasViewManager**: exposes `PdfDrawingView` as `<PdfCanvasView>`, adds `pdfUri` prop
- **ColorGradientViewManager**: exposes `ColorGradientView`, props for hue/saturation/brightness

### Native Modules (imperative commands from JS)
- **CanvasModule**: `undo()`, `redo()`, `clear()`, `getStrokes()`, `loadStrokes()`
- **PdfCanvasModule**: same as CanvasModule plus `scrollToPage()`, `getPageCount()`
- **CanvasPackage**: registers all views and modules with the React Native runtime

### JS Wrappers (`src/native/`)
- `CanvasView.tsx`: wraps native view, subscribes to `DeviceEventEmitter` for undo/redo state and eraser lift events
- `CanvasModule.ts`: typed JS interface to native module methods
- `PdfCanvasView.tsx`, `PdfCanvasModule.ts`: same pattern for PDF canvas
- `ColorGradientView.tsx`: wraps native view with event coalescing

---

## UI Components

### Toolbar (`src/components/Toolbar.tsx`, ~340 lines)
- Tool buttons: Pen, Eraser (with pixel/stroke mode picker), Hand/Select (PDF only)
- Pop-ups: color button + thickness slider for pen; mode selector + thickness slider for eraser
- Undo/Redo buttons with disabled state tracking
- Page indicator for PDF mode

### Sidebar (`src/components/Sidebar.tsx`, ~380 lines)
- Animated left-side panel (0→240px width)
- Built-in categories: All, Favorites, PDFs, Notes
- Custom category list with add/delete
- **Settings modal** (gear icon):
  - Appearance: dark mode toggle
  - Drawing: pen/eraser thickness defaults, auto-switch toggle
  - Action Mapping: S-Pen single/double press action pickers
  - Preset Colors: editable 10-swatch grid
  - Data: clear all notes

### ColorPickerPanel (`src/components/ColorPickerPanel.tsx`, ~135 lines)
- Native HSV gradient square + hue bar via `ColorGradientView`
- 5x2 preset swatch grid (tap to select, long-press to save current color)
- Current color hex preview
- HSV ↔ hex conversion utilities

### ThicknessSlider (`src/components/ThicknessSlider.tsx`, ~120 lines)
- PanResponder-based drag slider with progress fill
- Draggable thumb + visual preview dot showing actual thickness
- Numeric value label

### ThumbnailStrip (`src/components/ThumbnailStrip.tsx`, ~140 lines)
- Horizontal FlatList of page thumbnails (72x96px)
- Async thumbnail generation via `react-native-pdf-thumbnail`
- LRU caching to `DocumentDirectoryPath/thumbnails/`
- Blue border highlight on current page, auto-scroll to active page

---

## Theming

### theme.ts
Two palettes (light/dark) with 12 semantic tokens:
- `bg`, `surface`, `surfaceAlt`, `border`, `text`, `textSub`, `textHint`, `accent`, `destructive`, `destructiveBg`, `overlay`
- Light: beige backgrounds (#F5F5F0), dark text (#1A1A1A)
- Dark: dark backgrounds (#1A1A1A), light text (#FFFFFF)
- `useTheme()` hook returns active palette based on `isDarkMode`

### Special Cases
- **Canvas drawing surface**: always white (not themed) — consistent drawing experience
- **PDF viewer background**: always dark (#1A1A1A) — intentional UX for document reading
- **NavigationContainer**: receives `DarkTheme`/`DefaultTheme` from `@react-navigation/native`

---

## S-Pen Hardware Integration

### Event Capture (MainActivity.kt)
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
  if (keyCode == 257 || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
    emitSpenEvent("spenButtonPress")
    return true
  }
}
```
- `KEYCODE_STYLUS_BUTTON_PRIMARY` (257) for Samsung S-Pen
- `KEYCODE_BUTTON_B` (6) as fallback for other styluses

### JS Handling (App.tsx)
`DeviceEventEmitter` listener maps the hardware event to the configured action from `useSettingsStore`:
- `'togglePenEraser'`: flip between pen and eraser
- `'pen'` / `'eraser'`: switch to that tool
- `'undo'`: stub at App level (requires canvas ref — not fully implemented)
- `'none'`: no action

The settings store exposes both `penButtonAction` (single-press) and `penButtonDoubleAction` (double-press) as configurable. However, the actual implementation only handles single-press: `MainActivity.kt` emits a single `spenButtonPress` event on every `onKeyDown` with no debouncing or double-tap timing logic, and `App.tsx` only reads `penButtonAction`. The `penButtonDoubleAction` setting exists in the store and UI but is never wired to any event handling — double-press detection is not implemented.

---

## Data Flow Examples

### Creating & Editing a Blank Note
1. HomeScreen → `addNote()` → Zustand store
2. Navigate to `NoteEditorScreen` with `{ note }` param
3. `CanvasView` receives tool/color/thickness props from `useToolStore`
4. User draws → native Kotlin handles all rendering
5. `beforeRemove` listener → `CanvasModule.getStrokes()` → write JSON to `drawings/{noteId}.json` → `updateNote({ drawingUri })`
6. Next open: `onLayout` → `RNFS.readFile(drawingUri)` → `CanvasModule.loadStrokes()`

### Importing & Annotating a PDF
1. HomeScreen → DocumentPicker → copy PDF to `pdfs/{timestamp}_{name}.pdf`
2. Generate cover thumbnail (page 1) → save to `thumbnails/`
3. Create Note `{ type: 'pdf', pdfUri, thumbnailUri, totalPages }`
4. Navigate to `PdfViewerScreen` → `PdfCanvasView` opens PDF via `PdfRenderer`
5. Native emits `pdfCanvasLoadComplete` → JS updates `totalPages`, jumps to `lastPage`
6. Draw in `pointerEvents='auto'` mode, scroll in `pointerEvents='none'` mode
7. `beforeRemove` → save `lastPage` + serialize strokes

---

## Build Configuration

### Dependencies (package.json)
| Category | Package | Version | Role |
|----------|---------|---------|------|
| Core | react | 18.2.0 | UI framework |
| Core | react-native | 0.73.0 | Android runtime |
| Navigation | @react-navigation/native-stack | 6.x | Screen routing |
| State | zustand | 5.0.11 | State management |
| Storage | @react-native-async-storage/async-storage | 1.23.1 | Persisted stores |
| Files | react-native-fs | 2.20.0 | File system access |
| Files | react-native-blob-util | 0.19.11 | File operations (pinned) |
| PDF | react-native-pdf | 6.7.7 | Possibly vestigial (see note below) |
| PDF | react-native-pdf-thumbnail | 1.3.1 | Page thumbnails |
| Picker | react-native-document-picker | 8.2.2 | PDF import |
| Animation | react-native-reanimated | 3.6.0 | Smooth animations |
| Gestures | react-native-gesture-handler | 2.14.0 | Gesture recognition |
| Types | typescript | 5.9.3 | Type checking |

### Android Build (Gradle)
- **AGP**: 8.1.1 (do NOT upgrade — breaks RN 0.73)
- **Gradle**: 8.3
- **Kotlin**: 1.8.0
- **compileSdk / targetSdk**: 34
- **minSdk**: 24 (Android 7.0)
- **NDK**: 25.1.8937393
- **Hermes**: enabled (JS engine)
- **Architecture**: arm64-v8a only
- **New Architecture**: disabled
- **JDK requirement**: JDK 17 (JDK 21+ breaks Gradle 8.3)

### Pinned Dependencies (resolutionStrategy)
- `androidx.core:core` → 1.13.1
- `androidx.transition:transition` → 1.5.0

These must stay below the compileSdk 34 threshold.

### Note on `react-native-pdf`
The `react-native-pdf` package is listed as a dependency, and CLAUDE.md references it for PDF viewing via base64 URIs. However, no source file in `src/` imports it — PDF rendering is handled entirely by the custom `PdfDrawingView.kt` using Android's native `PdfRenderer` API. This suggests `react-native-pdf` may be a vestigial dependency from before the custom engine was built. Only `react-native-pdf-thumbnail` (a separate package) is actively used, for generating page thumbnails in `ThumbnailStrip.tsx` and `HomeScreen.tsx`.

---

## Notable Design Decisions & Patterns

1. **Hybrid architecture**: React Native for UI productivity, Kotlin for latency-critical drawing — best of both worlds
2. **No JS bridge for drawing**: touch events → native canvas → bitmap, zero round-trip to JS
3. **Logical coordinate system** (PDF): strokes stored zoom-invariant, transformed at render time
4. **Event coalescing** (ColorGradientView): prevents React update flooding from high-frequency native touch events
5. **Lazy PDF rendering**: async background thread + LRU cache (6 pages) balances memory vs. responsiveness
6. **saveLayer isolation**: pixel eraser uses `PorterDuff.CLEAR` which requires layer isolation from parent background
7. **Software rendering forced**: `LAYER_TYPE_SOFTWARE` because `PorterDuff.CLEAR` doesn't work with GPU acceleration
8. **Undo via full replay**: undo rebuilds the bitmap from scratch (all committed strokes minus undone), ensuring correctness at the cost of performance on long stroke histories
9. **Note ID = timestamp**: `Date.now().toString()` — simple, unique enough for single-user, no UUID dependency
10. **Navigation params**: only `{ note: Note }` passed between screens — no prop bloat
11. **Auto-save on exit**: `beforeRemove` navigation listener triggers serialization — no explicit save button. Note: this only fires on normal navigation; if the app is killed by the OS, force-closed, or crashes, unsaved work since the last navigation event is lost. There is no periodic background save or dirty-state recovery mechanism.

---

## Known Limitations & Gaps

1. **No test coverage**: no unit, integration, or e2e tests exist
2. **Undo action stub**: S-Pen `'undo'` action in `App.tsx` is a no-op — would need canvas ref forwarding to implement
3. **Pressure data unused**: touch pressure is captured and serialized but not used in stroke rendering (thickness is constant)
4. **Undo performance**: full bitmap replay on each undo could degrade with very long stroke histories
5. **Single architecture**: only `arm64-v8a` — no x86 emulator support without rebuild
6. **No iOS support**: entirely Android-focused (Kotlin native modules, S-Pen integration)
7. **No cloud sync**: fully offline, local storage only
8. **Debug APK size**: ~164MB (too large for GitHub, excluded via .gitignore)
9. **useEditorStore**: defined but empty — reserved for future editor state (zoom, current page, etc.)
10. **Auto-save fragility**: save is only triggered by `beforeRemove` navigation listener — app kill, force-close, or crash loses all unsaved strokes since last navigation
11. **PDF annotations lack page tagging**: strokes stored as a flat array with no `pageNumber` field; page association is inferred from Y-coordinate position, which complicates per-page export and means all strokes are loaded regardless of current page
12. **`react-native-pdf` possibly unused**: no source file imports it; PDF rendering uses the custom Kotlin `PdfRenderer` engine instead — likely vestigial dead weight in the 164MB APK
13. **S-Pen double-press not implemented**: `penButtonDoubleAction` exists in the settings store and UI, but `MainActivity.kt` has no debounce/timing logic to distinguish single from double press, and `App.tsx` only reads `penButtonAction` — double-press config is dead UI
