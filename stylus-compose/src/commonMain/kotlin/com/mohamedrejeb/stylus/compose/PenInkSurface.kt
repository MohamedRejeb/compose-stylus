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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType
import com.mohamedrejeb.stylus.compose.prediction.PenEventPredictor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Low-latency stylus drawing surface.
 *
 * On **Android** this is backed by [Jetpack Ink](https://developer.android.com/jetpack/androidx/releases/ink)
 * (`androidx.ink:ink-authoring-compose`), which renders in-progress strokes
 * through a front-buffered `SurfaceControl` for sub-frame latency, with
 * built-in motion prediction.
 *
 * On **Desktop / iOS / Web** the surface uses a pure-Compose pipeline with
 * Catmull-Rom smoothing and a Kalman-filter motion predictor (a faithful
 * port of `androidx.input.motionprediction` — same algorithm and tuning
 * constants as the platform predictor Jetpack Ink uses on Android, just
 * not the OS-level front-buffer compositor bypass).
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
 * Catmull-Rom smoothing and Kalman-filter motion prediction (via
 * [PenEventPredictor]) during in-progress drawing, and emits a [PenStroke]
 * on each release.
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
    val predictor = remember { PenEventPredictor() }
    var strokeStartMs by remember { mutableStateOf(0L) }

    fun finalizeActiveStroke() {
        if (active.size >= 2) {
            val finished = PenStroke(brush = brush, points = active.toList())
            state.appendStrokes(listOf(finished))
            onStrokesFinished(listOf(finished))
        }
        predictor.reset()
        active.clear()
    }

    Box(
        modifier = modifier.penInput { event ->
            onPenEvent(event)
            when (event.type) {
                PenEventType.Press -> {
                    strokeStartMs = event.timestamp
                    predictor.reset()
                    active.clear()
                    val point = event.toStrokePoint(strokeStartMs)
                    predictor.record(point)
                    active.add(point)
                }
                PenEventType.Move -> {
                    if (active.isNotEmpty()) {
                        // A Move with zero pressure means the stylus isn't
                        // in contact, regardless of whether the platform
                        // fired a clean Release. Browsers in particular
                        // sometimes dispatch synthesised mousemoves while a
                        // pen hovers after lift without ever firing
                        // mouseup, and we'd otherwise keep extending the
                        // stroke into the post-release hover trail. Treat
                        // it as drag-end: finalise whatever stroke we have
                        // and stop appending until the next real Press.
                        if (event.pressure <= 0.0) {
                            finalizeActiveStroke()
                        } else {
                            val point = event.toStrokePoint(strokeStartMs)
                            predictor.record(point)
                            active.add(point)
                        }
                    }
                }
                PenEventType.Release -> {
                    finalizeActiveStroke()
                }
                PenEventType.Hover -> Unit
            }
        },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // Finished strokes — use cached smoothed points (computed once
            // per stroke on first access, then reused every frame).
            state.finishedStrokes.forEach { stroke ->
                drawSmoothedStroke(stroke.smoothedPoints, stroke.brush)
            }
            // Active stroke — index-copy to a stable list before iterating.
            // `active` is a SnapshotStateList; iterating it directly via
            // `active + predicted` (Kotlin `Collection.plus` → `addAll` →
            // `toArray` → fail-fast iterator) would throw
            // ConcurrentModificationException if any other thread mutates
            // the list mid-iteration.
            val snapshot = active.snapshotPoints()
            if (snapshot.isNotEmpty()) {
                val predicted = predictor.predict()
                val toDraw = if (predicted != null) snapshot + predicted else snapshot
                drawSmoothedStroke(catmullRomSmooth(toDraw), brush)
            }
        }
        content()
    }
}

/**
 * Copy [this] to a stable [List] via index access. Avoids `Iterator`-based
 * traversal so a concurrent write can't trigger fail-fast
 * `ConcurrentModificationException` mid-iteration. The size is sampled once
 * up-front; if a write extends or shrinks the list while we're copying we
 * simply observe a slightly stale snapshot rather than crash — preferable
 * to losing the whole frame.
 */
private fun List<PenStrokePoint>.snapshotPoints(): List<PenStrokePoint> {
    val n = size
    if (n == 0) return emptyList()
    val out = ArrayList<PenStrokePoint>(n)
    for (i in 0 until n) {
        out.add(getOrNull(i) ?: break)
    }
    return out
}

private fun PenEvent.toStrokePoint(strokeStartMs: Long): PenStrokePoint =
    PenStrokePoint(
        x = x.toFloat(),
        y = y.toFloat(),
        pressure = pressure.toFloat(),
        elapsedMillis = timestamp - strokeStartMs,
        tiltX = tiltX.toFloat(),
        tiltY = tiltY.toFloat(),
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
    // Tilt is interpolated linearly in Cartesian (tiltX, tiltY) space rather
    // than as polar (magnitude, angle). Linear interp on polar would wrap
    // discontinuously across ±π; linear interp on Cartesian is smooth and
    // also tracks pen rotation more naturally — the tilt vector slides
    // through the origin on direction reversals instead of jumping.
    val tiltX = p1.tiltX + (p2.tiltX - p1.tiltX) * t
    val tiltY = p1.tiltY + (p2.tiltY - p1.tiltY) * t
    return PenStrokePoint(x, y, pressure, elapsed, tiltX, tiltY)
}

// === Rendering ===

/**
 * Render an already-smoothed point list as a single varying-width filled
 * ribbon, with semicircular caps at both ends.
 *
 * The Catmull-Rom pass is intentionally left to the caller: finished strokes
 * hand in their cached `smoothedPoints` (computed once and stored on the
 * immutable [PenStroke]), while the active stroke runs `catmullRomSmooth`
 * per frame because it grows each event.
 *
 * The ribbon is built by walking the smoothed point list and offsetting each
 * sample by ±half-width along the local perpendicular. Walking the left edge
 * forward, capping at the end, and walking the right edge back gives a
 * single closed path that fills with one `drawPath` call. This is the same
 * shape Jetpack Ink's `CanvasStrokeRenderer` produces — width varies
 * continuously between samples instead of stepping per `drawLine` segment,
 * and there is no internal overlap, so translucent brushes (highlighter)
 * don't darken at sample joints.
 */
private fun DrawScope.drawSmoothedStroke(smoothed: List<PenStrokePoint>, brush: PenBrush) {
    if (smoothed.size < 2) return
    val color = colorFor(brush)
    val n = smoothed.size

    // Pen brushes thin out at high speed for a natural ink feel — Marker and
    // Highlighter keep constant width because that matches their physical
    // counterparts (a marker tip lays the same line whether you flick or
    // crawl). Velocity is sampled over a window wider than one Catmull-Rom
    // subdivision so the modulation tracks the input gesture, not the
    // smoothing artifacts.
    val velocity: FloatArray? =
        if (brush.family == PenBrushFamily.Pen) computeVelocity(smoothed) else null

    // Per-sample perpendicular (unit). Half-width is filled in below after
    // tangents are known, so the calligraphic tilt factor can use the
    // dot-product of stroke tangent and tilt direction.
    val perpX = FloatArray(n)
    val perpY = FloatArray(n)
    val halfW = FloatArray(n)

    val tiltShaped = brush.family == PenBrushFamily.Pen
    for (i in 0 until n) {
        // Tangent: forward diff at endpoints, centered diff in the interior.
        // Centered tangents give continuous offsets across joins, so the
        // ribbon edge stays smooth where width changes.
        val dx: Float
        val dy: Float
        when (i) {
            0 -> {
                dx = smoothed[1].x - smoothed[0].x
                dy = smoothed[1].y - smoothed[0].y
            }
            n - 1 -> {
                dx = smoothed[n - 1].x - smoothed[n - 2].x
                dy = smoothed[n - 1].y - smoothed[n - 2].y
            }
            else -> {
                dx = smoothed[i + 1].x - smoothed[i - 1].x
                dy = smoothed[i + 1].y - smoothed[i - 1].y
            }
        }
        val len = sqrt(dx * dx + dy * dy)
        val tx: Float
        val ty: Float
        if (len > MIN_TANGENT_LEN) {
            tx = dx / len
            ty = dy / len
            perpX[i] = -ty
            perpY[i] = tx
        } else {
            tx = 0f
            ty = 0f
            perpX[i] = 0f
            perpY[i] = 0f
        }

        var w = strokeWidthFor(brush, smoothed[i].pressure) / 2f
        if (velocity != null) w *= velocityWidthFactor(velocity[i])
        if (tiltShaped) w *= tiltWidthFactor(smoothed[i].tiltX, smoothed[i].tiltY, tx, ty)
        halfW[i] = w
    }

    val path = Path()

    // Forward along the "left" edge.
    val first = smoothed[0]
    path.moveTo(first.x + perpX[0] * halfW[0], first.y + perpY[0] * halfW[0])
    for (i in 1 until n) {
        val p = smoothed[i]
        path.lineTo(p.x + perpX[i] * halfW[i], p.y + perpY[i] * halfW[i])
    }

    // End cap: semicircle from left[n-1] to right[n-1], bulging in the
    // direction of motion. Compose's arcTo follows Skia conventions —
    // angles are measured from +x, increasing clockwise (since y is down).
    val end = smoothed[n - 1]
    val rEnd = halfW[n - 1]
    if (rEnd > 0f) {
        val endAngle = atan2(perpY[n - 1], perpX[n - 1]) * RAD_TO_DEG
        path.arcTo(
            rect = Rect(
                offset = Offset(end.x - rEnd, end.y - rEnd),
                size = Size(rEnd * 2f, rEnd * 2f),
            ),
            startAngleDegrees = endAngle,
            sweepAngleDegrees = -180f,
            forceMoveTo = false,
        )
    }

    // Back along the "right" edge.
    for (i in n - 2 downTo 0) {
        val p = smoothed[i]
        path.lineTo(p.x - perpX[i] * halfW[i], p.y - perpY[i] * halfW[i])
    }

    // Start cap: semicircle from right[0] back to left[0], bulging away
    // from the direction of motion.
    val rStart = halfW[0]
    if (rStart > 0f) {
        val startAngle = atan2(-perpY[0], -perpX[0]) * RAD_TO_DEG
        path.arcTo(
            rect = Rect(
                offset = Offset(first.x - rStart, first.y - rStart),
                size = Size(rStart * 2f, rStart * 2f),
            ),
            startAngleDegrees = startAngle,
            sweepAngleDegrees = -180f,
            forceMoveTo = false,
        )
    }

    path.close()
    drawPath(path = path, color = color)
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

/**
 * Per-sample stylus speed, in pixels per millisecond.
 *
 * Sampled over a window rather than adjacent pairs because Catmull-Rom
 * subdivision shrinks the dt between consecutive smoothed points by ~8x —
 * an adjacent-pair velocity would amplify timing jitter from `currentTimeMillis`
 * resolution. The window straddles a full input segment, so the estimate
 * tracks the gesture's actual speed rather than the smoother's interpolation.
 */
private fun computeVelocity(points: List<PenStrokePoint>): FloatArray {
    val n = points.size
    val out = FloatArray(n)
    for (i in 0 until n) {
        val lo = (i - VELOCITY_WINDOW).coerceAtLeast(0)
        val hi = (i + VELOCITY_WINDOW).coerceAtMost(n - 1)
        if (hi == lo) continue
        val a = points[lo]
        val b = points[hi]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dist = sqrt(dx * dx + dy * dy)
        // Guard against zero/negative dt — `elapsedMillis` is monotonic but
        // the source clock has finite resolution, so adjacent points can
        // share a timestamp on fast devices.
        val dt = (b.elapsedMillis - a.elapsedMillis).coerceAtLeast(1L).toFloat()
        out[i] = dist / dt
    }
    return out
}

/**
 * Map a per-sample speed (px/ms) to a multiplicative width factor.
 *
 * Smooth roll-off: factor = 1 / (1 + v · k). Slow strokes (v ≈ 0) keep their
 * full width; fast strokes thin out asymptotically toward
 * [VELOCITY_MIN_FACTOR]. The shape is monotonic and has no abrupt knee, so
 * a stroke that decelerates feels like ink pooling rather than a discrete
 * width step.
 */
private fun velocityWidthFactor(velocity: Float): Float {
    val factor = 1f / (1f + velocity * VELOCITY_THINNING_K)
    return factor.coerceIn(VELOCITY_MIN_FACTOR, 1f)
}

/**
 * Calligraphic-nib width factor: thin when the stroke moves along the tilt
 * direction, full-width when it moves across it.
 *
 * Models a chisel-tip pen: the nib is broader perpendicular to the lean
 * direction, so a stroke that runs *with* the lean sweeps the narrow side of
 * the nib (thin) while a stroke that runs *across* the lean sweeps the broad
 * side (thick). The factor uses `(t · tilt_unit)²` — squared so the falloff
 * is smooth around zero crossings (parallel ↔ perpendicular) rather than
 * V-shaped, which would put a visible kink in the stroke as direction
 * rotates.
 *
 * Effect strength scales with `|tilt|` (radians), normalised by π/2: a
 * vertical pen leaves the factor at 1.0 (no calligraphic effect), a fully
 * laid-down pen reaches the maximum modulation. Returns 1.0 immediately when
 * tilt is unavailable or zero, so platforms that don't report tilt
 * (Compose's Android/iOS modifier paths today) keep their existing circular
 * stroke shape.
 */
private fun tiltWidthFactor(
    tiltX: Float,
    tiltY: Float,
    tangentX: Float,
    tangentY: Float,
): Float {
    val tiltMag2 = tiltX * tiltX + tiltY * tiltY
    if (tiltMag2 < TILT_MIN_MAG_SQ) return 1f
    val tiltMag = sqrt(tiltMag2)
    val strength = (tiltMag / TILT_HALF_PI).coerceIn(0f, 1f)
    val tdx = tiltX / tiltMag
    val tdy = tiltY / tiltMag
    // Dot product of the unit stroke tangent with the unit tilt direction —
    // |t · tilt_unit| = |cos(angle between them)|.
    val dot = tangentX * tdx + tangentY * tdy
    val alignment = dot * dot
    return 1f - strength * (1f - TILT_MIN_FACTOR) * alignment
}

private const val SMOOTHING_SUBDIVISIONS: Int = 8
private const val PEN_MIN_WIDTH_FACTOR: Float = 0.3f
private const val PEN_PRESSURE_FACTOR: Float = 1.4f
private const val HIGHLIGHTER_ALPHA: Float = 0.3f
private const val MIN_TANGENT_LEN: Float = 1e-4f
private const val RAD_TO_DEG: Float = (180.0 / PI).toFloat()

// Velocity-based width modulation (Pen brush only). The window is roughly
// one Catmull-Rom subdivision so the speed estimate spans an input
// inter-sample interval. Thinning constant tuned so a typical fast drag
// (~2 px/ms ≈ 2000 px/s) thins to ~0.5×; tap-and-hold stays at 1.0×.
private const val VELOCITY_WINDOW: Int = 8
private const val VELOCITY_THINNING_K: Float = 0.5f
private const val VELOCITY_MIN_FACTOR: Float = 0.35f

// Tilt-based calligraphic width modulation (Pen brush only). Tilt magnitudes
// below √TILT_MIN_MAG_SQ rad (~3°) collapse to no effect — within reporting
// noise on most digitisers. TILT_HALF_PI normalises the lean magnitude so
// fully-laid-down (~π/2) reaches max modulation. TILT_MIN_FACTOR is the floor
// for strokes parallel to a fully-tilted pen — chisel pens go thin but never
// disappear.
private const val TILT_MIN_MAG_SQ: Float = 0.0025f
private const val TILT_HALF_PI: Float = (PI / 2.0).toFloat()
private const val TILT_MIN_FACTOR: Float = 0.4f
