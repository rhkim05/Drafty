# Issues — Drafty

> Issue log for tracking problems encountered during development.
> Links: [TODO.md](./TODO.md) | [plan.md](./plan.md)

---

## ISS-001: `mipmap/ic_launcher` resource not found (FIXED)

**Phase**: 0 — Scaffold
**Module**: `:app`
**Symptom**: AAPT error — `AndroidManifest.xml` referenced `@mipmap/ic_launcher` but no launcher icon PNGs existed in `res/mipmap-*` directories.
**Fix**: Removed `android:icon` attribute from manifest. A proper adaptive icon will be added in Phase 6 (branding).

---

## ISS-002: `DraftyIcons.kt` unresolved references (FIXED)

**Phase**: 0 — Scaffold
**Module**: `:core:ui`
**Symptom**: 18 unresolved references — file used `Icons.Extended.*` imports that don't exist in the Material Icons API (`Icons.Extended` is not a real accessor).
**Fix**: Rewrote to use correct accessors: `Icons.Filled.*`, `Icons.Outlined.*`, and `Icons.AutoMirrored.Filled.*` for directional icons (Undo/Redo).

---

## ISS-003: `core:domain` use cases — unresolved `javax.inject` and wrong method names (FIXED)

**Phase**: 0 — Scaffold
**Module**: `:core:domain`
**Symptom**: All 13 use case files (a) imported `javax.inject.Inject` which is not a dependency of the pure Kotlin module, and (b) called non-existent repository methods (e.g., `repository.create()` instead of `repository.createNotebook()`, `repository.getAll()` instead of `repository.getNotebooks()`).
**Fix**: Removed `@Inject` annotations (domain is a pure Kotlin module — DI wiring happens in `:core:data`'s Hilt module). Fixed all method calls to match the actual repository interface signatures.

---

## ISS-004: `core:data` — wrong domain model import paths and method signature mismatches (FIXED)

**Phase**: 0 — Scaffold
**Module**: `:core:data`
**Symptom**: All mappers, repositories, and `StrokeFileManager` imported domain models from `com.drafty.core.model.*` (non-existent) instead of `com.drafty.core.domain.model.*`. Additionally, repository implementations had method names that didn't match their interfaces: `getAllNotebooks()` vs `getNotebooks()`, `createPage()` vs `addPage()`, `updatePageSortOrders()` vs `reorderPages()`, and `PdfRepositoryImpl` had completely wrong method signatures (`exportPageToPdf`, `exportNotebookToPdf`, `importPdfAsPages`) vs the interface (`importPdf`, `getPageCount`, `exportAnnotatedPdf`, `deletePdf`).
**Fix**: Corrected all import paths to `com.drafty.core.domain.model.*`. Rewrote all four repository implementations to match their interface contracts exactly.

---

## ISS-005: `core:ink-engine` — wrong imports and missing Compose dependency (FIXED)

**Phase**: 1 — Ink Engine
**Module**: `:core:ink-engine`
**Symptom**: `ViewportManager.kt` used `@Stable` annotation from Compose runtime, but `core:ink-engine` has no Compose dependency. `HitTester.kt` and `RTree.kt` imported `com.drafty.core.ink.stroke.Stroke` (non-existent) instead of `com.drafty.core.domain.model.Stroke`.
**Fix**: Removed `@Stable` annotation from `ViewportState` (ink-engine is a pure Android/Canvas module, not Compose). Fixed `Stroke` imports to use `com.drafty.core.domain.model.Stroke`.

---

## ISS-006: `StrokeRepositoryImpl` return type mismatch for `saveStrokes` and `deleteStrokes` (FIXED)

**Phase**: 2 — Canvas & Page Management
**Module**: `:core:data`
**Symptom**: Compile error: `Return type of 'saveStrokes' is not a subtype of the return type of the overridden member`. `StrokeFileManager.saveStrokes` inferred `Boolean` return from `File.renameTo()`, and `deleteStrokes` from `File.delete()`, causing the repository impl to return `Boolean` instead of `Unit`.
**Fix**: Added explicit `: Unit` return types on both `saveStrokes` and `deleteStrokes` in `StrokeFileManager`, with `Unit` as the last expression in the `withContext` blocks.
**Related**: [TODO.md — 2E](./TODO.md)

---

## ISS-007: Eraser misses strokes and feels laggy (FIXED)

**Phase**: 2 — Canvas & Page Management (2H device testing)
**Module**: `:core:ink-engine`
**Symptom**: When drawing many strokes and erasing across them, some strokes are missed and erasing feels laggy. User report: "If user draws many lines and try to erase all, some strokes are not erased, and the erasing feels bit lagging."
**Root cause**: (1) Eraser tolerance was `currentThickness` (~4px pen width) — too small to reliably hit strokes. (2) `removeStrokes()` called `rebuildCompletedBitmap()` on every single eraser hit during a drag, re-rendering ALL strokes to the cache bitmap each time.
**Fix**: Rewrote eraser in `InkSurfaceView` to use `ERASER_TOLERANCE` (24f screen px, divided by zoom) for a generous hit radius. Batched all removals during a drag gesture (`eraserPendingRemovals` / `eraserRemovedIds`) and flush only once per move event, avoiding repeated full bitmap rebuilds.
**Related**: [TODO.md — 2H](./TODO.md)

---

## ISS-008: Lasso selection cannot move selected strokes (FIXED)

**Phase**: 2 — Canvas & Page Management (2H device testing)
**Module**: `:core:ink-engine`
**Symptom**: After selecting strokes with lasso, tapping to move them starts a new selection instead. User report: "I can select object, but cannot move it now. If I try to move it, the tool just select again."
**Root cause**: `handleLassoEvent` always cleared the previous selection on ACTION_DOWN and started a new lasso path, with no detection of "touch inside existing selection bounding box = drag mode".
**Fix**: Rewrote `handleLassoEvent` with two modes: (1) if `selectedStrokeIds` is non-empty and ACTION_DOWN lands inside the selection bbox (with 16px padding), enter drag mode — track delta and call `moveSelectedStrokesInternal()` each frame; (2) otherwise start a new lasso selection. Added `onStrokesMoved` callback for undo recording with total delta, wired to `MoveStrokesCommand` in `CanvasViewModel`.
**Related**: [TODO.md — 2G, 2H](./TODO.md)

---

## ISS-009: Fast drawing produces dotted/disconnected strokes (FIXED)

**Phase**: 2 — Canvas & Page Management (2H device testing)
**Module**: `:core:ink-engine`
**Symptom**: Drawing fast produces visible dots instead of a continuous line. On stylus lift, some dots connect (completed stroke re-render) but some gaps remain. Slow drawing looks fine.
**Root cause**: (1) `StrokeRenderer.renderActiveStroke` drew a filled circle at each raw input point with no interpolation — fast drawing = sparse points = gaps between circles. (2) `StrokePath.sampleSegments` used a fixed 8 samples per Bézier segment regardless of segment length — long segments from fast drawing had insufficient density for the circle-strip to appear continuous.
**Fix**: (1) Active stroke now interpolates circles between consecutive input points, stepping at half the average radius so circles always overlap. (2) Completed stroke sampling is now adaptive — computes chord length of each Bézier segment and increases sample count so each step is at most 40% of the average stroke width, with a floor of 8 samples.
**Related**: [TODO.md — 2H](./TODO.md)

---

## ISS-010: Lasso selection UX — partial strokes not selected, rectangle highlight, drag zone (FIXED)

**Phase**: 2 — Canvas & Page Management (2H device testing)
**Module**: `:core:ink-engine`
**Symptom**: (1) Lasso only selected strokes whose bounding box center was inside the polygon — if part of a stroke crossed through the lasso but the center was outside, it was missed. (2) Selection was shown as a blue rectangle around selected strokes, which felt unnatural. (3) Dragging only worked inside the rectangle bbox, not inside the lasso shape the user drew.
**Fix**: (1) `HitTester.intersectsPath` now checks if ANY point of a stroke is inside the polygon (not just bbox center). (2) Removed the rectangle highlight; the lasso polygon itself stays visible with a subtle fill after selection. (3) Drag detection uses `isPointInPolygon` on the lasso shape, so the user can tap anywhere inside their drawn lasso to move. The lasso polygon shifts along with the strokes during drag.
**Related**: [TODO.md — 2G, 2H](./TODO.md)

---

## ISS-012: NotebookDetailViewModel combine feedback loop — crash on add section (FIXED)

**Phase**: 3 — Home Screen & Canvas Navigation
**Module**: `:feature:notebooks`
**Symptom**: App crashed when adding a section on the Notebook Detail screen. Overall app instability and lag. User report: "app cracked when adding section" and "the app suddenly stops... the app is overally unstable and laggy now."
**Root cause**: `NotebookDetailViewModel` line ~72 modifies `_selectedSectionId.value` inside a `combine` transform that includes `_selectedSectionId` as one of its source flows — creating an infinite feedback loop that causes stack overflow.
**Fix**: Removed NotebookDetailScreen from the navigation graph entirely. The app now uses a flat structure (home → canvas) so this code path is never reached. The buggy file still exists for potential future use but is not wired in `DraftyNavGraph`.
**Related**: [TODO.md — 3G](./TODO.md)

---

## ISS-013: No back button on canvas screen (FIXED)

**Phase**: 3 — Home Screen & Canvas Navigation
**Module**: `:feature:canvas`
**Symptom**: User had to rely on the emulator/system back button to leave the canvas. User report: "there's no back button on screen."
**Fix**: Added a floating `CircleShape` `Surface` back button at top-left of `CanvasScreen` (with `systemBarsPadding`). Calls `viewModel.forceSave()` then `onBack()`.
**Related**: [TODO.md — 3E](./TODO.md)

---

## ISS-014: Canvas background indistinguishable from page (FIXED)

**Phase**: 3 — Home Screen & Canvas Navigation
**Module**: `:core:ink-engine`
**Symptom**: Both the canvas background and the page were white, making it impossible to see page boundaries. User report: "both canvas and the background is in white color so I cannot distinguish them."
**Fix**: Changed `canvasBackgroundColor` in `InkSurfaceView` from `Color.WHITE` to `Color.parseColor("#E0E0E0")` (light gray).
**Related**: [TODO.md — 3G](./TODO.md)

---

## ISS-015: Home grid showed 2 columns instead of 3 (FIXED)

**Phase**: 3 — Home Screen & Canvas Navigation
**Module**: `:feature:notebooks`
**Symptom**: Home screen showed 2 notes per row, which felt sparse on a tablet.
**Fix**: Changed `NotebookGrid` from 2 to 3 columns in `LazyVerticalGrid`.
**Related**: [TODO.md — 3C](./TODO.md)

---

## ISS-016: Delete action not discoverable (OPEN)

**Phase**: 3 — Home Screen & Canvas Navigation
**Module**: `:feature:notebooks`
**Symptom**: User couldn't figure out how to delete a note. User report: "Not sure how to delete." Delete is only accessible via long-press context menu on `NotebookCard`.
**Expected**: Consider adding a more discoverable delete affordance — swipe-to-delete, visible icon, or edit mode with checkboxes.
**Priority**: Medium — usability issue.
**Related**: [TODO.md — 3C](./TODO.md)

---

## ISS-017: Pen size should be constant in canvas space regardless of zoom (FIXED)

**Phase**: 2 — Canvas & Page Management (reported during Phase 3 device testing)
**Module**: `:core:ink-engine`
**Symptom**: Pen produced different canvas-space thickness depending on zoom level. At 2x zoom the stroke was half as thick on the canvas; at 0.5x it was double. User report: "pen size changes when zoom in/out. If you set a pen size once, then you can draw on canvas with that size, regardless of zoom in/out."
**Root cause**: `InkSurfaceView` line 239 divided `currentThickness` by zoom at stroke creation: `strokeBuilder.begin(..., currentThickness / viewportManager.zoom, ...)`. This made the canvas-space thickness vary with zoom — a stroke drawn at 2x zoom had half the canvas width of one drawn at 1x. Standard note-taking app behavior (GoodNotes, Noteshelf) uses a fixed canvas-space pen size — the pen always lays down the same mark regardless of zoom, like a real pen on paper.
**Fix**: Removed all `/ viewportManager.zoom` from thickness at stroke creation and active stroke rendering. `currentThickness` is now used directly as the canvas-space thickness. The viewport transform naturally scales it on screen (zoomed in → looks bigger, zoomed out → looks smaller), which is the expected behavior.
**Related**: ISS-018, [TODO.md — 2C](./TODO.md)

---

## ISS-018: Pen looks blurry when zoomed in (FIXED)

**Phase**: 2 — Canvas & Page Management (reported during Phase 3 device testing)
**Module**: `:core:ink-engine`
**Symptom**: When zooming in to the maximum, strokes look very blurry/pixelated. User report: "If I zoom in until end, the pen looks very blurry."
**Root cause**: The completed-strokes bitmap (`completedBitmap`) is created at screen resolution (`Bitmap.createBitmap(width, height, ...)`). It is then drawn inside the viewport transform (after `canvas.scale(zoom, zoom)`) at line 769-771. At 3x zoom, each bitmap pixel is stretched to 3×3 screen pixels, causing visible blur. The bitmap is never re-rasterized at higher resolution for the current zoom level.
**Fix**: Removed the intermediate bitmap cache entirely. Completed strokes are now rendered directly to the hardware canvas each frame via `strokeRenderer.renderStrokes(canvas, completedStrokes)`. Since rendering happens inside the viewport transform (`canvas.scale(zoom)`), strokes are drawn at full resolution at every zoom level — no bitmap stretching. This also fixed the perceived pen-size issue in ISS-017.
**Related**: ISS-017, [TODO.md — 2B](./TODO.md)

---

## ISS-011: Highlighter needs more transparency and layered blending (OPEN)

**Phase**: 2 — Canvas & Page Management
**Module**: `:core:ink-engine`
**Symptom**: Highlighter is not transparent enough, and overlapping strokes don't blend naturally. A real highlighter should accumulate opacity when drawing over the same area multiple times (덧칠), but currently strokes just stack with a fixed alpha.
**Expected**: Highlighter should use higher transparency (lower alpha) and support layered blending so overlapping passes gradually darken, mimicking a real highlighter marker.
**Priority**: Low — not urgent, cosmetic improvement.
**Related**: StrokeRenderer highlighterPaint, CanvasUiState default highlighterColor (`0x80FFFF00`)
