package com.mohamedrejeb.stylus.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType

/**
 * Low-latency stylus drawing surface.
 *
 * On **Android** this is backed by [Jetpack Ink](https://developer.android.com/jetpack/androidx/releases/ink)
 * (`androidx.ink:ink-authoring-compose`), which renders in-progress strokes
 * through a front-buffered `SurfaceControl` for sub-frame latency, with
 * built-in motion prediction.
 *
 * On **Desktop / iOS / Web** the surface uses a pure-Compose pipeline with
 * Catmull-Rom smoothing and linear motion prediction — visibly tighter than a
 * naive `Canvas` per-event renderer, but without the OS-level compositor
 * bypass that Android has.
 *
 * `Modifier.penInput {}` continues to fire alongside, so consumers that need
 * raw pen telemetry (pressure, tilt, hover) can still subscribe via
 * [onPenEvent] without losing the rendering benefits.
 *
 * @param modifier Layout modifier applied to the surface's outer `Box`.
 * @param state State holder for finished strokes. Defaults to a freshly
 *   remembered [PenInkState].
 * @param brush Brush used for in-progress strokes. Defaults to [PenBrush.Default].
 * @param onStrokesFinished Optional callback fired with the newly-completed
 *   strokes on each pen lift, in addition to them being appended to [state].
 * @param onPenEvent Optional raw [PenEvent] callback — fires on hover, move,
 *   press, and release.
 * @param content Content drawn on top of the ink surface (e.g. UI overlays).
 */
@Composable
expect fun PenInkSurface(
    modifier: Modifier = Modifier,
    state: PenInkState = rememberPenInkState(),
    brush: PenBrush = PenBrush.Default,
    onStrokesFinished: (List<PenStroke>) -> Unit = {},
    onPenEvent: (PenEvent) -> Unit = {},
    content: @Composable BoxScope.() -> Unit = {},
)

/**
 * Shared pure-Compose implementation of [PenInkSurface] used by every actual
 * except Android. Records pen events into an active stroke, applies
 * Catmull-Rom smoothing and linear prediction during in-progress drawing,
 * and emits a [PenStroke] on each release.
 *
 * Stroke capture flows through `Modifier.penInput {}` so consumers get the
 * same `PenEvent` stream both for app logic ([onPenEvent]) and for the
 * rendered stroke — including pressure on Desktop (JNI), iOS (UITouch.force),
 * and Web (PointerEvent.pressure).
 */
@Composable
internal fun ComposePenInkSurface(
    modifier: Modifier,
    state: PenInkState,
    brush: PenBrush,
    onStrokesFinished: (List<PenStroke>) -> Unit,
    onPenEvent: (PenEvent) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val active = remember { mutableStateListOf<PenStrokePoint>() }
    var strokeStartMs by remember { mutableStateOf(0L) }

    Box(
        modifier = modifier.penInput { event ->
            onPenEvent(event)
            when (event.type) {
                PenEventType.Press -> {
                    strokeStartMs = event.timestamp
                    active.clear()
                    active.add(event.toStrokePoint(strokeStartMs))
                }
                PenEventType.Move -> {
                    if (active.isNotEmpty()) active.add(event.toStrokePoint(strokeStartMs))
                }
                PenEventType.Release -> {
                    if (active.size >= 2) {
                        val finished = PenStroke(brush = brush, points = active.toList())
                        state.appendStrokes(listOf(finished))
                        onStrokesFinished(listOf(finished))
                    }
                    active.clear()
                }
                PenEventType.Hover -> Unit
            }
        },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            state.finishedStrokes.forEach { stroke ->
                drawPenStroke(stroke.points, stroke.brush)
            }
            if (active.isNotEmpty()) {
                drawPenStroke(predictNextPoint(active), brush)
            }
        }
        content()
    }
}

private fun PenEvent.toStrokePoint(strokeStartMs: Long): PenStrokePoint =
    PenStrokePoint(
        x = x.toFloat(),
        y = y.toFloat(),
        pressure = pressure.toFloat(),
        elapsedMillis = timestamp - strokeStartMs,
    )

// === Catmull-Rom smoothing ===

internal fun catmullRomSmooth(
    points: List<PenStrokePoint>,
    subdivisions: Int = SMOOTHING_SUBDIVISIONS,
): List<PenStrokePoint> {
    if (points.size < 2) return points
    // Pad endpoints by duplicating first and last so the Catmull-Rom kernel
    // (which needs a neighbor on each side) can interpolate every segment.
    val padded = ArrayList<PenStrokePoint>(points.size + 2).apply {
        add(points.first())
        addAll(points)
        add(points.last())
    }
    val result = ArrayList<PenStrokePoint>(points.size * subdivisions)
    for (i in 1 until padded.size - 2) {
        val p0 = padded[i - 1]
        val p1 = padded[i]
        val p2 = padded[i + 1]
        val p3 = padded[i + 2]
        for (j in 0 until subdivisions) {
            val t = j.toFloat() / subdivisions
            result.add(catmullRomPoint(p0, p1, p2, p3, t))
        }
    }
    result.add(points.last())
    return result
}

private fun catmullRomPoint(
    p0: PenStrokePoint,
    p1: PenStrokePoint,
    p2: PenStrokePoint,
    p3: PenStrokePoint,
    t: Float,
): PenStrokePoint {
    val t2 = t * t
    val t3 = t2 * t
    val x = 0.5f * (
        (2f * p1.x) +
            (-p0.x + p2.x) * t +
            (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
            (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3
        )
    val y = 0.5f * (
        (2f * p1.y) +
            (-p0.y + p2.y) * t +
            (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
            (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
        )
    val pressure = p1.pressure + (p2.pressure - p1.pressure) * t
    val elapsed = p1.elapsedMillis + ((p2.elapsedMillis - p1.elapsedMillis) * t).toLong()
    return PenStrokePoint(x, y, pressure, elapsed)
}

// === Prediction (linear extrapolation, ~1 frame ahead) ===

internal fun predictNextPoint(points: List<PenStrokePoint>): List<PenStrokePoint> {
    if (points.size < 2) return points.toList()
    val last = points.last()
    val prev = points[points.size - 2]
    val deltaT = last.elapsedMillis - prev.elapsedMillis
    if (deltaT <= 0) return points.toList()
    val ratio = PREDICT_FRAME_MS.toFloat() / deltaT
    val predicted = PenStrokePoint(
        x = last.x + (last.x - prev.x) * ratio,
        y = last.y + (last.y - prev.y) * ratio,
        pressure = last.pressure,
        elapsedMillis = last.elapsedMillis + PREDICT_FRAME_MS,
    )
    return points.toList() + predicted
}

// === Rendering ===

private fun DrawScope.drawPenStroke(points: List<PenStrokePoint>, brush: PenBrush) {
    if (points.size < 2) return
    val smoothed = catmullRomSmooth(points)
    val color = colorFor(brush)
    for (i in 1 until smoothed.size) {
        val a = smoothed[i - 1]
        val b = smoothed[i]
        val avgPressure = (a.pressure + b.pressure) / 2f
        drawLine(
            color = color,
            start = Offset(a.x, a.y),
            end = Offset(b.x, b.y),
            strokeWidth = strokeWidthFor(brush, avgPressure),
            cap = StrokeCap.Round,
        )
    }
}

private fun strokeWidthFor(brush: PenBrush, pressure: Float): Float = when (brush.family) {
    PenBrushFamily.Pen -> brush.size * (PEN_MIN_WIDTH_FACTOR + pressure * PEN_PRESSURE_FACTOR)
    PenBrushFamily.Marker -> brush.size
    PenBrushFamily.Highlighter -> brush.size
}

private fun colorFor(brush: PenBrush): Color = when (brush.family) {
    PenBrushFamily.Pen, PenBrushFamily.Marker -> brush.color
    PenBrushFamily.Highlighter -> brush.color.copy(alpha = brush.color.alpha * HIGHLIGHTER_ALPHA)
}

private const val SMOOTHING_SUBDIVISIONS: Int = 8
private const val PREDICT_FRAME_MS: Long = 16L
private const val PEN_MIN_WIDTH_FACTOR: Float = 0.3f
private const val PEN_PRESSURE_FACTOR: Float = 1.4f
private const val HIGHLIGHTER_ALPHA: Float = 0.3f
