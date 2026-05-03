package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Indexed triangle mesh ready to hand to Skia's `drawVertices` with
 * `VertexMode.TRIANGLES`. Three buffers laid out as a single mesh:
 *
 *  - [positions]: 9 vertices per smoothed sample, packed as
 *    `[cx,cy, oL_in.x,oL_in.y, …, oR_out.x,oR_out.y, …]`. The slot
 *    layout per sample is `(centre, in-set ×4, out-set ×4)` along the
 *    incoming and outgoing perpendiculars; see [tessellateRibbon] for
 *    the full layout. Two cap blocks of `1 + 2·(K + 1)` vertices each
 *    follow the body samples.
 *  - [colors]: matching ARGB per vertex. The centre and inner pairs
 *    carry full-alpha white, the outer pairs fully-transparent white.
 *    Skia's `drawVertices` linearly interpolates these across each
 *    triangle and multiplies the result by the paint colour (via
 *    `BlendMode.MODULATE`), so the lateral edge of the ribbon fades
 *    smoothly to transparent — that's how we get anti-aliased edges
 *    that the raw triangle rasterizer (which does **not** apply Skia's
 *    coverage AA) would otherwise leave stair-stepped.
 *  - [indices]: triangle list. Each body segment contributes 18
 *    indices (left feather, solid core, right feather), each interior
 *    round join another `18·J` indices (a `J`-step arc fan on each
 *    side that reuses the body's in-set / out-set as endpoints), and
 *    each rounded cap `9·K` indices.
 */
internal class RibbonMesh(
    val positions: FloatArray,
    val colors: IntArray,
    val indices: ShortArray,
)

private val EmptyRibbonMesh = RibbonMesh(FloatArray(0), IntArray(0), ShortArray(0))

/**
 * Pure-math stroke tessellation: turns a smoothed point list into a
 * GPU-ready [RibbonMesh] for `drawVertices`.
 *
 * ## Topology
 *
 * Per sample we emit **9 vertices**:
 *
 * ```
 *   slot 0:                     centre  (alpha 1)
 *   slot 1..4 (in-set):  oL_in   iL_in   iR_in   oR_in
 *                        α 0     α 1     α 1     α 0   along inPerp
 *   slot 5..8 (out-set): oL_out  iL_out  iR_out  oR_out
 *                        α 0     α 1     α 1     α 0   along outPerp
 * ```
 *
 * `inPerp` is the perpendicular of the segment that arrives at the
 * sample, `outPerp` the perpendicular of the one leaving. At endpoints
 * both perpendiculars are taken from the only adjacent segment, so the
 * in-set and out-set coincide and the "join" topology degenerates
 * cleanly.
 *
 * **Body segment** (sample s → sample s+1) consumes the out-set of
 * sample s and the in-set of sample s+1, with the same three-quad
 * triangulation as before (left feather, solid core, right feather)
 * for 18 indices per segment.
 *
 * **Interior round join** (at every sample 1 ≤ s ≤ n−2) replaces the
 * gap between the in-set and out-set with a `JOIN_SUBDIVISIONS`-step
 * arc fan on each side. Endpoints reuse the body's in-set / out-set
 * vertices, and `JOIN_SUBDIVISIONS − 1` intermediate inner+outer ring
 * vertices per side land in a join-intermediate buffer region appended
 * after the body samples.
 *
 * Per arc step on each side we emit three triangles — one solid fan
 * triangle anchored at the centre `(C, inner_k, inner_{k+1})` plus
 * two feather triangles `(inner_k, outer_k, outer_{k+1})` and
 * `(inner_k, outer_{k+1}, inner_{k+1})` — for `18·JOIN_SUBDIVISIONS`
 * indices per interior sample. With `JOIN_SUBDIVISIONS = 1` this
 * collapses to the previous bevel join; values ≥ 4 round sharp turns
 * into smooth arcs at thick widths.
 *
 * The signed bend angle is `atan2(inPerp × outPerp, inPerp · outPerp)`,
 * so the arc parametrisation
 *   `arcDir(t) = inPerp · cos(t·θ) + (−inPerp_y, inPerp_x) · sin(t·θ)`
 * is well-defined for any `θ` including the cusp `±π` (no division by
 * `sin θ`). At straight stretches `θ ≈ 0` and the intermediates
 * collapse to the in-set/out-set positions, leaving zero-area
 * triangles that the GPU discards.
 *
 * Replaces both the older miter approach (which extruded vertices up
 * to `MITER_LIMIT · halfW` along the bisector and produced spikes —
 * regression test: `sharp corner does not extrude body vertex past
 * halfW from its sample`) and the intermediate bevel topology (whose
 * flat-cut corners read as polygon facets at thick widths).
 *
 * ## Anti-aliasing
 *
 * Skia's `drawVertices` does **not** apply the coverage AA that
 * `drawPath` gets — `paint.isAntiAlias = true` is honoured by Skia's
 * path rasterizer, not its triangle rasterizer. The 1-logical-pixel
 * outer-ring alpha gradient (`OUTER_COLOR` at the perimeter,
 * `INNER_COLOR` one pixel inside) gives the GPU a smooth ramp to
 * interpolate, which the framebuffer composites as a soft edge —
 * visually equivalent to coverage AA at typical stroke widths.
 *
 * The total visible width still equals `2 · halfW`: outer vertices sit
 * at `halfW` with alpha 0 (so they fade to nothing) and the inner pair
 * at `halfW − feather` with full colour. Strokes therefore stay the
 * same width they were in the path-based renderer.
 *
 * For very thin strokes (`halfW < feather`) the inner half-width
 * clamps to zero — the solid core collapses and the entire ribbon
 * becomes a feather gradient, which is the correct visual fallback
 * when approaching sub-pixel width.
 *
 * Returns an empty mesh when there are fewer than 2 samples.
 */
internal fun tessellateRibbon(
    smoothed: List<PenStrokePoint>,
    brush: PenBrush,
    featherPx: Float = AA_FEATHER_PX,
): RibbonMesh {
    val n = smoothed.size
    if (n < 2) return EmptyRibbonMesh

    // Velocity-based thinning is only meaningful for the Pen brush family —
    // Marker and Highlighter keep constant width per their physical analogues.
    val velocity: FloatArray? =
        if (brush.family == PenBrushFamily.Pen) computeVelocity(smoothed) else null
    val tiltShaped = brush.family == PenBrushFamily.Pen

    // Per-segment unit direction — used for both perpendiculars and the
    // tilt tangent. Each interior sample picks up the previous segment's
    // direction for its in-perp and the next segment's direction for its
    // out-perp; endpoints just reuse the only adjacent segment.
    val segCount = n - 1
    val segDx = FloatArray(segCount)
    val segDy = FloatArray(segCount)
    for (s in 0 until segCount) {
        val dx = smoothed[s + 1].x - smoothed[s].x
        val dy = smoothed[s + 1].y - smoothed[s].y
        val len = sqrt(dx * dx + dy * dy)
        if (len > MIN_TANGENT_LEN) {
            segDx[s] = dx / len
            segDy[s] = dy / len
        }
    }

    // Buffer layout (vertex offsets):
    //   [body         ][join intermediates                  ][start cap        ][end cap          ]
    //    9n vertices    JOIN_INTERMEDIATES_PER_SAMPLE·(n−2)   capVertexCount    capVertexCount
    //
    // Index buffer:
    //   [body segments     ][interior round joins                  ][start cap     ][end cap]
    //    18·(n−1) indices    18·JOIN_SUBDIVISIONS·(n−2) indices      9·K indices    9·K indices
    val capVertexCount = 1 + 2 * (CAP_SUBDIVISIONS + 1)
    val interiorSampleCount = (n - 2).coerceAtLeast(0)
    val joinIntermediateCount = JOIN_INTERMEDIATES_PER_SAMPLE * interiorSampleCount
    val totalVertices = VERTS_PER_SAMPLE * n + joinIntermediateCount + 2 * capVertexCount
    val positions = FloatArray(totalVertices * 2)
    val colors = IntArray(totalVertices)

    val totalIndices = (n - 1) * 18 +
        interiorSampleCount * 18 * JOIN_SUBDIVISIONS +
        2 * CAP_SUBDIVISIONS * 9
    val indices = ShortArray(totalIndices)

    // Per-sample half-widths memoised here so the round-join loop can
    // reuse the same velocity/tilt-shaped value the body loop computed
    // — recomputing would duplicate the chain and risk drift if any of
    // those helpers stop being pure.
    val halfWidths = FloatArray(n)

    for (i in 0 until n) {
        // In-perp: the previous segment's perpendicular (or the only
        // segment, at sample 0). Out-perp: the next segment's
        // perpendicular (or the only segment, at sample n−1). At
        // endpoints both are equal so the bevel triangles below
        // degenerate to zero area — handled implicitly by the GPU.
        val inDx = segDx[if (i == 0) 0 else i - 1]
        val inDy = segDy[if (i == 0) 0 else i - 1]
        val outDx = segDx[if (i == n - 1) segCount - 1 else i]
        val outDy = segDy[if (i == n - 1) segCount - 1 else i]
        val inPerpX = -inDy
        val inPerpY = inDx
        val outPerpX = -outDy
        val outPerpY = outDx

        // Tangent for the tilt-width factor — bisector at interior
        // samples, single-segment direction at endpoints.
        val tx: Float
        val ty: Float
        if (i == 0 || i == n - 1) {
            tx = inDx
            ty = inDy
        } else {
            val sx = inDx + outDx
            val sy = inDy + outDy
            val l = sqrt(sx * sx + sy * sy)
            if (l > MIN_TANGENT_LEN) {
                tx = sx / l
                ty = sy / l
            } else {
                tx = 0f
                ty = 0f
            }
        }

        var halfW = strokeWidthFor(brush, smoothed[i].pressure) / 2f
        if (velocity != null) halfW *= velocityWidthFactor(velocity[i])
        if (tiltShaped) halfW *= tiltWidthFactor(smoothed[i].tiltX, smoothed[i].tiltY, tx, ty)
        halfWidths[i] = halfW

        val outerHW = halfW
        val innerHW = (halfW - featherPx).coerceAtLeast(0f)

        val p = smoothed[i]
        val k = i * VERTS_PER_SAMPLE * 2

        // Slot 0 — sample centre. Carries alpha 1 because the bevel's
        // inner solid-bridge triangles want full colour at the corner
        // pivot. Doubles as the conceptual "sample-position vertex"
        // for the bevel feather alpha gradient.
        positions[k] = p.x
        positions[k + 1] = p.y
        // Slots 1..4 — in-set: oL_in, iL_in, iR_in, oR_in along inPerp.
        positions[k + 2] = p.x + inPerpX * outerHW
        positions[k + 3] = p.y + inPerpY * outerHW
        positions[k + 4] = p.x + inPerpX * innerHW
        positions[k + 5] = p.y + inPerpY * innerHW
        positions[k + 6] = p.x - inPerpX * innerHW
        positions[k + 7] = p.y - inPerpY * innerHW
        positions[k + 8] = p.x - inPerpX * outerHW
        positions[k + 9] = p.y - inPerpY * outerHW
        // Slots 5..8 — out-set: oL_out, iL_out, iR_out, oR_out along outPerp.
        positions[k + 10] = p.x + outPerpX * outerHW
        positions[k + 11] = p.y + outPerpY * outerHW
        positions[k + 12] = p.x + outPerpX * innerHW
        positions[k + 13] = p.y + outPerpY * innerHW
        positions[k + 14] = p.x - outPerpX * innerHW
        positions[k + 15] = p.y - outPerpY * innerHW
        positions[k + 16] = p.x - outPerpX * outerHW
        positions[k + 17] = p.y - outPerpY * outerHW

        val c = i * VERTS_PER_SAMPLE
        colors[c] = INNER_COLOR // centre
        colors[c + 1] = OUTER_COLOR // oL_in
        colors[c + 2] = INNER_COLOR // iL_in
        colors[c + 3] = INNER_COLOR // iR_in
        colors[c + 4] = OUTER_COLOR // oR_in
        colors[c + 5] = OUTER_COLOR // oL_out
        colors[c + 6] = INNER_COLOR // iL_out
        colors[c + 7] = INNER_COLOR // iR_out
        colors[c + 8] = OUTER_COLOR // oR_out
    }

    // Body segments — the upstream sample's out-set links to the
    // downstream sample's in-set, with the same three-quad
    // (left feather / solid core / right feather) triangulation as the
    // previous miter implementation.
    for (s in 0 until n - 1) {
        val outBase = s * VERTS_PER_SAMPLE + SLOT_OUT_OL
        val inBase = (s + 1) * VERTS_PER_SAMPLE + SLOT_IN_OL

        val v0 = outBase.toShort() // oL_out_s
        val v1 = (outBase + 1).toShort() // iL_out_s
        val v2 = (outBase + 2).toShort() // iR_out_s
        val v3 = (outBase + 3).toShort() // oR_out_s
        val v4 = inBase.toShort() // oL_in_{s+1}
        val v5 = (inBase + 1).toShort() // iL_in_{s+1}
        val v6 = (inBase + 2).toShort() // iR_in_{s+1}
        val v7 = (inBase + 3).toShort() // oR_in_{s+1}

        val ix = s * 18
        // Left feather quad: (v0, v1, v4, v5)
        indices[ix] = v0; indices[ix + 1] = v1; indices[ix + 2] = v4
        indices[ix + 3] = v1; indices[ix + 4] = v5; indices[ix + 5] = v4
        // Solid core quad: (v1, v2, v5, v6)
        indices[ix + 6] = v1; indices[ix + 7] = v2; indices[ix + 8] = v5
        indices[ix + 9] = v2; indices[ix + 10] = v6; indices[ix + 11] = v5
        // Right feather quad: (v2, v3, v6, v7)
        indices[ix + 12] = v2; indices[ix + 13] = v3; indices[ix + 14] = v6
        indices[ix + 15] = v3; indices[ix + 16] = v7; indices[ix + 17] = v6
    }

    // Interior round joins — emitted only when n ≥ 3. At endpoints the
    // in-set and out-set coincide so the arc collapses to zero area;
    // skipping interior=0 avoids that whole loop.
    //
    // Each interior sample contributes:
    //   - JOIN_INTERMEDIATES_PER_SAMPLE intermediate ring vertices
    //     (left inner + left outer + right inner + right outer, each
    //     of length JOIN_SUBDIVISIONS − 1) appended after the body
    //     samples.
    //   - 18·JOIN_SUBDIVISIONS index entries (J arc steps × 2 sides ×
    //     3 triangles × 3 indices) appended after the body segments.
    //
    // The arc parametrisation rotates inPerp toward outPerp using the
    // signed bend angle θ — see [tessellateRibbon]'s topology section.
    // Endpoints (k=0, k=JOIN_SUBDIVISIONS) coincide with body in-set /
    // out-set vertices respectively, so they aren't re-emitted: the fan
    // index buffer references those body vertices for k=0 and k=J,
    // intermediates only for k in [1, J−1].
    val joinBase = VERTS_PER_SAMPLE * n
    var joinIx = (n - 1) * 18
    for (sIdx in 0 until interiorSampleCount) {
        val s = sIdx + 1
        val base = s * VERTS_PER_SAMPLE
        val centre = base.toShort()

        val inPerpX = -segDy[s - 1]
        val inPerpY = segDx[s - 1]
        val outPerpX = -segDy[s]
        val outPerpY = segDx[s]
        val halfW = halfWidths[s]
        val outerHW = halfW
        val innerHW = (halfW - featherPx).coerceAtLeast(0f)
        val pSample = smoothed[s]

        // Signed bend angle from inPerp to outPerp. atan2 keeps it
        // well-defined at the cusp (θ → ±π) and at straight stretches
        // (θ → 0); both edges fall out of the trig naturally.
        val cosTheta = inPerpX * outPerpX + inPerpY * outPerpY
        val sinTheta = inPerpX * outPerpY - inPerpY * outPerpX
        val theta = atan2(sinTheta, cosTheta)

        // Intermediate ring layout: per interior sample,
        //   [left inner ×(J−1)][left outer ×(J−1)][right inner ×(J−1)][right outer ×(J−1)]
        val intermediatesBase = joinBase + sIdx * JOIN_INTERMEDIATES_PER_SAMPLE
        val leftInnerBase = intermediatesBase
        val leftOuterBase = intermediatesBase + (JOIN_SUBDIVISIONS - 1)
        val rightInnerBase = intermediatesBase + 2 * (JOIN_SUBDIVISIONS - 1)
        val rightOuterBase = intermediatesBase + 3 * (JOIN_SUBDIVISIONS - 1)

        for (k in 1 until JOIN_SUBDIVISIONS) {
            val t = k.toFloat() / JOIN_SUBDIVISIONS.toFloat()
            val angle = t * theta
            val cosA = cos(angle)
            val sinA = sin(angle)
            // Rotate inPerp by `angle`. The basis2 vector is the 90°-CCW
            // rotation of inPerp = (−inPerp.y, inPerp.x), so inlining
            // gives this single multiply-add per axis.
            val dirX = inPerpX * cosA - inPerpY * sinA
            val dirY = inPerpY * cosA + inPerpX * sinA

            val kSlot = k - 1
            val liIdx = leftInnerBase + kSlot
            val loIdx = leftOuterBase + kSlot
            val riIdx = rightInnerBase + kSlot
            val roIdx = rightOuterBase + kSlot

            positions[liIdx * 2] = pSample.x + dirX * innerHW
            positions[liIdx * 2 + 1] = pSample.y + dirY * innerHW
            positions[loIdx * 2] = pSample.x + dirX * outerHW
            positions[loIdx * 2 + 1] = pSample.y + dirY * outerHW
            positions[riIdx * 2] = pSample.x - dirX * innerHW
            positions[riIdx * 2 + 1] = pSample.y - dirY * innerHW
            positions[roIdx * 2] = pSample.x - dirX * outerHW
            positions[roIdx * 2 + 1] = pSample.y - dirY * outerHW

            colors[liIdx] = INNER_COLOR
            colors[loIdx] = OUTER_COLOR
            colors[riIdx] = INNER_COLOR
            colors[roIdx] = OUTER_COLOR
        }

        // Emit fan triangles for both sides. Endpoints (k=0, k=J) point
        // back into the body's in-set / out-set; intermediates point
        // into the ring slots emitted above.
        for (side in 0..1) {
            val innerStartBoundary: Short
            val outerStartBoundary: Short
            val innerEndBoundary: Short
            val outerEndBoundary: Short
            val innerIntermediateBase: Int
            val outerIntermediateBase: Int
            if (side == 0) {
                innerStartBoundary = (base + SLOT_IN_IL).toShort()
                outerStartBoundary = (base + SLOT_IN_OL).toShort()
                innerEndBoundary = (base + SLOT_OUT_IL).toShort()
                outerEndBoundary = (base + SLOT_OUT_OL).toShort()
                innerIntermediateBase = leftInnerBase
                outerIntermediateBase = leftOuterBase
            } else {
                innerStartBoundary = (base + SLOT_IN_IR).toShort()
                outerStartBoundary = (base + SLOT_IN_OR).toShort()
                innerEndBoundary = (base + SLOT_OUT_IR).toShort()
                outerEndBoundary = (base + SLOT_OUT_OR).toShort()
                innerIntermediateBase = rightInnerBase
                outerIntermediateBase = rightOuterBase
            }

            for (k in 0 until JOIN_SUBDIVISIONS) {
                val iStart =
                    if (k == 0) innerStartBoundary
                    else (innerIntermediateBase + k - 1).toShort()
                val iEnd =
                    if (k == JOIN_SUBDIVISIONS - 1) innerEndBoundary
                    else (innerIntermediateBase + k).toShort()
                val oStart =
                    if (k == 0) outerStartBoundary
                    else (outerIntermediateBase + k - 1).toShort()
                val oEnd =
                    if (k == JOIN_SUBDIVISIONS - 1) outerEndBoundary
                    else (outerIntermediateBase + k).toShort()

                // Solid fan triangle: anchored at centre, sweeping the
                // inner-radius arc step.
                indices[joinIx] = centre
                indices[joinIx + 1] = iStart
                indices[joinIx + 2] = iEnd
                // Lateral feather quad → two triangles. Alpha gradient
                // is from inner ring (1) to outer ring (0), matching
                // the body's lateral feather so AA reads continuous.
                indices[joinIx + 3] = iStart
                indices[joinIx + 4] = oStart
                indices[joinIx + 5] = oEnd
                indices[joinIx + 6] = iStart
                indices[joinIx + 7] = oEnd
                indices[joinIx + 8] = iEnd
                joinIx += 9
            }
        }
    }

    // === End caps ===
    //
    // Square ends look fine on a thin pen at low pressure but become a
    // visible artefact on marker / highlighter widths. Match Android Ink:
    // emit a half-disc at each terminus, sized to the body's endpoint
    // half-width and feathered to the same 1-pixel AA falloff so the cap
    // tiles seamlessly with the body's outermost vertices.
    //
    // The cap is rendered as a centre vertex + (K + 1) ring positions
    // sampled around a 180° arc; each arc step contributes one inner-fan
    // triangle (centre → inner_k → inner_{k+1}) and two AA-ring triangles
    // bridging inner / outer arcs. Endpoint halfW is recomputed inline —
    // the body loop doesn't memoise per-sample half-widths and the cost
    // here is two scalar evaluations.
    //
    // The arc parametrisation: u(θ) = cos(θ)·n + sin(θ)·capDir, where
    // capDir points from the terminus toward the open side of the
    // stroke. For the start cap that's −d (away from the first segment);
    // for the end cap that's +d (along the last segment). At θ=0 and
    // θ=π the arc snaps onto the body's left / right perpendiculars, so
    // the cap's first and last ring vertices coincide exactly with the
    // body endpoint vertices — no visible seam.

    // Body+joins consumed before either cap is emitted: every body
    // segment's three quads, plus J arc-fan triangles at each interior
    // sample. Caps are appended after these in both buffers.
    val bodyIndexCount = (n - 1) * 18 + interiorSampleCount * 18 * JOIN_SUBDIVISIONS
    val capsVertexBase = VERTS_PER_SAMPLE * n + joinIntermediateCount

    // Start cap.
    run {
        val dX = segDx[0]
        val dY = segDy[0]
        val capN_x = -dY
        val capN_y = dX
        val p0 = smoothed[0]
        val halfW = halfWidths[0]
        emitCap(
            positions = positions,
            colors = colors,
            indices = indices,
            vertexBase = capsVertexBase,
            indexBase = bodyIndexCount,
            cx = p0.x,
            cy = p0.y,
            nx = capN_x,
            ny = capN_y,
            capDirX = -dX,
            capDirY = -dY,
            outerHW = halfW,
            innerHW = (halfW - featherPx).coerceAtLeast(0f),
        )
    }

    // End cap.
    run {
        val dX = segDx[segCount - 1]
        val dY = segDy[segCount - 1]
        val capN_x = -dY
        val capN_y = dX
        val pn = smoothed[n - 1]
        val halfW = halfWidths[n - 1]
        emitCap(
            positions = positions,
            colors = colors,
            indices = indices,
            vertexBase = capsVertexBase + capVertexCount,
            indexBase = bodyIndexCount + CAP_SUBDIVISIONS * 9,
            cx = pn.x,
            cy = pn.y,
            nx = capN_x,
            ny = capN_y,
            capDirX = dX,
            capDirY = dY,
            outerHW = halfW,
            innerHW = (halfW - featherPx).coerceAtLeast(0f),
        )
    }

    return RibbonMesh(positions, colors, indices)
}

/**
 * Emit one rounded end cap into the shared mesh buffers.
 *
 * Lays out `1 + 2·(K+1)` vertices starting at [vertexBase]:
 *  - `vertexBase` — centre (alpha 1)
 *  - `vertexBase + 1 + 2·k` — inner ring vertex at arc step `k` (alpha 1)
 *  - `vertexBase + 2 + 2·k` — outer ring vertex at arc step `k` (alpha 0)
 *
 * Writes `9·K` indices starting at [indexBase].
 *
 * The arc sweeps from `θ = 0` (along `+n`) through `θ = π` (along `−n`)
 * with the bulge in the [capDirX] / [capDirY] direction — pass `−d` for
 * the start cap and `+d` for the end cap, where `d` is the adjacent
 * segment's unit direction.
 */
private fun emitCap(
    positions: FloatArray,
    colors: IntArray,
    indices: ShortArray,
    vertexBase: Int,
    indexBase: Int,
    cx: Float,
    cy: Float,
    nx: Float,
    ny: Float,
    capDirX: Float,
    capDirY: Float,
    outerHW: Float,
    innerHW: Float,
) {
    // Centre.
    positions[vertexBase * 2] = cx
    positions[vertexBase * 2 + 1] = cy
    colors[vertexBase] = INNER_COLOR

    // Ring.
    for (k in 0..CAP_SUBDIVISIONS) {
        val theta = k.toFloat() / CAP_SUBDIVISIONS.toFloat() * PI.toFloat()
        val cosT = cos(theta)
        val sinT = sin(theta)
        val uX = cosT * nx + sinT * capDirX
        val uY = cosT * ny + sinT * capDirY

        val innerIdx = vertexBase + 1 + 2 * k
        val outerIdx = vertexBase + 2 + 2 * k
        positions[innerIdx * 2] = cx + uX * innerHW
        positions[innerIdx * 2 + 1] = cy + uY * innerHW
        positions[outerIdx * 2] = cx + uX * outerHW
        positions[outerIdx * 2 + 1] = cy + uY * outerHW
        colors[innerIdx] = INNER_COLOR
        colors[outerIdx] = OUTER_COLOR
    }

    // Triangles.
    val centerIdx = vertexBase.toShort()
    for (k in 0 until CAP_SUBDIVISIONS) {
        val ix = indexBase + k * 9
        val ik = (vertexBase + 1 + 2 * k).toShort()
        val ok = (vertexBase + 2 + 2 * k).toShort()
        val ikp1 = (vertexBase + 3 + 2 * k).toShort()
        val okp1 = (vertexBase + 4 + 2 * k).toShort()

        // Inner solid fan triangle (centre → inner_k → inner_{k+1}).
        indices[ix] = centerIdx
        indices[ix + 1] = ik
        indices[ix + 2] = ikp1
        // AA ring triangles bridging inner ↔ outer arcs.
        indices[ix + 3] = ik
        indices[ix + 4] = ok
        indices[ix + 5] = okp1
        indices[ix + 6] = ik
        indices[ix + 7] = okp1
        indices[ix + 8] = ikp1
    }
}

// === Catmull-Rom smoothing ===

/**
 * Catmull-Rom interpolation on the input point list.
 *
 * Subdivides each segment into [subdivisions] interior points using a
 * uniform centripetal-style kernel (this is the "uniform" variant — alpha=0
 * — which is fine for hand input where samples are temporally close).
 *
 * The first/last samples are duplicated as virtual neighbors so the kernel
 * has a left/right neighbor for every real segment.
 *
 * Pressure and tilt are linearly interpolated alongside position. Tilt is
 * interpolated component-wise (tiltX, tiltY) rather than as polar
 * (magnitude, angle) so we don't wrap discontinuously across ±π — linear
 * interpolation in Cartesian space slides through zero on direction
 * reversals, which matches how a real pen's tilt vector actually behaves.
 */
internal fun catmullRomSmooth(
    points: List<PenStrokePoint>,
    subdivisions: Int = SMOOTHING_SUBDIVISIONS,
): List<PenStrokePoint> {
    if (points.size < 2) return points
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
    val tiltX = p1.tiltX + (p2.tiltX - p1.tiltX) * t
    val tiltY = p1.tiltY + (p2.tiltY - p1.tiltY) * t
    return PenStrokePoint(x, y, pressure, elapsed, tiltX, tiltY)
}

// === Width / colour helpers ===

internal fun strokeWidthFor(brush: PenBrush, pressure: Float): Float = when (brush.family) {
    PenBrushFamily.Pen -> brush.size * (PEN_MIN_WIDTH_FACTOR + shapePressure(pressure) * PEN_PRESSURE_FACTOR)
    PenBrushFamily.Marker -> brush.size
    PenBrushFamily.Highlighter -> brush.size
}

/**
 * Apply a power-curve response to the raw platform pressure value before
 * it drives stroke width.
 *
 * Most digitisers report pressure roughly linearly through their dynamic
 * range, but a linear mapping feels under-responsive at the light end:
 * a barely-touching pen with reported pressure ≈ 0.1 produces a barely-
 * visible line, even though the user perceives the contact as
 * "definitely drawing". A power curve with exponent < 1 boosts the low
 * end (`p^0.7` lifts 0.1 → 0.20, 0.5 → 0.62) without saturating the
 * high end, so light strokes register as proper ink while firm strokes
 * still hit the brush's full width. Roughly matches the default Ink
 * pressure-response on Android.
 *
 * Exponent is a global tuning constant for now — exposing it per-brush
 * would be the next step if we ever add per-brush dynamics.
 */
private fun shapePressure(pressure: Float): Float =
    pressure.coerceIn(0f, 1f).pow(PRESSURE_CURVE_EXPONENT)

internal fun colorFor(brush: PenBrush): Color = when (brush.family) {
    PenBrushFamily.Pen, PenBrushFamily.Marker -> brush.color
    PenBrushFamily.Highlighter -> brush.color.copy(alpha = brush.color.alpha * HIGHLIGHTER_ALPHA)
}

/**
 * Per-sample stylus speed, in pixels per millisecond.
 *
 * Sampled over a window rather than adjacent pairs because Catmull-Rom
 * subdivision shrinks the dt between consecutive smoothed points by ~8x —
 * an adjacent-pair velocity would amplify timing jitter from clock
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
 * [VELOCITY_MIN_FACTOR].
 */
private fun velocityWidthFactor(velocity: Float): Float {
    val factor = 1f / (1f + velocity * VELOCITY_THINNING_K)
    return factor.coerceIn(VELOCITY_MIN_FACTOR, 1f)
}

/**
 * Calligraphic-nib width factor: thin when the stroke moves along the tilt
 * direction, full-width when it moves across it. Models a chisel-tip pen.
 *
 * Effect strength scales with `|tilt|` (radians), normalised by π/2: a
 * vertical pen leaves the factor at 1.0 (no calligraphic effect), a fully
 * laid-down pen reaches the maximum modulation. Returns 1.0 immediately when
 * tilt is unavailable or zero.
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
    // Dot product of unit stroke tangent with unit tilt direction.
    val dot = tangentX * tdx + tangentY * tdy
    val alignment = dot * dot
    return 1f - strength * (1f - TILT_MIN_FACTOR) * alignment
}

// === Tuning constants ===

internal const val SMOOTHING_SUBDIVISIONS: Int = 8
private const val PEN_MIN_WIDTH_FACTOR: Float = 0.3f
private const val PEN_PRESSURE_FACTOR: Float = 1.4f
private const val HIGHLIGHTER_ALPHA: Float = 0.3f
private const val MIN_TANGENT_LEN: Float = 1e-4f

// Pressure response curve exponent. < 1 boosts light pressure (more
// responsive on barely-touching pens), > 1 dampens it. 0.7 matches the
// general feel of Android Ink's default pen brush — light strokes feel
// "alive" without making firm strokes feel stuck at maximum width.
private const val PRESSURE_CURVE_EXPONENT: Float = 0.7f

// Velocity-based width modulation (Pen brush only). Window roughly one
// Catmull-Rom subdivision so the speed estimate spans an input
// inter-sample interval. Thinning constant tuned so a typical fast drag
// (~2 px/ms ≈ 2000 px/s) thins to ~0.5×; tap-and-hold stays at 1.0×.
private const val VELOCITY_WINDOW: Int = 8
private const val VELOCITY_THINNING_K: Float = 0.5f
private const val VELOCITY_MIN_FACTOR: Float = 0.35f

// Tilt-based calligraphic width modulation (Pen brush only). Tilt magnitudes
// below √TILT_MIN_MAG_SQ rad (~3°) collapse to no effect — within reporting
// noise on most digitisers.
private const val TILT_MIN_MAG_SQ: Float = 0.0025f
private const val TILT_HALF_PI: Float = (PI / 2.0).toFloat()
private const val TILT_MIN_FACTOR: Float = 0.4f

// Feather width for lateral edge AA, in DrawScope coordinates (logical px).
// 1.0 px softens the stair-step on retina/HiDPI without making the edge
// look fuzzy on thin pens. Tuned visually against the Android Ink output.
private const val AA_FEATHER_PX: Float = 1.0f

// Number of subdivisions per rounded end cap. 8 gives a visually clean
// half-circle at typical pen widths (≤ ~20 dp). Bumping it costs 4 extra
// vertices and 9 extra indices per stroke per cap, so we can afford a
// little headroom — but 12+ is wasted at thin widths.
private const val CAP_SUBDIVISIONS: Int = 8

// Per-sample vertex layout. Each smoothed sample emits one centre vertex,
// then an in-set (along the previous segment's perpendicular) and an
// out-set (along the next segment's perpendicular), with four vertices in
// each set: outer-left, inner-left, inner-right, outer-right. At endpoints
// in-perp and out-perp coincide so the in-set and out-set occupy the same
// world positions; the duplication is harmless and lets the body and
// join emit code share one indexing scheme across all samples.
private const val VERTS_PER_SAMPLE: Int = 9
private const val SLOT_IN_OL: Int = 1
private const val SLOT_IN_IL: Int = 2
private const val SLOT_IN_IR: Int = 3
private const val SLOT_IN_OR: Int = 4
private const val SLOT_OUT_OL: Int = 5
private const val SLOT_OUT_IL: Int = 6
private const val SLOT_OUT_IR: Int = 7
private const val SLOT_OUT_OR: Int = 8

// Round-join arc subdivisions per side. Each interior sample fills the
// gap between its in-set and out-set with a J-step arc fan on each
// side. Higher J → smoother arcs at thick widths; lower J → fewer
// triangles but more visible polygon facets at sharp turns.
//
// J = 4 keeps the per-step angular gap ≤ 22.5° on a 90° turn (chord
// length ≈ 0.39·innerHW), which is a good balance for typical pen and
// marker widths up to ~40 dp. Bumping it costs more triangles per
// sample on the entire smoothed point list, so we keep it conservative.
// J = 1 would collapse this back to the old bevel topology.
private const val JOIN_SUBDIVISIONS: Int = 4

// Per-interior-sample intermediate ring slots: J−1 inner + J−1 outer
// per side × 2 sides. Endpoints (k=0 and k=J) reuse the body's in-set
// and out-set so they aren't re-emitted here. Layout per sample is
//   [left inner ×(J−1)][left outer ×(J−1)][right inner ×(J−1)][right outer ×(J−1)]
private const val JOIN_INTERMEDIATES_PER_SAMPLE: Int = 4 * (JOIN_SUBDIVISIONS - 1)

// Per-vertex colours for the feathered ribbon. Ribbon vertices stay white
// — only their alpha varies — so `BlendMode.MODULATE` against the paint
// colour reduces to multiplying just the alpha channel: paint.rgb stays
// intact, vertex.alpha drives the feather. Inner = full alpha, outer =
// zero. The GPU's linear interpolation between them produces the smooth
// edge gradient that gives us anti-aliasing on a renderer that otherwise
// has none. (Skia's `Paint.isAntiAlias` only affects path rasterization,
// not `drawVertices`.)
//
// Packed ARGB: inner = 0xFFFFFFFF (-1 as signed), outer = 0x00FFFFFF.
private const val INNER_COLOR: Int = -0x1
private const val OUTER_COLOR: Int = 0x00FFFFFF
