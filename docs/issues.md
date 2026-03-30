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
