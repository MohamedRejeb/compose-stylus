package stylus

import androidx.compose.ui.geometry.Offset
import stylus.compose.demo.StylusPoint
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

fun simplifyPoints(
    points: List<StylusPoint>,
    epsilon: Float = .25f,
): List<StylusPoint> {
    if (points.size < 3) return points

    // Find the point with the maximum distance
    var dmax = 0f
    var index = 0

    for (i in 1 until points.lastIndex) {
        val d = perpendicularDistance(points[i], points[0], points[points.lastIndex])
        if (d > dmax) {
            index = i
            dmax = d
        }
    }
    // If max distance is greater than epsilon, recursively simplify
    return if (dmax > epsilon) {
        // Recursive call
        val recResults1 = simplifyPoints(points.subList(0, index + 1), epsilon)
        val recResults2 = simplifyPoints(points.subList(index, points.size), epsilon)

        // Build the result list
        listOf(recResults1.subList(0, recResults1.lastIndex), recResults2).flatMap { it.toList() }
    } else {
        listOf(points[0], points[points.lastIndex])
    }
}

private fun perpendicularDistance(pt: StylusPoint, lineFrom: StylusPoint, lineTo: StylusPoint): Float =
    abs((lineTo.x - lineFrom.x) * (lineFrom.y - pt.y) - (lineFrom.x - pt.x) * (lineTo.y - lineFrom.y)) /
            sqrt((lineTo.x - lineFrom.x).pow(2) + (lineTo.y - lineFrom.y).pow(2))

private fun calcDistance(pt1: StylusPoint, pt2: StylusPoint): Float =
    sqrt((pt1.x - pt2.x).pow(2) + (pt1.y - pt2.y).pow(2))

fun calcDistance(pt1: Offset, pt2: Offset): Float =
    sqrt((pt1.x - pt2.x).pow(2) + (pt1.y - pt2.y).pow(2))

fun calcDistanceSquared(pt1: Offset, pt2: Offset): Float =
    (pt1.x - pt2.x).pow(2) + (pt1.y - pt2.y).pow(2)