package com.drafty.core.domain.model

/**
 * Canvas rendering modes.
 */
enum class CanvasMode {
    /** Fixed-size pages (A4/Letter) with paper templates. */
    PAGINATED,
    /** Unbounded infinite canvas for freeform work. */
    WHITEBOARD,
}
