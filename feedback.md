# Feedback — research.md Review

This document records all issues found during a thorough review of `research.md`, why each was a problem, and how it was fixed. Linked from [`research.md`](research.md).

---

## Factual Errors

### 1. Eraser row in BrushProvider table was wrong
**Issue:** The BrushProvider table listed Eraser as using `StockBrushes.pressurePenLatest + Color.TRANSPARENT`, implying the eraser draws an invisible stroke. This contradicts the actual eraser mechanism (hit-testing via `EraserHandler` + commands).
**Why it matters:** Drawing a transparent stroke does nothing useful. The eraser doesn't use `InProgressStrokesView` at all — it removes/splits existing strokes. Following this would produce a non-functional eraser with no visual feedback.
**Fix:** Removed the Eraser row from the BrushProvider table. Added a note explaining that the eraser works via `EraserHandler` and described the visual feedback mechanism (cursor overlay + real-time back-buffer redraw).

### 2. `@JvmInline` on a commonMain class
**Issue:** `StrokeId` was declared as `@JvmInline value class` but lives in `commonMain`. `@JvmInline` is a JVM-specific annotation that is unnecessary in multiplatform code.
**Why it matters:** While the Kotlin compiler handles this gracefully (it would compile), it signals misunderstanding of KMP source sets and could confuse implementation. In `commonMain`, `value class` alone is correct.
**Fix:** Changed to `value class StrokeId(val value: String)` with an explanatory note.

### 3. Unverified latency claim
**Issue:** "Sub-frame latency (~4ms on Samsung Tab S8)" was stated as fact. This is an unverified benchmark number not from official documentation.
**Why it matters:** Specific device numbers set false expectations. Actual latency depends on hardware, Android version, and OEM display pipeline.
**Fix:** Reworded to "sub-frame latency (Google claims sub-8ms on supported hardware)".

### 4. `CanvasExportRenderer` incorrectly declared as `expect class`
**Issue:** The export renderer was shown as an `expect class` in commonMain. Export rendering is inherently platform-specific (Android uses `PdfDocument` + `Bitmap`, iOS would use Core Graphics).
**Why it matters:** Declaring it as `expect/actual` forces iOS to implement the exact same method signatures, unnecessarily constraining the iOS implementation.
**Fix:** Changed to a platform-only class. Removed from the expect/actual table. Added note explaining why.

### 5. Color type inconsistency — incomplete analysis
**Issue:** The document flagged `Stroke.colorArgb: Long` vs `ActiveToolState.colorArgb: Int` as an inconsistency but didn't fully trace the type through all layers (SQL, StrokeMetadata, ViewModel conversion).
**Why it matters:** Without a complete trace, the implementer doesn't know exactly what to change and where.
**Fix:** Added a "Color type unification" block listing every color representation and stating the concrete action: change `ActiveToolState.colorArgb` to `Long`, remove `toInt()` conversion.

---

## Missing Information

### 6. No DI strategy
**Issue:** No discussion of how dependencies get wired. `CanvasViewModel` takes repositories as constructor params, but nothing explains how those instances are created or provided.
**Why it matters:** This is the first implementation blocker. You can't instantiate ViewModels without solving DI.
**Fix:** Added Section 15. Resolved to use Koin — lightweight, KMP-native, handles canvas-scoped ViewModel lifecycle without reimplementing it manually. Dependencies: `koin-core` (commonMain), `koin-android` + `koin-androidx-compose` (androidMain).

### 7. No navigation architecture
**Issue:** `DraftyApp.kt` was described as "navigation host (stub)" with no discussion of how navigation works.
**Why it matters:** Library-to-canvas transitions, back-stack, and deep links for `.drafty` file imports all depend on navigation infrastructure.
**Fix:** Added Section 16. Resolved to use Compose Navigation (`navigation-compose` 2.8+, KMP-compatible). Chosen for deep link support (`.drafty` file imports via intent), official Google backing, and nav-destination-scoped ViewModels via `koinNavGraphViewModel()`.

### 8. No canvas coordinate system
**Issue:** The document never defined what coordinate system strokes use, what units template spacing is in, how touch coordinates map to canvas coordinates, or whether the canvas is infinite or fixed-size.
**Why it matters:** Everything depends on this — rendering, PDF export, cross-device consistency, spatial indexing, eraser hit-testing. Template spacing was stated as "32px" without clarifying whether those are screen pixels or canvas units.
**Fix:** Added "Canvas Coordinate System" subsection defining canvas-space units, origin, dimensions (infinite scroll), zoom range, and touch-to-canvas coordinate mapping.

### 9. No threading/concurrency model
**Issue:** No discussion of which operations run on which thread, or why the spatial index doesn't need synchronization.
**Why it matters:** Race conditions between main-thread rendering and background autosave would corrupt state. The `AutosaveManager` used `synchronized` (JVM-only) in a `commonMain` class.
**Fix:** Added "Threading Model" subsection defining main-thread-only state mutations, background dispatchers for IO, `Mutex` requirement for `AutosaveManager`, and SQLite WAL mode requirement.

### 10. No touch-to-canvas coordinate mapping
**Issue:** The `StrokeInputHandler` passes `canvasTransform` to `startStroke()` but there was no explanation of how screen touches become canvas coordinates, or what coordinate space `EraserHandler` operates in.
**Why it matters:** Incorrect coordinate mapping means strokes render at wrong positions and eraser hits wrong strokes.
**Fix:** Addressed in the Canvas Coordinate System subsection.

### 11. No tool-mode switching behavior defined
**Issue:** No specification of what happens when the user changes tools while a stroke is in progress.
**Why it matters:** Could cause state corruption or undefined behavior during fast tool switching.
**Fix:** Added note in Section 6: tool is read at `ACTION_DOWN` and locked for the stroke's duration.

### 12. No thumbnail generation strategy
**Issue:** `Canvas.thumbnail` exists but nowhere does the document explain when, how, or at what resolution thumbnails are generated.
**Why it matters:** Without this, the library screen would show empty thumbnail placeholders.
**Fix:** Added Section 17 defining generation timing (canvas close / app background), resolution (400x560), and mechanism.

### 13. No error handling discussion
**Issue:** Only the happy path was documented. No discussion of corrupted data, missing files, or write failures.
**Why it matters:** The app will encounter these cases in production. Without a strategy, each implementer makes ad-hoc decisions that may be inconsistent or data-destructive.
**Fix:** Added Section 18 covering corrupted protobuf blobs, missing backing PDFs, and SQLite write failures.

### 14. Missing `StrokeRepository.update()` — undocumented design choice
**Issue:** The repository has `insert` and `delete` but no `update`. The `AutosaveManager` does delete + insert for modifications, but this was never flagged as intentional.
**Why it matters:** An implementer might add an `update()` method assuming it was forgotten, creating inconsistency with the autosave pipeline.
**Fix:** Added Section 19 documenting this as an intentional design choice with rationale.

---

## Insufficient Depth

### 15. Partial erase was hand-waved
**Issue:** The single hardest feature in the app got a two-line description: "uses ink-geometry to find intersections and compute split points."
**Why it matters:** Splitting pressure-sensitive strokes requires handling new IDs, bounding boxes, sort orders, timestamp preservation, and multi-intersection splits. The ink-geometry API is undocumented alpha.
**Fix:** Expanded into a full pipeline description with 6 steps, data handling for fragment strokes, and a fallback strategy if ink-geometry proves unreliable.

### 16. Spatial index cell size unjustified
**Issue:** 256px cell size was stated without rationale.
**Why it matters:** A bad cell size degrades performance. Too small = excessive cells per stroke. Too large = poor culling granularity.
**Fix:** Added justification paragraph with concrete numbers for typical handwriting density and large-stroke overhead.

### 17. PDF memory analysis missing
**Issue:** "High-zoom rendering allocates oversized bitmaps" was noted without concrete numbers.
**Why it matters:** Without numbers, "reasonable max zoom cap" is undefined.
**Fix:** Added concrete calculation: 400% zoom = ~340MB bitmap (OOM guaranteed), 200% cap = ~85MB (acceptable).

### 18. `collectAsState()` risk undersold
**Issue:** Flagged as "Low" severity.
**Why it matters:** On a drawing app using `AndroidView` interop, continued flow collection while backgrounded can cause state updates against an inactive Compose tree, leading to inconsistency on resume.
**Fix:** Upgraded to "Medium" severity. Changed from suggestion to requirement: use `collectAsStateWithLifecycle()` from day one.

---

## Unstated Assumptions

### 19. Critical assumptions were implicit
**Issue:** The architecture implicitly assumes single canvas at a time, locked orientation, English-only, no accessibility, single-window mode, main-thread-only mutations, and WAL mode. None were stated.
**Why it matters:** Violating any of these assumptions (e.g., allowing rotation without surviving config changes) causes bugs that are hard to diagnose because the constraint was never documented.
**Fix:** Added Section 22 with an explicit assumptions table.

---

## Contradictions

### 20. Commit flow step order disagreed with architecture.md
**Issue:** research.md listed steps 3/4 as separate (push command, then update state). architecture.md had them in different order. The actual code in `StrokeCommitHandler` calls `viewModel.commitStroke()` which does both internally.
**Why it matters:** Implementers following the wrong order might try to update state separately from the command execution.
**Fix:** Merged steps 3/4 to accurately reflect that `commitStroke()` pushes the command which executes the state update atomically.

### 21. `CanvasState.templateRenderer` ownership undefined
**Issue:** architecture.md's `CommittedStrokeView.onDraw()` calls `state.templateRenderer?.draw()`, but `CanvasState` in research.md has no such field. A renderer reference in a commonMain data class would break KMP.
**Why it matters:** Implementer wouldn't know who creates/owns the renderer.
**Fix:** Added Section 21 stating `CommittedStrokeView` owns the renderer, selects it based on `CanvasState.template` enum. Noted that `architecture.md` must be corrected.

### 22. `CanvasClipboard` in architecture.md but absent from research.md
**Issue:** architecture.md's system diagram includes `CanvasClipboard` but it's not mentioned anywhere in research.md.
**Why it matters:** Unclear whether clipboard is a v1 feature or was forgotten.
**Fix:** Added Section 20 deferring clipboard to v2 and noting it should be removed from architecture.md's v1 diagram.

### 23. Dual UUID solutions
**Issue:** Both `com.benasher44:uuid` (external dependency) and `expect fun generateUuid()` (platform abstraction) existed for the same purpose. `StrokeAdapter` called `uuid4()` (benasher44) while the platform table listed `java.util.UUID`.
**Why it matters:** Two UUID mechanisms create confusion about which to use and add unnecessary dependencies.
**Fix:** Removed `com.benasher44:uuid` from dependencies. Resolved to use `kotlin.uuid.Uuid` from the Kotlin stdlib — zero dependencies, KMP-native, available since Kotlin 2.0. The `expect fun generateUuid()` / `actual fun` pattern and `UuidGeneratorAndroid` / `UuidGeneratorIos` files are removed.

---

## Structural Issues

### 24. Massive duplication with architecture.md
**Issue:** Sections 5-10 of research.md are near-identical to architecture.md's content — same rendering pipeline, input pipeline, state management, persistence, and export descriptions with similar code snippets.
**Why it matters:** Two copies of the same information drift apart (as evidenced by the commit flow contradiction). Readers don't know which is authoritative.
**Fix:** Added a "Note on document roles" clarifying that research.md provides analysis/decisions/risks while architecture.md is the source of truth for implementation details. Full deduplication (removing code from research.md) deferred to avoid a disruptive rewrite of the document.

### 25. Stroke data size estimate was wrong
**Issue:** "Typical canvas 100-2000 strokes (~1MB)" — actual protobuf size is 6-12MB for 2000 strokes.
**Why it matters:** The "full load on canvas open" decision was justified by a size that was off by ~10x. The decision is still correct (6-12MB is fast to read), but the estimate was misleading.
**Fix:** Corrected the estimate in the design decisions table and added a dedicated calculation in Section 14.

### 26. `CanvasState` mutable member in data class
**Issue:** `StrokeSpatialIndex` is mutable but embedded in a `data class`, breaking `equals()`, `hashCode()`, and `copy()` semantics.
**Why it matters:** `copy()` shallow-copies the index, so two state instances share the same mutable index. Using `CanvasState` in comparisons or collections would produce wrong results.
**Fix:** Added implementation warning noting the constraint: only access the index via current `StateFlow.value`, never compare across snapshots. Noted the escape hatch of moving the index to `CanvasViewModel`.

### 27. `AutosaveManager` uses `synchronized` in commonMain
**Issue:** `synchronized` is JVM-only and doesn't exist in Kotlin/Native (iOS). `AutosaveManager` is in `commonMain`.
**Why it matters:** Won't compile for iOS.
**Fix:** Added thread-safety note to `AutosaveManager` section requiring `Mutex` instead. Added KMP concurrency note to Threading Model section.

### 28. `lateinit var autosaveManager` on ViewModel
**Issue:** architecture.md shows `lateinit var autosaveManager` on `CanvasViewModel`, creating a temporal coupling where `commitStroke()` crashes if called before initialization.
**Why it matters:** `lateinit var` is a code smell for dependencies — it bypasses the compiler's null safety and creates an implicit ordering requirement.
**Fix:** Added note under CanvasViewModel requiring constructor injection for all dependencies.
