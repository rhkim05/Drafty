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

---
---

# Research: Phase 2 — Canvas & Page Management

> References: [plan.md](./plan.md) | [architecture.md](./architecture.md) | [TODO.md](./TODO.md) | [project-structure.md](./project-structure.md)

---

## 1. ViewportManager — Zoom, Pan, and Viewport Culling

### Coordinate System Design

The canvas uses two coordinate spaces:

- **Canvas coordinates**: the "world" space where strokes live. In paginated mode, bounded by page dimensions (e.g., 2480×3508 for A4 at 300dpi). In whiteboard mode, unbounded (effectively ±100,000 in each axis).
- **Screen coordinates**: pixel coordinates of the SurfaceView. Conversion is via an affine transform: `screen = (canvas - panOffset) × zoom`.

The `ViewportManager` owns this transform and exposes both forward (canvas→screen) and inverse (screen→canvas) mappings. All touch input must be converted to canvas coordinates before reaching StrokeBuilder.

### Zoom Behavior

- **Zoom range**: clamp to `[0.25, 5.0]` for paginated mode, `[0.1, 10.0]` for whiteboard mode.
- **Zoom anchor**: pinch-to-zoom anchors around the midpoint of the two fingers. This means pan offset must adjust simultaneously so the pinch center stays fixed on screen.
- **Formula**: When zoom changes from `z₀` to `z₁` with focal point `f` (in screen coords):
  ```
  newPanX = f.x - (f.x - panX) × (z₁ / z₀)
  newPanY = f.y - (f.y - panY) × (z₁ / z₀)
  ```
- **Double-tap to reset**: animate zoom back to 1.0 and center the page.

### Pan Behavior

- In paginated mode, clamp pan so the page stays at least partially visible (allow overscroll with rubber-band bounce).
- In whiteboard mode, no hard clamp — allow free pan in all directions.
- Kinetic fling: after two-finger pan ends, apply velocity-based deceleration (use `android.widget.OverScroller` for physics).

### Viewport Culling

Given the visible rectangle in canvas coordinates, query the RTree for strokes whose bounding boxes intersect the viewport. Only those strokes are rendered. This is critical for whiteboard mode where thousands of strokes may exist but only a fraction are on screen.

Viewport rect calculation:
```
viewportLeft   = -panOffsetX / zoom
viewportTop    = -panOffsetY / zoom
viewportRight  = viewportLeft + screenWidth / zoom
viewportBottom = viewportTop + screenHeight / zoom
```

### Integration with InkSurfaceView

InkSurfaceView currently renders at 1:1 with no transform. To add viewport support:

1. Apply `canvas.translate(panOffsetX, panOffsetY)` and `canvas.scale(zoom, zoom)` before drawing strokes on the SurfaceView's Canvas.
2. The completed strokes bitmap must be re-rendered when zoom changes (since bitmap is rasterized at a specific zoom level). Options:
   - **Option A**: Re-rasterize the bitmap on zoom change (simple but causes a frame stutter on zoom).
   - **Option B**: Keep the bitmap at base resolution, scale it with the Canvas transform, and re-rasterize in the background when zoom stabilizes.
   - **Decision**: Option B for smooth pinch-to-zoom. Use a debounce (300ms after last zoom event) to trigger background re-rasterization.

3. Touch input coordinates from MotionEvent must be inverse-transformed before feeding to InputProcessor:
   ```
   canvasX = (screenX - panOffsetX) / zoom
   canvasY = (screenY - panOffsetY) / zoom
   ```

---

## 2. GestureDetector — Two-Finger Pinch and Pan

### Android Gesture APIs

Android provides `ScaleGestureDetector` for pinch-to-zoom and `GestureDetector` for fling/scroll. For our use case, we need a custom composite detector because:

- Stylus single-pointer events → drawing (handled by InputProcessor)
- Two-finger touch events → zoom/pan (GestureDetector)
- The palm rejection filter already separates stylus from finger, so GestureDetector only receives finger events

### Implementation Approach

Use `ScaleGestureDetector` for pinch zoom and manual two-finger tracking for pan:

**ScaleGestureDetector** (built-in):
- `onScaleBegin` → record initial zoom
- `onScale` → multiply zoom by `detector.scaleFactor`, apply focal point adjustment
- `onScaleEnd` → trigger bitmap re-rasterization debounce

**Two-finger pan** (manual tracking):
- On `ACTION_POINTER_DOWN` with 2 pointers → record initial midpoint
- On `ACTION_MOVE` with 2 pointers → compute midpoint delta → update panOffset
- On `ACTION_POINTER_UP` → end gesture
- Apply `OverScroller` for fling momentum on gesture end

**Double-tap detection**:
- Use `GestureDetector.SimpleOnGestureListener.onDoubleTap()` — animate to zoom=1.0 with `ValueAnimator`.

### Gesture Arbitration with Drawing

The core question: how to decide if a touch event is drawing vs. panning?

Rules:
1. If `toolType == TOOL_TYPE_STYLUS` → always draw (even if two stylus pointers, which is rare)
2. If `toolType == TOOL_TYPE_FINGER` and pointer count >= 2 → always pan/zoom
3. If `toolType == TOOL_TYPE_FINGER` and pointer count == 1 → depends on mode:
   - If "finger drawing" mode is enabled → draw
   - If stylus-only mode → pan/scroll
   - Default: stylus-only mode (finger pans, stylus draws)

This arbitration lives in `InkSurfaceView.onTouchEvent()`, routing events to either `InputProcessor` or `GestureDetector`.

---

## 3. R-Tree Spatial Index

### Why R-Tree

With hundreds or thousands of strokes, linear search for hit testing (eraser) and viewport culling becomes expensive. An R-tree provides O(log n) queries for "which strokes intersect this rectangle?"

### Data Structure

Each stroke has a bounding box (min/max x/y computed from its points). The R-tree organizes these bounding boxes in a balanced tree where:
- Each leaf node holds up to M entries (stroke bounding boxes)
- Each internal node holds child node bounding boxes
- Typical M = 9 (good balance of breadth vs. depth)

### Implementation Plan

For a note-taking app (not millions of entries), a simplified R-tree suffices:

**Simplified approach — flat grid index**:
- Divide the canvas into a grid of cells (e.g., 256×256 pixels per cell)
- Each cell stores a list of stroke IDs whose bounding box overlaps the cell
- Insert: compute which cells a stroke overlaps, add its ID to each
- Query: compute which cells the query rectangle overlaps, collect unique stroke IDs
- Remove: iterate cells, remove stroke ID

**Pros**: Trivial to implement, O(1) insert/query for typical cell sizes, very cache-friendly.
**Cons**: Memory waste for sparse canvases, doesn't adapt to stroke density.

**Decision**: Start with a grid-based spatial index for Phase 2. If profiling shows issues with large whiteboard canvases, upgrade to a proper R-tree later. The `RTree` class name and interface remain the same either way.

Grid cell size: 256px in canvas coordinates. For an A4 page (2480×3508), that's ~10×14 = 140 cells. Very manageable.

### Bounding Box Computation

Each `Stroke` needs a cached bounding box. Compute on stroke creation:
```kotlin
data class BoundingBox(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

fun Stroke.computeBoundingBox(): BoundingBox {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
    for (point in points) {
        minX = min(minX, point.x); minY = min(minY, point.y)
        maxX = max(maxX, point.x); maxY = max(maxY, point.y)
    }
    // Expand by half of max possible stroke width to account for rendering
    val padding = baseThickness / 2
    return BoundingBox(minX - padding, minY - padding, maxX + padding, maxY + padding)
}
```

---

## 4. HitTester — Eraser and Lasso Selection

### Stroke Eraser (Whole-Stroke Mode)

Stroke eraser removes an entire stroke when the eraser path touches it. Two-step check:

1. **Coarse phase**: Query spatial index for strokes whose bounding box intersects the eraser cursor's bounding box (a small square around the touch point, e.g., 20×20px in canvas coords).
2. **Fine phase**: For each candidate stroke, check if any point in the stroke is within `tolerance` distance of the eraser touch point.

Fine-phase distance check:
```kotlin
fun isStrokeHit(stroke: Stroke, touchX: Float, touchY: Float, tolerance: Float): Boolean {
    return stroke.points.any { point ->
        val dx = point.x - touchX
        val dy = point.y - touchY
        dx * dx + dy * dy <= tolerance * tolerance
    }
}
```

For continuous eraser movement (dragging), check against a swept path (the line segment between consecutive eraser positions) rather than just the current point. This prevents "skipping over" strokes at fast eraser speeds.

### Lasso Selection

Lasso draws a freeform closed path. Strokes are selected if their bounding box center (or majority of points) lies inside the lasso polygon.

Point-in-polygon test (ray casting algorithm):
```kotlin
fun isPointInPolygon(px: Float, py: Float, polygon: List<Pair<Float, Float>>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val (xi, yi) = polygon[i]
        val (xj, yj) = polygon[j]
        if ((yi > py) != (yj > py) &&
            px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
            inside = !inside
        }
        j = i
    }
    return inside
}
```

A stroke is "selected" if its bounding box center is inside the lasso polygon. For more precision, check if >50% of the stroke's points are inside.

### Selection Actions

Once strokes are selected (highlighted with a selection box and handles):
- **Move**: offset all points by drag delta, update bounding boxes, re-insert into spatial index
- **Resize**: scale points around selection center, update bounding boxes
- **Copy**: duplicate strokes with new IDs
- **Delete**: remove from stroke list, record DeleteStrokeCommand for undo
- **Recolor**: update stroke color property

All selection actions go through UndoManager as commands.

---

## 5. Paper Templates — Background Rendering

### Template Types

| Template | Pattern |
|----------|---------|
| BLANK | White/cream background, no lines |
| LINED | Horizontal rules at ~32px spacing (canvas coords), optional margin line |
| GRID | Square grid at ~32px spacing |
| DOTTED | Dots at grid intersections, ~32px spacing |
| CORNELL | Header area (top 15%), cue column (left 30%), notes area, summary area (bottom 15%) |

### Rendering Approach

Templates are rendered as the first layer in InkSurfaceView, below the completed strokes bitmap:

1. **On page load or template change**: Render the template pattern to a `Bitmap` at the page's resolution.
2. **On each frame**: Draw the template bitmap first (with viewport transform), then completed strokes bitmap, then active stroke overlay.

For zoom-independent line crispness:
- At high zoom, template lines should remain 1px wide on screen (not scale up)
- Render template lines dynamically with `canvas.drawLine()` using hairline stroke width (`paint.strokeWidth = 1f / zoom`)
- Cache the template bitmap at a resolution matching the current zoom level; re-render when zoom changes significantly (>2x delta from cached zoom)

### Template Configuration

Templates are defined as pure data in `:core:domain`:

```kotlin
data class TemplateConfig(
    val type: PaperTemplate,
    val lineColor: Long = 0xFFCCCCCC,        // light gray
    val lineSpacing: Float = 32f,             // canvas coords
    val marginX: Float = 100f,               // for LINED with margin
    val marginColor: Long = 0xFFFF9999,      // red margin line
)
```

This keeps template rendering deterministic and serializable.

---

## 6. Paginated Mode — Page System

### Page Dimensions

Standard page sizes in canvas coordinates (at 300 DPI equivalent density):
- A4: 2480 × 3508
- Letter: 2550 × 3300
- Custom: user-defined

The `Page` model already has `width` and `height` fields. These define the canvas bounds in paginated mode.

### Page Navigation

Navigation between pages in a section:
- **Swipe gesture**: horizontal swipe (two-finger or edge swipe) to go to next/previous page
- **PageNavigator UI**: forward/back buttons + page number indicator (e.g., "3 / 12")
- **Thumbnail sidebar**: toggle-able panel showing all page thumbnails for quick jump

When navigating away from a page:
1. Auto-save current strokes (debounced)
2. Clear InkSurfaceView
3. Load new page's strokes
4. Apply new page's template

### Page Management

CanvasViewModel manages page state through use cases:
- **Add page**: Insert after current page, reorder sort indices
- **Delete page**: Remove current, navigate to adjacent page
- **Reorder**: drag-and-drop in thumbnail sidebar (future enhancement)

### Clipping

In paginated mode, strokes that extend beyond page bounds are clipped during rendering:
```kotlin
canvas.save()
canvas.clipRect(0f, 0f, page.width, page.height)
// draw strokes
canvas.restore()
```

This prevents strokes from bleeding off the page edge.

---

## 7. Whiteboard Mode (Infinite Canvas)

### Coordinate Handling

No page bounds — the canvas extends indefinitely. The ViewportManager doesn't clamp pan in whiteboard mode.

The spatial index (grid) needs to handle negative coordinates and large ranges. The grid index uses a `HashMap<Pair<Int, Int>, MutableList<String>>` keyed by grid cell coordinates, so it naturally supports any coordinate range.

### Performance Considerations

With no page bounds, users can accumulate many strokes across a large area. Mitigation:
- Viewport culling via spatial index (only render visible strokes)
- Completed strokes bitmap only covers the current viewport + a margin
- When panning, re-render the bitmap shifted, filling in new areas incrementally

### Mode Switching

The `CanvasMode` enum already supports `PAGINATED` and `WHITEBOARD`. CanvasViewModel switches between modes, which affects:
- ViewportManager bounds clamping (on/off)
- Template rendering (paginated templates vs. plain background for whiteboard)
- Page navigation UI visibility (hidden in whiteboard mode)
- Completed strokes bitmap strategy (page-sized vs. viewport-sized)

---

## 8. Stroke Persistence — StrokeFileManager

### File Layout

Each page's strokes are stored as a protobuf file at:
```
{app_internal_storage}/strokes/{pageId}.pb
```

The protobuf schema (`strokes.proto`) is already defined and matches the domain models.

### Serialization/Deserialization

Using the generated protobuf classes:

```kotlin
// Save
fun saveStrokes(pageId: String, strokes: List<Stroke>) {
    val proto = StrokesFile.newBuilder()
        .setVersion(1)
        .addAllStrokes(strokes.map { it.toProto() })
        .build()
    val file = File(strokesDir, "$pageId.pb")
    file.outputStream().use { proto.writeTo(it) }
}

// Load
fun loadStrokes(pageId: String): List<Stroke> {
    val file = File(strokesDir, "$pageId.pb")
    if (!file.exists()) return emptyList()
    val proto = StrokesFile.parseFrom(file.inputStream())
    return proto.strokesList.map { it.toDomain() }
}
```

### Auto-Save Strategy

- After each stroke completion (`onStrokeCompleted`), schedule a save with 500ms debounce
- On page navigation or app background, force immediate save
- Save runs on `Dispatchers.IO` to avoid blocking the render thread
- Save failures logged to issues.md pattern (retry once, then warn user with toast)

---

## 9. CanvasViewModel Integration Gaps

### Current State

The CanvasViewModel has tool selection, undo/redo, and surface binding. It's missing:

1. **Page loading**: Inject `LoadStrokesUseCase` and `SaveStrokesUseCase`. On `loadPage(pageId)`, fetch strokes and push to InkSurfaceView.
2. **Stroke completion callback**: When InkSurfaceView reports `onStrokeCompleted`, add the stroke to the ViewModel's list, push to UndoManager, and schedule auto-save.
3. **Canvas mode switching**: Toggle PAGINATED ↔ WHITEBOARD, update ViewportManager bounds behavior.
4. **Lasso selection state**: When tool is LASSO, track selected strokes, expose move/copy/delete actions.
5. **Page navigation**: `nextPage()`, `previousPage()`, `goToPage(index)` methods that save current and load new.
6. **Zoom/pan state**: Receive updates from ViewportManager, update UI state for display.

### Data Flow After Phase 2

```
User touches screen
    │
    ├── Stylus → PalmRejection → InputProcessor → StrokeBuilder → InkSurfaceView render
    │                                                   │
    │                                         ACTION_UP → onStrokeCompleted
    │                                                   │
    │                                         CanvasViewModel.addStroke()
    │                                                   │
    │                                         UndoManager.record(AddStrokeCommand)
    │                                                   │
    │                                         SpatialIndex.insert(stroke)
    │                                                   │
    │                                         AutoSave (debounced 500ms)
    │
    └── Two fingers → GestureDetector → ViewportManager → update zoom/pan
                                              │
                                    InkSurfaceView re-renders with transform
                                              │
                                    Viewport culling via SpatialIndex.query()
```

---

## 10. Key Decisions for Phase 2

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Spatial index | Grid-based (256px cells) | Simple, fast, sufficient for note-taking scale; upgrade to R-tree if needed |
| Zoom caching | Scale bitmap transform + debounced re-rasterize | Smooth pinch-to-zoom without frame stutter |
| Pan physics | `OverScroller` for fling | Built-in Android physics, battle-tested |
| Template rendering | Dynamic lines at render time | Zoom-independent crispness, minimal memory |
| Gesture arbitration | Stylus=draw, 2-finger=zoom/pan, 1-finger=pan | Clear separation, no ambiguity |
| Page auto-save | 500ms debounce after stroke completion | Responsive feel without excessive I/O |
| Lasso selection criteria | Bounding box center inside polygon | Good enough for note-taking; avoids expensive per-point checks |
| Eraser mode | Whole-stroke (not partial) | Simpler for MVP; partial eraser in Phase 5+ |

---

*Next step: Update [TODO.md](./TODO.md) with detailed Phase 2 subtasks, then implement.*

---

> **Phase 3 research has been split into a separate file due to size:** [research-phase3.md](./research-phase3.md)
