# Research: Phase 3 — Notebook Organization & UI

> References: [plan.md](./plan.md) | [tech-stack.md](./tech-stack.md) | [architecture.md](./architecture.md) | [TODO.md](./TODO.md) | [research.md](./research.md) (Phases 1–2)

---

## 1. Current Codebase State for Phase 3

### 1.1 :feature:notebooks — Existing Stubs

The module has the right architecture skeleton already in place from Phase 0:

**Home screen layer:**
- `HomeUiState` — data class with `notebooks: List<Notebook>`, `isLoading`, `searchQuery`, `sortOrder: SortOrder` (enum: RECENT, NAME, CREATED). Ready to use as-is.
- `HomeViewModel` — Hilt-annotated shell. No use cases injected, no methods. Needs: `GetNotebooksUseCase`, `SearchNotebooksUseCase`, `CreateNotebookUseCase`, `DeleteNotebookUseCase`, `UpdateNotebookUseCase`.
- `HomeScreen` — composable stub showing placeholder text. Parameters: `onNotebookClick(notebookId)`, `onCreateNotebook()`.

**Detail screen layer:**
- `NotebookDetailUiState` — data class with `notebook: Notebook?`, `sections: List<Section>`, `selectedSectionId: String?`, `pages: List<Page>`, `isLoading`. Ready to use as-is.
- `NotebookDetailViewModel` — Hilt-annotated shell. No use cases injected. Needs section and page use cases.
- `NotebookDetailScreen` — composable stub. Parameters: `notebookId: String`, `onPageClick(pageId)`, `onBack()`.

**Components (all empty stubs):**
- `NotebookGrid` — no params, empty body. TODO: LazyVerticalGrid.
- `NewNotebookDialog` — params: `onDismiss`, `onCreate(title)`. Empty body.
- `SectionTabs` — no params, empty body. TODO: scrollable tab row.

### 1.2 :core:domain — Fully Implemented

Domain layer is complete and production-ready. Key models:

**Hierarchy:** `Notebook → Section → Page → Stroke → InkPoint`

| Model | Key Fields | Notes |
|-------|-----------|-------|
| Notebook | id, title, coverColor (Long/ARGB), createdAt, modifiedAt, isFavorite, sortOrder | Root entity |
| Section | id, notebookId (FK), title, sortOrder | Groups pages within notebook |
| Page | id, sectionId (FK), type (PageType enum), template (PaperTemplate), width, height, sortOrder | Canvas target |
| Tag | id, name, color (Long/ARGB) | Independent; no FK to notebook yet in domain model |

**Repository interfaces (all fully defined):**
- `NotebookRepository` — `getNotebooks(): Flow`, `getNotebookById(id): Flow`, `getFavoriteNotebooks(): Flow`, `searchNotebooks(query): Flow`, `createNotebook(notebook)`, `updateNotebook(notebook)`, `deleteNotebook(id)`
- `PageRepository` — `getPagesBySectionId(sectionId): Flow`, `getPageById(id): Page?`, `addPage(page)`, `deletePage(id)`, `reorderPages(pages)`, `updatePage(page)`

**Missing repository:** No `SectionRepository` interface exists. Section CRUD must either be added to an existing repo, or a new `SectionRepository` created. The `SectionDao` exists in :core:data but has no repository wrapper.

**Use cases (all implemented with operator invoke):**
- Notebook: `GetNotebooksUseCase`, `CreateNotebookUseCase`, `UpdateNotebookUseCase`, `DeleteNotebookUseCase`, `SearchNotebooksUseCase`
- Page: `GetPagesUseCase(sectionId)`, `AddPageUseCase`, `DeletePageUseCase`, `ReorderPagesUseCase`

**Missing use cases:** No section-level use cases (GetSections, CreateSection, DeleteSection, RenameSection, ReorderSections). These need to be created.

### 1.3 :core:data — Room Layer

**Database:** `drafty.db`, version 1, 5 entities.

**Entities and tables:**
- `NotebookEntity` (table: `notebooks`) — all Notebook fields mapped
- `SectionEntity` (table: `sections`) — FK to notebooks with CASCADE delete, indexed on `notebook_id`
- `PageEntity` (table: `pages`) — FK to sections with CASCADE delete, indexed on `section_id`; type/template stored as strings
- `TagEntity` (table: `tags`) — standalone
- `NotebookTagCrossRef` (table: `notebook_tags`) — M:N junction, composite PK, both FKs CASCADE

**DAOs:**
- `NotebookDao` — getAll (sorted by sortOrder), getById, insert (REPLACE), update, deleteById, searchByTitle (LIKE)
- `SectionDao` — getByNotebookId (sorted by sortOrder), insert, update, deleteById
- `PageDao` — getBySectionId (sorted by sortOrder), getById, insert, update, deleteById, updateSortOrders (batch)
- `TagDao` — getAll, insert, deleteById

**Repository implementations:**
- `NotebookRepositoryImpl` — fully implemented, uses NotebookDao + mappers
- `PageRepositoryImpl` — fully implemented, uses PageDao + mappers
- `StrokeRepositoryImpl` — fully implemented, wraps StrokeFileManager

**Missing:** No `SectionRepositoryImpl`. SectionDao exists and works, but there's no repository layer above it and no Hilt binding for it.

**Mappers:** `NotebookMapper`, `SectionMapper`, `PageMapper` — all bidirectional (toDomain / toEntity). Enums map via `valueOf()` / `.name`.

**DI (DataModule):** Provides database, all 4 DAOs, 3 repositories (Notebook, Page, Stroke, Pdf), and 3 use cases (LoadStrokes, SaveStrokes, GetPages). Missing: section repository binding and notebook/page use case bindings.

### 1.4 :core:ui — Theme & Component Stubs

**Theme (fully implemented):**
- Material 3 color system with light/dark palettes (primary: calm blue `#4A6FA5`)
- Dynamic color on Android 12+ with graceful fallback
- Typography: full M3 scale, 11 levels
- Shapes: 8dp / 12dp / 16dp corner radii
- `DraftyTheme` composable wraps everything

**Shared components (all stubs, empty bodies):**
- `NotebookCard(title, pageCount, lastModified, onClick)` — needs implementation for home grid
- `PageThumbnail(pageNumber, isSelected, onClick)` — needs implementation for detail screen
- `ConfirmDialog(title, message, confirmText, cancelText, onConfirm, onCancel, isVisible)`
- `ToolButton`, `ThicknessSlider`, `ColorPicker` — drawing tool UI, partially used by canvas

**Icons (DraftyIcons):** Pen, Highlighter, Eraser, Lasso, Undo, Redo, Add, Delete, Export, Search, Info. All using Material icons.

### 1.5 :app — Navigation Gap

**Current nav graph (`DraftyNavGraph`):**
```kotlin
NavHost(startDestination = "canvas") {
    composable("canvas") { CanvasScreen() }
    // TODO: Add "home" and "pdf-viewer" destinations
}
```

- Start destination is `"canvas"` — no home screen yet
- No route parameters defined for any destination
- CanvasScreen takes no nav args; `loadSection()` must be called explicitly but isn't wired to navigation

**CanvasViewModel integration gap:**
- `loadSection(sectionId, startPageId?)` exists and works, but it's never called from navigation
- No `SavedStateHandle` usage to read nav args
- Canvas currently opens blank with no loaded content

### 1.6 :feature:canvas — Integration Points

**CanvasViewModel** already supports:
- `loadSection(sectionId, startPageId?)` — fetches pages, loads first page
- `nextPage()`, `previousPage()`, `goToPage(index)` — with auto-save before switching
- `forceSave()` on `onCleared()` and page navigation
- 500ms debounced auto-save after each stroke
- Tool/color/thickness state

**PageNavigator** composable shows `‹ [3/12] ›` in paginated mode.

**What Phase 3 must wire:** Navigation args → CanvasViewModel.loadSection() → actual content shown.

---

## 2. Architectural Decisions for Phase 3

### 2.1 Navigation Strategy

**Decision: String-based routes with arguments (Compose Navigation)**

Routes to define:
- `"home"` — start destination (notebook grid)
- `"notebook/{notebookId}"` — notebook detail (sections + pages)
- `"canvas/{sectionId}?pageId={pageId}"` — canvas with section context and optional page

Rationale: Matches existing pattern in codebase. Type-safe navigation (Kotlin serialization routes) would be better long-term but adds complexity and would require refactoring the existing canvas route. Keeping string routes consistent with current code.

**CanvasViewModel** needs `SavedStateHandle` to extract `sectionId` and `pageId` from route, then call `loadSection()` in init block.

### 2.2 Section Repository

**Decision: Create `SectionRepository` interface + `SectionRepositoryImpl`**

The `SectionDao` already exists with full CRUD. We need:
- `SectionRepository` interface in `:core:domain` (Flow-based getByNotebookId, suspend create/update/delete/reorder)
- `SectionRepositoryImpl` in `:core:data` wrapping SectionDao with mapper
- Section use cases: `GetSectionsUseCase`, `CreateSectionUseCase`, `RenameSectionUseCase`, `DeleteSectionUseCase`, `ReorderSectionsUseCase`
- Hilt bindings in DataModule

### 2.3 Notebook Default Structure

**Decision: Auto-create one section + one page on notebook creation**

When a user creates a new notebook, the flow should:
1. Create `Notebook` entity
2. Auto-create a default `Section` (title: "Section 1")
3. Auto-create a default `Page` in that section (A4 portrait, blank template)

This ensures the user can immediately start drawing after creating a notebook. The `CreateNotebookUseCase` should orchestrate this (or a new `CreateNotebookWithDefaultsUseCase`).

### 2.4 Home Screen Layout

**Decision: Adaptive grid using `WindowSizeClass`**

- Compact (phone): 2-column grid
- Medium (small tablet): 3-column grid
- Expanded (large tablet): 4-column grid

`NotebookCard` shows: cover color swatch, title, page count, last modified date. Long-press or overflow menu for rename/duplicate/delete/favorite.

### 2.5 Notebook Detail Layout

**Decision: Section tabs (top) + page thumbnail grid (body)**

- `ScrollableTabRow` for sections — horizontally scrollable, with "+" button to add section
- Page thumbnails in `LazyVerticalGrid` below — 3-column on tablet
- Tap page → navigate to canvas at that page
- FAB for "Add Page" in current section

### 2.6 Tagging (Deferred)

**Decision: Defer full tagging UI to Phase 5**

The database schema (`NotebookTagCrossRef`) already supports M:N notebook-tag relationships. However, implementing tag CRUD, tag filters, and tag assignment UI adds significant scope. Phase 3 will focus on core notebook/section/page management. Tags can be added as an enhancement.

### 2.7 Settings Screen (Lightweight)

**Decision: Minimal settings in Phase 3**

Settings screen with:
- Default pen color/thickness
- Default paper template
- Palm rejection toggle
- Dark mode toggle (system/light/dark)
- App version info

Storage: Jetpack DataStore (Preferences). No need for Proto DataStore for this simple config.

---

## 3. Missing Infrastructure Inventory

| What's Missing | Where | Priority |
|---------------|-------|----------|
| `SectionRepository` interface | `:core:domain/repository/` | P0 — blocks detail screen |
| `SectionRepositoryImpl` | `:core:data/repository/` | P0 |
| Section use cases (Get, Create, Rename, Delete, Reorder) | `:core:domain/usecase/section/` | P0 |
| Hilt bindings for section repo + all missing use cases | `:core:data/di/DataModule` | P0 |
| `SectionMapper` already exists | `:core:data/db/mapper/` | Done |
| `NotebookCard` composable (stub exists) | `:core:ui/components/` | P0 |
| `PageThumbnail` composable (stub exists) | `:core:ui/components/` | P1 |
| `ConfirmDialog` composable (stub exists) | `:core:ui/components/` | P0 |
| Navigation routes with args | `:app/navigation/` | P0 |
| `SavedStateHandle` in CanvasViewModel | `:feature:canvas` | P0 |
| `ThumbnailManager` (stub in :core:data) | `:core:data/file/` | P2 — can use placeholder |
| DataStore for settings | `:core:data` or `:app` | P1 |
| `getFavoriteNotebooks()` DAO query | `:core:data/db/dao/NotebookDao` | P1 — currently filtered in-memory |

---

## 4. Key Design Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Navigation | String routes with args | Matches existing pattern; type-safe nav deferred |
| Start destination | Change to `"home"` | Users should see notebooks first, not empty canvas |
| Section management | New SectionRepository + use cases | SectionDao exists but has no domain/use-case layer |
| Default notebook contents | Auto-create 1 section + 1 page | Immediate drawing experience after creation |
| Home grid | Adaptive columns via WindowSizeClass | Tablet-first app needs responsive layout |
| Detail layout | Tabs (sections) + grid (pages) | Matches GoodNotes/Noteshelf UX patterns |
| Tagging | Defer to Phase 5 | DB schema ready, but UI scope is too large for Phase 3 |
| Settings storage | Jetpack DataStore (Preferences) | Lightweight, sufficient for simple key-value prefs |
| Page thumbnails | Placeholder color + page number initially | ThumbnailManager stub exists; real rendering deferred |
| Confirm dialogs | Shared `ConfirmDialog` from :core:ui | Reusable for delete notebook/section/page |

---

*Next step: Write detailed [plan.md — Phase 3](./plan.md) implementation plan, then break into [TODO.md](./TODO.md) tasks.*
