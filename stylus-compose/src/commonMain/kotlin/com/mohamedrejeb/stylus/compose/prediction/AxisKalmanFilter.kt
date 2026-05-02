package com.mohamedrejeb.stylus.compose.prediction

/**
 * 4-state Kalman filter for one axis with constant-jerk motion model.
 * Faithful port of `androidx.input.motionprediction.kalman.KalmanFilter`
 * specialised to the (4, 1) shape used by `PointerKalmanFilter`.
 *
 * State vector x = [position, velocity, acceleration, jerk].
 *
 * Transition F operates in **sample-rate units** (dt = 1 sample), matching
 * androidx — milliseconds → samples conversion is the [PenEventPredictor]'s
 * job via the running mean report rate. F is the constant-jerk Taylor
 * expansion truncated at the jerk term:
 * ```
 *     | 1   1   1/2   1/6 |
 *     | 0   1   1     1/2 |
 *     | 0   0   1     1   |
 *     | 0   0   0     1   |
 * ```
 * Measurement H = [1, 0, 0, 0] — only position is observed.
 *
 * Process noise Q = g·gᵀ · [sigmaProcess], with g = [1/6, 1/2, 1, 1].
 * Measurement noise R = sigmaMeasurement.
 *
 * Matrices are stored as flat row-major [FloatArray]s; with [stateDim] = 4
 * the inner products are short enough to inline rather than loop, which
 * costs ~zero extra LOC for a healthy speedup over generic matrix code.
 */
internal class AxisKalmanFilter(
    sigmaProcess: Float,
    sigmaMeasurement: Float,
) {
    /** State vector x[0..3] = [position, velocity, acceleration, jerk]. */
    private val x = FloatArray(4)

    /** Covariance P (4x4 row-major). Initialised to the identity. */
    private val P = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    /** Process noise Q = g·gᵀ · sigmaProcess. */
    private val Q: FloatArray = run {
        val g = floatArrayOf(1f / 6f, 0.5f, 1f, 1f)
        FloatArray(16) { idx -> g[idx / 4] * g[idx % 4] * sigmaProcess }
    }

    /** Scalar measurement noise R (R is 1×1 since H is 1×4). */
    private val R: Float = sigmaMeasurement

    /** Scratch space for `predict()` to avoid allocating per call. */
    private val tempFP = FloatArray(16)

    val position: Float get() = x[0]
    val velocity: Float get() = x[1]
    val acceleration: Float get() = x[2]
    val jerk: Float get() = x[3]

    fun reset() {
        x.fill(0f)
        P.fill(0f)
        P[0] = 1f
        P[5] = 1f
        P[10] = 1f
        P[15] = 1f
    }

    /**
     * Initialise state from the first measurement without running the
     * predict/update cycle — matches androidx's first-sample handling
     * (`mNumIterations == 0` ⇒ `mXKalman.x[0] = x`).
     */
    fun initialize(measurement: Float) {
        reset()
        x[0] = measurement
    }

    /**
     * Kalman predict step: x ← F·x; P ← F·P·Fᵀ + Q.
     *
     * F is upper triangular with the constant-jerk Taylor coefficients
     * (see class kdoc). Hand-inlined rather than a generic 4×4 matmul.
     */
    fun predict() {
        // x = F * x
        // F[0,*] = [1, 1, 0.5, 1/6] → x'[0] = x[0] + x[1] + 0.5*x[2] + (1/6)*x[3]
        // F[1,*] = [0, 1, 1, 0.5]   → x'[1] = x[1] + x[2] + 0.5*x[3]
        // F[2,*] = [0, 0, 1, 1]     → x'[2] = x[2] + x[3]
        // F[3,*] = [0, 0, 0, 1]     → x'[3] = x[3]   (unchanged)
        val newX0 = x[0] + x[1] + 0.5f * x[2] + ONE_SIXTH * x[3]
        val newX1 = x[1] + x[2] + 0.5f * x[3]
        val newX2 = x[2] + x[3]
        x[0] = newX0
        x[1] = newX1
        x[2] = newX2

        // P ← F·P·Fᵀ + Q.
        // Step 1: tempFP = F · P. Each row uses the same upper-triangular
        // F coefficients applied to the column slice of P.
        for (j in 0..3) {
            tempFP[0 * 4 + j] = P[0 * 4 + j] + P[1 * 4 + j] + 0.5f * P[2 * 4 + j] + ONE_SIXTH * P[3 * 4 + j]
            tempFP[1 * 4 + j] = P[1 * 4 + j] + P[2 * 4 + j] + 0.5f * P[3 * 4 + j]
            tempFP[2 * 4 + j] = P[2 * 4 + j] + P[3 * 4 + j]
            tempFP[3 * 4 + j] = P[3 * 4 + j]
        }
        // Step 2: P = tempFP · Fᵀ + Q. (tempFP·Fᵀ)[i,j] = Σ_k tempFP[i,k]·F[j,k].
        // The same F[j,k] coefficients above apply column-wise here.
        for (i in 0..3) {
            val fp0 = tempFP[i * 4 + 0]
            val fp1 = tempFP[i * 4 + 1]
            val fp2 = tempFP[i * 4 + 2]
            val fp3 = tempFP[i * 4 + 3]
            P[i * 4 + 0] = fp0 + fp1 + 0.5f * fp2 + ONE_SIXTH * fp3 + Q[i * 4 + 0]
            P[i * 4 + 1] = fp1 + fp2 + 0.5f * fp3 + Q[i * 4 + 1]
            P[i * 4 + 2] = fp2 + fp3 + Q[i * 4 + 2]
            P[i * 4 + 3] = fp3 + Q[i * 4 + 3]
        }
    }

    /**
     * Kalman update step with scalar measurement [z].
     *
     * Innovation y = z − H·x = z − x[0].
     * Innovation covariance S = H·P·Hᵀ + R = P[0,0] + R.
     * Gain K = (P·Hᵀ)/S = column-0 of P / S.
     * x ← x + K·y; P ← (I − K·H)·P, which simplifies (since H = [1,0,0,0])
     * to P[i,j] ← P[i,j] − K[i] · P[0,j].
     */
    fun update(z: Float) {
        val y = z - x[0]
        val s = P[0] + R
        if (s <= 0f) return

        val k0 = P[0] / s    // P[0,0] / s
        val k1 = P[4] / s    // P[1,0] / s
        val k2 = P[8] / s    // P[2,0] / s
        val k3 = P[12] / s   // P[3,0] / s

        x[0] += k0 * y
        x[1] += k1 * y
        x[2] += k2 * y
        x[3] += k3 * y

        // Save P[0,*] before we overwrite row 0 — every row's update
        // references the original P[0,j] terms.
        val p00 = P[0]; val p01 = P[1]; val p02 = P[2]; val p03 = P[3]
        P[0] = p00 - k0 * p00
        P[1] = p01 - k0 * p01
        P[2] = p02 - k0 * p02
        P[3] = p03 - k0 * p03
        P[4] -= k1 * p00; P[5] -= k1 * p01; P[6] -= k1 * p02; P[7] -= k1 * p03
        P[8] -= k2 * p00; P[9] -= k2 * p01; P[10] -= k2 * p02; P[11] -= k2 * p03
        P[12] -= k3 * p00; P[13] -= k3 * p01; P[14] -= k3 * p02; P[15] -= k3 * p03
    }

    private companion object {
        const val ONE_SIXTH: Float = 1f / 6f
    }
}
