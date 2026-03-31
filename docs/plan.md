# Plan: Drafty — Android Tablet Note-Taking App

> References: [idea.md](./idea.md) | [tech-stack.md](./tech-stack.md) | [architecture.md](./architecture.md) | [project-structure.md](./project-structure.md)

---

## Development Phases Overview

| Phase | Focus | Est. Duration |
|-------|-------|---------------|
| Phase 1 | Project setup & ink engine | 3-4 weeks |
| Phase 2 | Canvas & page management | 2-3 weeks |
| Phase 3 | Notebook organization & UI | 2-3 weeks |
| Phase 4 | PDF import & annotation | 2-3 weeks |
| Phase 5 | Export, search & polish | 2 weeks |
| Phase 6 | Testing, optimization & release prep | 2 weeks |

**Total estimated: ~14-18 weeks for MVP**

---

## Phase 1: Project Setup & Ink Engine

### Goal
Set up the project scaffold and build the core ink rendering engine — the heart of the app.

### Features
1. **Project initialization**
   - Android project with Kotlin, Jetpack Compose, Gradle KTS
   - Module structure: `:app`, `:core:ink-engine`, `:core:data`, `:core:ui`, `:feature:canvas`, `:feature:notebooks`
   - CI/CD setup (GitHub Actions for build + lint)
   - Dependency injection (Hilt)

2. **Ink rendering engine** (`:core:ink-engine`)
   - Custom `SurfaceView` or `TextureView` for high-performance rendering
   - Stroke capture from stylus input (`MotionEvent` processing)
   - Pressure sensitivity mapping (stroke width varies with pressure)
   - Tilt sensitivity (optional — pen angle affects stroke)
   - Bézier curve smoothing for natural-looking strokes
   - GPU-accelerated stroke rendering using OpenGL ES 3.0 or Android Canvas with hardware acceleration
   - Stroke data model: `Stroke(id, points: List<InkPoint>, color, thickness, tool, timestamp)`
   - `InkPoint(x, y, pressure, tilt, timestamp)`

3. **Basic tools**
   - Pen tool (variable width based on pressure)
   - Highlighter tool (semi-transparent, wider stroke)
   - Eraser tool (stroke eraser — removes whole strokes on touch; partial eraser in post-MVP)
   - Color picker (preset palette + custom color)
   - Stroke thickness selector

4. **Palm rejection**
   - Detect stylus vs. finger vs. palm via `MotionEvent.getToolType()`
   - When stylus is active, ignore finger/palm touches on canvas
   - Option to use finger for pan/zoom while stylus draws

### Implementation Approach
- Start with Android `Canvas` API on a custom `View`/`SurfaceView` for initial prototyping
- Profile performance; if Canvas API can't maintain 60fps with 500+ strokes, migrate to OpenGL ES
- Stroke smoothing: Catmull-Rom spline or cubic Bézier interpolation between raw input points
- Store strokes as serializable data objects (not just bitmaps) for undo/redo, selection, and efficient storage

### Key Decisions
- **Rendering**: Start with hardware-accelerated `Canvas` on `SurfaceView`, upgrade to OpenGL ES if needed
- **Input pipeline**: Process `MotionEvent` batched historical events for capturing all stylus samples between frames

---

## Phase 2: Canvas & Page Management

### Goal
Build the canvas system that supports both paginated (notebook) and infinite (whiteboard) modes.

### Features
1. **Paginated canvas mode**
   - Fixed page dimensions (e.g., A4/Letter ratio)
   - Paper background templates: blank, lined, grid, dotted, Cornell notes
   - Page navigation: swipe between pages, page thumbnails sidebar
   - Add/delete/reorder pages within a section

2. **Infinite whiteboard mode**
   - Unbounded canvas with pan and zoom
   - Minimap for navigation (optional, post-MVP)
   - Coordinate system that handles large canvas areas efficiently

3. **Canvas interaction**
   - Pinch-to-zoom (two-finger gesture)
   - Two-finger pan/scroll
   - Smooth zoom with stroke re-rendering at appropriate detail level
   - Double-tap to reset zoom

4. **Undo/Redo system**
   - Command pattern: each action (add stroke, delete stroke, move stroke, etc.) is a reversible command
   - Undo stack with configurable depth (default: 50 actions)
   - Undo/redo buttons in toolbar

5. **Selection tool (Lasso)**
   - Draw freeform lasso to select strokes
   - Selected strokes can be: moved, resized, copied, deleted, recolored
   - Cut/copy/paste strokes between pages

### Implementation Approach
- Canvas state managed by a `CanvasViewModel` holding current page data, zoom level, offset, and active tool
- Page backgrounds rendered as tiled textures for efficient scrolling
- Strokes organized in a spatial index (R-tree or grid-based) for efficient hit-testing and partial rendering
- Viewport culling: only render strokes visible in the current viewport

---

## Phase 3: Home Screen & Canvas Navigation

> Research: [research-phase3.md](./research-phase3.md) | Tasks: [TODO.md](./TODO.md)
>
> **Design change (2026-03-30):** Removed notebook/section hierarchy from UX.
> The app uses a flat "notes" structure: Home grid of notes → tap "+" → instantly create and open a blank canvas.
> The underlying data model (Notebook → Section → Page) still exists for future extensibility, but the user never sees sections or notebook detail screens.

### Goal
Build the home screen, settings screen, and simplified navigation so users can create, manage, and open canvas notes from a flat grid.

### Actual Architecture (Post-Simplification)

**Navigation graph (3 routes):**
- `home` → `canvas/{notebookId}` (direct, no detail screen)
- `home` → `settings`
- Back from canvas/settings via `popBackStack()`

**Flow for creating a new note:**
1. User taps FAB on home screen
2. `HomeViewModel.createAndOpenCanvas()` creates a Notebook ("Untitled", random cover color) + auto-creates Section + Page via `CreateNotebookUseCase`
3. Emits notebook ID via `SharedFlow` → navigates to `canvas/{notebookId}`
4. `CanvasViewModel` reads `notebookId` from `SavedStateHandle`, calls `loadNotebook()` which finds the first section and loads its first page

**Home screen features:**
- 3-column grid of `NotebookCard` items
- Search bar (live filter by title)
- Sort menu (Recent / Name / Created)
- Long-press context menu: Favorite, Rename, Duplicate, Delete
- Settings icon → SettingsScreen

### Sub-phases (as implemented)

#### 3A–3B. Infrastructure & UI Components
- ✅ SectionRepository + use cases + Hilt bindings
- ✅ CreateNotebookUseCase auto-creates Section + Page
- ✅ NotebookCard, PageThumbnail, ConfirmDialog composables

#### 3C. Home Screen (Flat Canvas Grid)
- ✅ HomeViewModel with reactive state, createAndOpenCanvas(), search, sort, CRUD
- ✅ HomeScreen with 3-column grid, FAB, search bar, sort menu, rename/delete dialogs

#### 3D. Notebook Detail Screen (Removed)
- ✅ Implemented but removed from nav graph — code exists for future use if hierarchy is desired

#### 3E. Navigation Graph (Simplified)
- ✅ 3 routes: home, canvas/{notebookId}, settings
- ✅ CanvasViewModel resolves notebookId → first section → first page automatically
- ✅ Floating back button on canvas screen

#### 3F. Settings Screen
- ✅ UserPreferencesRepository (DataStore), SettingsScreen, SettingsViewModel

### Implementation Approach
- Jetpack Compose for all UI (Material 3 components)
- Navigation via Jetpack Navigation Compose (string routes with arguments)
- 3-column grid on tablet (hardcoded for now, adaptive columns deferred)
- Notebooks/sections/pages stored in Room database, stroke data in local protobuf files
- Settings via Jetpack DataStore (Preferences)
- Canvas background: gray (#E0E0E0) to distinguish from white page

---

## Phase 4: PDF Import & Annotation

### Goal
Enable importing PDFs and annotating them with the ink engine.

### Features
1. **PDF import**
   - Import PDF from device storage (file picker)
   - Render PDF pages as background images on canvas pages
   - Each PDF page becomes a canvas page with the PDF content as a non-editable background layer
   - Support multi-page PDFs (create one canvas page per PDF page)

2. **PDF annotation**
   - Draw/highlight on top of PDF pages using the same ink tools
   - Annotation layer is separate from PDF content (can toggle visibility, clear annotations)
   - Text highlight tool (select region, apply color highlight — simplified for MVP)

3. **PDF navigation**
   - Page-by-page navigation with thumbnails
   - Pinch-to-zoom on PDF pages
   - Smooth scrolling between pages

4. **PDF export**
   - Export annotated PDF (flatten annotations onto PDF pages)
   - Export as new PDF (original + annotations merged)
   - Share annotated PDF via Android share sheet

### Implementation Approach
- Use Android's `PdfRenderer` API for rendering PDF pages to `Bitmap`
- For more advanced PDF features (text extraction, annotation flattening), use a library like **PDFium** (via pdfium-android) or **Apache PDFBox**
- PDF pages rendered as background layers; ink strokes drawn on a transparent overlay
- Export: render strokes onto PDF page bitmaps, then write to new PDF using iText or PDFBox

### Key Decisions
- **PDF library**: `PdfRenderer` (lightweight, built-in) for rendering + PDFium or iText for export with annotations
- **Performance**: Cache rendered PDF page bitmaps, render at appropriate resolution for current zoom level

---

## Phase 5: Export, Search & Polish

### Goal
Add export capabilities, improve search, and polish the overall UX.

### Features
1. **Export**
   - Export individual pages as PNG/JPEG images
   - Export notebooks/sections as multi-page PDF
   - Share via Android share sheet (images, PDFs)

2. **Search improvements**
   - Full-text search across notebook/section/page names
   - Recent searches
   - Search results with page thumbnails

3. **UX Polish**
   - Smooth animations and transitions
   - Loading states and progress indicators
   - Empty states (no notebooks yet, no pages, etc.)
   - Onboarding flow (first launch — quick tutorial)
   - Haptic feedback for tool selection

4. **Settings & preferences**
   - Default pen color, thickness
   - Palm rejection sensitivity
   - Auto-save interval
   - Storage management (see storage used, clear cache)

### Implementation Approach
- Export pipeline: render strokes to bitmap → encode to PNG/PDF
- Multi-page PDF export using a PDF generation library
- Preferences stored in DataStore (Jetpack)

---

## Phase 6: Testing, Optimization & Release Prep

### Goal
Ensure stability, performance, and readiness for release.

### Tasks
1. **Testing**
   - Unit tests for data layer (Room, repositories)
   - Unit tests for ink engine math (Bézier curves, hit testing)
   - Integration tests for ViewModel logic
   - UI tests for critical flows (create notebook, draw, save, export)
   - Manual testing on target devices (Samsung Galaxy Tab S series, Pixel Tablet)

2. **Performance optimization**
   - Profile rendering pipeline — ensure <20ms ink latency
   - Optimize stroke storage and loading for large notebooks
   - Memory management: handle large PDFs without OOM
   - Battery usage profiling

3. **Release preparation**
   - App icon and branding
   - Play Store listing (screenshots, description)
   - ProGuard/R8 configuration
   - Crash reporting (Firebase Crashlytics)
   - Analytics (optional, privacy-respecting)
   - Version 1.0.0 tagging

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Ink latency too high with Canvas API | High | Profile early; have OpenGL ES fallback ready |
| PDF rendering performance on large documents | Medium | Lazy page loading, bitmap caching, resolution scaling |
| Stroke data grows large for heavy users | Medium | Efficient binary format, page-level lazy loading |
| Cross-platform (iOS) portability | Low (future) | Use KMP-friendly architecture from start; keep platform-specific rendering isolated |
| Stylus compatibility across devices | Medium | Test on multiple devices; use standard Android stylus APIs |

---

*Next step: Define technology stack → see [tech-stack.md](./tech-stack.md)*
