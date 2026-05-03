package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Outline-based stroke renderer ported from the
 * [perfect-freehand](https://github.com/steveruizok/perfect-freehand) algorithm
 * (Steve Ruiz), as adapted in `kanvas-freehand` for Compose Multiplatform.
 *
 * Where the [tessellateRibbon] pipeline emits a triangle mesh for
 * `drawVertices`, this pipeline emits a Compose [Path] of the stroke's outline
 * — left and right tracks stitched with C1-continuous quadratic Beziers and
 * closed at each end with a cubic-Bezier semicircle. The result is fed to
 * `drawPath`, which gets Skia's coverage AA "for free" and avoids the
 * polygonal corner artefacts that show up on the GPU triangle rasteriser.
 *
 * Pipeline:
 *  1. [computeStrokePoints] — input streamline smoothing, head/tail trim,
 *     near-duplicate dedup. Tracks per-point distance, running length, and
 *     unit tangent.
 *  2. [applyStrokePointRadii] — pressure → radius mapping with start-of-line
 *     pressure ramp-up and optional taper at start/end. The pressure curve
 *     and `thinning` factor are sourced from the brush family.
 *  3. [partitionAtElbows] — splits the stroke at sharp elbows so each
 *     partition can be rendered as its own closed sub-path. This is the
 *     trick that makes hard zigzag corners read clean instead of bending a
 *     single ribbon around them.
 *  4. [computeOutlineTracks] — walks each partition, projecting left and
 *     right tracks at the per-point radius. Sharp internal corners get an
 *     in-place fan instead of a single offset.
 *  5. [renderPartition] — emits the actual `Path` ops: `quadraticTo` between
 *     midpoints (with reflected control points for C1 continuity) along the
 *     left track, a [semicircularArcTo] at the end cap, the same back along
 *     the right track, and another `semicircularArcTo` at the start cap.
 *
 * The whole file is pure Kotlin / Compose Multiplatform — no Skia imports —
 * so it works identically across JVM, iOS, Web (and Android, though
 * Android's `PenInkSurface` actual still routes finished strokes through
 * Jetpack Ink and only invokes this engine when explicitly requested).
 */
internal fun tessellateSmoothPath(
    points: List<PenStrokePoint>,
    brush: PenBrush,
): Path {
    if (points.isEmpty()) return Path()
    val options = brush.toFreehandOptions()
    val strokePoints = computeStrokePoints(points, options)
    if (strokePoints.isEmpty()) return Path()
    applyStrokePointRadii(strokePoints, options)
    val path = Path()
    val partitions = partitionAtElbows(strokePoints)
    for (partition in partitions) {
        path.renderPartition(partition, options)
    }
    return path
}

// ──────────────────────────────────────────────────────────────────────────
// Internal types
// ──────────────────────────────────────────────────────────────────────────

/**
 * Internal pipeline element. Mutable on purpose — `applyStrokePointRadii`
 * walks the list and writes back per-point radii, and `cleanUpPartition`
 * patches up endpoint vectors after trimming. Both are local concerns; this
 * type never leaks to the public API.
 */
private class FreehandStrokePoint(
    var point: Offset,
    val input: Offset,
    var vector: Offset,
    var pressure: Float,
    val distance: Float,
    val runningLength: Float,
    var radius: Float,
)

/**
 * Bag of tuning knobs the freehand algorithm consumes. Per-brush defaults
 * live in [PenBrush.toFreehandOptions]; we don't expose this type publicly
 * because the public API is "pick a brush family", not "pick 9 floats".
 */
private class FreehandOptions(
    val size: Float,
    val thinning: Float,
    val smoothing: Float,
    val streamline: Float,
    val simulatePressure: Boolean,
    val easing: (Float) -> Float,
    val taperStart: Float,
    val taperEnd: Float,
    val taperStartEasing: (Float) -> Float,
    val taperEndEasing: (Float) -> Float,
    val last: Boolean,
)

/**
 * Map our public [PenBrush] to the freehand algorithm's tuning knobs.
 *
 * Values aligned with [tldraw's `getPath.ts`](https://github.com/tldraw/tldraw/blob/main/packages/tldraw/src/lib/shapes/draw/getPath.ts):
 *
 *  - **Pen** mirrors `realPressureSettings` — `0.62` across thinning,
 *    streamline, and smoothing pairs with the perfect-freehand "pen"
 *    pressure curve to give a calligraphic feel that responds to light
 *    contact without saturating at firm pressure.
 *  - **Marker** mirrors `solidSettings` — constant width (`thinning = 0`),
 *    `0.62` smoothing, and `0.7` streamline (the midpoint of tldraw's
 *    width-dependent `modulate` ramp) for a clean inked feel.
 *  - **Highlighter** mirrors `getHighlightFreehandSettings` — constant
 *    width with `easeOutSine` taper, `0.5` streamline / smoothing so
 *    quick highlighter sweeps follow the input closely.
 *
 * `simulatePressure = false` everywhere because we always have real
 * pressure data from the platform; tldraw flips it on only for mouse
 * input where the velocity-based fallback is the only signal.
 */
private fun PenBrush.toFreehandOptions(): FreehandOptions = when (family) {
    PenBrushFamily.Pen -> FreehandOptions(
        size = size,
        thinning = 0.62f,
        smoothing = 0.62f,
        streamline = 0.62f,
        simulatePressure = false,
        easing = ::easePen,
        taperStart = 0f,
        taperEnd = 0f,
        taperStartEasing = ::easeOutQuad,
        taperEndEasing = ::easeOutCubic,
        last = true,
    )
    PenBrushFamily.Marker -> FreehandOptions(
        size = size,
        thinning = 0f,
        smoothing = 0.62f,
        streamline = 0.7f,
        simulatePressure = false,
        easing = ::easeLinear,
        taperStart = 0f,
        taperEnd = 0f,
        taperStartEasing = ::easeOutQuad,
        taperEndEasing = ::easeOutCubic,
        last = true,
    )
    PenBrushFamily.Highlighter -> FreehandOptions(
        size = size,
        thinning = 0f,
        smoothing = 0.5f,
        streamline = 0.5f,
        simulatePressure = false,
        easing = ::easeOutSine,
        taperStart = 0f,
        taperEnd = 0f,
        taperStartEasing = ::easeOutQuad,
        taperEndEasing = ::easeOutCubic,
        last = true,
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Stage 1 — getStrokePoints
// ──────────────────────────────────────────────────────────────────────────

/**
 * Dedupe / smooth the raw input points and pre-compute per-point geometry
 * (vector, distance, runningLength).
 *
 *  - Clamps any sub-[MIN_PRESSURE] samples up to [MIN_PRESSURE] (matches
 *    [tldraw's robustness fix](https://github.com/tldraw/tldraw/blob/main/packages/tldraw/src/lib/shapes/shared/freehand/getStrokePoints.ts)
 *    over the original perfect-freehand strip-from-ends behaviour) —
 *    some pens / OSes report `pressure = 0` even while the pen is in
 *    contact, and stripping those points lost real input.
 *  - Drops samples that are within `size/3` of the first/last point — these
 *    are the "stop noise" frames the digitiser emits while you press down
 *    or lift.
 *  - Streamline: each sample is lerped toward the previous output by
 *    `1 − t` where `t = 0.15 + (1 − streamline) · 0.85`. This is an
 *    exponential moving average; it filters high-frequency jitter while
 *    leaving the gesture's underlying trajectory intact.
 */
private fun computeStrokePoints(
    points: List<PenStrokePoint>,
    options: FreehandOptions,
): MutableList<FreehandStrokePoint> {
    val streamline = options.streamline
    val size = options.size
    val simulatePressure = options.simulatePressure

    if (points.isEmpty()) return mutableListOf()

    val t = 0.15f + (1f - streamline) * 0.85f

    // Local mutable copy of (offset, pressure) pairs — avoids touching the
    // input list and lets us trim/insert freely.
    data class P(var offset: Offset, var pressure: Float)
    val pts = points.mapTo(mutableListOf()) { P(Offset(it.x, it.y), it.pressure) }
    var pointsRemovedFromNearEnd = 0

    if (!simulatePressure) {
        // Clamp (don't strip) sub-MIN_PRESSURE samples. Stripping caused
        // real input to disappear when the pen reported `pressure = 0`
        // mid-stroke, which some OSes / pens do; clamping keeps the
        // sample but writes a faint stroke at that spot.
        for (p in pts) {
            if (p.pressure < MIN_PRESSURE) p.pressure = MIN_PRESSURE
        }
    }

    val firstInput = points.first()
    if (pts.isEmpty()) {
        return mutableListOf(
            FreehandStrokePoint(
                point = Offset(firstInput.x, firstInput.y),
                input = Offset(firstInput.x, firstInput.y),
                vector = Offset(1f, 1f),
                pressure = if (simulatePressure) 0.5f else 0.15f,
                distance = 0f,
                runningLength = 0f,
                radius = 1f,
            ),
        )
    }

    // Strip points near the first sample, accumulating their max pressure
    // into pts[0] so a firm-tap-then-skim doesn't lose the press intent.
    val sizeThirdSq = (size / 3f).pow(2)
    while (pts.size > 1 && pts[1].offset.dist2(pts[0].offset) <= sizeThirdSq) {
        pts[0].pressure = max(pts[0].pressure, pts[1].pressure)
        pts.removeAt(1)
    }

    if (pts.size > 1) {
        val last = pts.removeAt(pts.size - 1)
        while (pts.isNotEmpty() && pts.last().offset.dist2(last.offset) <= sizeThirdSq) {
            pts.removeAt(pts.size - 1)
            pointsRemovedFromNearEnd++
        }
        pts.add(last)
    }

    val isComplete = options.last ||
        !options.simulatePressure ||
        (pts.size > 1 && pts.last().offset.dist2(pts[pts.lastIndex - 1].offset) < size.pow(2)) ||
        pointsRemovedFromNearEnd > 0

    // Two-point degenerate case for tap → drag → release: synthesise four
    // intermediate samples between the two so the renderer doesn't draw a
    // "dash". Only meaningful with simulatePressure; we leave the branch
    // intact to mirror the upstream algorithm exactly.
    if (pts.size == 2 && options.simulatePressure) {
        val first = pts[0]
        val last = pts[1]
        pts.removeAt(1)
        for (i in 1 until 5) {
            val frac = i / 4f
            val interp = first.offset + (last.offset - first.offset) * frac
            val pressure = ((first.pressure + (last.pressure - first.pressure)) * i) / 4f
            pts.add(P(interp, pressure))
        }
    }

    val firstPts = pts.firstOrNull()
    val lastPts = pts.lastOrNull()
    if (firstPts == null || lastPts == null) {
        return mutableListOf(
            FreehandStrokePoint(
                point = Offset(firstInput.x, firstInput.y),
                input = Offset(firstInput.x, firstInput.y),
                vector = Offset(1f, 1f),
                pressure = if (simulatePressure) 0.5f else 0.15f,
                distance = 0f,
                runningLength = 0f,
                radius = 1f,
            ),
        )
    }

    val strokePoints = mutableListOf(
        FreehandStrokePoint(
            point = firstPts.offset,
            input = firstPts.offset,
            vector = Offset(1f, 1f),
            pressure = if (simulatePressure) 0.5f else firstPts.pressure,
            distance = 0f,
            runningLength = 0f,
            radius = 1f,
        ),
    )

    var totalLength = 0f
    var prev = strokePoints.first()

    // The duplicated last sample lets the streamlined output reach all the
    // way to the input's last point even when the EMA would otherwise lag
    // a fraction behind.
    if (isComplete && streamline > 0f) {
        pts.add(P(lastPts.offset, lastPts.pressure))
    }

    for (i in 1 until pts.size) {
        val current = pts[i]
        val pt = if (t == 0f || (options.last && i == pts.lastIndex)) {
            current.offset
        } else {
            current.offset + (prev.point - current.offset) * (1f - t)
        }
        if (prev.point == pt) continue

        val distance = pt.dist(prev.point)
        totalLength += distance

        // Skip the very-first short hairs while the cumulative length is
        // still under one stroke width — they tend to be jitter from the
        // press-down event chain rather than meaningful motion.
        if (i < 4 && totalLength < size) continue

        prev = FreehandStrokePoint(
            point = pt,
            input = current.offset,
            vector = (prev.point - pt).uni(),
            pressure = if (simulatePressure) 0.5f else current.pressure,
            distance = distance,
            runningLength = totalLength,
            radius = 1f,
        )
        strokePoints.add(prev)
    }

    if (strokePoints.size > 1) {
        // Tangent at sample 0 isn't well defined from the input alone; copy
        // the next sample's tangent so the start-cap orientation matches
        // the line direction.
        strokePoints[0].vector = strokePoints[1].vector
    }

    if (totalLength < 1f) {
        val maxP = strokePoints.fold(0.5f) { acc, sp -> max(acc, sp.pressure) }
        for (sp in strokePoints) sp.pressure = maxP
    }

    return strokePoints
}

// ──────────────────────────────────────────────────────────────────────────
// Stage 2 — setStrokePointRadii
// ──────────────────────────────────────────────────────────────────────────

/**
 * Map per-point pressure (or simulated velocity) to a per-point radius and
 * apply optional start/end tapers in place.
 *
 *  - Initial pressure: pre-rolled over the first few samples so a fast
 *    drag-out from a stationary tap doesn't show as a single fat dot.
 *    [RATE_OF_PRESSURE_CHANGE] caps how quickly the per-frame pressure
 *    can move toward the new sample's value.
 *  - Radius formula: `size · easing(0.5 − thinning · (0.5 − pressure))`
 *    so `thinning = 0` gives a constant `size · easing(0.5)`. The easing
 *    function is per-brush — `pen` curves wider at low pressure to keep
 *    light strokes visible, others are linear.
 *  - Taper: at the start, multiplies radius by `taperStartEasing(rl /
 *    taperStart)`; at the end by `taperEndEasing((total − rl) / taperEnd)`.
 *    Default tapers are 0 (no taper) for a marker / pen feel; flip them on
 *    if you want a calligraphic taper.
 */
private fun applyStrokePointRadii(
    strokePoints: MutableList<FreehandStrokePoint>,
    options: FreehandOptions,
) {
    if (strokePoints.isEmpty()) return

    val size = options.size
    val thinning = options.thinning
    val simulatePressure = options.simulatePressure
    val easing = options.easing

    val totalLength = strokePoints.last().runningLength

    // Tiny strokes (length below one base size) get a uniform radius based
    // on their max pressure. Avoids sub-stroke radius wobble that would
    // otherwise show as a visible bulge on a quick tap.
    if (!simulatePressure && totalLength < size) {
        val maxP = strokePoints.fold(0.5f) { acc, sp -> max(acc, sp.pressure) }
        for (sp in strokePoints) {
            sp.pressure = maxP
            sp.radius = size * easing(0.5f - thinning * (0.5f - sp.pressure))
        }
        applyTapers(strokePoints, options, totalLength)
        return
    }

    var prevPressure = strokePoints[0].pressure

    // Two-pass pressure roll: first a "pre-roll" that smooths the leading
    // few-stroke-widths (so a slow press doesn't start with a jitter) then
    // the main pass that writes radii.
    for (sp in strokePoints) {
        if (sp.runningLength > size * 5f) break
        val sp1 = min(1f, sp.distance / size)
        val p = if (simulatePressure) {
            val rp = min(1f, 1f - sp1)
            min(1f, prevPressure + (rp - prevPressure) * (sp1 * RATE_OF_PRESSURE_CHANGE))
        } else {
            min(1f, prevPressure + (sp.pressure - prevPressure) * 0.5f)
        }
        prevPressure = prevPressure + (p - prevPressure) * 0.5f
    }

    for (sp in strokePoints) {
        if (thinning != 0f) {
            val sp1 = min(1f, sp.distance / size)
            val pressure = if (simulatePressure) {
                val rp = min(1f, 1f - sp1)
                min(1f, prevPressure + (rp - prevPressure) * (sp1 * RATE_OF_PRESSURE_CHANGE))
            } else {
                min(1f, prevPressure + (sp.pressure - prevPressure) * (sp1 * RATE_OF_PRESSURE_CHANGE))
            }
            sp.radius = size * easing(0.5f - thinning * (0.5f - pressure))
            prevPressure = pressure
        } else {
            sp.radius = size / 2f
        }
    }

    applyTapers(strokePoints, options, totalLength)
}

private fun applyTapers(
    strokePoints: MutableList<FreehandStrokePoint>,
    options: FreehandOptions,
    totalLength: Float,
) {
    val taperStart = options.taperStart
    val taperEnd = options.taperEnd
    if (taperStart == 0f && taperEnd == 0f) return

    val tStart = options.taperStartEasing
    val tEnd = options.taperEndEasing
    for (sp in strokePoints) {
        val rl = sp.runningLength
        val ts = if (rl < taperStart) tStart(rl / taperStart) else 1f
        val te = if (totalLength - rl < taperEnd) tEnd((totalLength - rl) / taperEnd) else 1f
        sp.radius = max(0.01f, sp.radius * min(ts, te))
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Stage 3 — partitionAtElbows
// ──────────────────────────────────────────────────────────────────────────

/**
 * Split the stroke at sharp elbows (zigzag-style direction reversals) so
 * each partition can be rendered as its own closed sub-path.
 *
 * Without partitioning, the left/right tracks try to bend a single ribbon
 * around a 170°-ish reversal and end up either crossing themselves on the
 * inside corner or producing a long thin spike on the outside — exactly
 * the artefact the [tessellateRibbon] pipeline still has at thick widths.
 * The freehand approach side-steps it by closing the ribbon at the elbow
 * with a cap and starting a fresh ribbon for the next leg.
 *
 *  - `dpr < −0.8` → hard elbow (≥ ~143° turn). Always partition; use the
 *    raw input position (not the streamlined point) as the elbow vertex
 *    to keep a "swooshy" shape instead of a flat bevel.
 *  - `dpr in (0.7, 1.0]` → smooth turn, no partition.
 *  - In between → partition only when neighbours are close enough that the
 *    geometry can't smoothly bend around the corner anyway.
 */
private fun partitionAtElbows(
    points: List<FreehandStrokePoint>,
): List<List<FreehandStrokePoint>> {
    if (points.size <= 2) return listOf(points)
    val result = mutableListOf<List<FreehandStrokePoint>>()
    var current = mutableListOf(points.first())
    var prevV = (points[1].point - points[0].point).uni()

    for (i in 1 until points.lastIndex) {
        val prevPoint = points[i - 1]
        val thisPoint = points[i]
        val nextPoint = points[i + 1]
        val nextV = (nextPoint.point - thisPoint.point).uni()
        val dpr = prevV.dpr(nextV)
        prevV = nextV

        if (dpr < -0.8f) {
            val elbow = FreehandStrokePoint(
                point = thisPoint.input,
                input = thisPoint.input,
                vector = thisPoint.vector,
                pressure = thisPoint.pressure,
                distance = thisPoint.distance,
                runningLength = thisPoint.runningLength,
                radius = thisPoint.radius,
            )
            current.add(elbow)
            result.add(cleanUpPartition(current))
            current = mutableListOf(elbow)
            continue
        }

        current.add(thisPoint)

        if (dpr > 0.7f) continue

        val avgRadius = (prevPoint.radius + thisPoint.radius + nextPoint.radius) / 3f
        val ratio = (prevPoint.point.dist2(thisPoint.point) + thisPoint.point.dist2(nextPoint.point)) /
            (avgRadius * avgRadius)
        if (ratio < 1.5f) {
            current.add(thisPoint)
            result.add(cleanUpPartition(current))
            current = mutableListOf(thisPoint)
        }
    }

    current.add(points.last())
    result.add(cleanUpPartition(current))
    return result
}

/**
 * Trim points near a partition boundary that are within half-radius of
 * each other — they'd be hidden under the cap anyway and only confuse
 * the cap-orientation tangent. Then re-orient the boundary tangent so the
 * cap arcs draw through the geometry's actual edge.
 */
private fun cleanUpPartition(
    partition: MutableList<FreehandStrokePoint>,
): MutableList<FreehandStrokePoint> {
    val startPoint = partition.first()
    while (partition.size > 2) {
        val nextPoint = partition[1]
        val avgR = (startPoint.radius + nextPoint.radius) / 2f
        if (startPoint.point.dist2(nextPoint.point) < (avgR * 0.5f).pow(2)) {
            partition.removeAt(1)
        } else break
    }
    val endPoint = partition.last()
    while (partition.size > 2) {
        val prevPoint = partition[partition.size - 2]
        val avgR = (endPoint.radius + prevPoint.radius) / 2f
        if (endPoint.point.dist2(prevPoint.point) < (avgR * 0.5f).pow(2)) {
            partition.removeAt(partition.size - 2)
        } else break
    }
    if (partition.size > 1) {
        partition[0].vector = (partition[0].point - partition[1].point).uni()
        val last = partition.size - 1
        partition[last].vector = (partition[last - 1].point - partition[last].point).uni()
    }
    return partition
}

// ──────────────────────────────────────────────────────────────────────────
// Stage 4 — outline tracks
// ──────────────────────────────────────────────────────────────────────────

/**
 * Walk a partition and emit the left and right outline tracks at each
 * sample. Internal sharp corners get an in-place fan rotated around the
 * sample's input position; the rest of the stroke gets a single offset
 * along the bisector of the current and next tangents.
 *
 * `smoothing` controls a minimum-distance filter on each track: a candidate
 * point is dropped if it's within `(size · smoothing)²` of the last emitted
 * point on that side. This filters per-pixel noise that would otherwise
 * make the quadratic Bezier reconstruction wobble visibly.
 */
private fun computeOutlineTracks(
    strokePoints: List<FreehandStrokePoint>,
    options: FreehandOptions,
): Pair<MutableList<Offset>, MutableList<Offset>> {
    val size = options.size
    val smoothing = options.smoothing
    if (strokePoints.isEmpty() || size <= 0f) {
        return mutableListOf<Offset>() to mutableListOf()
    }

    val first = strokePoints.first()
    val last = strokePoints.last()
    val totalLength = last.runningLength
    val minDistance = (size * smoothing).pow(2)

    val leftPts = mutableListOf<Offset>()
    val rightPts = mutableListOf<Offset>()

    var prevVector = first.vector
    var pl = first.point
    var pr = pl
    var tl = pl
    var tr = pr
    var isPrevSharp = false

    for (i in strokePoints.indices) {
        val sp = strokePoints[i]
        val point = sp.point
        val vector = sp.vector

        val prevDpr = sp.vector.dpr(prevVector)
        val nextVector = if (i < strokePoints.size - 1) strokePoints[i + 1].vector else sp.vector
        val nextDpr = if (i < strokePoints.size - 1) nextVector.dpr(sp.vector) else 1f

        val isPointSharp = prevDpr < 0f && !isPrevSharp
        val isNextPointSharp = nextDpr < 0.2f

        if (isPointSharp || isNextPointSharp) {
            if (nextDpr > -0.62f && totalLength - sp.runningLength > sp.radius) {
                // Moderately sharp; project a single perpendicular offset
                // and keep walking. The cross product picks which side
                // gets the "inside" vs "outside" of the bend.
                val offset = prevVector * sp.radius
                val cpr = prevVector.cpr(nextVector)
                if (cpr < 0f) { tl = point + offset; tr = point - offset }
                else { tl = point - offset; tr = point + offset }
                leftPts.add(tl)
                rightPts.add(tr)
            } else {
                // Hard internal corner — fan 13 rotated points around the
                // input position. Each side fans through a half-turn so
                // the closed path comes back to the opposite outline.
                val offset = (prevVector * sp.radius).per()
                val start = sp.input - offset
                var t2 = 0f
                val step = 1f / 13f
                while (t2 < 1f) {
                    tl = start.rotAround(sp.input, FIXED_PI * t2)
                    leftPts.add(tl)
                    tr = start.rotAround(sp.input, FIXED_PI + FIXED_PI * (-t2))
                    rightPts.add(tr)
                    t2 += step
                }
            }
            pl = tl
            pr = tr
            if (isNextPointSharp) isPrevSharp = true
            continue
        }

        isPrevSharp = false

        if (sp === first || sp === last) {
            val offset = vector.per() * sp.radius
            leftPts.add(point - offset)
            rightPts.add(point + offset)
            continue
        }

        val offset = nextVector.lrp(vector, nextDpr).per() * sp.radius
        tl = point - offset
        if (i <= 1 || pl.dist2(tl) > minDistance) {
            leftPts.add(tl); pl = tl
        }
        tr = point + offset
        if (i <= 1 || pr.dist2(tr) > minDistance) {
            rightPts.add(tr); pr = tr
        }

        prevVector = vector
    }

    return leftPts to rightPts
}

// ──────────────────────────────────────────────────────────────────────────
// Stage 5 — render the partition into a Path
// ──────────────────────────────────────────────────────────────────────────

/**
 * Emit the actual `Path` ops for one partition.
 *
 * For ≥ 2 stroke points: walk the left track with quadratic Beziers
 * between midpoints, draw a half-disc cap at the end, walk the right
 * track back to the start, draw another half-disc cap, and close the
 * sub-path. Single-point partitions just draw a circle.
 *
 * The "midpoint quadratic with reflected control points" trick gives the
 * outline C1 continuity (matching tangents at every joint) without
 * needing actual Catmull-Rom evaluation. It's the same construction the
 * original perfect-freehand library uses to render to SVG.
 */
private fun Path.renderPartition(
    strokePoints: List<FreehandStrokePoint>,
    options: FreehandOptions,
) {
    if (strokePoints.isEmpty()) return
    if (strokePoints.size == 1) {
        val sp = strokePoints.first()
        circlePath(sp.point.x, sp.point.y, sp.radius)
        return
    }

    val (left, right) = computeOutlineTracks(strokePoints, options)
    right.reverse()

    if (left.isEmpty() || right.isEmpty()) return

    var previousControl = left[0]
    var current = previousControl
    moveTo(current.x, current.y)

    // Walk the left track unconditionally. The kanvas-freehand port had a
    // `left.size > 3` guard here that tldraw doesn't, which silently
    // dropped the left side of short strokes — the first iteration's
    // `quadraticTo` correctly degenerates to a `lineTo` because the
    // reflected control collapses to `current`, matching SVG's `T`
    // behaviour after `M`.
    for (i in 1 until left.size) {
        val end = average(left[i - 1], left[i])
        val reflected = Offset(
            2f * current.x - previousControl.x,
            2f * current.y - previousControl.y,
        )
        quadraticTo(reflected.x, reflected.y, end.x, end.y)
        previousControl = reflected
        current = end
    }

    val lastPoint = strokePoints.last()
    val endRadius = lastPoint.radius
    val endDir = -lastPoint.vector.per()
    val endArcStart = lastPoint.point + endDir * endRadius
    val endArcEnd = lastPoint.point + endDir * -endRadius

    var reflected = Offset(
        2f * current.x - previousControl.x,
        2f * current.y - previousControl.y,
    )
    quadraticTo(reflected.x, reflected.y, endArcStart.x, endArcStart.y)
    semicircularArcTo(endArcStart.x, endArcStart.y, endArcEnd.x, endArcEnd.y)

    current = endArcEnd
    previousControl = endArcEnd
    for (i in 1 until right.size) {
        val end = average(right[i - 1], right[i])
        reflected = Offset(
            2f * current.x - previousControl.x,
            2f * current.y - previousControl.y,
        )
        quadraticTo(reflected.x, reflected.y, end.x, end.y)
        previousControl = reflected
        current = end
    }

    val firstPoint = strokePoints.first()
    val startRadius = firstPoint.radius
    val startDir = firstPoint.vector.per()
    val startArcStart = firstPoint.point + startDir * startRadius
    val startArcEnd = firstPoint.point + startDir * -startRadius

    reflected = Offset(
        2f * current.x - previousControl.x,
        2f * current.y - previousControl.y,
    )
    quadraticTo(reflected.x, reflected.y, startArcStart.x, startArcStart.y)
    semicircularArcTo(startArcStart.x, startArcStart.y, startArcEnd.x, startArcEnd.y)
    close()
}

private fun Path.circlePath(x: Float, y: Float, r: Float) {
    moveTo(x - r, y)
    semicircularArcTo(x - r, y, x + r, y)
    semicircularArcTo(x + r, y, x - r, y)
}

/**
 * Draw a 180° arc from `(startX, startY)` to `(endX, endY)` using two
 * cubic Beziers whose control-point magnitude is [KAPPA] times the
 * radius. The two endpoints must be diametrically opposite on the arc's
 * circle. Max error vs a true arc is ≈ 0.027% — visually identical at
 * any pen width we care about.
 */
private fun Path.semicircularArcTo(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
) {
    val cx = (startX + endX) * 0.5f
    val cy = (startY + endY) * 0.5f
    val dx = startX - cx
    val dy = startY - cy
    // 90° CCW rotation in y-down space.
    val nx = -dy
    val ny = dx
    val mx = cx + nx
    val my = cy + ny
    cubicTo(
        startX + KAPPA * nx, startY + KAPPA * ny,
        mx + KAPPA * dx, my + KAPPA * dy,
        mx, my,
    )
    cubicTo(
        mx - KAPPA * dx, my - KAPPA * dy,
        endX + KAPPA * nx, endY + KAPPA * ny,
        endX, endY,
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Easing functions
// ──────────────────────────────────────────────────────────────────────────

private fun easeLinear(t: Float): Float = t
private fun easeOutQuad(t: Float): Float = t * (2f - t)
private fun easeOutCubic(t: Float): Float {
    val u = t - 1f
    return u * u * u + 1f
}

/**
 * `sin(t · π/2)` — the highlighter brush's pressure curve, matching
 * tldraw's `getHighlightFreehandSettings`. Lifts the low end smoothly so
 * a light highlighter stroke still reads as a coloured swath.
 */
private fun easeOutSine(t: Float): Float = sin(t * (PI / 2.0)).toFloat()

/**
 * Default pen pressure curve: 65% linear blended with 35% quarter-sine.
 * Boosts the low end so light pressure still produces a visible line,
 * without saturating at full pressure.
 */
private fun easePen(t: Float): Float =
    (t * 0.65f + sin(t * (PI / 2.0)).toFloat() * 0.35f)

// ──────────────────────────────────────────────────────────────────────────
// Offset utilities
// ──────────────────────────────────────────────────────────────────────────

/** Perpendicular rotation in the 2D plane (y-down). */
private fun Offset.per(): Offset = Offset(y, -x)

private fun Offset.dpr(other: Offset): Float = x * other.x + y * other.y

private fun Offset.cpr(other: Offset): Float = x * other.y - other.x * y

private fun Offset.len2(): Float = x * x + y * y

private fun Offset.dist2(other: Offset): Float = (this - other).len2()

private fun Offset.dist(other: Offset): Float = hypot(y - other.y, x - other.x)

private fun Offset.uni(): Offset {
    val l = hypot(x, y)
    return if (l > 0f) this / l else this
}

private fun Offset.lrp(other: Offset, t: Float): Offset = this + (other - this) * t

private fun Offset.rotAround(center: Offset, r: Float): Offset {
    if (r == 0f) return this
    val s = sin(r)
    val c = cos(r)
    val px = x - center.x
    val py = y - center.y
    return Offset(center.x + px * c - py * s, center.y + px * s + py * c)
}

private fun average(a: Offset, b: Offset): Offset = (a + b) * 0.5f

// ──────────────────────────────────────────────────────────────────────────
// Tuning constants
// ──────────────────────────────────────────────────────────────────────────

/**
 * Minimum reportable pressure. Any sample below this is clamped (not
 * dropped) to this value — see the rationale in [computeStrokePoints].
 * Matches the tldraw `MIN_PRESSURE` constant.
 */
private const val MIN_PRESSURE: Float = 0.025f

/**
 * Per-frame max pressure delta during the initial pressure roll. 0.275
 * matches the upstream perfect-freehand tuning — slow enough to filter
 * sample noise, fast enough that a press-and-immediately-drag stroke
 * reaches its target pressure within a stroke-width of motion.
 */
private const val RATE_OF_PRESSURE_CHANGE: Float = 0.275f

/**
 * `π + 0.0001` — guards against the rotation falling exactly on the seam
 * between two arc segments, which can stutter at high zoom levels. The
 * extra ε gives the rotation a hair of wrap so the arc closes cleanly.
 */
private val FIXED_PI: Float = (PI + 0.0001).toFloat()

/**
 * Cubic Bezier control-point magnitude for a quarter-circle arc:
 * `4·(√2 − 1)/3 ≈ 0.5522847498`. Two of these stitched together
 * approximate a half-circle to within ~0.027% — well below sub-pixel
 * error for any pen width.
 */
private const val KAPPA: Float = 0.5522848f
