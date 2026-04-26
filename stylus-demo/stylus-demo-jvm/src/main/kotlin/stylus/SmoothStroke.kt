package stylus

import stylus.compose.demo.StylusPoint
import kotlin.math.sqrt

/**
 * Turn a noisy raw stylus point stream into a smooth, dense one so the
 * variable-width polygon renderer (`drawPoints2`) can produce clean strokes
 * instead of jittery, faceted shapes.
 *
 * Pipeline:
 *  1. **Pressure low-pass** – damps sample-to-sample noise on the pressure
 *     axis. Sensors are noisy at low force; without this the stroke width
 *     visibly pulses.
 *  2. **Centripetal Catmull-Rom interpolation** – produces `segmentsPerSpan`
 *     intermediate points between every pair of input samples, rounding
 *     direction changes and filling in detail when the pen moves fast. The
 *     centripetal (α=0.5) parameterization avoids overshoot/self-intersection
 *     on unevenly-spaced samples (typical for variable-rate tablet input).
 *
 * Tuning knobs:
 *  - [pressureAlpha] – higher = follow raw pressure more closely (less smooth);
 *    lower = smoother but laggier. 0.35 is a good default.
 *  - [segmentsPerSpan] – higher = smoother curves but more polygon vertices
 *    downstream. 6 is enough for visually smooth strokes.
 */
fun smoothStroke(
    raw: List<StylusPoint>,
    pressureAlpha: Float = 0.35f,
    segmentsPerSpan: Int = 6,
): List<StylusPoint> {
    if (raw.size < 2) return raw
    val pressureSmoothed = lowPassPressure(raw, pressureAlpha)
    return centripetalCatmullRom(pressureSmoothed, segmentsPerSpan)
}

private fun lowPassPressure(points: List<StylusPoint>, alpha: Float): List<StylusPoint> {
    if (points.isEmpty()) return points
    val out = ArrayList<StylusPoint>(points.size)
    var smoothed = points[0].pressure
    out += StylusPoint(points[0].x, points[0].y, smoothed)
    for (i in 1 until points.size) {
        val p = points[i]
        smoothed = alpha * p.pressure + (1f - alpha) * smoothed
        out += StylusPoint(p.x, p.y, smoothed)
    }
    return out
}

private fun centripetalCatmullRom(
    points: List<StylusPoint>,
    segmentsPerSpan: Int,
): List<StylusPoint> {
    if (points.size < 2) return points
    if (points.size == 2) return points

    val out = ArrayList<StylusPoint>(points.size * segmentsPerSpan + 1)

    for (i in 0 until points.size - 1) {
        // Clamp the virtual control points at the endpoints so the curve
        // actually starts/ends at the first/last input sample.
        val p0 = if (i == 0) points[0] else points[i - 1]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 >= points.size) points.last() else points[i + 2]

        val t0 = 0f
        val t1 = t0 + sqrtDist(p0, p1).coerceAtLeast(EPS)
        val t2 = t1 + sqrtDist(p1, p2).coerceAtLeast(EPS)
        val t3 = t2 + sqrtDist(p2, p3).coerceAtLeast(EPS)

        for (s in 0 until segmentsPerSpan) {
            val t = t1 + (t2 - t1) * (s.toFloat() / segmentsPerSpan)
            out += interpolate(p0, p1, p2, p3, t0, t1, t2, t3, t)
        }
    }
    out += points.last()
    return out
}

private fun interpolate(
    p0: StylusPoint, p1: StylusPoint, p2: StylusPoint, p3: StylusPoint,
    t0: Float, t1: Float, t2: Float, t3: Float, t: Float,
): StylusPoint {
    val w10 = (t1 - t) / (t1 - t0); val w11 = (t - t0) / (t1 - t0)
    val w20 = (t2 - t) / (t2 - t1); val w21 = (t - t1) / (t2 - t1)
    val w30 = (t3 - t) / (t3 - t2); val w31 = (t - t2) / (t3 - t2)

    val a1x = w10 * p0.x + w11 * p1.x
    val a1y = w10 * p0.y + w11 * p1.y
    val a1p = w10 * p0.pressure + w11 * p1.pressure

    val a2x = w20 * p1.x + w21 * p2.x
    val a2y = w20 * p1.y + w21 * p2.y
    val a2p = w20 * p1.pressure + w21 * p2.pressure

    val a3x = w30 * p2.x + w31 * p3.x
    val a3y = w30 * p2.y + w31 * p3.y
    val a3p = w30 * p2.pressure + w31 * p3.pressure

    val b10 = (t2 - t) / (t2 - t0); val b11 = (t - t0) / (t2 - t0)
    val b20 = (t3 - t) / (t3 - t1); val b21 = (t - t1) / (t3 - t1)

    val b1x = b10 * a1x + b11 * a2x
    val b1y = b10 * a1y + b11 * a2y
    val b1p = b10 * a1p + b11 * a2p

    val b2x = b20 * a2x + b21 * a3x
    val b2y = b20 * a2y + b21 * a3y
    val b2p = b20 * a2p + b21 * a3p

    val cx = w20 * b1x + w21 * b2x
    val cy = w20 * b1y + w21 * b2y
    val cp = w20 * b1p + w21 * b2p

    return StylusPoint(cx, cy, cp.coerceIn(0f, 1f))
}

private fun sqrtDist(a: StylusPoint, b: StylusPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(sqrt(dx * dx + dy * dy))
}

private const val EPS = 1e-4f
