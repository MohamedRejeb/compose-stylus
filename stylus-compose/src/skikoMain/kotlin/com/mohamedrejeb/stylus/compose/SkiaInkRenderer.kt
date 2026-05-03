package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode

/**
 * Render a pre-tessellated stroke ribbon by handing the [mesh] straight
 * to Skia's `drawVertices`.
 *
 * Why not `drawPath`?
 *
 *  - **Pre-computed buffers.** Finished strokes pre-tessellate once
 *    ([PenStroke.tessellatedMesh]) and reuse the same `FloatArray` /
 *    `IntArray` / `ShortArray` on every redraw. There is no `Path`
 *    rebuild, no allocation, no per-frame `arcTo`/`lineTo` calls.
 *    Compose's `Path` is per-`DrawScope`-frame and must be repopulated
 *    every paint, so 100 finished strokes × 100 samples each used to do
 *    ~20k path ops per frame just to tell Skia something it could be
 *    told once.
 *
 *  - **Skia's tessellator gets skipped.** With `drawVertices` we hand
 *    Skia the triangle list directly; it goes to the GPU as-is. With
 *    `drawPath` Skia walks the path, flattens curves, and runs its own
 *    tessellator even though we already know the geometry is a flat
 *    triangle mesh.
 *
 * Why feathered topology?
 *
 *    `drawVertices` does **not** apply the coverage anti-aliasing that
 *    `drawPath` gets — `paint.isAntiAlias` is honoured by Skia's path
 *    rasterizer, not its triangle rasterizer. The feathered outer ring
 *    in [RibbonMesh] (alpha-gradient vertex colours) recovers smooth
 *    lateral edges via standard GPU vertex-colour interpolation: about
 *    a 1-logical-pixel-wide ramp from full alpha to zero on each side
 *    of the ribbon. `BlendMode.MODULATE` multiplies the per-vertex
 *    colour by the paint colour, and since the vertex RGB is fixed at
 *    white, only the alpha channel carries the gradient — the stroke's
 *    actual colour comes entirely from the paint.
 *
 * The same code path runs on Desktop (JVM/Skiko-AWT), iOS (Skiko-Native),
 * and Web (Skiko-WASM) because `nativeCanvas` resolves to
 * `org.jetbrains.skia.Canvas` on all three. Android takes a different
 * actual that uses Jetpack Ink's `CanvasStrokeRenderer` and never reaches
 * this file.
 *
 * @param mesh indexed triangle mesh produced by [tessellateRibbon].
 *   Empty meshes (`< 2` samples) no-op.
 * @param color stroke fill colour. Alpha is honoured (highlighter brush
 *   already pre-multiplies its base alpha into [colorFor]).
 */
internal fun DrawScope.drawTessellatedStroke(
    mesh: RibbonMesh,
    color: Color,
) {
    if (mesh.indices.isEmpty()) return

    drawIntoCanvas { canvas ->
        // Allocate-and-close the Paint per draw. Caching across frames
        // would save a few microseconds, but Skia's Paint constructor
        // is itself thin (a couple of native struct inits) and a cached
        // top-level `val Paint` would need careful disposal at process
        // teardown to avoid a native handle leak — not worth the
        // complexity unless profiling flags it. Plain try/finally
        // instead of `Managed.use` to avoid leaking the
        // `org.jetbrains.skia.impl` package import into the call site.
        val paint = Paint()
        try {
            paint.color = color.toArgb()
            // `isAntiAlias` is a no-op for `drawVertices` — Skia's
            // coverage AA only kicks in for path rasterization. We get
            // the AA from the feathered alpha gradient in [mesh.colors]
            // instead. Leaving the flag on is harmless and matches
            // the rest of Compose's draw conventions.
            paint.isAntiAlias = true
            canvas.nativeCanvas.drawVertices(
                vertexMode = VertexMode.TRIANGLES,
                positions = mesh.positions,
                colors = mesh.colors,
                texCoords = null,
                indices = mesh.indices,
                // MODULATE multiplies vertex colour by the paint shader
                // (the solid paint colour, here). With white-RGB
                // vertices, the multiplication is a pure alpha gate
                // applied to the paint colour — exactly what we want
                // for the feather.
                blendMode = BlendMode.MODULATE,
                paint = paint,
            )
        } finally {
            paint.close()
        }
    }
}
