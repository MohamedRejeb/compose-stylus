package com.mohamedrejeb.stylus.compose

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import com.mohamedrejeb.stylus.PenEvent

/**
 * Low-latency stylus drawing surface backed by androidx.ink.
 *
 * Uses Jetpack Ink's `InProgressStrokes` Compose composable to render strokes
 * with sub-frame latency (front-buffered SurfaceControl) and built-in motion
 * prediction. Finished strokes are persisted in [state] (a [PenInkState]) and
 * re-rendered via `CanvasStrokeRenderer` so they remain on-screen after the
 * pen lifts.
 *
 * The surrounding `Modifier.penInput {}` keeps firing for every event, so
 * callers that need raw pen telemetry (pressure, tilt, hover) can subscribe
 * via [onPenEvent] without losing the rendering benefits.
 *
 * Currently Android-only — other targets should keep using
 * `Modifier.penInput {}` over their own `Canvas`. We may promote this
 * composable to a common `expect` once non-Android targets gain an equivalent
 * accelerated renderer.
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
fun PenInkSurface(
    modifier: Modifier = Modifier,
    state: PenInkState = rememberPenInkState(),
    brush: PenBrush = PenBrush.Default,
    onStrokesFinished: (List<PenStroke>) -> Unit = {},
    onPenEvent: (PenEvent) -> Unit = {},
    content: @Composable BoxScope.() -> Unit = {},
) {
    val renderer = remember { CanvasStrokeRenderer.create() }
    val identityMatrix = remember { Matrix() }

    Box(modifier = modifier.penInput(onEvent = onPenEvent)) {
        // 1. Persisted finished strokes — drawn via Ink's CanvasStrokeRenderer
        //    so the visual is identical to the in-progress front-buffer pass.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                state.finishedStrokes.forEach { stroke ->
                    renderer.draw(nativeCanvas, stroke.delegate, identityMatrix)
                }
            }
        }
        // 2. In-progress stroke — rendered on the front buffer for low latency.
        InProgressStrokes(
            defaultBrush = brush.delegate,
            onStrokesFinished = { strokes ->
                val penStrokes = strokes.map(::PenStroke)
                state.appendStrokes(penStrokes)
                onStrokesFinished(penStrokes)
            },
        )
        // 3. Caller overlay content.
        content()
    }
}

/**
 * Opaque wrapper over an `androidx.ink.brush.Brush`.
 *
 * Wrapping keeps the `androidx.ink.*` types out of consumer imports so the
 * public API stays portable — when other platforms gain stylus rendering,
 * the concrete delegate can change without breaking source compatibility.
 */
@Immutable
class PenBrush internal constructor(internal val delegate: Brush) {
    companion object {
        /** Pressure-sensitive default pen, black, ~5px nominal width. */
        val Default: PenBrush = pen(Color.Black)

        /** Pressure-sensitive pen — width modulates with stylus pressure. */
        fun pen(color: Color, size: Float = DEFAULT_PEN_SIZE): PenBrush =
            create(StockBrushes.pressurePen(), color, size)

        /** Constant-width marker. */
        fun marker(color: Color, size: Float = DEFAULT_MARKER_SIZE): PenBrush =
            create(StockBrushes.marker(), color, size)

        /** Translucent highlighter (use a semi-opaque [color]). */
        fun highlighter(color: Color, size: Float = DEFAULT_HIGHLIGHTER_SIZE): PenBrush =
            create(StockBrushes.highlighter(), color, size)

        private fun create(family: BrushFamily, color: Color, size: Float): PenBrush =
            PenBrush(
                Brush.createWithColorIntArgb(
                    family = family,
                    colorIntArgb = color.toArgb(),
                    size = size,
                    epsilon = DEFAULT_EPSILON,
                ),
            )
    }
}

/** Opaque wrapper over an `androidx.ink.strokes.Stroke`. */
@Immutable
class PenStroke internal constructor(internal val delegate: Stroke)

private const val DEFAULT_PEN_SIZE: Float = 5f
private const val DEFAULT_MARKER_SIZE: Float = 10f
private const val DEFAULT_HIGHLIGHTER_SIZE: Float = 20f
private const val DEFAULT_EPSILON: Float = 0.1f
