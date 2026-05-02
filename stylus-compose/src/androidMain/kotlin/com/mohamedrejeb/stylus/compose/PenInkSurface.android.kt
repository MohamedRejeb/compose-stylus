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

@Composable
actual fun PenInkSurface(
    modifier: Modifier,
    state: PenInkState,
    brush: PenBrush,
    onStrokesFinished: (List<PenStroke>) -> Unit,
    onPenEvent: (PenEvent) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val renderer = remember { CanvasStrokeRenderer.create() }
    val identityMatrix = remember { Matrix() }
    val inkBrush = remember(brush) { brush.toInkBrush() }

    Box(modifier = modifier.penInput(onEvent = onPenEvent)) {
        // 1. Persisted finished strokes — drawn via Ink's CanvasStrokeRenderer
        //    so the visual matches the in-progress front-buffer pass.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                state.finishedStrokes.forEach { penStroke ->
                    val inkStroke = penStroke.toInkStroke(inkBrush) ?: return@forEach
                    renderer.draw(nativeCanvas, inkStroke, identityMatrix)
                }
            }
        }
        // 2. In-progress stroke — rendered on the front buffer for low latency.
        InProgressStrokes(
            defaultBrush = inkBrush,
            onStrokesFinished = { strokes ->
                val penStrokes = strokes.map { it.toPenStroke(brush) }
                state.appendStrokes(penStrokes)
                onStrokesFinished(penStrokes)
            },
        )
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
    for (i in 0 until n) {
        val input: StrokeInput = inputs[i]
        val pressure = if (input.hasPressure) input.pressure else 1f
        points.add(
            PenStrokePoint(
                x = input.x,
                y = input.y,
                pressure = pressure,
                elapsedMillis = input.elapsedTimeMillis,
            ),
        )
    }
    return PenStroke(brush = brush, points = points)
}

/**
 * Re-render a [PenStroke] (recorded by any platform) through Ink's stroke
 * pipeline so [CanvasStrokeRenderer] can draw it. Returns null if the stroke
 * has fewer than two points.
 */
private fun PenStroke.toInkStroke(inkBrush: Brush): Stroke? {
    if (points.size < 2) return null
    val batch = MutableStrokeInputBatch()
    points.forEach { p ->
        batch.add(
            type = InputToolType.STYLUS,
            x = p.x,
            y = p.y,
            elapsedTimeMillis = p.elapsedMillis,
            pressure = p.pressure,
        )
    }
    return Stroke(brush = inkBrush, inputs = batch)
}

private const val INK_BRUSH_EPSILON: Float = 0.1f
