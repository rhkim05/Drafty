# Architecture

> Linked from [CLAUDE.md](../CLAUDE.md)

## Navigation (`src/navigation/index.tsx`)

Uses `@react-navigation/native-stack`. All three routes are wired and active: `Home`, `PdfViewer: { note: Note }`, `NoteEditor: { note: Note }`. `App.tsx` just mounts `<Navigation />`.

## Drawing Engine

There are **two separate native drawing views** — both follow the same patterns but serve different purposes:

- **`DrawingCanvas.kt`** — used in `NoteEditorScreen` (blank notes), exposed as `"CanvasView"`.
- **`PdfDrawingView.kt`** — used in `PdfViewerScreen`, handles PDF rendering + drawing overlay, exposed as `"PdfCanvasView"`. Adds `scrollToPage` command.

Both views share these implementation details:

- Off-screen `Bitmap` caches committed strokes; only the active in-progress stroke is replayed each `onDraw`.
- Eraser uses `PorterDuff.Mode.CLEAR` — requires `LAYER_TYPE_SOFTWARE` (hardware acceleration breaks it). In `DrawingCanvas`, the active eraser stroke is drawn inside `canvas.saveLayer()` so CLEAR only affects the layer and transparent holes reveal the white view background rather than punching through to the dark parent in dark mode. `PdfDrawingView` uses the same `saveLayer` pattern around all annotation strokes.
- Undo replays all committed strokes onto a fresh bitmap; redo appends the restored stroke.
- After each stroke commit or undo/redo, Kotlin emits a `canvasUndoRedoState` device event `{ canUndo, canRedo }`. The respective native view wrapper in `src/native/` subscribes via `DeviceEventEmitter` and syncs `useToolStore`.
- On eraser `ACTION_UP`, Kotlin emits `canvasEraserLift` (no payload). `CanvasView.tsx` / `PdfCanvasView.tsx` listen and call `setTool('pen')` if `useSettingsStore.autoSwitchToPen` is true.

## Bridge (`reactbridge/` <-> `src/native/`)

- **`CanvasViewManager.kt`** / **`PdfCanvasViewManager.kt`** — expose `DrawingCanvas`/`PdfDrawingView` as native views. Props: `tool`, `penColor`, `penThickness`, `eraserThickness` (PdfCanvasView also accepts `pdfUri`).
- **`CanvasModule.kt`** / **`PdfCanvasModule.kt`** — imperative commands via view tag from `findNodeHandle`: `undo`, `redo`, `clear`, `getStrokes` (async, returns JSON string), `loadStrokes`. `PdfCanvasModule` also has `scrollToPage(viewTag, page)`.
- **`ColorGradientViewManager.kt`** — exposes `ColorGradientView` as native view `"ColorGradientView"`. Props: `hue`, `sat`, `brightness`. Emits `colorPickerSVChange` and `colorPickerHueChange` device events.
- **`src/native/CanvasView.tsx`** / **`src/native/PdfCanvasView.tsx`** — `requireNativeComponent` wrappers with `forwardRef`; own the `DeviceEventEmitter` subscription for undo/redo state.
- **`src/native/ColorGradientView.tsx`** — wraps the native gradient view; coalesces rapid `colorPickerSVChange`/`colorPickerHueChange` events to avoid flooding React with updates.

## Stroke Persistence

Both `NoteEditorScreen` and `PdfViewerScreen` use the same pattern:

- **Save** (`beforeRemove` navigation event): `getStrokes(tag)` -> write JSON to `DocumentDirectoryPath/drawings/<noteId>.json` -> `updateNote` stores the path in `note.drawingUri`. Note: `beforeRemove` only fires on in-app navigation — app kill, force-close, or OS background termination will lose unsaved work. There is no periodic auto-save or `AppState` listener.
- **Load** (canvas layout callback): if `note.drawingUri` exists, read the file and call `loadStrokes(tag, json)`.
- **PDF annotations**: strokes are stored as a flat JSON array with no `pageNumber` field. Page association is implicit via Y-coordinate in logical document coordinates. This means all strokes are loaded/saved regardless of current page.

## PDF Flow

1. Import: `react-native-document-picker` -> copy to `DocumentDirectoryPath/pdfs/` via `react-native-fs` -> save `Note { type: 'pdf', pdfUri }` to store.
2. View: `PdfDrawingView.kt` opens the PDF directly via Android's `PdfRenderer` API. Pages are rendered asynchronously on a background thread with an LRU cache (max 6 pages). Note: `react-native-pdf` is listed in `package.json` but is not imported anywhere in `src/` — it may be vestigial from before the custom Kotlin engine was built. Only `react-native-pdf-thumbnail` is actively used (for thumbnail generation).
3. Canvas overlay is always mounted over the PDF. `pointerEvents` on its wrapper toggles between `'none'` (scroll/select mode) and `'auto'` (draw mode) based on `useToolStore.activeTool`.

## State Management

- **`useNotebookStore`** — persists `Note[]` + `Category[]` via zustand `persist` + AsyncStorage (key: `notebook-store`). `Note` includes optional `drawingUri` and `categoryId`. `Category` defined in `src/types/categoryTypes.ts`; built-in categories (`all`, `pdfs`, `notes`) are defined as constants and merged with user-created ones at render time.
- **`useToolStore`** — in-memory only. Holds `activeTool` (pen/eraser/select), `canUndo`, `canRedo`, `penColor`, `penThickness`, `eraserThickness`, `presetColors`. Updated by native view `DeviceEventEmitter` listeners.
- **`useSettingsStore`** — persists settings via AsyncStorage (key: `settings-store`). Contains: S-Pen button action mappings (`penButtonAction` / `penButtonDoubleAction`, `PenAction` type), `autoSwitchToPen: boolean` (auto-switch to pen after eraser lift, default `true`), `isDarkMode: boolean` (default `false`).

## Sidebar & S-Pen Button

- **`Sidebar.tsx`** — push-content (not overlay) flex child. Width animates 0 -> 240px via `Animated.Value`; the toggle button lives in `HomeScreen`'s header outside the sidebar. Contains the settings modal (APPEARANCE: dark mode toggle; DRAWING: pen/eraser thickness, auto-switch toggle; ACTION MAPPING: S-Pen button pickers; PRESET COLORS; DATA: clear-all).
- **S-Pen flow**: `MainActivity.kt` overrides `onKeyDown` to catch `KEYCODE_STYLUS_BUTTON_PRIMARY` (257) and emit `spenButtonPress` via `DeviceEventEmitter`. `App.tsx` subscribes and reads `penButtonAction` from `useSettingsStore` to execute the mapped action against `useToolStore`. Known gaps: (1) `undo` action is a no-op at the `App.tsx` level (requires an active canvas ref inside the editor screen); (2) `penButtonDoubleAction` exists in the store and settings UI but is never wired — Kotlin emits a single event on every `onKeyDown` with no debounce/double-tap timing logic.

## Theming (Dark Mode)

- **`src/styles/theme.ts`** — defines `lightTheme` and `darkTheme` color token objects (12 tokens: `bg`, `surface`, `surfaceAlt`, `border`, `text`, `textSub`, `textHint`, `accent`, `destructive`, `destructiveBg`, `overlay`). Exports `useTheme()` hook that reads `isDarkMode` from `useSettingsStore` and returns the active palette.
- All themed UI components call `const theme = useTheme()` and apply colors via inline style overrides on top of layout-only `StyleSheet` entries (e.g. `style={[styles.container, { backgroundColor: theme.bg }]}`).
- **Canvas drawing surface is intentionally always white** — `DrawingCanvas.kt` and `PdfDrawingView.kt` are not themed. `PdfViewerScreen` and `ThumbnailStrip` are also always dark (intentional PDF-reading UX).
- `NavigationContainer` receives `theme={isDarkMode ? DarkTheme : DefaultTheme}` from `@react-navigation/native`.
