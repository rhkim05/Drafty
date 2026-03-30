# Research: Phase 1 — Ink Engine

> References: [plan.md](./plan.md) | [tech-stack.md](./tech-stack.md) | [architecture.md](./architecture.md) | [TODO.md](./TODO.md)

---

## 1. Android MotionEvent for Stylus Input

### Batched Historical Events

Android buffers motion events between frames. A single `ACTION_MOVE` can contain multiple historical samples. **All must be consumed** for smooth strokes.

Key APIs:
- `getHistoricalX/Y/Pressure/Size(pointerIndex, historyIndex)` — past positions in batch
- `getHistorySize()` — number of historical samples
- `getToolType(pointerIndex)` — `TOOL_TYPE_STYLUS`, `TOOL_TYPE_FINGER`, `TOOL_TYPE_PALM`, `TOOL_TYPE_ERASER`

Processing pattern: iterate `0 until event.historySize` for historical samples, then process the current event.

### Action Lifecycle

- `ACTION_DOWN` → initialize stroke
- `ACTION_MOVE` → process batched samples (historical + current)
- `ACTION_UP` → finalize stroke
- `ACTION_CANCEL` / `FLAG_CANCELED` (Android 13+) → discard active stroke

### Key Principle

Always consume all historical samples. Failure results in dropped points and visible gaps, especially damaging for pressure-sensitive rendering.

---

## 2. Catmull-Rom to Cubic Bézier Conversion

### Mathematical Approach

For four Catmull-Rom points p₀, p₁, p₂, p₃, the segment from p₁→p₂ converts to a cubic Bézier with control points:

**Step 1 — Tangent vectors:**
- T₁ = (p₂ - p₀) / 2
- T₂ = (p₃ - p₁) / 2

**Step 2 — Bézier control points:**
- B₀ = p₁ (start)
- B₁ = p₁ + T₁/3 = p₁ + (p₂ - p₀) / 6
- B₂ = p₂ - T₂/3 = p₂ - (p₃ - p₁) / 6
- B₃ = p₂ (end)

This uses only linear transformations — efficient for real-time. Feeds directly into Android `Path.cubicTo(b1.x, b1.y, b2.x, b2.y, b3.x, b3.y)`.

### Edge Cases

- First segment: duplicate p₀ = p₁ (phantom start point)
- Last segment: duplicate p₃ = p₂ (phantom end point)

---

## 3. Variable-Width Stroke Rendering

### Approach Comparison

| Method | Performance | Quality | Complexity |
|--------|-------------|---------|------------|
| Quad strip tessellation | Best (GPU) | Best | High (OpenGL) |
| Circle strip | Good | Good | Low |
| Path segments with varying width | Medium | Okay (visible steps) | Low |

### Decision: Circle Strip for MVP

For the initial implementation, use **circle strip rendering** — draw filled circles at each point with radius = pressure-mapped width. Reasons:
- Works with standard Android Canvas API
- Simple implementation
- Natural joins
- Sufficient for 60fps with typical stroke density
- Can upgrade to quad tessellation later if needed

Optimization: reuse `Paint` objects, use `Canvas.drawCircle()`, batch as much as possible.

### Completed Stroke Caching

Cache completed strokes as a bitmap. Only the active stroke (being drawn) needs per-frame rendering. This keeps frame cost constant regardless of stroke count.

---

## 4. SurfaceView Architecture

### Why SurfaceView over TextureView

- **Lower latency**: direct surface access, no View hierarchy composition delay
- **Dedicated render thread**: drawing doesn't block UI thread
- **Hardware acceleration**: `lockHardwareCanvas()` on API 26+

### Double-Buffer Strategy

1. **Completed strokes bitmap**: all finalized strokes rasterized into a single `Bitmap`
2. **Active stroke overlay**: current stroke drawn on top each frame
3. **On ACTION_UP**: active stroke merged into completed bitmap

### Render Thread Pattern

```
while (running) {
    canvas = holder.lockHardwareCanvas()
    canvas.drawBitmap(completedStrokesBitmap)  // fast blit
    drawActiveStroke(canvas)                     // real-time
    holder.unlockCanvasAndPost(canvas)
}
```

Thread safety: synchronize touch input (main thread) and rendering (render thread) on a shared lock.

---

## 5. Palm Rejection

### Primary Signal: `getToolType()`

- `TOOL_TYPE_STYLUS` → accept (draw)
- `TOOL_TYPE_FINGER` → when stylus active, use for pan/zoom; otherwise ignore on canvas
- `TOOL_TYPE_PALM` → always reject
- `TOOL_TYPE_ERASER` → eraser mode

### Multi-Pointer State Machine

Track pointer IDs and their tool types. Only process stylus pointers for drawing. State transitions:

```
IDLE → ACTION_DOWN(stylus) → DRAWING
DRAWING → ACTION_MOVE → process samples → DRAWING
DRAWING → ACTION_UP → finalize → IDLE
DRAWING → ACTION_CANCEL → discard → IDLE
Any → POINTER_DOWN(palm) → ignore pointer
```

### Android 13+ Enhancement

Check `FLAG_CANCELED` bit: `(event.flags and MotionEvent.FLAG_CANCELED) != 0` — cancel and discard the active stroke.

---

## 6. Pressure-to-Width Mapping

### Recommended Curves

**Pen tool — Power curve (exponent = 1.8):**
```
width = minWidth + pressure^1.8 × (maxWidth - minWidth)
```
- minWidth: 1.5px, maxWidth: 18px
- Bias toward thin lines; heavy pressure for thick

**Highlighter — Sigmoid (steepness = 5):**
```
width = minWidth + sigmoid(5 × (pressure - 0.5)) × (maxWidth - minWidth)
```
- minWidth: 10px, maxWidth: 35px
- "On/off" feel with middle threshold

### Implementation

Pre-compute curve parameters. Use `Float.coerceIn(0f, 1f)` on raw pressure. Cache `Paint` with mapped width — don't allocate per point.

---

## 7. Key Decisions for Phase 1

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Rendering surface | SurfaceView | Lower latency than TextureView |
| Stroke rendering | Circle strip | Simple, good quality, upgradable later |
| Smoothing | Catmull-Rom → Bézier | Industry standard, efficient conversion |
| Pressure curve (pen) | Power (exp=1.8) | Natural writing feel |
| Pressure curve (highlighter) | Sigmoid (k=5) | Marker-like threshold |
| Thread model | Render thread + main thread sync | Non-blocking drawing |
| Buffer strategy | Completed bitmap + active overlay | Constant frame cost |

---

*Next step: Update [TODO.md](./TODO.md) with detailed Phase 1 subtasks, then implement.*
