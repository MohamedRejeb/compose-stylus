package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-math invariants of [tessellateRibbon] — pins the geometry the
 * Skia `drawVertices` renderer relies on without involving the GPU.
 *
 * The tessellator owns a handful of numerically interesting decisions:
 * mesh size, per-sample bevel offsets, the cap-arc geometry, and the
 * "no body vertex extends past `halfW`" invariant that distinguishes
 * the bevel topology from the older miter approach. These tests
 * exercise each of those independently, so a regression shows up here
 * long before it reaches a visual screenshot.
 */
class RibbonTessellationTest {

    private fun pt(x: Float, y: Float, p: Float = 1f): PenStrokePoint =
        PenStrokePoint(x = x, y = y, pressure = p, elapsedMillis = 0L)

    private val testBrush = PenBrush.marker(Color.Black, size = 10f)

    // ─── Empty / degenerate inputs ───────────────────────────────────

    @Test
    fun `empty input returns empty mesh`() {
        val mesh = tessellateRibbon(emptyList(), testBrush)
        assertEquals(0, mesh.positions.size)
        assertEquals(0, mesh.colors.size)
        assertEquals(0, mesh.indices.size)
    }

    @Test
    fun `single point returns empty mesh`() {
        val mesh = tessellateRibbon(listOf(pt(0f, 0f)), testBrush)
        assertEquals(0, mesh.indices.size)
    }

    // ─── Mesh sizing ─────────────────────────────────────────────────

    /**
     * Buffer sizes are load-bearing: the renderer feeds them straight
     * to `drawVertices` without bounds-checking, and the index buffer
     * uses raw offsets into the vertex buffer. Wrong sizes either
     * silently render nothing or read out-of-bounds vertex memory.
     */
    @Test
    fun `vertex and index counts match the body plus joins plus two caps formula`() {
        val n = 5
        val points = (0 until n).map { i -> pt(i * 10f, 0f) }
        val mesh = tessellateRibbon(points, testBrush)

        val capVertexCount = 1 + 2 * (CAP_SUBDIVISIONS_FOR_TEST + 1)
        val interiorSamples = n - 2
        val joinIntermediates = interiorSamples * JOIN_INTERMEDIATES_PER_SAMPLE_FOR_TEST
        val expectedVertices =
            VERTS_PER_SAMPLE_FOR_TEST * n + joinIntermediates + 2 * capVertexCount
        val expectedFloats = expectedVertices * 2
        val expectedIndices =
            (n - 1) * 18 +
                interiorSamples * 18 * JOIN_SUBDIVISIONS_FOR_TEST +
                2 * CAP_SUBDIVISIONS_FOR_TEST * 9

        assertEquals(expectedFloats, mesh.positions.size, "positions")
        assertEquals(expectedVertices, mesh.colors.size, "colors")
        assertEquals(expectedIndices, mesh.indices.size, "indices")
    }

    // ─── Straight stroke ─────────────────────────────────────────────

    /**
     * On a horizontal stroke the perpendicular is straight up/down and
     * in-perp ≡ out-perp at every sample, so in-set and out-set vertices
     * coincide exactly: oL_in / oL_out land at +halfW·y, iL_in / iL_out
     * at +innerHW·y, and the right-side mirror at −y. Any deviation
     * means perpendiculars or half-width are being computed wrong.
     */
    @Test
    fun `horizontal straight stroke produces vertical perpendicular offsets`() {
        val points = (0..4).map { pt(it * 10f, 100f) }
        val mesh = tessellateRibbon(points, testBrush)

        // Body half-width for marker @ size 10 is just the brush size / 2 = 5.
        val halfW = 5f
        // Feather is 1 logical px (matches AA_FEATHER_PX).
        val innerHW = halfW - 1f

        for (i in points.indices) {
            // Each sample writes 18 floats: centre + in-set ×4 + out-set ×4.
            val k = i * 18
            val cx = points[i].x
            val cy = points[i].y

            // Centre.
            assertNear(cx, mesh.positions[k], label = "centre.x @$i")
            assertNear(cy, mesh.positions[k + 1], label = "centre.y @$i")
            // In-set.
            assertNear(cx, mesh.positions[k + 2], label = "oL_in.x @$i")
            assertNear(cy + halfW, mesh.positions[k + 3], label = "oL_in.y @$i")
            assertNear(cx, mesh.positions[k + 4], label = "iL_in.x @$i")
            assertNear(cy + innerHW, mesh.positions[k + 5], label = "iL_in.y @$i")
            assertNear(cx, mesh.positions[k + 6], label = "iR_in.x @$i")
            assertNear(cy - innerHW, mesh.positions[k + 7], label = "iR_in.y @$i")
            assertNear(cx, mesh.positions[k + 8], label = "oR_in.x @$i")
            assertNear(cy - halfW, mesh.positions[k + 9], label = "oR_in.y @$i")
            // Out-set — coincides with in-set on a straight stroke.
            assertNear(cx, mesh.positions[k + 10], label = "oL_out.x @$i")
            assertNear(cy + halfW, mesh.positions[k + 11], label = "oL_out.y @$i")
            assertNear(cx, mesh.positions[k + 12], label = "iL_out.x @$i")
            assertNear(cy + innerHW, mesh.positions[k + 13], label = "iL_out.y @$i")
            assertNear(cx, mesh.positions[k + 14], label = "iR_out.x @$i")
            assertNear(cy - innerHW, mesh.positions[k + 15], label = "iR_out.y @$i")
            assertNear(cx, mesh.positions[k + 16], label = "oR_out.x @$i")
            assertNear(cy - halfW, mesh.positions[k + 17], label = "oR_out.y @$i")
        }
    }

    /**
     * Per sample the colours follow a fixed pattern:
     * `(centre=INNER, in-set=OOII reversed wait — outer-inner-inner-outer,
     * out-set= same)`. The Skia renderer modulates the paint colour by
     * these vertex alphas to draw the AA feather, and the bevel triangles
     * rely on the centre vertex being full-alpha so the inside-corner
     * solid bridges read as solid.
     */
    @Test
    fun `vertex colours follow centre-inset-outset pattern per sample`() {
        val points = (0..3).map { pt(it * 10f, 0f) }
        val mesh = tessellateRibbon(points, testBrush)
        val inner = -0x1 // 0xFFFFFFFF
        val outer = 0x00FFFFFF
        for (i in points.indices) {
            val c = i * 9
            assertEquals(inner, mesh.colors[c], "centre @$i")
            assertEquals(outer, mesh.colors[c + 1], "oL_in @$i")
            assertEquals(inner, mesh.colors[c + 2], "iL_in @$i")
            assertEquals(inner, mesh.colors[c + 3], "iR_in @$i")
            assertEquals(outer, mesh.colors[c + 4], "oR_in @$i")
            assertEquals(outer, mesh.colors[c + 5], "oL_out @$i")
            assertEquals(inner, mesh.colors[c + 6], "iL_out @$i")
            assertEquals(inner, mesh.colors[c + 7], "iR_out @$i")
            assertEquals(outer, mesh.colors[c + 8], "oR_out @$i")
        }
    }

    // ─── Bevel join ──────────────────────────────────────────────────

    /**
     * At a 90° corner the in-set should sit on the previous segment's
     * perpendicular and the out-set on the next segment's perpendicular —
     * **without** any extension along the bisector. That's the defining
     * feature of the bevel topology: every body vertex stays exactly
     * `halfW` (or `innerHW`) from its sample, regardless of corner
     * sharpness.
     *
     * Layout: three samples making an L. Centre sample at (10, 0).
     * In-segment goes right (dx=1, dy=0) → in-perp = (0, 1).
     * Out-segment goes down (dx=0, dy=1) → out-perp = (−1, 0).
     */
    @Test
    fun `90 degree corner places in-set and out-set on segment perpendiculars`() {
        val points = listOf(pt(0f, 0f), pt(10f, 0f), pt(10f, 10f))
        val mesh = tessellateRibbon(points, testBrush)
        val halfW = 5f
        val innerHW = halfW - 1f

        // Corner sample is index 1 → vertex byte offset = 18.
        val k = 1 * 18

        // In-set lies along +y perpendicular through (10, 0):
        //   oL_in = (10, +halfW), iL_in = (10, +innerHW),
        //   iR_in = (10, −innerHW), oR_in = (10, −halfW).
        assertNear(10f, mesh.positions[k + 2]); assertNear(halfW, mesh.positions[k + 3])
        assertNear(10f, mesh.positions[k + 4]); assertNear(innerHW, mesh.positions[k + 5])
        assertNear(10f, mesh.positions[k + 6]); assertNear(-innerHW, mesh.positions[k + 7])
        assertNear(10f, mesh.positions[k + 8]); assertNear(-halfW, mesh.positions[k + 9])

        // Out-set lies along −x perpendicular through (10, 0):
        //   oL_out = (10 − halfW, 0), iL_out = (10 − innerHW, 0),
        //   iR_out = (10 + innerHW, 0), oR_out = (10 + halfW, 0).
        assertNear(10f - halfW, mesh.positions[k + 10]); assertNear(0f, mesh.positions[k + 11])
        assertNear(10f - innerHW, mesh.positions[k + 12]); assertNear(0f, mesh.positions[k + 13])
        assertNear(10f + innerHW, mesh.positions[k + 14]); assertNear(0f, mesh.positions[k + 15])
        assertNear(10f + halfW, mesh.positions[k + 16]); assertNear(0f, mesh.positions[k + 17])
    }

    /**
     * Numerical hygiene: a near-180° cusp would have blown up the old
     * miter math via 1/cos(half-turn-angle). The bevel topology side-
     * steps that division entirely by writing each side's perpendicular
     * separately, so even pathological inputs stay finite.
     */
    @Test
    fun `near-cusp turn does not produce NaN or Infinity`() {
        // Half-turn very close to 180° — d_in and d_out nearly opposite.
        val points = listOf(pt(0f, 0f), pt(10f, 0f), pt(0.1f, 0.01f))
        val mesh = tessellateRibbon(points, testBrush)
        for (v in mesh.positions) {
            assertTrue(v.isFinite(), "non-finite position: $v")
        }
    }

    // ─── Spike / overlap regression ──────────────────────────────────

    /**
     * The visible artefact at thick widths is a body vertex sitting far
     * outside the sample's perpendicular cross-section — that's the
     * miter spike. With the bevel topology every body vertex must stay
     * within `halfW` of its sample centre, regardless of how sharp the
     * turn is.
     *
     * Input is a single 60° zigzag corner at width 40, which under the
     * old miter implementation extruded a spike up to ~2·halfW (40 dp)
     * along the bisector — the small triangular wings visible in
     * `zigzag-thick.png` before the fix.
     */
    @Test
    fun `sharp corner does not extrude body vertex past halfW from its sample`() {
        val brush = PenBrush.pen(Color.Black, size = 40f)
        val halfW = 20f
        val tolerance = 0.5f // Sub-pixel slack — feather can't push past halfW.
        // 60° zigzag corner.
        val points = listOf(
            pt(0f, 0f),
            pt(50f, 0f),
            pt(75f, 43.3f), // 60° from horizontal × 50 px segment.
        )
        val mesh = tessellateRibbon(points, brush)

        // Body vertices are the first 9·n entries; the rest are caps.
        val bodyVertexCount = points.size * VERTS_PER_SAMPLE_FOR_TEST
        for (i in points.indices) {
            val sample = points[i]
            for (slot in 0 until VERTS_PER_SAMPLE_FOR_TEST) {
                val vIdx = i * VERTS_PER_SAMPLE_FOR_TEST + slot
                if (vIdx >= bodyVertexCount) break
                val vx = mesh.positions[vIdx * 2]
                val vy = mesh.positions[vIdx * 2 + 1]
                val d = sqrt(
                    (vx - sample.x) * (vx - sample.x) +
                        (vy - sample.y) * (vy - sample.y),
                )
                assertTrue(
                    d <= halfW + tolerance,
                    "body vertex sample=$i slot=$slot at ($vx, $vy) is $d from " +
                        "sample (${sample.x}, ${sample.y}); exceeds halfW=$halfW + " +
                        "tolerance=$tolerance — miter spike regression",
                )
            }
        }
    }

    /**
     * Round-join intermediate ring vertices must sit on circles of
     * radius `innerHW` (inner ring) and `halfW` (outer ring) around
     * their sample centre, otherwise the arc fan no longer tiles with
     * the body's in-set / out-set endpoints and a visible seam appears.
     *
     * Three samples → exactly one interior join at sample 1. With a
     * 90° corner the fan sweeps a quarter-arc; we just need every
     * intermediate vertex (left + right, inner + outer) to land on
     * the right circle.
     */
    @Test
    fun `round join intermediate vertices lie on expected radii`() {
        val points = listOf(pt(0f, 0f), pt(10f, 0f), pt(10f, 10f))
        val mesh = tessellateRibbon(points, testBrush)
        val halfW = 5f
        val innerHW = halfW - 1f

        // Join intermediates begin right after the body block.
        val joinBase = VERTS_PER_SAMPLE_FOR_TEST * points.size
        val cx = 10f
        val cy = 0f
        val perSample = JOIN_INTERMEDIATES_PER_SAMPLE_FOR_TEST
        val perSide = JOIN_SUBDIVISIONS_FOR_TEST - 1
        for (sIdx in 0 until 1) {
            val base = joinBase + sIdx * perSample
            for (offset in 0 until perSample) {
                val vIdx = base + offset
                val vx = mesh.positions[vIdx * 2]
                val vy = mesh.positions[vIdx * 2 + 1]
                val r = sqrt((vx - cx) * (vx - cx) + (vy - cy) * (vy - cy))
                // Layout per sample: [L_in ×K-1][L_out ×K-1][R_in ×K-1][R_out ×K-1].
                val expected = when ((offset / perSide) % 2) {
                    0 -> innerHW
                    else -> halfW
                }
                assertNear(expected, r, eps = 0.01f, label = "join intermediate $offset")
            }
        }
    }

    // ─── End caps ────────────────────────────────────────────────────

    /**
     * Each cap's outer ring should sit on a circle of radius `halfW`
     * around the cap centre; the inner ring on a circle of radius
     * `halfW − feather`. If the cap radii drift away from those they
     * stop tiling cleanly with the body's outer / inner vertices and
     * a visible seam appears at the cap-body join.
     */
    @Test
    fun `start cap ring vertices lie on circles of expected radii`() {
        val points = (0..3).map { pt(it * 10f, 0f) }
        val mesh = tessellateRibbon(points, testBrush)
        val halfW = 5f
        val innerHW = halfW - 1f

        val interiorSamples = (points.size - 2).coerceAtLeast(0)
        val capVertexBase = VERTS_PER_SAMPLE_FOR_TEST * points.size +
            interiorSamples * JOIN_INTERMEDIATES_PER_SAMPLE_FOR_TEST
        val cx = points[0].x
        val cy = points[0].y
        // Centre vertex.
        assertNear(cx, mesh.positions[capVertexBase * 2])
        assertNear(cy, mesh.positions[capVertexBase * 2 + 1])

        // Ring vertices at every arc step.
        for (k in 0..CAP_SUBDIVISIONS_FOR_TEST) {
            val innerIdx = capVertexBase + 1 + 2 * k
            val outerIdx = capVertexBase + 2 + 2 * k
            val iX = mesh.positions[innerIdx * 2]
            val iY = mesh.positions[innerIdx * 2 + 1]
            val oX = mesh.positions[outerIdx * 2]
            val oY = mesh.positions[outerIdx * 2 + 1]
            val innerR = sqrt((iX - cx) * (iX - cx) + (iY - cy) * (iY - cy))
            val outerR = sqrt((oX - cx) * (oX - cx) + (oY - cy) * (oY - cy))
            assertNear(innerHW, innerR, eps = 0.01f, label = "inner ring k=$k")
            assertNear(halfW, outerR, eps = 0.01f, label = "outer ring k=$k")
        }
    }

    /**
     * Cap arc endpoints (θ = 0 and θ = π) must coincide with the body's
     * outer-left / outer-right vertices at the same sample, otherwise
     * the cap and the body don't tile and a visible seam appears.
     *
     * For the start cap (sample 0, in-set ≡ out-set on a straight start):
     *   - oL_in lives at body sample 0, slot 1 → positions[2..3]
     *   - oR_in lives at body sample 0, slot 4 → positions[8..9]
     */
    @Test
    fun `start cap endpoints coincide with body outer-left and outer-right`() {
        val points = listOf(pt(0f, 0f), pt(10f, 0f), pt(20f, 0f))
        val mesh = tessellateRibbon(points, testBrush)

        val interiorSamples = (points.size - 2).coerceAtLeast(0)
        val capVertexBase = VERTS_PER_SAMPLE_FOR_TEST * points.size +
            interiorSamples * JOIN_INTERMEDIATES_PER_SAMPLE_FOR_TEST
        // θ = 0 → first outer-ring vertex.
        val capOuter0Idx = capVertexBase + 2
        val capOuter0X = mesh.positions[capOuter0Idx * 2]
        val capOuter0Y = mesh.positions[capOuter0Idx * 2 + 1]
        // Body oL_in at sample 0 → slot 1, positions[(0*9+1)*2..]
        val bodyOL0X = mesh.positions[2]
        val bodyOL0Y = mesh.positions[3]
        assertNear(bodyOL0X, capOuter0X, label = "cap θ=0 outer x vs body oL_in.x")
        assertNear(bodyOL0Y, capOuter0Y, label = "cap θ=0 outer y vs body oL_in.y")

        // θ = π → last outer-ring vertex. Body oR_in at sample 0 → slot 4.
        val capOuterLastIdx = capVertexBase + 2 + 2 * CAP_SUBDIVISIONS_FOR_TEST
        val capOuterLastX = mesh.positions[capOuterLastIdx * 2]
        val capOuterLastY = mesh.positions[capOuterLastIdx * 2 + 1]
        val bodyOR0X = mesh.positions[(0 * 9 + 4) * 2]
        val bodyOR0Y = mesh.positions[(0 * 9 + 4) * 2 + 1]
        assertNear(bodyOR0X, capOuterLastX, label = "cap θ=π outer x vs body oR_in.x")
        assertNear(bodyOR0Y, capOuterLastY, label = "cap θ=π outer y vs body oR_in.y")
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun assertNear(expected: Float, actual: Float, eps: Float = 1e-3f, label: String = "") {
        assertTrue(
            abs(expected - actual) <= eps,
            "$label expected $expected, got $actual (diff ${abs(expected - actual)} > $eps)",
        )
    }

    /**
     * Mirrors the private `CAP_SUBDIVISIONS`, `VERTS_PER_SAMPLE`, and
     * `JOIN_SUBDIVISIONS` constants in `RibbonTessellation.kt`. If
     * those change, change these too — the duplication is intentional
     * so the test stays a black-box check on the public mesh shape.
     */
    private val CAP_SUBDIVISIONS_FOR_TEST = 8
    private val VERTS_PER_SAMPLE_FOR_TEST = 9
    private val JOIN_SUBDIVISIONS_FOR_TEST = 4
    private val JOIN_INTERMEDIATES_PER_SAMPLE_FOR_TEST = 4 * (JOIN_SUBDIVISIONS_FOR_TEST - 1)
}
