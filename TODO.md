# TODO — Phase 1: Core Drawing Canvas

## Phase 1A: Data Layer

- [x] Add missing SQLDelight queries to `DraftyDatabase.sq`
- [x] Create domain models (`StrokePoint`, `BrushConfig`, `BrushType`, `StrokeData`, `BoundingBox`)
- [x] Create `Tool` enum
- [x] Implement `StrokeSerializer` expect/actual (android + ios stub)
- [x] Implement `StrokeRepository`
- [x] Update `SharedModule` Koin config

## Phase 1B: State Management

- [x] Implement `CanvasStore` (MVI) with `CanvasState`, `CanvasIntent`, dispatch
- [x] Update `AppModule` Koin config with `CanvasStore` factory

## Phase 1C: Canvas UI

- [x] Implement `StrokeMapper` (InkStroke <-> StrokeData conversion)
- [x] Implement `CanvasTouchListener` (stylus input + palm rejection)
- [x] Rewrite `CanvasScreen` with InProgressStrokesView + CanvasStrokeRenderer
- [x] Implement `CanvasToolbar` (color presets + brush size slider)

## Phase 1D: Integration & Verification

- [x] Wire `CanvasScreen` to `CanvasStore` via Koin
- [x] Add `ink-brush`, `ink-strokes`, `ink-storage` deps to `shared/build.gradle.kts` androidMain
- [x] Run `./gradlew build` — clean build passes
- [x] Run `./gradlew :androidApp:lint` — no new warnings
- [ ] Verify strokes persist across page close/reopen (manual test on device)
- [ ] Verify pressure sensitivity with stylus (manual test on device)
- [ ] Verify low-latency rendering on physical tablet (manual test on device)

## Deferred (Future Phases)

- Eraser tool (Phase 2)
- Undo/redo (Phase 3)
- Pan/zoom (Phase 5)
- Multi-touch (Phase 5)
- Highlighter (future)
- iOS canvas implementation (future)
