# Issues Log

All issues, errors, and problems encountered while working on this project. Each entry documents what happened, why, how it was fixed (or remains open), and what files/architecture were affected.

---

## ISSUE-001: Gradle Build Not Found at Project Root

**Status:** Resolved
**Severity:** Blocker (build)
**Date discovered:** 2026-03-31

**What happened:**
Running `gradle build` or opening the project root in Android Studio produced:
```
Directory '/Users/dev/Code/Drafty' does not contain a Gradle build.
```

**Why it happened:**
Drafty is a React Native project. The Gradle build files (`build.gradle`, `settings.gradle`) live inside the `android/` subdirectory, not at the project root. The root is a Node.js/TypeScript project managed by `package.json` and Metro bundler.

**How it was fixed:**
Run Gradle commands from the `android/` directory:
```bash
cd android && ./gradlew build
```
Or open `android/` as the project root in Android Studio. For React Native development, use `npx react-native run-android` from the project root — it delegates to `android/gradlew` automatically.

**Files/architecture affected:**
- `android/build.gradle` — root Gradle build
- `android/settings.gradle` — Gradle settings
- `android/app/build.gradle` — app module build

---

## ISSUE-002: `react-native-pdf` Dependency Possibly Vestigial

**Status:** Open (needs confirmation)
**Severity:** Low (dead code / APK bloat)
**Date discovered:** 2026-03-31

**What happened:**
`react-native-pdf@6.7.7` is listed in `package.json` and referenced in the original CLAUDE.md as being used for PDF viewing via base64 URIs. However, no source file in `src/` imports `react-native-pdf` (only `react-native-pdf-thumbnail` is imported).

**Why it happened:**
PDF rendering was likely migrated from `react-native-pdf` (JS library) to a custom Kotlin implementation (`PdfDrawingView.kt`) using Android's native `PdfRenderer` API. The dependency was never removed from `package.json` after the migration.

**How to fix:**
1. Confirm `react-native-pdf` is truly unused by searching all files (including native Android auto-linking).
2. If confirmed unused, remove it: `npm uninstall react-native-pdf --legacy-peer-deps`
3. Rebuild and verify PDF viewing still works.

**Files/architecture affected:**
- `package.json` — dependency listing
- `android/` — native auto-linked modules (removing the dep will reduce APK size)
- `PdfDrawingView.kt` — the replacement implementation

---

## ISSUE-003: S-Pen `undo` Action Is a Silent No-Op

**Status:** Open
**Severity:** Medium (UX bug — configurable option does nothing)
**Date discovered:** 2026-03-31

**What happened:**
Users can configure `'undo'` as their S-Pen button action in the Settings modal (Sidebar.tsx). When pressed, nothing happens — no undo, no error, no feedback.

**Why it happened:**
S-Pen hardware events are caught globally in `MainActivity.kt` and emitted as `spenButtonPress` to `App.tsx`. But `App.tsx` doesn't have a reference to the active canvas (`DrawingCanvas` or `PdfDrawingView`), and `CanvasModule.undo()` requires a view tag from `findNodeHandle`. The handler in `App.tsx` simply comments: `// 'undo' and 'none' are no-ops here; undo requires the active canvas ref`.

**How to fix:**
Options:
1. **Forward via useEditorStore** (currently a stub): store the active canvas view tag in `useEditorStore` when a canvas mounts, read it in `App.tsx` to call `CanvasModule.undo()`.
2. **Forward via DeviceEventEmitter**: emit a custom `spenUndo` event from `App.tsx`, subscribe in `NoteEditorScreen`/`PdfViewerScreen` where the canvas ref is available.
3. **Remove from UI**: if not implementing soon, remove `'undo'` from the `PenAction` picker options to avoid confusing users.

**Files/architecture affected:**
- `App.tsx` — S-Pen event handler (line 16)
- `src/store/useEditorStore.ts` — empty stub, candidate for storing canvas ref
- `src/store/useSettingsStore.ts` — `PenAction` type includes `'undo'`
- `src/components/Sidebar.tsx` — settings UI exposes `'undo'` as a picker option

---

## ISSUE-004: S-Pen Double-Press Action Not Implemented

**Status:** Open
**Severity:** Medium (dead UI — setting has no effect)
**Date discovered:** 2026-03-31

**What happened:**
`useSettingsStore` exposes `penButtonDoubleAction` and the Sidebar settings UI lets users configure a double-press action. But pressing the S-Pen button twice quickly just triggers the single-press action twice.

**Why it happened:**
`MainActivity.kt` emits a raw `spenButtonPress` event on every `onKeyDown` with no timing/debounce logic to distinguish single from double press. `App.tsx` only reads `penButtonAction` (single-press) — `penButtonDoubleAction` is never referenced outside the store and UI.

**How to fix:**
1. **Native debounce (preferred)**: In `MainActivity.kt`, implement a ~300ms delay after the first press. If a second press arrives within the window, emit `spenButtonDoublePress`; otherwise emit `spenButtonPress`.
2. **JS debounce (simpler)**: In `App.tsx`, buffer the first event and wait ~300ms before deciding single vs. double. Downside: adds perceptible delay to single-press actions.
3. **Remove from UI**: if not implementing, remove the double-press picker from Sidebar settings.

**Files/architecture affected:**
- `android/app/src/main/java/com/tabletnoteapp/MainActivity.kt` — event emission
- `App.tsx` — event listener
- `src/store/useSettingsStore.ts` — `penButtonDoubleAction` field
- `src/components/Sidebar.tsx` — double-press picker UI

---

## ISSUE-005: Auto-Save Only Triggers on Navigation (Data Loss Risk)

**Status:** Open
**Severity:** High (potential data loss)
**Date discovered:** 2026-03-31

**What happened:**
Stroke data is only saved when the user navigates away from the editor screen. If the app is killed by the OS (low memory), the user force-closes it, the tablet battery dies, or the app crashes, all unsaved work since entering the editor is permanently lost.

**Why it happened:**
Both `NoteEditorScreen.tsx` and `PdfViewerScreen.tsx` rely solely on React Navigation's `beforeRemove` listener to trigger `getStrokes()` → write to disk. There is no periodic background save, no `AppState` listener for backgrounding, and no dirty-state recovery mechanism.

**How to fix:**
1. **AppState listener**: subscribe to `AppState.addEventListener('change')` — when state becomes `'background'` or `'inactive'`, trigger save immediately.
2. **Periodic auto-save**: set an interval (e.g., every 30–60 seconds) that calls `getStrokes()` and writes to disk if the canvas is dirty.
3. **Both**: AppState for immediate backgrounding + interval as a safety net.

**Files/architecture affected:**
- `src/screens/NoteEditorScreen.tsx` — save logic (line ~49)
- `src/screens/PdfViewerScreen.tsx` — save logic (line ~101)
- `src/native/CanvasModule.ts` / `PdfCanvasModule.ts` — `getStrokes()` calls

---

## ISSUE-006: PDF Annotations Stored as Flat Array Without Page Tagging

**Status:** Open (architectural limitation)
**Severity:** Medium (performance and export implications)
**Date discovered:** 2026-03-31

**What happened:**
All PDF annotation strokes are serialized as a single flat JSON array per note with no `pageNumber` field. Page association is implicit — a stroke "belongs" to a page based on its Y-coordinate in the continuous vertical document coordinate system.

**Why it happened:**
`PdfDrawingView.kt` treats the entire multi-page PDF as one continuous scrollable canvas in logical coordinates. Strokes are stored in this logical space. There was no need for page tagging when strokes are always rendered in the same coordinate system.

**Implications:**
1. **Performance**: opening any page loads and processes ALL strokes for all pages.
2. **Export**: "export annotations for pages 3–5" requires geometric calculation to filter strokes by Y-offset ranges, not a simple field filter.
3. **Ambiguity**: strokes near page boundaries could be geometrically ambiguous about which page they belong to.

**How to fix:**
Add an optional `page: number` field to the stroke serialization format. When committing a stroke in `PdfDrawingView.kt`, compute which page it falls on using `pageYOffsets` and tag it. For backwards compatibility, treat strokes without a `page` field as legacy (compute page from Y-coordinate on load).

**Files/architecture affected:**
- `android/app/src/main/java/com/tabletnoteapp/canvas/models/Stroke.kt` — add page field
- `android/app/src/main/java/com/tabletnoteapp/canvas/PdfDrawingView.kt` — tag strokes on commit, filter on render
- Stroke JSON format — schema change (needs migration strategy)

---

## ISSUE-007: Undo Performance Degrades with Stroke Count

**Status:** Open (architectural limitation)
**Severity:** Medium (latency scales linearly with note complexity)
**Date discovered:** 2026-03-31

**What happened:**
Every undo operation clears the off-screen bitmap and replays ALL committed strokes from scratch. Combined with forced software rendering (`LAYER_TYPE_SOFTWARE`), this becomes progressively slower as notes grow.

**Why it happened:**
The undo implementation in `DrawingCanvas.kt` and `PdfDrawingView.kt` takes the simplest correct approach: rebuild the entire bitmap state. This avoids complex incremental state management. Software rendering is forced because `PorterDuff.Mode.CLEAR` (pixel eraser) doesn't work with hardware acceleration.

**When it becomes a problem:**
- Short sketches (< 50 strokes): imperceptible
- Medium notes (100–500 strokes): slight delay
- Long sessions (1000+ strokes, e.g., lecture notes): visible UI freeze on each undo

**How to fix:**
1. **Checkpoint snapshots**: periodically save the bitmap state (e.g., every 50 strokes). On undo, replay from the nearest checkpoint instead of from scratch.
2. **Incremental undo for AddStroke**: instead of full replay, redraw only the area affected by the removed stroke (partial invalidation).
3. **Hybrid rendering**: only use `LAYER_TYPE_SOFTWARE` during active eraser strokes; switch to hardware acceleration for normal pen rendering.

**Files/architecture affected:**
- `android/app/src/main/java/com/tabletnoteapp/canvas/DrawingCanvas.kt` — undo/replay logic
- `android/app/src/main/java/com/tabletnoteapp/canvas/PdfDrawingView.kt` — same pattern

---

## ISSUE-008: Forced Software Rendering on Entire Canvas

**Status:** Open (architectural tradeoff)
**Severity:** Low-Medium (performance cost accepted for correctness)
**Date discovered:** 2026-03-31

**What happened:**
Both `DrawingCanvas.kt` (line 25) and `PdfDrawingView.kt` (line 173) call `setLayerType(LAYER_TYPE_SOFTWARE, null)`, forcing all drawing through the CPU renderer — not just eraser operations.

**Why it happened:**
The pixel eraser uses `PorterDuff.Mode.CLEAR` which requires software rendering. Since the canvas can switch between pen and eraser at any time, software rendering is forced globally rather than toggled per-stroke.

**Implications:**
All pen drawing, Bezier curve rendering, and bitmap operations run on CPU. On high-resolution tablet screens with complex drawings, this reduces frame rate and increases battery drain. This partially undermines the low-latency goal of the native Kotlin engine.

**How to fix (if pursued):**
Dynamically toggle layer type: use `LAYER_TYPE_HARDWARE` by default, switch to `LAYER_TYPE_SOFTWARE` only when the eraser tool is active, and switch back when returning to pen. Requires testing for visual artifacts during transitions.

**Files/architecture affected:**
- `android/app/src/main/java/com/tabletnoteapp/canvas/DrawingCanvas.kt` — line 25
- `android/app/src/main/java/com/tabletnoteapp/canvas/PdfDrawingView.kt` — line 173

---

## ISSUE-009: Gradle/AGP/JDK Version Lock Creates Fragile Build Matrix

**Status:** Open (maintenance burden)
**Severity:** Low (works now, risk increases over time)
**Date discovered:** 2026-03-31

**What happened:**
The build requires a specific combination of versions that cannot be independently upgraded:
- Gradle 8.3 (JDK 21+ breaks it)
- AGP 8.1.1 (upgrading breaks RN 0.73)
- JDK 17 (must be exactly 17)
- `androidx.core@1.13.1` and `androidx.transition@1.5.0` (pinned via `resolutionStrategy` to stay within compileSdk 34)
- Multiple React Native packages pinned to last RN 0.73-compatible versions

**Why it happened:**
React Native 0.73 has Kotlin warnings that become errors under Gradle 8.11+. RN 0.73 also lacks `BaseReactPackage`, which newer versions of `react-native-screens`, `react-native-safe-area-context`, etc. require.

**Implications:**
Security patches and library updates requiring newer SDKs will be blocked. Upgrading React Native to 0.74+ is the path forward but requires updating the entire dependency chain simultaneously.

**Files/architecture affected:**
- `android/build.gradle` — AGP version, `resolutionStrategy` pins
- `android/gradle/wrapper/gradle-wrapper.properties` — Gradle version
- `android/gradle.properties` — Kotlin version, arch flags
- `package.json` — pinned JS dependency versions
