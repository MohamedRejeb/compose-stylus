package com.mohamedrejeb.stylus.compose

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenTool
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
actual fun PenInkSurface(
    modifier: Modifier,
    state: PenInkState,
    brush: PenBrush,
    inkEnabled: Boolean,
    @Suppress("UNUSED_PARAMETER") engine: PenInkEngine,
    onStrokesFinished: (List<PenStroke>) -> Unit,
    onPenEvent: (PenEvent) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    // [engine] is intentionally ignored on Android — finished strokes go
    // through Jetpack Ink's `CanvasStrokeRenderer` and in-progress strokes
    // through `InProgressStrokesView`'s front-buffered SurfaceControl,
    // both of which carry their own native tessellation / motion
    // prediction. The cross-platform engine knob is a Skiko-only concern.
    val renderer = remember { CanvasStrokeRenderer.create() }
    val identityMatrix = remember { Matrix() }
    val inkBrush = remember(brush) { brush.toInkBrush() }

    Box(modifier = modifier.penInput(onEvent = onPenEvent)) {
        // 1. Persisted finished strokes — drawn via Ink's CanvasStrokeRenderer
        //    so the visual matches the in-progress front-buffer pass. These
        //    are durable user content and render regardless of [inkEnabled].
        Canvas(modifier = Modifier.matchParentSize()) {
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                state.finishedStrokes.forEach { penStroke ->
                    val inkStroke = penStroke.toInkStroke(inkBrush) ?: return@forEach
                    renderer.draw(nativeCanvas, inkStroke, identityMatrix)
                }
            }
        }
        // 2. In-progress stroke — rendered on the front buffer for low
        //    latency. Conditionally mounted: when [inkEnabled] is false the
        //    `InProgressStrokesView` is removed from the tree, releasing its
        //    `SurfaceControl` and ensuring touch events flow only to the
        //    caller's gesture handler (via `Modifier.penInput` -> `onPenEvent`).
        //    The cost on re-enable is one front-buffer surface creation,
        //    which is amortised across the next stroke. Consumers that
        //    switch tools many times per second should keep this `true` and
        //    instead use a no-op brush, but for typical tool-palette UX the
        //    conditional mount is the right tradeoff (and avoids leaking
        //    pen events into Ink's own gesture recognition while a
        //    select / shape tool is active).
        if (inkEnabled) {
            InProgressStrokes(
                defaultBrush = inkBrush,
                onStrokesFinished = { strokes ->
                    val penStrokes = strokes.map { it.toPenStroke(brush) }
                    state.appendStrokes(penStrokes)
                    onStrokesFinished(penStrokes)
                },
            )
        }
        // 3. Caller overlay content.
        content()
    }
}

private fun PenBrush.toInkBrush(): Brush {
    val inkFamily: BrushFamily = when (family) {
        PenBrushFamily.Pen -> StockBrushes.pressurePen()
        PenBrushFamily.Marker -> StockBrushes.marker()
        PenBrushFamily.Highlighter -> StockBrushes.highlighter()
    }
    return Brush.createWithColorIntArgb(
        family = inkFamily,
        colorIntArgb = color.toArgb(),
        size = size,
        epsilon = INK_BRUSH_EPSILON,
    )
}

private fun Stroke.toPenStroke(brush: PenBrush): PenStroke {
    val inputs = this.inputs
    val n = inputs.size
    val points = ArrayList<PenStrokePoint>(n)
    // Capture the tool from the first input — Ink keeps it constant for the
    // lifetime of a stroke. Preserving it here is what lets the round-trip
    // back through `toInkStroke` re-render a finger draw at the same
    // thickness as the in-progress front-buffer pass: a TOUCH input under
    // `pressurePen` falls back to Ink's constant-width branch, while
    // re-feeding it as STYLUS would force the pressure-modulated branch and
    // make the finished stroke visibly thicker than it was during drag.
    val tool: PenTool = if (n > 0) inputs[0].toolType.toPenTool() else PenTool.Pen
    for (i in 0 until n) {
        val input: StrokeInput = inputs[i]
        val pressure = if (input.hasPressure) input.pressure else 1f
        // Ink stores tilt in polar form (magnitude + azimuth). Convert to
        // the same Cartesian (tiltX, tiltY) shape PenEvent / PenStrokePoint
        // use so a stroke captured here re-renders identically on the
        // shared Compose pipeline (desktop / iOS / web).
        val tiltX: Float
        val tiltY: Float
        if (input.hasTilt && input.hasOrientation) {
            val mag = input.tiltRadians
            val orient = input.orientationRadians
            tiltX = mag * cos(orient)
            tiltY = mag * sin(orient)
        } else {
            tiltX = 0f
            tiltY = 0f
        }
        points.add(
            PenStrokePoint(
                x = input.x,
                y = input.y,
                pressure = pressure,
                elapsedMillis = input.elapsedTimeMillis,
                tiltX = tiltX,
                tiltY = tiltY,
            ),
        )
    }
    return PenStroke(brush = brush, points = points, tool = tool)
}

/**
 * Re-render a [PenStroke] (recorded by any platform) through Ink's stroke
 * pipeline so [CanvasStrokeRenderer] can draw it. Returns null if the stroke
 * has fewer than two points.
 */
private fun PenStroke.toInkStroke(inkBrush: Brush): Stroke? {
    if (points.size < 2) return null
    val batch = MutableStrokeInputBatch()
    val inkTool = tool.toInputToolType()
    points.forEach { p ->
        // Cartesian tilt → Ink polar (tiltRadians, orientationRadians).
        // Pass NO_TILT / NO_ORIENTATION when the source didn't report tilt
        // so Ink's brushes that branch on `hasTilt` keep their existing
        // behavior instead of treating "vertical pen" as a real reading.
        val tiltMag = sqrt(p.tiltX * p.tiltX + p.tiltY * p.tiltY)
        if (tiltMag > 0f) {
            // Ink validates tiltRadians ∈ [0, π/2] and orientationRadians ∈ [0, 2π).
            // atan2 returns (-π, π] and tiltMag can exceed π/2 if both axes are
            // tilted near the limit, so clamp/wrap before handing them off.
            val clampedTilt = tiltMag.coerceIn(0f, (PI / 2).toFloat())
            val twoPi = (2 * PI).toFloat()
            val rawOrientation = atan2(p.tiltY, p.tiltX)
            val wrapped = ((rawOrientation % twoPi) + twoPi) % twoPi
            val orientation = if (wrapped >= twoPi) 0f else wrapped
            batch.add(
                type = inkTool,
                x = p.x,
                y = p.y,
                elapsedTimeMillis = p.elapsedMillis,
                pressure = p.pressure,
                tiltRadians = clampedTilt,
                orientationRadians = orientation,
            )
        } else {
            batch.add(
                type = inkTool,
                x = p.x,
                y = p.y,
                elapsedTimeMillis = p.elapsedMillis,
                pressure = p.pressure,
            )
        }
    }
    return Stroke(brush = inkBrush, inputs = batch)
}

/**
 * Map Ink's tool discriminator onto the cross-platform [PenTool] used by
 * [PenStroke]. Ink only exposes `STYLUS` / `TOUCH` / `MOUSE` / `UNKNOWN`, so
 * `Eraser` is collapsed onto `Pen` on input — Android doesn't distinguish a
 * stylus eraser tip at the Ink layer, and the ambiguity is harmless because
 * [PenTool.toInputToolType] still routes `Eraser` back to `STYLUS`.
 */
private fun InputToolType.toPenTool(): PenTool = when (this) {
    InputToolType.STYLUS -> PenTool.Pen
    InputToolType.TOUCH -> PenTool.Touch
    InputToolType.MOUSE -> PenTool.Mouse
    else -> PenTool.None
}

/**
 * Map a cross-platform [PenTool] onto Ink's [InputToolType]. `Eraser` and
 * `None` collapse to `STYLUS` because Ink lacks both — `Eraser` is used as a
 * pressure-sensitive pen, and `None` would otherwise prevent re-rendering a
 * stroke with an unknown source.
 */
private fun PenTool.toInputToolType(): InputToolType = when (this) {
    PenTool.Pen, PenTool.Eraser, PenTool.None -> InputToolType.STYLUS
    PenTool.Touch -> InputToolType.TOUCH
    PenTool.Mouse -> InputToolType.MOUSE
}

private const val INK_BRUSH_EPSILON: Float = 0.1f
