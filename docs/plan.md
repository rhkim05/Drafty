# Plan: Unify DrawingCanvas and PdfDrawingView

> Status: **Not started**
> Created: 2026-03-31

## Motivation

`DrawingCanvas.kt` (blank notes) and `PdfDrawingView.kt` (PDF annotations) share ~150 lines of copy-pasted logic but are completely separate classes. The blank canvas needs to become scrollable and multi-page. Adding scroll/zoom/page infrastructure to `DrawingCanvas` would duplicate another ~200 lines that `PdfDrawingView` already has. Merging avoids this and fixes several open issues along the way.

## Current State

### Shared logic (copy-pasted today)
- Stroke-eraser state machine (~80 lines, nearly identical)
- Pen/eraser touch dispatch (~40 lines, differs only in coordinate conversion)
- Hit-test geometry: `strokeHitsPoint()`, `segmentDistSq()` (21 lines, identical)
- Paint setup: strokePaint, eraserPaint, cursor paints (identical)
- JSON serialization/deserialization (nearly identical)
- Undo/redo data structures and stack manipulation (same semantics, different rendering)
- Pressure handling and mouse event routing (identical)

### Key differences
| Aspect | DrawingCanvas | PdfDrawingView |
|--------|---------------|----------------|
| Coordinates | Screen pixels (1:1) | Logical document space with transform |
| Scrolling | None | Pan, fling, scrollbar |
| Zoom | None | Pinch zoom [0.85, 4.0] |
| Pages | Single page | Multi-page with offsets and gaps |
| Background | White rectangle | Async-rendered PDF page bitmaps (LRU cache, max 6) |
| Undo rendering | Full bitmap replay (O(n), gets slow) | Remove from list + invalidate (O(1)) |
| Bridge commands | undo, redo, clear, getStrokes, loadStrokes | Same + scrollToPage, getPageCount |
| Default tool | PEN | SELECT (scroll mode) |

## Architecture

One `UnifiedCanvasView` that composes two extracted helper classes:

```
UnifiedCanvasView (View)
├── BaseDrawingEngine    — stroke management, touch dispatch, undo/redo, serialization
└── PagedScrollEngine    — coordinate transforms, scroll/fling/zoom, page layout, scrollbar
```

- Blank notes = scrollable pages with white backgrounds, no PDF renderer
- PDF notes = same scrollable pages with PDF bitmaps as background
- The only branching point is `onDraw()`: draw white rectangles vs. draw cached PDF bitmaps

### Helper class: BaseDrawingEngine

Owns all drawing logic. Not a View — a plain Kotlin class that the View delegates to.

**State it owns:**
- `committedStrokes: MutableList<Stroke>`
- `undoStack`, `redoStack: ArrayDeque<UndoAction>`
- `activeStroke: Stroke?`
- `strokeEraserBuffer`, `strokeOriginalIndexMap`
- All Paint objects (stroke, eraser, cursor, stroke-eraser fill/border)

**Methods:**
- `handleDraw(event, tool, logicalX, logicalY, pressure, scale)` — pen/eraser/stroke-eraser dispatch
- `commitStroke(stroke)`
- `undo()` / `redo()` — manipulate stacks and stroke list, no rendering
- `eraseStrokesAtPoint(x, y, threshold)`
- `strokeHitsPoint()` / `segmentDistSq()`
- `drawCommittedStrokes(canvas)` — iterate and draw all committed strokes
- `drawActiveStroke(canvas)` — draw in-progress stroke
- `drawEraserCursor(canvas, scale)` — scale-compensated cursor circle
- `getStrokesJson()` / `loadStrokesJson(json)`
- `clear()`

**Callback interface:**
```kotlin
interface DrawingEngineHost {
    fun invalidateView()
    fun notifyUndoRedoState(canUndo: Boolean, canRedo: Boolean)
    fun notifyEraserLift()
}
```

> **Design rule:** `BaseDrawingEngine` operates exclusively in logical coordinates. It never sees screen coordinates. The `UnifiedCanvasView.onTouchEvent()` converts screen→logical via `PagedScrollEngine.toLogicalX/Y()` *before* calling `handleDraw()`, and passes `scrollEngine.currentScale()` as a parameter where needed (e.g., eraser threshold scaling). This keeps the engine coordinate-system-agnostic and avoids a circular dependency between the drawing engine and the scroll engine.

### Helper class: PagedScrollEngine

Owns coordinate transforms, scrolling, zoom, and page layout.

**State it owns:**
- `scale`, `scrollY`, `translateX`
- `pageHeights`, `pageYOffsets`, `pageAspects`, `totalDocHeight`
- `pageGapPx`
- `ScaleGestureDetector`, `VelocityTracker`, `OverScroller`
- Scrollbar state (`scrollbarDragging`, thumb dimensions)
- `currentPage`

**Methods:**
- `initPages(count, aspects)` — set up page layout from aspect ratios (PDF or fixed A4)
- `reLayoutPages(viewWidth)` — recompute heights/offsets when width changes
- `toLogicalX(screenX)` / `toLogicalY(screenY)` — coordinate conversion
- `currentScale()` — return current zoom level
- `handleScroll(event)` — drag, pinch-zoom, fling
- `handleScrollbar(event)` — scrollbar drag
- `isOnScrollbar(event)` — hit-test scrollbar touch zone
- `drawScrollbar(canvas)` — render scrollbar thumb
- `drawOverscrollIndicator(canvas)` — draw "↑/↓ New page" label when overscrolled past threshold
- `applyTransform(canvas)` — `canvas.translate(translateX, -scrollY); canvas.scale(scale, scale)`
- `scrollToPage(page)` / `getCurrentPage()` / `addPage(aspectRatio)` / `prependPage(aspectRatio)`
- `computeScroll()` — update from fling, return true if still animating
- `maxScrollY()` / `constrain()`
- `overscrollState()` — returns `NONE`, `TOP_READY`, or `BOTTOM_READY` based on current drag distance vs. 80 dp threshold (only when not pinch-zooming)

### UnifiedCanvasView

Composes the two engines:

```kotlin
class UnifiedCanvasView(context: Context) : View(context), DrawingEngineHost {
    private val drawingEngine = BaseDrawingEngine(this)
    private val scrollEngine = PagedScrollEngine(context)

    // PDF-specific (null for blank notes)
    private var pdfRenderer: PdfRenderer? = null
    private var pageCache: HashMap<Int, Bitmap> = hashMapOf()
    private var renderExecutor: ExecutorService? = null

    var isPdf: Boolean = false; private set
}
```

**`onDraw()` flow:**
1. `scrollEngine.applyTransform(canvas)` — set up coordinate system
2. Background: if PDF → draw cached page bitmaps; if blank → draw white page rectangles
3. `canvas.saveLayer(null, null)` — for eraser PorterDuff.CLEAR
4. `drawingEngine.drawCommittedStrokes(canvas)` — annotation strokes
5. `drawingEngine.drawActiveStroke(canvas)` — in-progress stroke
6. `canvas.restore()`
7. Restore transform, draw UI overlays (scrollbar, eraser cursor, overscroll indicator) in screen space
8. If `!isPdf` → `scrollEngine.drawOverscrollIndicator(canvas)` (draws "New page" label at top/bottom edge when overscrolled past 80 dp)
9. `scrollEngine.computeScroll()` → `postInvalidateOnAnimation()` if fling active

**`onTouchEvent()` flow:**
1. If scrollbar hit → `scrollEngine.handleScrollbar(event)`
2. If tool is SELECT → `scrollEngine.handleScroll(event)`. On `ACTION_UP`: if `!isPdf` and `scrollEngine.overscrollState() == BOTTOM_READY` → `addPage()`; if `TOP_READY` → `prependPage()`. Overscroll state resets on release regardless.
3. Otherwise → convert screen coords to logical via `scrollEngine.toLogicalX/Y(event.x, event.y)`, then pass the logical coords and `scrollEngine.currentScale()` to `drawingEngine.handleDraw(logicalX, logicalY, pressure, scale, tool)`

**PDF methods:**
- `openPdf(uri)` — load PdfRenderer, compute page aspects, call `scrollEngine.initPages()`
- `renderPageAsync(index)` — background render + cache + evict distant pages
- `onDetachedFromWindow()` — close renderer and executor

**Blank note methods:**
- `initBlankPages(count, aspectRatio)` — call `scrollEngine.initPages()` with uniform aspect
- `addPage()` — call `scrollEngine.addPage()`, push `UndoAction.AddPage(index)`, invalidate
- `prependPage()` — call `scrollEngine.prependPage()`, shift all stroke Y-coordinates by new page height, adjust `scrollY` to keep viewport stable, push `UndoAction.AddPage(0)`, invalidate

### Unified bridge

**`UnifiedCanvasViewManager.kt`** — registers native component name `"UnifiedCanvasView"`.

> **Transition naming:** During Steps 3–4, three native component names coexist: `"CanvasView"` (old blank), `"PdfCanvasView"` (old PDF), and `"UnifiedCanvasView"` (new). The old names are removed only in Step 6 when the old ViewManagers are deleted. This avoids duplicate registration crashes and allows incremental migration.

Props (superset):
- `tool` — pen/eraser/select
- `penColor`, `penThickness`, `eraserThickness`, `eraserMode`
- `pdfUri` (optional — if set, opens PDF; if null, blank canvas)

Default tool: PEN for blank, SELECT for PDF (determined by whether `pdfUri` is set).

Callbacks emitted via DeviceEventEmitter:
- `canvasUndoRedoState { canUndo, canRedo }`
- `canvasEraserLift`
- `canvasPageChanged { page }` (both modes)
- `canvasLoadComplete { pageCount }` (PDF only)

**`UnifiedCanvasModule.kt`** replaces both Modules.

Commands:
- `undo(viewTag)`, `redo(viewTag)`, `clear(viewTag)`
- `getStrokes(viewTag, promise)`, `loadStrokes(viewTag, json)`
- `scrollToPage(viewTag, page)` — works for both modes
- `getPageCount(viewTag, promise)` — works for both modes
- `addPage(viewTag)` — blank notes only, appends a new page
- `prependPage(viewTag)` — blank notes only, inserts a new page at the beginning

**React side:** single `src/native/CanvasView.tsx` and `src/native/CanvasModule.ts`. Delete `PdfCanvasView.tsx` and `PdfCanvasModule.ts`.

### Undo/redo strategy

Adopt PdfDrawingView's list-based approach for both modes:
- Undo = remove stroke from `committedStrokes`, push to redo stack, `invalidateView()`
- Redo = restore stroke, push to undo stack, `invalidateView()`
- No bitmap caching — strokes are redrawn each frame within the visible viewport

**Page add/remove undo actions (blank notes only):**
- `UndoAction.AddPage(index: Int)` — undo removes the page and any strokes on it (strokes are saved in the action for redo); if `index == 0`, also reverses the Y-coordinate shift applied during prepend
- `UndoAction.RemovePage(index: Int, strokes: List<Stroke>)` — for redo of a removed page
- Page actions go on the same undo stack as stroke actions, maintaining correct ordering

This is O(1) for undo (vs current O(n) bitmap replay in DrawingCanvas) and simplifies the code. If performance degrades with 1000+ strokes, add bitmap checkpoints later as an optimization.

### Blank note multi-page features

Once the unified view exists, blank notes get these capabilities:

1. **Initial state**: mount with 1 page (A4 aspect ratio)
2. **Add page by overscroll**: when the user drags (SELECT tool / scroll mode) past the bottom edge of the last page or past the top edge of the first page, a "pull to add page" indicator appears (similar to pull-to-refresh). Releasing after the indicator is fully visible appends a page at the end (or prepends at the beginning). Details:
   - **Trigger**: only fires in scroll/SELECT mode, not while drawing with pen/eraser
   - **Threshold**: 80 dp of overscroll beyond the document boundary activates the indicator; releasing below 80 dp cancels
   - **Bottom overscroll**: appends a new blank page, then auto-scrolls so the new page is visible
   - **Top overscroll**: prepends a new blank page, adjusts `scrollY` and all stroke Y-coordinates by the new page's height so existing content stays visually in place
   - **Indicator**: a simple "↓ New page" / "↑ New page" label drawn in the overscroll area, fading in proportionally to drag distance
   - **PDF mode**: overscroll-to-add is disabled (page count is fixed by the PDF)
   - **Undo**: adding a page is an undoable action (`UndoAction.AddPage(index)` / `UndoAction.RemovePage(index)`). Undo removes the page and any strokes on it; redo restores both.
3. **Add page button**: toolbar button as a secondary mechanism — always appends at the end
4. **Page navigation**: `scrollToPage()` works, thumbnail strip works
4. **Page-aware serialization**: add optional `page: Int` field to stroke JSON (also fixes ISSUE-006 for PDF annotations)
5. **Backwards compatibility**: strokes without a `page` field are treated as legacy — compute page from Y-coordinate on load

### Legacy blank-note coordinate migration

Existing blank-note stroke files use **screen pixel coordinates** (e.g., x=540 on a 1080px-wide view). The unified view stores strokes in **logical document coordinates** (where the page width equals `viewWidth / scale`, independent of screen size). Loading old files without conversion will place strokes at wrong positions.

**Detection:** Add a `"version"` field to the stroke JSON wrapper. The new format is:
```json
{"version": 2, "strokes": [...]}
```
Old files are bare arrays (`[{...}, ...]`) with no version field. On load, if the top-level value is an array (no `"version"` key), the file is legacy.

**Conversion strategy (blank notes only — PDF strokes are already in logical coords):**

Legacy blank-note strokes were drawn on a single-page canvas whose dimensions matched the View size exactly (1:1 screen pixels). The logical coordinate system in the unified view defines the page width as `viewWidth` at `scale=1.0`. Since the old canvas also used the full view width with no scaling, **the X coordinates are numerically identical** — no conversion needed for X.

For Y coordinates: the old canvas also used the full view height with no transform, so Y values are also numerically identical to logical coords at scale=1.0 on a single page. **No arithmetic conversion is required.** The migration is:

1. Wrap the bare stroke array in `{"version": 2, "strokes": [...]}`.
2. Assign `"page": 0` to all strokes (they were single-page).
3. Write back the updated format on next save.

This works because `PagedScrollEngine` at scale=1.0 with `scrollY=0` and `translateX=0` produces an identity transform — logical coords equal screen coords. The only real migration is adding the version wrapper and page tags.

**Edge case — different screen sizes:** If a user saved strokes on a 1080px-wide tablet and later opens on a 1920px-wide tablet, the old screen-pixel strokes were always relative to the old view width. In the unified view, page width adapts to the current view width, and strokes in logical coords scale proportionally. Since the old format had no such scaling (strokes were fixed to the original pixel positions), strokes from a narrower device will appear compressed to the left side. To handle this, store `"canvasWidth"` in the version-2 wrapper on save. On load, if the current view width differs from the saved `canvasWidth`, scale all stroke X/Y coords by `currentViewWidth / savedCanvasWidth`. For legacy files without `canvasWidth`, assume the current view width (accept minor misalignment as the best-effort fallback).

## Implementation Steps

Each step is independently shippable and testable. The app stays fully functional after every step.

### Step 1: Extract BaseDrawingEngine

**Files:**
- Create `android/.../canvas/BaseDrawingEngine.kt`
- Modify `DrawingCanvas.kt` — delegate stroke/undo/erase logic to engine
- Modify `PdfDrawingView.kt` — delegate stroke/undo/erase logic to engine

**Test:** All drawing, undo/redo, erasing works identically in both blank notes and PDF viewer.

### Step 2: Extract PagedScrollEngine

**Files:**
- Create `android/.../canvas/PagedScrollEngine.kt`
- Modify `PdfDrawingView.kt` — delegate scroll/zoom/page logic to engine

**Test:** PDF scroll, zoom, fling, scrollbar, page navigation all work identically.

### Step 3: Build UnifiedCanvasView with PDF mode

**Files:**
- Create `android/.../canvas/UnifiedCanvasView.kt`
- Create `android/.../reactbridge/UnifiedCanvasViewManager.kt` — registers as `"UnifiedCanvasView"` (not `"PdfCanvasView"`)
- Create `android/.../reactbridge/UnifiedCanvasModule.kt` — registers as `"UnifiedCanvasModule"`
- Modify `reactbridge/CanvasPackage.kt` — register new view and module **alongside** old ones (three ViewManagers coexist)
- Modify `src/native/PdfCanvasView.tsx` — change `requireNativeComponent` to `'UnifiedCanvasView'`
- Modify `src/native/PdfCanvasModule.ts` — change `NativeModules.PdfCanvasModule` to `NativeModules.UnifiedCanvasModule`

**Test:** PDF viewing and annotation works through the unified view. Blank notes still use the old `DrawingCanvas` via `"CanvasView"` (unchanged).

### Step 4: Add blank-page mode to UnifiedCanvasView

**Files:**
- Modify `UnifiedCanvasView.kt` — add `initBlankPages()`, `addPage()`, `prependPage()`, blank page rendering, overscroll-to-add detection, legacy coordinate migration (see below)
- Modify `PagedScrollEngine.kt` — add `overscrollState()`, `drawOverscrollIndicator()`, `prependPage()`, allow temporary overscroll beyond document bounds during drag (snap back on release if threshold not met)
- Modify `src/native/CanvasView.tsx` — change `requireNativeComponent` to `'UnifiedCanvasView'`, change module to `'UnifiedCanvasModule'`
- Modify `src/native/CanvasModule.ts` — change `NativeModules.CanvasModule` to `NativeModules.UnifiedCanvasModule`, add `addPage` and `prependPage` commands
- Modify `src/screens/NoteEditorScreen.tsx` — use scrollable canvas, add page controls (toolbar add-page button)

**Test:** Blank notes are now scrollable with multiple pages. PDF still works. **Regression test:** open existing blank notes saved with the old screen-pixel format — strokes must appear in the correct positions after migration.

### Step 5: Merge bridge and React wrappers

**Files:**
- Delete `src/native/PdfCanvasView.tsx` — functionality absorbed into `CanvasView.tsx`
- Delete `src/native/PdfCanvasModule.ts` — functionality absorbed into `CanvasModule.ts`
- Modify `src/screens/PdfViewerScreen.tsx` — import from `CanvasView` instead of `PdfCanvasView`
- Modify `src/components/ThumbnailStrip.tsx` — update imports if affected

**Test:** Both screens use the same React wrapper. Full app regression test.

### Step 6: Delete old files

**Files to delete:**
- `android/.../canvas/DrawingCanvas.kt`
- `android/.../canvas/PdfDrawingView.kt`
- `android/.../reactbridge/CanvasViewManager.kt`
- `android/.../reactbridge/PdfCanvasViewManager.kt`
- `android/.../reactbridge/CanvasModule.kt`
- `android/.../reactbridge/PdfCanvasModule.kt`

**Modify:**
- `reactbridge/CanvasPackage.kt` — remove old registrations

**Test:** Clean build, full regression.

## Issues addressed by this work

- **ISSUE-006** (PDF annotations stored without page tagging) — fixed by page-aware serialization
- **ISSUE-007** (undo performance degrades with stroke count) — fixed by switching DrawingCanvas from bitmap replay to list-based undo
- **ISSUE-005** (auto-save only on navigation) — not directly fixed, but the unified view makes it easier to add AppState/interval saving in one place instead of two

## Risks

- **Step 3 is the riskiest** — replacing `PdfDrawingView` with the unified view touches the most complex rendering code. Mitigate by keeping `PdfDrawingView` until the unified view passes all manual tests.
- **Coordinate system bugs** — the transform math (screen ↔ logical) is subtle. Off-by-one errors in page offsets or scale compensation will cause strokes to appear offset. Test zoom + draw + undo combinations thoroughly.
- **Performance regression** — removing bitmap caching for blank notes means all strokes are redrawn each frame. Acceptable for typical note sizes but watch for regression on 500+ stroke notes.
- **Existing stroke files** — ~~saved JSON files use screen coordinates (DrawingCanvas) vs logical coordinates (PdfDrawingView). Need a migration path or detection heuristic when loading old blank-note stroke files into the new logical coordinate system.~~ **Addressed:** see "Legacy blank-note coordinate migration" section above. Version-tagged JSON format with `canvasWidth` for cross-device scaling.
