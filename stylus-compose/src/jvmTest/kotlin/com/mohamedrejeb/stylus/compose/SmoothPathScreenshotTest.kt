package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Visual regression harness for the perfect-freehand-style outline
 * renderer ([tessellateSmoothPath]).
 *
 * Mirrors the scenarios from [StrokeScreenshotTest] so the two engines'
 * output can be eyeballed side by side: same stroke shapes, same widths,
 * just routed through `tessellateSmoothPath` + `drawPath` instead of
 * `tessellateRibbon` + `drawVertices`. Screenshots land in
 * `build/reports/smooth-path-screenshots/` and are named identically to
 * their tessellated counterparts so a `diff` of the two report directories
 * shows exactly what changed.
 */
class SmoothPathScreenshotTest {

    @Test
    fun `straight horizontal stroke - thin`() {
        renderToPng("straight-thin.png", 600, 200) {
            drawStroke(buildStraightStroke(), brush(5f))
        }
    }

    @Test
    fun `straight horizontal stroke - thick`() {
        renderToPng("straight-thick.png", 600, 200) {
            drawStroke(buildStraightStroke(), brush(40f))
        }
    }

    @Test
    fun `L-shape 90 degree corner - thick`() {
        renderToPng("l-shape-thick.png", 400, 400) {
            drawStroke(buildLShape(), brush(40f))
        }
    }

    @Test
    fun `zigzag - thick`() {
        renderToPng("zigzag-thick.png", 600, 400) {
            drawStroke(buildZigzag(), brush(40f))
        }
    }

    @Test
    fun `S-curve - thick`() {
        renderToPng("s-curve-thick.png", 600, 400) {
            drawStroke(buildSCurve(), brush(40f))
        }
    }

    @Test
    fun `U-turn cusp - thick`() {
        renderToPng("u-turn-thick.png", 400, 400) {
            drawStroke(buildUTurn(), brush(40f))
        }
    }

    @Test
    fun `tight spiral - thick`() {
        renderToPng("spiral-thick.png", 500, 500) {
            drawStroke(buildSpiral(), brush(40f))
        }
    }

    @Test
    fun `variable pressure - pen brush`() {
        renderToPng("variable-pressure-pen.png", 600, 200) {
            drawStroke(buildVariablePressure(), PenBrush.pen(Color.Black, size = 20f))
        }
    }

    @Test
    fun `variable pressure - marker brush`() {
        renderToPng("variable-pressure-marker.png", 600, 200) {
            drawStroke(buildVariablePressure(), PenBrush.marker(Color.Black, size = 20f))
        }
    }

    // ─── Stroke shape builders (identical to StrokeScreenshotTest) ────

    private fun buildStraightStroke(): List<PenStrokePoint> =
        (0..40).map { i -> PenStrokePoint(50f + i * 12.5f, 100f, 0.6f, i * 16L) }

    private fun buildLShape(): List<PenStrokePoint> {
        val horizontal = (0..15).map { i -> PenStrokePoint(50f + i * 13f, 100f, 0.6f, i * 16L) }
        val vertical = (1..15).map { i -> PenStrokePoint(245f, 100f + i * 13f, 0.6f, (15 + i) * 16L) }
        return horizontal + vertical
    }

    private fun buildZigzag(): List<PenStrokePoint> {
        val pts = mutableListOf<PenStrokePoint>()
        var x = 50f
        var goDown = true
        var t = 0L
        while (x < 550f) {
            val yStart = if (goDown) 100f else 300f
            val yEnd = if (goDown) 300f else 100f
            for (i in 0..6) {
                val frac = i / 6f
                pts += PenStrokePoint(x + frac * 80f, yStart + frac * (yEnd - yStart), 0.6f, t)
                t += 16L
            }
            x += 80f
            goDown = !goDown
        }
        return pts
    }

    private fun buildSCurve(): List<PenStrokePoint> {
        val pts = mutableListOf<PenStrokePoint>()
        for (i in 0..60) {
            val t = i / 60f
            val x = 50f + t * 500f
            val y = 200f + sin(t * 2f * Math.PI.toFloat()) * 100f
            pts += PenStrokePoint(x, y, 0.6f, i * 16L)
        }
        return pts
    }

    private fun buildUTurn(): List<PenStrokePoint> {
        val out = (0..15).map { i -> PenStrokePoint(100f + i * 12f, 200f, 0.6f, i * 16L) }
        val back = (1..15).map { i ->
            PenStrokePoint(280f - i * 12f, 200.5f + i * 0.2f, 0.6f, (15 + i) * 16L)
        }
        return out + back
    }

    private fun buildSpiral(): List<PenStrokePoint> {
        val pts = mutableListOf<PenStrokePoint>()
        val cx = 250f
        val cy = 250f
        for (i in 0..120) {
            val t = i / 120f
            val angle = t * 3f * 2f * Math.PI.toFloat()
            val r = 20f + t * 180f
            pts += PenStrokePoint(cx + cos(angle) * r, cy + sin(angle) * r, 0.6f, i * 16L)
        }
        return pts
    }

    private fun buildVariablePressure(): List<PenStrokePoint> {
        val pts = mutableListOf<PenStrokePoint>()
        for (i in 0..60) {
            val t = i / 60f
            val p = if (t < 0.5f) 0.1f + t * 1.8f else 1.0f - (t - 0.5f) * 1.8f
            pts += PenStrokePoint(50f + t * 500f, 100f, p.coerceIn(0.05f, 1f), i * 16L)
        }
        return pts
    }

    // ─── Render helpers ──────────────────────────────────────────────

    private fun brush(size: Float): PenBrush = PenBrush.pen(Color.Black, size = size)

    private fun DrawScope.drawStroke(input: List<PenStrokePoint>, brush: PenBrush) {
        drawRect(Color.White, size = size)
        val path = tessellateSmoothPath(input, brush)
        drawPath(path, colorFor(brush))
    }

    private fun renderToPng(
        name: String,
        width: Int,
        height: Int,
        block: DrawScope.() -> Unit,
    ) {
        val imageBitmap = ImageBitmap(width, height)
        val composeCanvas = Canvas(imageBitmap)
        CanvasDrawScope().draw(
            density = Density(1f, 1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = composeCanvas,
            size = Size(width.toFloat(), height.toFloat()),
        ) {
            block()
        }
        val skiaImage = Image.makeFromBitmap(imageBitmap.asSkiaBitmap())
        val pngBytes = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: error("PNG encoding failed for $name")
        val outFile = File(reportsDir, name)
        outFile.parentFile?.mkdirs()
        outFile.writeBytes(pngBytes)
        assertTrue(outFile.length() > 0, "wrote empty file for $name")
    }

    private val reportsDir: File by lazy {
        File("build/reports/smooth-path-screenshots").apply { mkdirs() }
    }
}
