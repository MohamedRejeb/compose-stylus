package com.mohamedrejeb.stylus.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType
import com.mohamedrejeb.stylus.PenTool
import com.mohamedrejeb.stylus.compose.prediction.PenEventPredictor

/**
 * Skiko-backed actual for [PenInkSurface]. Shared by Desktop (JVM/AWT),
 * iOS (Skiko-Native), and Web (Skiko-WASM) via the `skikoMain` intermediate
 * source set — Android takes a different actual that uses Jetpack Ink.
 *
 * Records pen events into an active stroke, applies Catmull-Rom smoothing
 * and Kalman-filter motion prediction during in-progress drawing, and
 * emits a [PenStroke] on each release. Each stroke is rendered as a
 * pre-tessellated triangle strip via [drawTessellatedStroke].
 */
@Composable
actual fun PenInkSurface(
    modifier: Modifier,
    state: PenInkState,
    brush: PenBrush,
    inkEnabled: Boolean,
    engine: PenInkEngine,
    onStrokesFinished: (List<PenStroke>) -> Unit,
    onPenEvent: (PenEvent) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val active = remember { mutableStateListOf<PenStrokePoint>() }
    val predictor = remember { PenEventPredictor() }
    var strokeStartMs by remember { mutableStateOf(0L) }
    // Tool latched on Press and stamped onto the finished `PenStroke` so the
    // data stays portable — a stroke captured here on touch / mouse / pen
    // re-renders identically when fed back through the Android Ink path,
    // which routes the tool into Ink's `InputToolType` to keep
    // pressure-vs-constant-width branches consistent.
    var activeTool by remember { mutableStateOf(PenTool.Pen) }

    // Read the current value through `rememberUpdatedState` so the
    // `Modifier.penInput` body — which captures references on first launch
    // and never re-launches across recompositions — observes flips of
    // `inkEnabled` immediately. Without this, toggling `inkEnabled` would be
    // ignored until the next pointer-input keying.
    val currentInkEnabled by rememberUpdatedState(inkEnabled)
    val currentBrush by rememberUpdatedState(brush)
    val currentOnStrokesFinished by rememberUpdatedState(onStrokesFinished)
    val currentOnPenEvent by rememberUpdatedState(onPenEvent)

    fun finalizeActiveStroke() {
        if (active.size >= 2) {
            val finished = PenStroke(brush = currentBrush, points = active.toList(), tool = activeTool)
            state.appendStrokes(listOf(finished))
            currentOnStrokesFinished(listOf(finished))
        }
        predictor.reset()
        active.clear()
    }

    // Toggling `inkEnabled` to false mid-stroke finalises the in-flight
    // stroke so the user's drag gets persisted rather than silently dropped.
    // `LaunchedEffect` keys on the disable transition; we explicitly only act
    // when the new value is false to avoid double-finalising on re-enable.
    LaunchedEffect(inkEnabled) {
        if (!inkEnabled) finalizeActiveStroke()
    }

    Box(
        modifier = modifier.penInput { event ->
            currentOnPenEvent(event)
            if (!currentInkEnabled) return@penInput
            when (event.type) {
                PenEventType.Press -> {
                    strokeStartMs = event.timestamp
                    activeTool = event.tool
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
                        // stroke into the post-release hover trail.
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
            // Finished strokes always render — they are durable user content
            // that should remain visible regardless of which tool is active.
            state.finishedStrokes.forEach { stroke ->
                drawFinishedStroke(stroke, engine)
            }

            // Active stroke: skipped entirely when ink is disabled. The
            // recording branch above already short-circuits on
            // `inkEnabled = false`, so `active` will be empty here in steady
            // state — but the explicit guard handles the inverse race where
            // a flip-to-false interleaves between recording and the next
            // frame.
            if (inkEnabled) {
                val snapshot = active.snapshotPoints()
                if (snapshot.isNotEmpty()) {
                    val predicted = predictor.predict()
                    val toDraw = if (predicted != null) snapshot + predicted else snapshot
                    drawActiveStroke(toDraw, brush, engine)
                }
            }
        }
        content()
    }
}

/**
 * Copy [this] to a stable [List] via index access. Avoids `Iterator`-based
 * traversal so a concurrent write can't trigger fail-fast
 * `ConcurrentModificationException` mid-iteration. The size is sampled
 * once up-front; if a write extends or shrinks the list while we're
 * copying we simply observe a slightly stale snapshot rather than crash —
 * preferable to losing the whole frame.
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

/**
 * Render a completed stroke under the chosen [engine]. Both branches reuse
 * the lazily-cached per-stroke geometry on [PenStroke] — the first frame
 * after lift pays the tessellation cost; every frame after that is just
 * the GPU upload + draw call.
 */
private fun DrawScope.drawFinishedStroke(stroke: PenStroke, engine: PenInkEngine) {
    val color = colorFor(stroke.brush)
    when (engine) {
        PenInkEngine.Tessellated ->
            drawTessellatedStroke(stroke.tessellatedMesh, color)
        PenInkEngine.SmoothPath ->
            drawPath(stroke.smoothPath, color)
    }
}

/**
 * Render the in-progress stroke for the chosen [engine]. The two engines
 * have different upstream pipelines:
 *
 *  - [PenInkEngine.Tessellated] gets Catmull-Rom-smoothed input on top of
 *    the predicted point chain so the triangle ribbon visibly tightens on
 *    fast curves.
 *  - [PenInkEngine.SmoothPath] feeds the raw + predicted points straight
 *    to the freehand pipeline, which has its own input streamline /
 *    pressure roll. Pre-smoothing here would over-round short flicks and
 *    fight the freehand algorithm's own tuning.
 */
private fun DrawScope.drawActiveStroke(
    points: List<PenStrokePoint>,
    brush: PenBrush,
    engine: PenInkEngine,
) {
    when (engine) {
        PenInkEngine.Tessellated -> {
            val smoothed = catmullRomSmooth(points)
            val mesh = tessellateRibbon(smoothed, brush)
            drawTessellatedStroke(mesh, colorFor(brush))
        }
        PenInkEngine.SmoothPath -> {
            val path = tessellateSmoothPath(points, brush)
            drawPath(path, colorFor(brush))
        }
    }
}
