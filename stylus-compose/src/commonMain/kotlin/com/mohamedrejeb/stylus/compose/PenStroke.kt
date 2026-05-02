package com.mohamedrejeb.stylus.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

/**
 * A single sampled point along a [PenStroke].
 *
 * @property x X coordinate, in the surface's local coordinate space.
 * @property y Y coordinate, in the surface's local coordinate space.
 * @property pressure Pen pressure in the range `[0, 1]`. `1` means the platform
 *   reported maximum pressure; `0` means no pressure.
 * @property elapsedMillis Milliseconds since the stroke began (i.e. since the
 *   pen pressed down).
 */
@Immutable
data class PenStrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val elapsedMillis: Long,
)

/**
 * A completed stylus stroke, surfaced via [PenInkState.finishedStrokes] and the
 * `onStrokesFinished` callback of [PenInkSurface].
 *
 * Construction is internal — strokes are only created by the platform actuals
 * of [PenInkSurface].
 */
@Immutable
class PenStroke internal constructor(
    val brush: PenBrush,
    val points: List<PenStrokePoint>,
) {
    /** Axis-aligned bounding box of all sampled points. Computed lazily. */
    val bounds: Rect by lazy { computeBounds(points) }

    /**
     * Catmull-Rom-smoothed point trail used by the non-Android Compose
     * renderer. Computed once on first access and reused for the lifetime
     * of the stroke — without this cache, every Compose frame re-ran the
     * full smoothing pipeline (`O(N · subdivisions)`) for every finished
     * stroke, which compounded fast on staccato drawing where dozens of
     * strokes accumulate quickly.
     *
     * Unused on Android, where finished strokes go through Ink's
     * `CanvasStrokeRenderer` instead. Lazy init means the smoothing work
     * never runs on Android.
     */
    internal val smoothedPoints: List<PenStrokePoint> by lazy { catmullRomSmooth(points) }
}

private fun computeBounds(points: List<PenStrokePoint>): Rect {
    if (points.isEmpty()) return Rect.Zero
    var minX = points[0].x
    var minY = points[0].y
    var maxX = minX
    var maxY = minY
    for (i in 1 until points.size) {
        val p = points[i]
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    return Rect(left = minX, top = minY, right = maxX, bottom = maxY)
}

/**
 * Stroke appearance — color, base size, and brush family. Construct via
 * [PenBrush.pen], [PenBrush.marker], or [PenBrush.highlighter].
 *
 * The same `PenBrush` instance is platform-agnostic. On Android it is
 * converted internally to an `androidx.ink.brush.Brush`; on other targets it
 * drives a Compose `Canvas` renderer directly.
 */
@Immutable
class PenBrush internal constructor(
    val color: Color,
    val size: Float,
    val family: PenBrushFamily,
) {
    companion object {
        /** Pressure-sensitive default pen, black, ~5px nominal width. */
        val Default: PenBrush = pen(Color.Black)

        /** Pressure-sensitive pen — width modulates with stylus pressure. */
        fun pen(color: Color, size: Float = DEFAULT_PEN_SIZE): PenBrush =
            PenBrush(color, size, PenBrushFamily.Pen)

        /** Constant-width marker — width does not vary with pressure. */
        fun marker(color: Color, size: Float = DEFAULT_MARKER_SIZE): PenBrush =
            PenBrush(color, size, PenBrushFamily.Marker)

        /** Translucent highlighter — alpha is reduced at render time. */
        fun highlighter(color: Color, size: Float = DEFAULT_HIGHLIGHTER_SIZE): PenBrush =
            PenBrush(color, size, PenBrushFamily.Highlighter)
    }
}

enum class PenBrushFamily { Pen, Marker, Highlighter }

internal const val DEFAULT_PEN_SIZE: Float = 5f
internal const val DEFAULT_MARKER_SIZE: Float = 10f
internal const val DEFAULT_HIGHLIGHTER_SIZE: Float = 20f
