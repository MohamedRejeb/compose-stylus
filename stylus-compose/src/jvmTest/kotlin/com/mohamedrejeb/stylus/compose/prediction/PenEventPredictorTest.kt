package com.mohamedrejeb.stylus.compose.prediction

import com.mohamedrejeb.stylus.compose.PenStrokePoint
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [PenEventPredictor] — the Kalman-filtered, faithful port of
 * `androidx.input.motionprediction.kalman.SinglePointerPredictor` used on
 * Compose Desktop / iOS / Web.
 *
 * These do not aim for bit-identity with the androidx output; they pin the
 * **behaviours** that make the algorithm robust:
 *   - prediction is suppressed until the filter has accumulated enough
 *     samples (`MIN_KALMAN_FILTER_ITERATIONS`)
 *   - duplicates within `EVENT_TIME_IGNORED_THRESHOLD_MS` don't perturb state
 *   - on steady linear motion the predicted point is *near* the cursor's
 *     extrapolated position (within a sample step or two — not bit-exact
 *     because the confidence factor scales the predict horizon)
 *   - jittery / very-slow motion drives the confidence factor to zero, so
 *     the predictor either returns null or stays close to the last sample
 *   - tiny `Δt` between adjacent samples cannot blow up the prediction
 *     (the historical regression of the old 2-point linear predictor)
 */
class PenEventPredictorTest {

    private fun pt(x: Float, y: Float, t: Long, pressure: Float = 0.5f) =
        PenStrokePoint(x = x, y = y, pressure = pressure, elapsedMillis = t)

    private fun PenStrokePoint.distTo(other: PenStrokePoint): Float =
        sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y))

    // ─── Initialisation / suppression ──────────────────────────────────

    @Test
    fun `predict returns null with no samples`() {
        assertNull(PenEventPredictor().predict())
    }

    @Test
    fun `predict is suppressed below MIN_KALMAN_FILTER_ITERATIONS samples`() {
        val predictor = PenEventPredictor()
        repeat(3) { i -> predictor.record(pt(i * 4f, 0f, i * 16L)) }
        assertNull(
            predictor.predict(),
            "below MIN_KALMAN_FILTER_ITERATIONS = ${PenEventPredictor.MIN_KALMAN_FILTER_ITERATIONS}",
        )
    }

    /**
     * After enough samples for the Kalman filter to converge AND with motion
     * fast enough for the confidence factor to clear zero, prediction must
     * fire. Matches androidx behaviour — `MIN_KALMAN_FILTER_ITERATIONS` is
     * a floor, not a guarantee; with `sigmaProcess = 0.01` the filter is
     * conservative and takes ~10 samples plus non-trivial speed before the
     * confidence factor admits a non-zero predict horizon.
     */
    @Test
    fun `prediction fires after Kalman convergence on fast steady motion`() {
        val predictor = PenEventPredictor()
        // 15 samples at 16 dp / 16 ms = 1 dp/ms (high enough for speedFactor).
        repeat(15) { i -> predictor.record(pt(i * 16f, 0f, i * 16L)) }
        assertNotNull(predictor.predict(), "Kalman + confidence should clear zero by 15 samples")
    }

    @Test
    fun `reset zeros the filter and a new stroke can produce predictions`() {
        val predictor = PenEventPredictor()
        repeat(15) { i -> predictor.record(pt(i * 16f, 0f, i * 16L)) }
        assertNotNull(predictor.predict(), "first stroke converges")

        predictor.reset()
        assertNull(predictor.predict(), "reset suppresses prediction")

        // No state bleed-through: a fresh stroke in a different direction
        // produces a fresh prediction.
        repeat(15) { i -> predictor.record(pt(500f - i * 16f, 200f, i * 16L)) }
        val predicted = predictor.predict()
        assertNotNull(predicted, "second stroke after reset should converge too")
        assertTrue(
            predicted.x < 500f,
            "new stroke moves leftward; prediction should follow (got x=${predicted.x})",
        )
    }

    // ─── Steady motion ────────────────────────────────────────────────

    @Test
    fun `steady horizontal motion predicts ahead in the direction of motion`() {
        val predictor = PenEventPredictor()
        // 15 steady samples at 1 dp/ms (fast enough to converge confidence).
        val samples = (0..14).map { pt(it * 16f, 0f, it * 16L) }
        samples.forEach(predictor::record)

        val predicted = predictor.predict()
        assertNotNull(predicted)
        assertTrue(
            predicted.x >= samples.last().x,
            "predicted x (${predicted.x}) must extend forward of cursor (${samples.last().x})",
        )
        assertTrue(
            abs(predicted.y) < 5f,
            "no y-motion in input → predicted y stays near 0 (got ${predicted.y})",
        )
    }

    @Test
    fun `predicted timestamp is lastTimestamp plus predictAheadMs`() {
        val predictor = PenEventPredictor(predictAheadMs = 16L)
        val samples = (0..14).map { pt(it * 16f, 0f, 100L + it * 16L) }
        samples.forEach(predictor::record)

        val predicted = predictor.predict()
        assertNotNull(predicted)
        assertEquals(samples.last().elapsedMillis + 16L, predicted.elapsedMillis)
    }

    // ─── Tiny-Δt regression ───────────────────────────────────────────

    /**
     * Old 2-point linear predictor would extrapolate by `(last − prev) *
     * (16 / 1)` when the last two samples landed 1 ms apart, producing a
     * 16× overshoot. A Kalman filter ignores instantaneous Δt — it tracks
     * the underlying motion through the report rate, not the last-segment
     * gap — so a single duplicate-ish sample cannot fling the prediction.
     */
    @Test
    fun `near-duplicate sample at the end does not balloon prediction`() {
        // 14 steady samples at 16 dp / 16 ms (Kalman converges), then a
        // 15th sample 1 ms after the 14th — the kind of input the old
        // 2-point linear predictor would have multiplied by `16/Δt = 16`.
        val predictorBaseline = PenEventPredictor()
        val baseline = (0..13).map { pt(it * 16f, 0f, it * 16L) }
        baseline.forEach(predictorBaseline::record)
        val baselinePred = predictorBaseline.predict()
        assertNotNull(baselinePred)

        val predictorJittered = PenEventPredictor()
        val jittered = baseline + pt(baseline.last().x + 1f, 0f, baseline.last().elapsedMillis + 1L)
        jittered.forEach(predictorJittered::record)
        val jitteredPred = predictorJittered.predict()

        // Either the confidence factor knocks the predict horizon to 0 and
        // the predictor returns null — the safest possible outcome, exactly
        // what the old linear predictor failed to do — or it returns a
        // point that stays within a sane envelope of the cursor.
        if (jitteredPred != null) {
            val drift = jitteredPred.distTo(baselinePred)
            assertTrue(
                drift < 30f,
                "tiny-Δt sample must not balloon prediction: jittered=$jitteredPred " +
                    "vs baseline=$baselinePred (drift=$drift dp)",
            )
        }
    }

    // ─── Duplicate-event suppression ─────────────────────────────────

    @Test
    fun `exact duplicate within ignore-threshold does not change state`() {
        val predictor = PenEventPredictor()
        // Bring the predictor past Kalman convergence so prediction is non-null.
        val baseline = (0..14).map { pt(it * 16f, 0f, it * 16L) }
        baseline.forEach(predictor::record)
        val before = predictor.predict()
        assertNotNull(before)

        // Record an exact-position duplicate within EVENT_TIME_IGNORED_THRESHOLD_MS.
        // The predictor must treat it as a no-op: no advance of state, no
        // new sample absorbed.
        predictor.record(pt(baseline.last().x, baseline.last().y, baseline.last().elapsedMillis + 5L))
        val after = predictor.predict()
        assertNotNull(after)
        assertEquals(before.x, after.x, "duplicate must not move predicted x")
        assertEquals(before.y, after.y, "duplicate must not move predicted y")
        assertEquals(before.elapsedMillis, after.elapsedMillis, "duplicate must not advance time")
    }

    // ─── Jitter / confidence factor ──────────────────────────────────

    @Test
    fun `single late jittery sample is dampened by the Kalman filter`() {
        // 14 steady eastward samples (Kalman converges), then a wild
        // perpendicular jump. Either the confidence factor knocks the
        // predict horizon down to zero (predict returns null) — perfectly
        // fine — or the prediction stays close to the long-run trend
        // because Kalman + low process noise weights history over outliers.
        val predictor = PenEventPredictor()
        val baseline = (0..13).map { pt(it * 16f, 0f, it * 16L) }
        baseline.forEach(predictor::record)
        predictor.record(pt(baseline.last().x, 200f, baseline.last().elapsedMillis + 16L))

        val predicted = predictor.predict()
        if (predicted != null) {
            // True trend was eastward; we should still mostly project east,
            // not get yanked far north because of one outlier.
            assertTrue(
                predicted.y < 100f,
                "Kalman should weight the eastward history over a single " +
                    "northward outlier (got predicted.y=${predicted.y})",
            )
        }
    }

    // ─── Pressure ────────────────────────────────────────────────────

    @Test
    fun `pressure passes through and stays bounded in 0 to 1`() {
        val predictor = PenEventPredictor()
        val samples = (0..14).map { i ->
            // Pressure walks from 0.3 to 0.65 over the stroke.
            pt(i * 16f, 0f, i * 16L, pressure = 0.3f + i * 0.025f)
        }
        samples.forEach(predictor::record)
        val predicted = predictor.predict()
        assertNotNull(predicted)
        assertTrue(predicted.pressure in 0f..1f, "pressure clamped to [0, 1]")
    }
}
