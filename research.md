# Phase 1 — Core Drawing Canvas

## Goal

Implement a functional drawing canvas: stylus input with pressure sensitivity, stroke persistence via SQLDelight, and low-latency rendering using `androidx.ink`.

---

## Tasks

- [ ] Implement `InkCanvas` composable using `InProgressStrokes` (Compose API)
- [ ] Wire `CanvasStrokeRenderer` for rendering finalized strokes
- [ ] Implement `StrokeMapper` (androidx.ink `Stroke` <-> shared `StrokeData`)
- [ ] Build `CanvasStore` (MVI) in shared module — state, intents, reducer
- [ ] Persist strokes to SQLDelight on draw completion
- [ ] Load and render strokes from DB on page open
- [ ] Implement pen tool with pressure sensitivity
- [ ] Implement color picker and brush size selector
- [ ] Verify 120Hz low-latency rendering on a physical tablet

---

## androidx.ink Modules (Relevant to Phase 1)

```
androidx.ink:ink-brush              — Brush, BrushFamily, BrushPaint definitions
androidx.ink:ink-strokes            — Stroke, StrokeInputBatch, MutableStrokeInputBatch
androidx.ink:ink-geometry           — Geometric helpers (Box, Vec, MeshEnvelope)
androidx.ink:ink-geometry-compose   — Compose interop (toComposeRect, etc.)
androidx.ink:ink-authoring-compose  — InProgressStrokes composable
androidx.ink:ink-rendering          — CanvasStrokeRenderer, ViewStrokeRenderer
androidx.ink:ink-storage            — Protobuf encode/decode for StrokeInputBatch
```

All at version **1.0.0 stable**.

---

## The Ink Bridge — Shared <-> Android Boundary

The critical architectural boundary is between **shared stroke data** (platform-agnostic) and **Android's `androidx.ink` rendering** (platform-specific).

```
Shared (commonMain)                  Android (androidApp)
-----------------                    --------------------
StrokeData                     <->   androidx.ink.Stroke
  - points: List<StrokePoint>        - StrokeInputBatch
  - brush: BrushConfig               - Brush / BrushFamily
  - color: Long                      - BrushPaint
  - strokeWidth: Float

StrokePoint                    <->   StrokeInputBatch entry
  - x: Float                        - x, y, elapsedTimeMillis
  - y: Float                        - pressure, tiltRadians
  - tiltRadians: Float               - orientationRadians
  - pressure: Float
  - timestamp: Long
```

A `StrokeMapper` on the Android side converts between these representations:
- **On draw complete**: `androidx.ink.Stroke` -> `StrokeData` -> persisted via shared repository
- **On page load**: `StrokeData` -> `androidx.ink.Stroke` -> rendered by `CanvasStrokeRenderer`

---

## Stroke Serialization

Use `androidx.ink:ink-storage` protobuf encoding with delta compression.

```kotlin
// Android: Serialize stroke inputs to binary blob
fun encodeStrokeInputs(stroke: InkStroke): ByteArray {
    return ByteArrayOutputStream().use { stream ->
        stroke.inputs.encode(stream)
        stream.toByteArray()
    }
}

// Android: Deserialize binary blob back to StrokeInputBatch
fun decodeStrokeInputs(data: ByteArray): StrokeInputBatch {
    return ByteArrayInputStream(data).use { stream ->
        StrokeInputBatch.decode(stream)
    }
}
```

Shared module stores raw `ByteArray` blobs. The Android layer handles encode/decode via `expect/actual`:

```kotlin
// commonMain
expect class StrokeSerializer {
    fun encode(points: List<StrokePoint>): ByteArray
    fun decode(data: ByteArray): List<StrokePoint>
}

// androidMain — delegates to androidx.ink.storage
actual class StrokeSerializer {
    actual fun encode(points: List<StrokePoint>): ByteArray { /* ink-storage */ }
    actual fun decode(data: ByteArray): List<StrokePoint> { /* ink-storage */ }
}
```

**Storage size estimates** (delta-compressed protobuf):
- Typical stroke: 100-300 points x ~6 bytes/point (delta) ~ **0.6-1.8 KB per stroke**
- Dense page (200 strokes): ~**200-360 KB**

---

## MVI Pattern — CanvasStore

```kotlin
data class CanvasState(
    val strokes: List<StrokeData>,
    val activeTool: Tool,
    val activeColor: Long,
    val activeBrushSize: Float,
    val isLoading: Boolean,
    // undoStack/redoStack deferred to Phase 3
)

sealed interface CanvasIntent {
    data class LoadPage(val pageId: String) : CanvasIntent
    data class StrokeCompleted(val stroke: StrokeData) : CanvasIntent
    data class ToolSelected(val tool: Tool) : CanvasIntent
    data class ColorSelected(val colorArgb: Long) : CanvasIntent
    data class BrushSizeChanged(val size: Float) : CanvasIntent
}
```

Store processes intents, updates `StateFlow<CanvasState>`, and persists strokes to the repository as a side effect.

---

## Pen Tool Spec

| Property | Value |
|---|---|
| Brush family | `BrushFamily.PRESSURE_PEN` |
| Pressure mapping | Variable width based on `MotionEvent` pressure |
| Default size | 3.0f |
| Size range | 1.0f - 20.0f |
| Default color | Black (`0xFF000000`) |

---

## Key Risks

| Risk | Mitigation |
|---|---|
| `ink-authoring-compose` is alpha-level | Fall back to View-based `InProgressStrokesView` via `AndroidView` composable |
| Stroke serialization across platforms | Shared module uses its own `StrokePoint` list; platform serializers handle encode/decode |
| Palm rejection conflicts | Use `MotionEvent.getToolType()` — stylus = draw, finger = ignore (pan/zoom deferred to Phase 5) |

---

## References

- [Ink API setup guide](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/ink-api-setup)
- [Ink API modules overview](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/ink-api-modules)
- [State preservation & persistent storage for Ink](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/ink-api-state-preservation)
- [Low-latency stylus rendering](https://medium.com/androiddevelopers/stylus-low-latency-d4a140a9c982)
