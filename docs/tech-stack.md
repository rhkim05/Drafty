# Tech Stack: Drafty

> References: [idea.md](./idea.md) | [plan.md](./plan.md) | [architecture.md](./architecture.md) | [project-structure.md](./project-structure.md)

---

## Language & Platform

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Language** | Kotlin | Modern Android standard; coroutines for async; KMP-ready for future iOS port |
| **Min SDK** | API 26 (Android 8.0) | Covers ~95% of active devices; required for some Canvas/PDF APIs |
| **Target SDK** | API 35 (Android 15) | Latest platform features and Play Store compliance |
| **Build system** | Gradle KTS | Type-safe build scripts; standard for Android projects |

---

## Core Framework & UI

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **UI toolkit** | Jetpack Compose (Material 3) | Modern declarative UI; excellent for tablet adaptive layouts |
| **Canvas rendering** | Custom `SurfaceView` + `android.graphics.Canvas` (hardware-accelerated) | Low-latency drawing; direct control over rendering pipeline |
| **Fallback renderer** | OpenGL ES 3.0 via `GLSurfaceView` | If Canvas API can't hit 60fps with dense strokes |
| **Navigation** | Jetpack Navigation Compose | Standard for Compose apps; type-safe routes |
| **Adaptive layout** | `WindowSizeClass` + Material 3 adaptive components | Tablet-first responsive design |

---

## Ink Engine (`:core:ink-engine`)

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Input processing** | `MotionEvent` with batched historical events | Captures all stylus samples between frames for smooth curves |
| **Curve smoothing** | Catmull-Rom splines → cubic Bézier conversion | Natural-looking strokes; industry-standard approach |
| **Stroke rendering** | `Path` + `Paint` with variable-width `PathEffect` | Pressure-sensitive stroke width; GPU-accelerated via hw Canvas |
| **Hit testing** | R-tree spatial index (custom or via a lightweight library) | Efficient stroke selection, eraser detection |
| **Serialization** | Protocol Buffers (protobuf) | Compact binary format; fast serialization; cross-platform compatible |

---

## Data & Storage

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Database** | Room (SQLite) | Metadata storage: notebooks, sections, pages, tags |
| **Stroke storage** | Protobuf files on local filesystem | Binary stroke data per page; efficient read/write; not suited for SQL |
| **Preferences** | Jetpack DataStore (Preferences) | Modern replacement for SharedPreferences; coroutine-based |
| **File management** | Android `Context.filesDir` + scoped storage | Internal app storage for notebooks; scoped storage for PDF import/export |
| **Image cache** | Coil 3 | Lightweight, Compose-native image loading for thumbnails, PDF page cache |

### Data Schema Overview

```
Room Database (drafty.db):
├── notebooks (id, title, cover_color, created_at, modified_at, is_favorite, sort_order)
├── sections (id, notebook_id, title, sort_order)
├── pages (id, section_id, type[NOTEBOOK|WHITEBOARD|PDF], template, width, height, sort_order)
├── tags (id, name, color)
└── notebook_tags (notebook_id, tag_id)

File System (app internal storage):
├── strokes/
│   └── {page_id}.pb          ← protobuf stroke data per page
├── thumbnails/
│   └── {page_id}.webp        ← page thumbnail cache
└── pdfs/
    └── {notebook_id}/
        └── source.pdf         ← imported PDF source file
```

---

## PDF Handling

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **PDF rendering** | Android `PdfRenderer` API | Built-in, lightweight; renders pages to Bitmap |
| **PDF export (annotated)** | iText 7 (Community Edition, AGPL) or Apache PDFBox Android port | Flatten ink annotations onto PDF pages for export |
| **PDF page caching** | LRU `Bitmap` cache | Avoid re-rendering PDF pages during navigation |

**Note**: If iText's AGPL license is a concern for distribution, we can evaluate **PDFium via pdfium-android** (Apache 2.0) for both rendering and export.

---

## Architecture & DI

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Architecture pattern** | MVVM + Clean Architecture (Use Cases) | Separation of concerns; testable; industry standard |
| **Dependency injection** | Hilt (Dagger) | Compile-time DI; first-class Android support; less boilerplate than manual DI |
| **Async/concurrency** | Kotlin Coroutines + Flow | Reactive streams for UI state; structured concurrency |
| **State management** | Compose State + `StateFlow` in ViewModels | Unidirectional data flow; predictable state updates |

---

## Testing

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Unit testing** | JUnit 5 + Turbine (for Flow) | Standard; Turbine simplifies Flow testing |
| **Mocking** | MockK | Kotlin-native mocking; better than Mockito for Kotlin |
| **UI testing** | Compose UI Test (`createComposeRule`) | Native Compose testing framework |
| **Integration testing** | Room in-memory database tests | Verify data layer without real SQLite |
| **Performance testing** | Android Benchmark (Macrobenchmark) | Measure ink latency, startup time, scroll performance |

---

## CI/CD & Quality

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **CI** | GitHub Actions | Free for open source; good Android support |
| **Linting** | Detekt + Android Lint | Kotlin-specific static analysis + Android-specific checks |
| **Code formatting** | ktfmt (Google style) | Consistent formatting; integrates with CI |
| **Crash reporting** | Firebase Crashlytics | Industry standard; free tier sufficient |
| **Analytics** | None in MVP | Privacy-first; add opt-in analytics later if needed |

---

## Future Cloud Sync Stack (Post-MVP)

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Backend** | Supabase (PostgreSQL + Auth + Storage) | Open-source Firebase alternative; easier self-hosting option |
| **Auth** | Supabase Auth (Google, email/password) | Integrated with storage; simple OAuth flows |
| **File sync** | Supabase Storage + custom sync logic | Store stroke files and PDFs; delta sync for efficiency |
| **Offline-first sync** | Custom CRDT or last-write-wins per page | Keep it simple for MVP+1; page-level conflict resolution |

---

## Cross-Platform Strategy (Future iOS Port)

| Layer | Approach |
|-------|----------|
| **Shared business logic** | Kotlin Multiplatform (KMP) — data models, repositories, use cases |
| **Shared stroke data** | Protobuf (cross-platform serialization) |
| **Platform-specific rendering** | Android: custom Canvas/OpenGL; iOS: Metal/Core Graphics |
| **Platform-specific UI** | Android: Jetpack Compose; iOS: SwiftUI |

The architecture is designed so that `:core:data` and `:core:domain` modules can become KMP modules, while `:core:ink-engine` and UI remain platform-specific.

---

*Next step: Design the architecture → see [architecture.md](./architecture.md)*
