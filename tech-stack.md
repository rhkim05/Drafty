# Tech Stack Options

This document presents technology options for each major decision area. Each section lists the candidates, their trade-offs, and maturity level. No decisions are final — pick the option that best fits your priorities and I'll incorporate your choices.

---

## 1. Rendering Engine (Android)

The rendering layer is the most critical decision. It determines ink latency, the defining feature of Drafty.

| Option | Latency | Maturity | Dev Effort | Notes |
|--------|---------|----------|------------|-------|
| **A. androidx.ink** | ~4ms (Samsung Tab S8) | Alpha (v1.1.0-alpha01, Oct 2024) | Low–Medium | Purpose-built by Google for stylus apps |
| **B. Canvas + androidx.graphics** | ~16–32ms | Stable | Medium | Familiar Canvas API with front-buffered layer |
| **C. Custom OpenGL/Vulkan** | <4ms (theoretical) | N/A (custom) | Very High | Maximum control, requires graphics expertise |

### A. androidx.ink (Jetpack Ink API)

Google's dedicated stylus library, introduced October 2024. Modular architecture with separate concerns: strokes, geometry (erasers/selection), brushes, authoring (real-time capture via `InProgressStrokesView`), rendering (`CanvasStrokeRenderer` for Compose), and storage.

**Pros:**
- Built-in motion prediction (Kalman filter) — reduces perceived latency beyond raw frame time
- Modular: adopt only the pieces you need
- Handles batched `MotionEvent` processing, pressure/tilt, palm rejection automatically
- Works with both Compose and Views
- Active development by Android team

**Cons:**
- Alpha status — API surface may change before stable
- Limited third-party docs/examples (it's new)
- Android-only — iOS requires a separate renderer consuming the same shared stroke model
- Optimal on API 29+; requires API 21+

### B. Canvas + androidx.graphics (Front-Buffered Rendering)

`LowLatencyCanvasView` and `CanvasFrontBufferedRenderer` wrap SurfaceView complexity. You draw with familiar `Canvas + Paint` APIs, and the library handles front-buffer/back-buffer management.

**Pros:**
- Stable, production-ready API
- Uses standard Canvas drawing — lower learning curve
- Can be combined with androidx.ink modules (e.g. use ink's stroke model but render with Canvas)

**Cons:**
- No built-in motion prediction (add via `androidx.input` separately)
- Manual stroke management — you build the stroke pipeline yourself
- Higher latency ceiling than ink API alone

### C. Custom OpenGL/Vulkan

Full control over the GPU pipeline. Android is transitioning to Vulkan as primary; OpenGL ES via ANGLE for legacy.

**Pros:**
- Absolute lowest theoretical latency
- Full control over custom brush effects, blending modes
- Future-proof with Vulkan

**Cons:**
- Massive development effort — requires dedicated graphics expertise
- Motion prediction, stroke management, palm rejection all manual
- Overkill for a note-taking app unless targeting professional illustration features
- Most rendering code is still Android-specific (no KMP benefit)

### Recommendation leaning

Option A is the natural fit for Drafty's "sub-frame latency" goal. The alpha status is a risk, but Google is actively investing in it and the modular design means you can swap pieces if needed. Option B is the safe fallback if ink API proves too unstable. Option C is only worth considering if you plan features that exceed what ink API can render.

---

## 2. iOS Rendering (Future Port)

This decision can be deferred, but the shared stroke model should be designed with both platforms in mind.

| Option | Latency | Customization | Dev Effort |
|--------|---------|--------------|------------|
| **A. PencilKit** | 7–20ms (9ms on ProMotion 120Hz) | Limited | Low |
| **B. Custom Metal renderer** | 5–15ms | Full | Very High |

### A. PencilKit

Apple's built-in drawing framework. Handles Apple Pencil input, pressure, tilt, and rendering automatically.

**Pros:**
- Zero-config low latency on modern iPads
- Native Apple Pencil feel — users expect this
- Minimal code to integrate
- Automatic palm rejection

**Cons:**
- Limited customization of brush rendering
- Can't access underlying rendering layer
- Third-party apps report ~5–10ms lag vs native Notes app
- Harder to match exact stroke appearance across platforms

### B. Custom Metal + Core Graphics

Build your own renderer on Apple's Metal GPU API.

**Pros:**
- Full control over rendering — match Android's exact brush behavior
- Fine-grained performance tuning
- Unique visual effects possible

**Cons:**
- Major engineering investment requiring Metal expertise
- Higher risk of latency issues if not done carefully
- Much more code to maintain on iOS side

### KMP integration pattern (both options)

```
commonMain/
  Stroke.kt              // Shared data model (points, pressure, tilt, brush)
  StrokeRepository.kt    // Persistence interface
  UndoRedoManager.kt     // Shared undo/redo logic

androidMain/
  InkStrokeAdapter.kt    // androidx.ink ↔ shared Stroke conversion
  InkCanvasRenderer.kt   // Renders via CanvasStrokeRenderer

iosMain/
  PKDrawingAdapter.kt    // PencilKit ↔ shared Stroke conversion
  PencilKitCanvas.kt     // SwiftUI/UIKit wrapper
```

The shared `Stroke` data class owns the canonical representation. Each platform converts to/from its native stroke format for rendering and input capture.

---

## 3. App Framework

How you build the UI layer (toolbars, library screen, settings) — separate from the canvas rendering.

| Option | Code Sharing | Native Feel | Dev Effort | iOS Readiness |
|--------|-------------|-------------|------------|---------------|
| **A. Compose Multiplatform** | ~90–96% | Good (Skiko on iOS) | Lowest | Stable since May 2025 |
| **B. Compose (Android) + SwiftUI (iOS)** | ~50–60% (logic only) | Best | Medium–High | Each platform native |
| **C. Fully native per platform** | ~30–40% (models only) | Best | Highest | Each platform native |

### A. Compose Multiplatform

Single Compose codebase for both platforms. iOS rendered via Skiko (Metal-backed). Full two-way interop with SwiftUI for embedding native views (e.g. PencilKit canvas).

**Pros:**
- Maximum code reuse — UI logic, screens, navigation all shared
- iOS stable since Compose Multiplatform 1.8.0 (May 2025)
- Single team can build both platforms
- Hot reload support
- Can embed native views where needed (canvas rendering)

**Cons:**
- Canvas-heavy custom drawing *may* have overhead on iOS via Skiko (but the actual drawing canvas will be native anyway)
- Smaller community than native SwiftUI
- iOS rendering not pixel-identical to SwiftUI

### B. Compose (Android) + SwiftUI (iOS) with KMP shared logic

Each platform uses its native UI framework. KMP shares business logic, data models, ViewModels, and persistence.

**Pros:**
- Best native feel on each platform
- Full access to latest platform UI features immediately
- Easier to hire platform-specific developers

**Cons:**
- ~40–50% more UI code to write and maintain
- Harder to keep visual consistency between platforms
- Requires expertise in both Compose and SwiftUI

### C. Fully native (View system + UIKit) with KMP shared logic

Traditional native development. KMP only shares non-UI code.

**Pros:**
- Maximum platform optimization
- Most familiar to native developers

**Cons:**
- Maximum code duplication
- Highest maintenance burden
- Hard to justify for a new project in 2025+

### Key consideration

The canvas itself will be a native view on both platforms regardless of choice (it must use platform rendering APIs for latency). The framework decision only affects the *surrounding UI*: library screen, toolbar, settings, dialogs, navigation. For these, Compose Multiplatform is the pragmatic choice unless you have strong reasons to go fully native.

---

## 4. State Management

How you manage app state, particularly undo/redo and canvas state.

| Option | Dependencies | Complexity | Debugging | KMP Support |
|--------|-------------|-----------|-----------|-------------|
| **A. StateFlow + Command pattern** | None (stdlib) | Low | Manual | Built-in |
| **B. Decompose + MVIKotlin** | 2 libraries | Medium–High | Time-travel built-in | Full |
| **C. Orbit MVI** | 1 library | Medium | Decent | Full |

### A. StateFlow/SharedFlow (built-in Kotlin)

MVVM pattern using Kotlin's built-in reactive state. Undo/redo via a manual command stack.

**Pros:**
- Zero external dependencies
- Native coroutines integration
- Lightweight, easy to understand
- Simple testing
- Most KMP projects use this approach

**Cons:**
- No enforced unidirectional data flow — requires discipline
- No built-in time-travel debugging
- Manual undo/redo implementation (Command or Memento pattern)

**Undo/redo pattern:**
```kotlin
// Command pattern — each operation is reversible
interface DrawCommand {
    fun execute(canvas: CanvasState)
    fun undo(canvas: CanvasState)
}

class AddStrokeCommand(val stroke: Stroke) : DrawCommand { ... }
class EraseStrokeCommand(val strokeId: StrokeId) : DrawCommand { ... }

// History managed via simple stack
class UndoRedoStack {
    private val undoStack = ArrayDeque<DrawCommand>()
    private val redoStack = ArrayDeque<DrawCommand>()
}
```

### B. Decompose + MVIKotlin

Strict MVI (Model-View-Intent) with lifecycle-aware components. By Arkadii Ivanov, well-known in KMP community.

**Pros:**
- Enforces unidirectional data flow
- Built-in time-travel debugging — invaluable for debugging gesture/stroke issues
- Strong architectural guardrails
- Handles navigation and component lifecycle

**Cons:**
- Steeper learning curve
- More boilerplate than plain StateFlow
- Two library dependencies to learn and maintain
- May be overengineered for a single-purpose app

### C. Orbit MVI

Lighter-weight MVI that bridges MVVM and MVI. Less boilerplate than MVIKotlin.

**Pros:**
- Less boilerplate than MVIKotlin
- Type-safe state management
- Good documentation

**Cons:**
- Younger library, smaller community
- Less battle-tested than options A or B
- Fewer advanced features

### Recommendation leaning

For a focused app like Drafty, Option A (StateFlow + Command pattern) is likely sufficient. The undo/redo logic is well-served by the Command pattern, and you avoid framework overhead. Option B is worth it if you value time-travel debugging for stroke replay/debugging, but adds complexity.

---

## 5. Data Storage

How you persist canvases, strokes, folders, and metadata.

| Option | KMP Support | Query Capability | Maturity |
|--------|------------|-----------------|----------|
| **A. SQLDelight** | Native (KMP-first) | Full SQL | High |
| **B. Room 2.7+** | Recent (May 2024) | Full SQL (DAO) | Medium for KMP |
| **C. File-based + index** | N/A | Manual | High |

### A. SQLDelight

KMP-first database library. Generates type-safe Kotlin from SQL schema. Stores structured metadata in tables, stroke data as BLOBs.

**Pros:**
- Designed for KMP from the ground up
- Type-safe generated APIs from `.sq` files
- Excellent performance
- Supports binary BLOBs for serialized stroke data
- Well-suited for metadata queries (sort by date, search by title)
- Mature, actively maintained

**Cons:**
- SQL-first approach (you write raw SQL, not ORM)
- Schema migrations are manual
- Less familiar to developers who only know Room

**Storage pattern:**
```sql
CREATE TABLE canvas (
  id TEXT PRIMARY KEY,
  folder_id TEXT REFERENCES folder(id),
  title TEXT NOT NULL,
  template TEXT NOT NULL DEFAULT 'blank',
  created INTEGER NOT NULL,
  modified INTEGER NOT NULL,
  thumbnail BLOB
);

CREATE TABLE stroke (
  id TEXT PRIMARY KEY,
  canvas_id TEXT NOT NULL REFERENCES canvas(id),
  sort_order INTEGER NOT NULL,
  data BLOB NOT NULL  -- serialized stroke points
);
```

### B. Room 2.7+ (KMP)

Google's ORM for Android, recently gained KMP support. DAO-based access pattern.

**Pros:**
- Familiar to Android developers
- Annotation-based, less raw SQL
- Google-backed
- Coroutines integration

**Cons:**
- KMP support is newer and less battle-tested than SQLDelight
- Heavier weight than SQLDelight
- Android-centric ergonomics may feel awkward on iOS
- Less mature for multiplatform use

### C. File-based storage

Each canvas stored as a file (e.g. `.drafty` format) with an in-memory or lightweight index.

**Pros:**
- Simple conceptually
- Files are easy to export, backup, share
- Compatible with file-based sync (Google Drive, iCloud)

**Cons:**
- No efficient querying without building your own index
- Harder to do partial loading (must read entire file)
- More complex sync conflict resolution
- Less structured than a database

### Recommendation leaning

SQLDelight (Option A) is the standard choice for KMP projects and handles the metadata+blob pattern well. Room is viable if you strongly prefer its ORM style, but SQLDelight has more KMP mileage.

---

## 6. Stroke Serialization

How you encode stroke point data (x, y, pressure, tilt, timestamp per sample) into the BLOBs stored in the database.

| Option | Parse Speed | Size | Schema Evolution | Complexity |
|--------|------------|------|-----------------|-----------|
| **A. Protocol Buffers** | Fast | Small | Built-in versioning | Low–Medium |
| **B. FlatBuffers** | Zero-copy (fastest) | Small–Medium | Built-in versioning | Medium |
| **C. Custom binary** | Fastest (tuned) | Smallest | Manual | High |

### A. Protocol Buffers (Protobuf)

Google's serialization format. Define a `.proto` schema, generate Kotlin code.

**Pros:**
- Well-documented, massive ecosystem
- Built-in schema evolution (add fields without breaking old data)
- Cross-language (useful if you ever need server-side processing)
- Compact binary encoding
- KMP support via `pbandk` or `wire` libraries

**Cons:**
- Requires a parse/unpack step before accessing data (allocates objects)
- Code generation adds build complexity
- Slightly larger than hand-tuned binary

### B. FlatBuffers

Google's zero-copy serialization. Access serialized data directly without parsing.

**Pros:**
- No deserialization step — read directly from the buffer
- No memory allocation during "parsing"
- Ideal for large stroke data that you may only partially read
- Schema evolution support
- Used in games and performance-critical apps

**Cons:**
- Less intuitive API than Protobuf
- KMP support less mature than Protobuf
- Slightly larger serialized size than Protobuf
- Builder pattern for writing is more verbose

### C. Custom binary format

Hand-roll a compact binary encoding tuned exactly to your stroke data.

**Pros:**
- Absolute smallest file size
- No external dependencies
- Complete control over layout — can optimize for partial reads
- Fastest possible write path (no framework overhead)

**Cons:**
- Must handle versioning yourself (what happens when you add tilt data later?)
- No tooling for debugging/inspecting
- Higher maintenance burden
- Bug-prone — binary format bugs are hard to diagnose

### Recommendation leaning

Protobuf (Option A) is the safe, pragmatic choice — mature tooling, good KMP support, handles schema evolution. FlatBuffers (Option B) is worth it if profiling shows deserialization is a bottleneck (thousands of strokes on canvas open). Custom binary (Option C) is only justified if you need absolute minimal size and have benchmarked the others as insufficient.

---

## 7. Cloud Sync

Premium feature for cross-device sync. Offline-first is a hard requirement.

| Option | KMP Support | Offline-First | Effort | Cost |
|--------|------------|--------------|--------|------|
| **A. Supabase** | Official (`supabase-kt`) | With local cache | Medium | Low (generous free tier) |
| **B. Firebase** | No official KMP SDK | Limited | Medium–High | Low (free tier) |
| **C. Simple file sync (Drive/iCloud)** | Platform APIs | Natural | Low | Free |
| **D. Custom backend + CRDTs** | Full control | Best | Very High | Variable |

### A. Supabase

Open-source Firebase alternative with PostgreSQL backend. Official Kotlin Multiplatform client (`supabase-kt`) with modules for auth, database, storage, and realtime.

**Pros:**
- Official KMP support — best-in-class for multiplatform
- PostgreSQL backend (powerful, open-source)
- Auth, storage, realtime modules available
- Self-hostable if needed
- Growing rapidly in KMP community

**Cons:**
- Younger than Firebase — less battle-tested at very large scale
- Offline-first requires client-side caching strategy (not built-in)
- PostgreSQL semantics (not a document store)

### B. Firebase (Firestore + Cloud Storage)

Google's BaaS platform. Mature but no official KMP SDK.

**Pros:**
- Extremely mature and battle-tested
- Excellent documentation
- Rich feature set (auth, analytics, crashlytics, etc.)

**Cons:**
- **No official Kotlin Multiplatform SDK** — major blocker
- Unofficial wrappers are incomplete
- Google ecosystem lock-in
- Many KMP developers actively moving away from Firebase for this reason

### C. Simple file sync (Google Drive API / iCloud)

Sync `.drafty` files through the user's existing cloud storage.

**Pros:**
- User owns their data in their own account
- Minimal backend infrastructure (no server to run)
- Privacy-friendly
- Free for the developer
- Natural platform integration (Drive on Android, iCloud on iOS)

**Cons:**
- No real-time sync — eventual consistency only
- Conflict resolution is primitive (last-write-wins or manual)
- Harder to build features like sync status indicators
- Platform-specific APIs (no unified KMP approach)

### D. Custom backend with CRDTs

Build your own sync server. Use CRDTs (Conflict-free Replicated Data Types) for automatic conflict resolution.

**Pros:**
- Best offline-first experience — conflicts resolve automatically
- Full control over sync logic
- Can add end-to-end encryption
- No vendor lock-in

**Cons:**
- Very high engineering effort
- Must build and operate a server
- CRDTs are well-proven for text but less common for stroke data
- Metadata overhead increases storage size
- Overkill unless collaboration or complex merging is needed

### Key consideration

Since Drafty is single-user (no collaboration) and cloud sync is premium, the sync problem is simpler than it first appears. You're syncing *canvases* between a user's own devices — not merging concurrent edits. This makes Option A (Supabase) or Option C (file sync) the practical choices. CRDTs and complex conflict resolution are unnecessary without real-time collaboration.

---

## Decision Summary

Copy this table, fill in your picks, and I'll incorporate them:

| Area | Pick | Notes |
|------|------|-------|
| Rendering (Android) | A | |
| Rendering (iOS) | A |
| App Framework | A | |
| State Management | A | |
| Data Storage | A | |
| Stroke Serialization | A | protobuf via wire | 
| Cloud Sync | C | **v1: Manual export/import.** Share a `.drafty` file via Android share sheet, user saves it wherever they want (Drive, email, files app). Zero sync infrastructure, still lets users back up their data. |



