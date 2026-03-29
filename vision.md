# Drafty — Product Vision

## Product Overview

Drafty is a handwriting and note-taking app for tablets built on one principle: **writing should feel instant**. While competitors have evolved into feature-heavy platforms with AI assistants, template marketplaces, and collaboration suites, Drafty stays focused on the core experience — pick up your stylus and write.

The app targets Android tablets first, with a native iOS port planned after the initial release. Both platforms will deliver the same smooth, sub-frame-latency writing experience. Drafty works offline-first with cloud sync available as a premium feature, ensuring your notes are always accessible even without a connection.

### Design Philosophy

1. **Speed-first** — Sub-frame ink latency using front-buffered rendering. Performance is the defining feature.
2. **Minimal UI** — Every pixel of toolbar chrome is canvas space lost. Show only what's needed, when it's needed.
3. **Distraction-free** — No interrupting dialogs, onboarding flows, feature tips, or upsells. Functional UI like tool option bars and settings are user-initiated and unobtrusive. Open the app, pick a canvas, start writing.
4. **Essential tools only** — A curated toolset covering 95% of use cases. No bloated tool drawers.
5. **Clean organization** — Folders and canvases in a simple hierarchy. No tags, smart folders, or search-driven filing.

---

## Target Users

### Primary Audience

- **Writers who want speed and simplicity** — People who use a tablet as a digital notebook and want the fastest path from "open app" to "writing." Students taking lecture notes, professionals sketching ideas in meetings, anyone who reaches for a stylus before a keyboard.
- **Users frustrated by bloat** — People who have tried Goodnotes, Noteshelf, or similar apps and found them overloaded with features they never use. They want fewer choices, not more.
- **Android tablet users** — The Android tablet ecosystem has fewer polished handwriting apps than iPad. Drafty fills that gap with a native, performance-first experience.

### Who Drafty Is Not For

- **Teams needing real-time collaboration** — Drafty is a personal tool with no shared editing.
- **Users who want AI-powered features** — No transcription, summarization, or smart search. Drafty is a notebook, not an assistant.
- **Heavy template/sticker customizers** — Four built-in templates. No marketplace, no sticker packs.
---

## Core Features (v1)

### Drawing Tools

| Tool | Purpose | Options |
|------|---------|---------|
| **Pen** | Pressure-sensitive ballpoint pen. Primary writing instrument with variable width based on pressure and velocity. | 3 preset size buttons (S/M/L) — press and slide to fine-tune each preset, 12 curated colors + custom picker |
| **Eraser** | Remove ink by stroke (tap to delete whole stroke) or by area (scrub to erase portions). Optional auto-switch: automatically returns to pen tool after lifting the stylus (configurable in settings). | Mode toggle: stroke / partial |
| **Highlighter** | Semi-transparent strokes rendered behind ink. Fixed width, pressure affects opacity. | 5 colors (yellow, green, blue, pink, orange), 3 preset size buttons (S/M/L) — press and slide to fine-tune |
Each tool retains its own independent settings. Selecting a tool and tapping it again reveals a slim option popup bar below the toolbar with that tool's controls.

### Editing

- **Undo/Redo** — Full per-session stack, accessible via toolbar buttons or two-finger tap gesture.
- **Lasso Select** — Freeform selection of strokes with cut, copy, paste, delete, recolor, move, and resize operations.

### Canvas Templates

Four built-in templates: **Blank**, **Lined** (college-ruled), **Grid** (square), and **Dotted** (dot grid). Selectable at canvas creation or changeable later.

### Organization

- **Folders** — Nestable containers with custom titles and cover colors.
- **Canvases** — Individual writing surfaces, each with its own template and stroke data.
- **Library screen** — Grid view with thumbnails, titles, and dates. Sort by last edited, title, or creation date.

### Gestures & Navigation

- Pinch-to-zoom (50%-400%), two-finger pan, two-finger tap to undo
- Double-tap to toggle zoom-to-fit / 100%
- Edge swipe to navigate between canvases (configurable)
- Stylus-only drawing mode with finger input reserved for gestures (configurable for stylus-free devices)

### PDF Import & Export

- Import PDFs as canvases (one page per canvas page), annotate with all standard tools
- Export canvases as PDF (with annotations baked in) or high-resolution PNG
- Share via Android share sheet

### Cloud Sync (Premium)

- Cross-device canvas sync for paid/subscribed users
- Offline-first: full functionality without a connection, syncs when online
- Free tier remains fully functional with local storage and manual export

---

## Key Technical Challenges

### 1. Sub-Frame Ink Latency

The defining technical challenge. Users must see ink appear under the stylus tip with no perceptible delay. This requires **front-buffered rendering** on Android via `androidx.ink`, bypassing the normal double-buffered rendering pipeline to draw partial strokes directly to the display. Achieving this while maintaining visual quality (anti-aliasing, pressure curves) and avoiding rendering artifacts is the hardest problem in the app.

### 2. Cross-Platform Architecture (KMP)

Kotlin Multiplatform shares business logic, data models, and persistence across Android and iOS — but the rendering and stylus input layers must be fully platform-native. The challenge is designing a clean boundary between shared code (stroke models, undo/redo logic, canvas state, persistence) and platform code (GPU rendering, input handling) so that neither side becomes a leaky abstraction. iOS will use PencilKit or a custom Metal renderer consuming the same shared models, and must match Android's latency — it cannot be a second-class port.

### 3. Stroke Storage & Serialization

Handwriting generates large volumes of point data (x, y, pressure, tilt, timestamp per sample). Strokes must be serialized into compact binary blobs for fast read/write via SQLDelight. The storage format must support efficient partial loading (opening a canvas shouldn't require deserializing every stroke upfront), and the serialization path must not introduce latency that the user can feel during autosave.

### 4. Stylus Input Processing

Raw stylus input needs real-time processing: pressure-to-width curves, velocity-based smoothing, palm rejection filtering, and tilt handling. This processing must happen on the input thread with minimal allocation to avoid GC pauses. Getting the pen feel right — the subjective quality of how ink responds to pressure and speed — is as important as raw latency and requires extensive tuning.

### 5. Canvas Rendering at Scale

A single canvas can accumulate thousands of strokes. The renderer must handle zoom levels from 50% to 400% without frame drops, efficiently cull off-screen strokes, and manage GPU memory for large canvases. The transition between "live" front-buffered rendering (current stroke) and "committed" back-buffered rendering (all previous strokes) must be seamless with no visual glitches.

### 6. PDF Rendering & Annotation Layering

Imported PDFs must render crisply at all zoom levels while maintaining a clear separation between the read-only PDF layer and the editable annotation layer above it. Multi-page PDF support adds complexity around page navigation, memory management (not all pages can be decoded simultaneously), and ensuring annotations stay aligned with their underlying PDF content.
