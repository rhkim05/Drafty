# TODO — Phase 1: Core Drawing Canvas

## Phase 1A: Data Layer

- [ ] Add missing SQLDelight queries to `DraftyDatabase.sq`
  - [ ] `insertStroke` — insert a stroke with all fields
  - [ ] `deleteStroke` — delete stroke by id
  - [ ] `deleteAllStrokesForPage` — delete all strokes for a page
  - [ ] `getMaxStrokeOrder` — get max stroke order for a page
  - [ ] `getPageById` — select page by id
  - [ ] `insertPage` — insert a page with all fields
- [ ] Create domain models in `shared/src/commonMain/kotlin/.../model/StrokeData.kt`
  - [ ] `StrokePoint` data class (x, y, pressure, tilt, orientation, timestamp)
  - [ ] `BrushConfig` data class (type, size, colorArgb)
  - [ ] `BrushType` enum with `PRESSURE_PEN` and `fromKey()` companion
  - [ ] `StrokeData` data class (id, pageId, points, brush, strokeOrder, boundingBox, createdAt)
  - [ ] `BoundingBox` data class (x, y, width, height)
- [ ] Create `Tool` enum in `shared/src/commonMain/kotlin/.../model/Tool.kt`
  - [ ] `PEN` entry (ERASER, HIGHLIGHTER deferred)
- [ ] Implement `StrokeSerializer` expect/actual
  - [ ] `expect class` in `shared/src/commonMain/.../data/serialization/StrokeSerializer.kt`
  - [ ] `actual class` Android impl using `StrokeInputBatch` encode/decode in `shared/src/androidMain/`
  - [ ] `actual class` iOS stub (empty encode/decode) in `shared/src/iosMain/`
- [ ] Implement `StrokeRepository` in `shared/src/commonMain/.../data/repository/StrokeRepository.kt`
  - [ ] `getStrokesForPage(pageId)` — query + map DB rows to `StrokeData`
  - [ ] `insertStroke(stroke)` — serialize points + insert into DB
  - [ ] `deleteStroke(id)` — delete by id
  - [ ] `getNextStrokeOrder(pageId)` — get max order + 1
- [ ] Update `SharedModule` Koin config
  - [ ] Register `StrokeSerializer` singleton
  - [ ] Register `StrokeRepository` singleton

## Phase 1B: State Management

- [ ] Implement `CanvasStore` (MVI) in `shared/src/commonMain/.../store/CanvasStore.kt`
  - [ ] `CanvasState` data class (pageId, strokes, activeTool, activeBrush, isLoading)
  - [ ] `CanvasIntent` sealed interface (LoadPage, StrokeCompleted, ToolSelected, ColorSelected, BrushSizeChanged)
  - [ ] `dispatch()` method routing intents
  - [ ] `loadPage()` — fetch strokes from repository
  - [ ] `onStrokeCompleted()` — optimistic UI update + persist to DB
- [ ] Update `AppModule` Koin config
  - [ ] Add `CanvasStore` factory with `CoroutineScope` parameter

## Phase 1C: Canvas UI

- [ ] Implement `StrokeMapper` in `androidApp/src/main/kotlin/.../ink/StrokeMapper.kt`
  - [ ] `toStrokeData()` — convert `androidx.ink.strokes.Stroke` to domain `StrokeData`
  - [ ] `toInkStroke()` — convert domain `StrokeData` to `androidx.ink.strokes.Stroke`
  - [ ] `createBrush()` — create `Brush` from `BrushConfig`
  - [ ] ARGB-to-ColorLong helper
- [ ] Implement `CanvasTouchListener` in `androidApp/src/main/kotlin/.../ui/canvas/CanvasTouchListener.kt`
  - [ ] `buildTouchListener()` function
  - [ ] Stylus-only filtering (palm rejection via `TOOL_TYPE_STYLUS` / `TOOL_TYPE_ERASER`)
  - [ ] `ACTION_DOWN` — `requestUnbufferedDispatch` + `startStroke`
  - [ ] `ACTION_MOVE` — `addToStroke` with `predict = true`
  - [ ] `ACTION_UP` — `finishStroke`
  - [ ] `ACTION_CANCEL` — `cancelStroke`
  - [ ] `FinishedStrokesListener` callback wiring
- [ ] Rewrite `CanvasScreen` in `androidApp/src/main/kotlin/.../ui/canvas/CanvasScreen.kt`
  - [ ] Bottom layer: `Canvas` composable rendering finalized strokes via `CanvasStrokeRenderer`
  - [ ] Middle layer: `InProgressStrokesView` via `AndroidView` for stylus capture
  - [ ] Top layer: Toolbar overlay
  - [ ] `LaunchedEffect` for page load and stroke conversion
  - [ ] Koin injection of `CanvasStore`
- [ ] Implement `CanvasToolbar` in `androidApp/src/main/kotlin/.../ui/canvas/CanvasToolbar.kt`
  - [ ] Color preset row (black, dark gray, blue, red, green, amber)
  - [ ] Brush size slider (range 1.0 - 20.0)
  - [ ] Wire to `CanvasIntent.ColorSelected` / `CanvasIntent.BrushSizeChanged`

## Phase 1D: Integration & Verification

- [ ] Wire `CanvasScreen` to `CanvasStore` via Koin
- [ ] Add `ink-strokes` and `ink-storage` deps to `shared/build.gradle.kts` androidMain (if not transitive)
- [ ] Verify strokes persist across page close/reopen
- [ ] Verify pressure sensitivity with stylus
- [ ] Verify low-latency rendering on physical tablet
- [ ] Run `./gradlew build` — clean build passes
- [ ] Run `./gradlew :shared:allTests` — all tests pass
- [ ] Run `./gradlew :androidApp:lint` — no new warnings

## Deferred (Future Phases)

- Eraser tool (Phase 2)
- Undo/redo (Phase 3)
- Pan/zoom (Phase 5)
- Multi-touch (Phase 5)
- Highlighter (future)
- iOS canvas implementation (future)
