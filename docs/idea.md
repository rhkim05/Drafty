# Idea: Tablet Note-Taking App

## Project Codename: **Drafty**

---

## Vision

Build a **handwriting-first, canvas-based** tablet note-taking app for Android (with future iOS portability) that rivals GoodNotes and Noteshelf. The core experience centers on a fluid, pressure-sensitive ink engine on an infinite/paginated canvas — not a text editor with drawing bolted on, but a true digital paper replacement.

---

## Problem Statement

Most Android tablet note-taking apps fall short of the iPad ecosystem (GoodNotes, Noteshelf, Notability). Users switching to Android tablets (Samsung Galaxy Tab, Pixel Tablet, etc.) lack a polished, performant handwriting-first app. The opportunity is to fill that gap with:

1. **A best-in-class ink engine** — low-latency, pressure-sensitive, smooth strokes that feel like real pen on paper.
2. **A flexible canvas** — paginated notebooks AND freeform whiteboard mode for different use cases.
3. **PDF annotation** — import, mark up, and export PDFs seamlessly, which is a core workflow for students and professionals.
4. **Cross-platform future** — architected from day one so the core engine can be shared with iOS via Kotlin Multiplatform (KMP).

---

## Core Concept

### What it IS:
- A digital notebook / whiteboard app
- Stylus/pen-first input (finger input supported but secondary)
- Canvas-based rendering (not a rich-text editor)
- A PDF reader and annotator
- Organized into notebooks → sections → pages
- Local-first with optional cloud backup/sync

### What it is NOT:
- Not a text/typing-focused note app (no Notion/Bear-style blocks)
- Not a collaborative whiteboard (no real-time multi-user editing in MVP)
- Not a document editor
- Not an image editor

---

## Target Users

1. **Students** — taking handwritten notes in lectures, annotating PDF textbooks and slides, sketching diagrams.
2. **Professionals** — meeting notes, whiteboard brainstorming, marking up PDF contracts/reports.
3. **Creatives** — quick sketches, storyboarding, visual thinking on an infinite canvas.
4. **Android tablet enthusiasts** — users who want a GoodNotes-quality experience on Android.

---

## Key Differentiators (vs. existing Android apps)

| Area | Our Approach |
|------|-------------|
| Ink quality | Custom low-latency rendering engine with pressure/tilt support, Bézier curve smoothing |
| Canvas modes | Both paginated (notebook paper) and infinite whiteboard in one app |
| PDF annotation | First-class PDF import, annotation layers, and export — not an afterthought |
| Organization | Notebooks → Sections → Pages hierarchy with tags and favorites |
| Performance | GPU-accelerated canvas rendering, efficient stroke storage, smooth zoom/pan |
| Cross-platform path | KMP-ready architecture so core logic can be shared with iOS later |

---

## MVP Feature Brainstorm

### Must Have (MVP)
- **Ink engine**: Pen, highlighter, eraser tools with pressure sensitivity
- **Canvas**: Paginated notebook mode with selectable paper templates (blank, lined, grid, dotted)
- **Whiteboard mode**: Infinite canvas per page/note
- **PDF import & annotation**: Load PDFs, draw/highlight on top, export annotated PDF
- **Notebook organization**: Create/rename/delete notebooks, sections, pages
- **Basic tools**: Undo/redo, lasso select, move/resize/copy strokes, color picker, stroke thickness
- **Palm rejection**: Distinguish stylus from palm/finger touches
- **Export**: Export pages/notebooks as PDF or PNG
- **Local storage**: All data saved locally using an efficient database + file storage
- **Search**: Search by notebook/section/page names

### Should Have (Post-MVP v1.x)
- Optional cloud sync (Firebase/Supabase) with user accounts
- Handwriting recognition (OCR) for searchable ink
- Text boxes on canvas
- Shape recognition (draw a rough circle → snap to perfect circle)
- Image insertion on canvas
- More paper templates and custom templates
- Dark mode / theme support
- Backup & restore (local file export/import)

### Could Have (Future)
- Audio recording synced to ink strokes
- AI-powered summarization of handwritten notes
- Real-time collaboration
- Web viewer for shared notebooks
- Apple Pencil support (iOS port)
- Presentation mode

---

## Inspiration & Reference Apps

- **GoodNotes 6** (iOS) — gold standard for handwriting notes, excellent ink engine, PDF annotation
- **Noteshelf** (iOS/Android) — beautiful UI, good ink feel, notebook metaphor
- **Samsung Notes** — good Android baseline, deep S Pen integration
- **Notability** (iOS) — audio sync feature, clean UI
- **Miro / FreeForm** — infinite whiteboard UX inspiration
- **Xodo / PDF Expert** — PDF annotation workflow reference

---

## Key Technical Questions to Resolve

1. **Ink rendering approach**: Custom OpenGL ES / Vulkan canvas vs. Android Canvas API vs. a library like libharu or Skia directly?
2. **Stroke data model**: How to store strokes efficiently for undo/redo, selection, and serialization?
3. **PDF rendering**: Which PDF library for Android? (PdfRenderer, PDFium, iText, Apache PDFBox?)
4. **Cross-platform strategy**: How much to invest in KMP from day one vs. refactor later?
5. **File format**: Custom binary format for notebooks vs. SQLite + file blobs vs. something like .goodnotes format?
6. **Canvas framework**: Jetpack Compose Canvas vs. custom SurfaceView/TextureView for performance?

---

## Success Metrics (MVP)

- Ink latency under 20ms (pen-to-screen)
- Smooth 60fps pan/zoom on pages with 1000+ strokes
- PDF load time under 2 seconds for a 50-page document
- App cold start under 1.5 seconds
- Zero data loss — robust local persistence

---

*Next step: Translate this into a concrete plan → see [plan.md](./plan.md)*
