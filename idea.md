# Drafty — App Idea

## Vision

Drafty is a handwriting and note-taking app for tablets that prioritizes speed, simplicity, and a distraction-free writing experience. Where apps like Goodnotes and Noteshelf have grown into feature-heavy platforms with AI assistants, marketplaces, and collaboration tools, Drafty stays focused: pick up your stylus and write. No subscriptions, no bloat — just a fast, reliable digital notebook.

Primary target: Android tablets, with a native iOS port planned after the Android release. Both platforms must deliver the same smooth, low-latency writing experience — the iOS version is not a lesser port but a first-class app built on the same performance-first principles.

---

## Design Philosophy

1. **Speed-first** — Sub-frame ink latency using front-buffered rendering. Writing must feel instant and natural. Performance is the #1 feature.
2. **Minimal UI** — Every pixel of chrome is canvas space lost. Show only what's needed, when it's needed.
3. **Distraction-free** — No popups, no tips, no upsells. Open the app, pick a canvas, start writing.
4. **Essential tools only** — A curated set of tools that cover 95% of use cases. No tool drawer with 30 options.
5. **Clean organization** — Folders and canvases. Simple hierarchy, fast navigation. No tags, no smart folders, no search-driven organization.

---

## UI/UX Overview

### Library Screen

The home screen. A grid of folders and canvases showing title and last-edited date. Clean, scannable layout.

- Items displayed as a responsive grid (2-3 columns on tablet)
- Each item shows: custom color/thumbnail, title, date
- Tapping a **canvas** opens it directly in the Canvas Screen
- Tapping a **folder** navigates into it, showing its sub-folders and canvases
- Floating action button (FAB) at bottom-right with options: **create folder**, **create canvas**, or **import PDF**
- Long-press an item for context menu: rename, duplicate, delete, move
- Sort by: last edited (default), title, date created

### Canvas Screen

The core experience. Full-screen writing surface with a thin, fixed top toolbar.

```
┌─────────────────────────────────────────────────────────────┐
│  <  Canvas Title  │  ✏️  ⌫  🖍  ▫️  │  ↩  ↪  ⋯        │
├─────────────────────────────────────────────────────────────┤
│  [ option popup bar — tool-specific settings ]              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                                                             │
│                                                             │
│                    (writing canvas)                          │
│                                                             │
│                                                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Top toolbar layout (left to right):**

| Section | Controls |
|---------|----------|
| **Left** | Back button, canvas title |
| **Center** | Tool selector: Pen, Eraser, Highlighter, Lasso |
| **Right** | Undo, Redo, Overflow menu (...) |

- Toolbar is always visible with a thin profile (~48dp) to maximize canvas space
- Tapping a tool selects it; tapping the **active tool again** opens its **option popup bar** just below the toolbar
- The option popup bar is a slim, horizontally-laid-out panel showing tool-specific settings (color, size, mode, etc.)
- Each tool retains its own independent settings (e.g., pen keeps its color/size separate from highlighter)
- Overflow menu: export, canvas template, zoom-to-fit

**Canvas interactions:**
- Stylus draws with the active tool
- Two-finger pinch to zoom, two-finger drag to pan
- Two-finger tap to undo (gesture shortcut)
- Double-tap to zoom to fit / zoom to 100%

---

## Core Features (v1)

### Drawing Tools

Each tool has its own **option popup bar** that appears below the toolbar when the active tool is tapped. The popup bar shows tool-specific settings.

| Tool | Description | Option Popup Bar |
|------|-------------|------------------|
| **Pen** | Pressure-sensitive ballpoint pen. Primary writing tool. Variable stroke width based on pressure and velocity. | Size slider (0.1–3), color palette (12 curated colors + custom) |
| **Eraser** | Two modes: stroke eraser (tap a stroke to remove it entirely) and partial eraser (scrub to erase portions). | Mode toggle: stroke eraser / partial eraser |
| **Highlighter** | Semi-transparent strokes rendered behind ink. Fixed width, pressure affects opacity slightly. | Color set (yellow, green, blue, pink, orange), size slider |

### Editing

- **Undo / Redo** — Full undo/redo stack per canvas session. Toolbar buttons + two-finger tap gesture.
- **Lasso Select** — Draw a freeform selection around strokes. An **option popup bar** appears with actions:
  - Cut, Copy, Paste, Delete
  - Change color of selected strokes
  - Move the selection by dragging
  - Resize by dragging handles

### Canvas Templates

Four built-in templates, selectable when creating a canvas or changeable later:

| Template | Description |
|----------|-------------|
| **Blank** | Clean white page, no guides |
| **Lined** | Horizontal ruled lines (college-ruled spacing) |
| **Grid** | Square grid pattern for diagrams and structured notes |
| **Dotted** | Dot grid — popular for bullet journaling and flexible layouts |

### Organization

- **Folders** — Containers for organizing canvases. Can be nested to create deeper hierarchies. Custom title and cover color.
- **Canvases** — Individual writing surfaces. Each canvas has its own template, strokes, and annotations.

### Gestures & Navigation

- **Pinch-to-zoom** — Smooth zoom from 50% to 400%
- **Two-finger pan** — Scroll around the canvas when zoomed in
- **Two-finger tap** — Quick undo
- **Swipe from edge** — Navigate to previous/next canvas (configurable)
- **Double-tap** — Toggle between zoom-to-fit and 100%

### Palm Rejection

- Stylus-only drawing mode: finger input is ignored for drawing, only used for gestures (zoom, pan, undo)
- Configurable: can enable finger drawing for devices without a stylus

### PDF Import & Annotation

- Import PDF files as a new canvas (each PDF page becomes a canvas page)
- Draw on top of PDF pages with all standard tools
- PDF content is read-only; annotations are layered on top
- Support for multi-page PDFs

### Export

- **Export as PDF** — Full canvas with annotations baked in
- **Export as PNG** — Canvas as a high-resolution image
- Share via Android share sheet (email, messaging, cloud storage)

---

## Future Features (v2+)

These are deliberately deferred to keep v1 focused:

- **Handwriting Search (OCR)** — Search across all canvases by handwritten text
- **Shape Recognition** — Auto-snap hand-drawn lines, circles, rectangles, arrows into clean geometric shapes
- **Text Tool** — Insert typed text boxes on the canvas
- **Dark Mode** — Dark canvas and UI theme
- **Bookmarks** — Star/bookmark important canvases for quick access
- **Wrist position setting** — Left-handed / right-handed mode for better palm rejection
- **Custom templates** — Import or create canvas templates beyond the built-in four

---

## Explicitly Out of Scope

These features are intentionally excluded from Drafty's roadmap to maintain simplicity:

| Feature | Why excluded |
|---------|-------------|
| AI features (summarize, transcribe, flashcards) | Adds complexity and cost without improving core writing |
| Real-time collaboration | Requires cloud infrastructure, adds latency to core experience |
| Template marketplace / stickers / emojis | Bloat — core templates are sufficient |
| Audio recording | Different use case, better served by dedicated apps |
| Subscription pricing (full app) | Core app remains one-time purchase or free — subscription only for cloud sync premium feature |

---

## Technical Notes

- **Kotlin Multiplatform (KMP)** — Shared business logic, data models, and persistence across platforms. Platform-specific code only for rendering and stylus input.
- **Android**: `androidx.ink` for stroke capture and rendering with front-buffered rendering for minimal latency. Jetpack Compose for UI, `AndroidView` interop for the drawing surface.
- **iOS (future)**: Native PencilKit or custom Metal-based renderer, consuming the same shared KMP models and persistence layer. Must match Android's latency and smoothness.
- **Storage**: SQLDelight for cross-platform database. Strokes serialized as binary blobs for compact, fast storage.
- **Architecture**: MVI (Model-View-Intent) pattern with unidirectional data flow. Clean separation between shared domain logic and platform-specific rendering.

---

## Competitive Positioning

| Dimension | Drafty | Goodnotes | Noteshelf |
|-----------|--------|-----------|-----------|
| **Philosophy** | Minimal & fast | Feature-rich platform | Creative & customizable |
| **Ink latency** | Sub-frame (priority #1) | Good | Good |
| **Tools** | Pen, eraser, highlighter, lasso | Pen, eraser, highlighter, lasso, shapes, text, laser pointer | 6 pen types, shapes, text, highlighter |
| **Tool options** | Per-tool popup bars | Shared toolbar settings | Shared toolbar settings |
| **AI features** | None | Spell-check, summarize, flashcards, transcription | Summarize, translate, generate |
| **Templates** | 4 built-in | Marketplace with hundreds | 200+ built-in |
| **Collaboration** | None | Real-time sharing | None |
| **PDF support** | Import & annotate | Import, annotate, fill forms | Import, annotate, fill forms |
| **Platforms** | Android (iOS planned) | iOS, Android, Windows, Mac, Web | iOS, Android, Windows, Mac |
| **Price model** | One-time / free | Freemium + subscription | One-time ($10) |
| **Target user** | Writers who want speed and simplicity | Students, professionals, teams | Creative journalers, planners |

**Drafty's edge**: When you open the app, you're writing in under 2 seconds. No onboarding, no feature discovery, no decision fatigue. For users who just want a fast digital notebook — not a platform — Drafty is the answer.
