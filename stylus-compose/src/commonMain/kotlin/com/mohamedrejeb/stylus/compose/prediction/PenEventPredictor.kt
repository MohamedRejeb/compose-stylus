package com.mohamedrejeb.stylus.compose.prediction

import com.mohamedrejeb.stylus.compose.PenStrokePoint
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Stateful pen-motion predictor for non-Android targets — Desktop, iOS, Web.
 *
 * Faithful port of `androidx.input.motionprediction.kalman.SinglePointerPredictor`
 * (and `PointerKalmanFilter`). On Android, prediction is delegated to the
 * platform predictor via Jetpack Ink's `InProgressStrokes`; this class is
 * not on the Android path.
 *
 * The algorithm:
 *
 *  1. **Three independent 4-state Kalman filters** track x, y, and pressure
 *     through the [AxisKalmanFilter] constant-jerk model. Independent axes
 *     match androidx — coupling them gains nothing for stylus motion.
 *
 *  2. **Report rate** is the running mean Δt over the first [REPORT_RATE_SAMPLES]
 *     events. The Kalman F matrix is in *sample-rate units*, so projections
 *     in milliseconds become projections in samples via this rate.
 *
 *  3. **Confidence factor** scales the predict horizon down when motion is
 *     either too slow to need prediction or too jittery to extrapolate
 *     reliably. confidenceFactor = speedFactor · jankFactor with both
 *     factors clamped to [0, 1].
 *
 *  4. **Forward roll-out** is *not* `F^k`. It's a damped Euler step repeated
 *     `predictionTargetInSamples` times — the [JANK_INFLUENCE]/
 *     [ACCELERATION_INFLUENCE]/[VELOCITY_INFLUENCE] constants are empirical
 *     dampers (lifted verbatim from androidx) that prevent the predicted
 *     point from flying off when projected far ahead. Don't "fix" them to
 *     dt-power Taylor coefficients — they aren't.
 *
 *  5. **Initialisation** matches androidx: the first sample is set directly
 *     into the position state with vel/acc/jerk left at 0, no predict/update
 *     cycle. Predictions are suppressed until at least
 *     [MIN_KALMAN_FILTER_ITERATIONS] samples have been recorded.
 *
 *  6. **Duplicate-event suppression**: if a sample arrives within
 *     [EVENT_TIME_IGNORED_THRESHOLD_MS] of the previous one with the exact
 *     same position, it's dropped.
 *
 *  Pressure is rolled forward linearly (not via the same damped roll-out)
 *  to match androidx — and clamped to [0, 1].
 *
 * @param predictAheadMs how far ahead to predict, in milliseconds. The
 *   default of 16 ms matches androidx's per-frame target on a 60 Hz display.
 *   Capped internally at [MAX_PREDICTION_MS] (32 ms) per androidx.
 * @param sigmaProcess Kalman process noise (jerk). androidx defaults to 0.01.
 * @param sigmaMeasurement Kalman measurement noise. androidx defaults to 1.0.
 */
internal class PenEventPredictor(
    private val predictAheadMs: Long = DEFAULT_PREDICT_AHEAD_MS,
    sigmaProcess: Float = DEFAULT_SIGMA_PROCESS,
    sigmaMeasurement: Float = DEFAULT_SIGMA_MEASUREMENT,
) {
    private val xFilter = AxisKalmanFilter(sigmaProcess, sigmaMeasurement)
    private val yFilter = AxisKalmanFilter(sigmaProcess, sigmaMeasurement)
    private val pressureFilter = AxisKalmanFilter(sigmaProcess, sigmaMeasurement)

    private var sampleCount: Int = 0
    private var lastTimestamp: Long = -1L
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var reportRateMs: Float = 0f

    fun reset() {
        xFilter.reset()
        yFilter.reset()
        pressureFilter.reset()
        sampleCount = 0
        lastTimestamp = -1L
        lastX = 0f
        lastY = 0f
        reportRateMs = 0f
    }

    /**
     * Feed a new pen sample into the filter. Drops duplicates within
     * [EVENT_TIME_IGNORED_THRESHOLD_MS].
     */
    fun record(point: PenStrokePoint) {
        val ts = point.elapsedMillis
        if (lastTimestamp >= 0 &&
            ts - lastTimestamp < EVENT_TIME_IGNORED_THRESHOLD_MS &&
            point.x == lastX &&
            point.y == lastY
        ) {
            return
        }

        if (sampleCount == 0) {
            xFilter.initialize(point.x)
            yFilter.initialize(point.y)
            pressureFilter.initialize(point.pressure)
        } else {
            // Update running mean Δt over the first REPORT_RATE_SAMPLES
            // observations. Welford-style streaming average — first dt
            // (sampleCount = 1 entering record) sets reportRateMs = dt.
            if (sampleCount < REPORT_RATE_SAMPLES) {
                val dt = (ts - lastTimestamp).toFloat()
                reportRateMs += (dt - reportRateMs) / sampleCount.toFloat()
            }
            xFilter.predict()
            yFilter.predict()
            pressureFilter.predict()
            xFilter.update(point.x)
            yFilter.update(point.y)
            pressureFilter.update(point.pressure)
        }

        sampleCount++
        lastTimestamp = ts
        lastX = point.x
        lastY = point.y
    }

    /**
     * Project the next pen sample [predictAheadMs] beyond the most recent
     * [record], or null if not enough samples have accumulated yet, the
     * report rate isn't available, or the confidence factor backs prediction
     * down to zero samples.
     */
    fun predict(): PenStrokePoint? {
        if (sampleCount < MIN_KALMAN_FILTER_ITERATIONS) return null
        if (reportRateMs <= 0f) return null

        val targetMs = predictAheadMs.coerceAtMost(MAX_PREDICTION_MS).toFloat()

        // Confidence factor: speedFactor · jankFactor, both in [0, 1].
        // velocity is in dp/sample, divide by reportRateMs → dp/ms.
        val vx = xFilter.velocity
        val vy = yFilter.velocity
        val speedDpPerMs = sqrt(vx * vx + vy * vy) / reportRateMs
        val jx = xFilter.jerk
        val jy = yFilter.jerk
        val jerkMag = sqrt(jx * jx + jy * jy)

        val speedFactor = ((speedDpPerMs - LOW_SPEED) / (HIGH_SPEED - LOW_SPEED))
            .coerceIn(0f, 1f)
        val jankFactor = 1f - ((jerkMag - LOW_JANK) / (HIGH_JANK - LOW_JANK))
            .coerceIn(0f, 1f)
        val confidenceFactor = speedFactor * jankFactor

        val targetSamples = ceil(targetMs / reportRateMs * confidenceFactor).toInt()
        if (targetSamples <= 0) return null

        // Damped Euler roll-out over targetSamples sample steps.
        var px = xFilter.position; var vxRoll = xFilter.velocity
        var ax = xFilter.acceleration; var jxRoll = xFilter.jerk
        var py = yFilter.position; var vyRoll = yFilter.velocity
        var ay = yFilter.acceleration; var jyRoll = yFilter.jerk
        var pp = pressureFilter.position
        val vp = pressureFilter.velocity

        for (i in 0 until targetSamples) {
            ax += jxRoll * JANK_INFLUENCE
            vxRoll += ax * ACCELERATION_INFLUENCE
            px += vxRoll * VELOCITY_INFLUENCE
            ay += jyRoll * JANK_INFLUENCE
            vyRoll += ay * ACCELERATION_INFLUENCE
            py += vyRoll * VELOCITY_INFLUENCE
            // Pressure rolls linearly per androidx (no damping).
            pp += vp
        }

        return PenStrokePoint(
            x = px,
            y = py,
            pressure = pp.coerceIn(0f, 1f),
            elapsedMillis = lastTimestamp + predictAheadMs,
        )
    }

    companion object {
        /** One frame at 60 Hz — androidx's per-frame predict target. */
        const val DEFAULT_PREDICT_AHEAD_MS: Long = 16L

        /** androidx's `MAX_PREDICTION_MS` cap. */
        const val MAX_PREDICTION_MS: Long = 32L

        /** androidx's `PointerKalmanFilter` default — jerk noise. */
        const val DEFAULT_SIGMA_PROCESS: Float = 0.01f

        /** androidx's `PointerKalmanFilter` default — measurement noise. */
        const val DEFAULT_SIGMA_MEASUREMENT: Float = 1.0f

        /** androidx's `MIN_KALMAN_FILTER_ITERATIONS`. */
        const val MIN_KALMAN_FILTER_ITERATIONS: Int = 4

        /** androidx's `EVENT_TIME_IGNORED_THRESHOLD_MS`. */
        const val EVENT_TIME_IGNORED_THRESHOLD_MS: Long = 20L

        /** Window over which androidx averages Δt. */
        const val REPORT_RATE_SAMPLES: Int = 20

        // === Forward roll-out dampers (androidx SinglePointerPredictor). ===
        // These are NOT dt-power Taylor coefficients — they're empirical
        // dampening factors that prevent overshoot on long predictions.
        const val JANK_INFLUENCE: Float = 0.1f
        const val ACCELERATION_INFLUENCE: Float = 0.5f
        const val VELOCITY_INFLUENCE: Float = 1.0f

        // === Confidence-factor thresholds (androidx). ===
        const val LOW_JANK: Float = 0.02f
        const val HIGH_JANK: Float = 0.2f
        const val LOW_SPEED: Float = 0.0f
        const val HIGH_SPEED: Float = 2.0f
    }
}
