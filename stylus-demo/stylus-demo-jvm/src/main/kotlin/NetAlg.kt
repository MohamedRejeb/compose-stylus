import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.util.fastForEachReversed
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import stylus.compose.demo.StylusBrush
import stylus.compose.demo.StylusPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun DrawScope.drawPoints2(
    points: List<StylusPoint>,
    brush: StylusBrush,
) {
    val path = Path()
    val path2 = Path()
    var lastPoint: StylusPoint? = null
    var lastPointOne: Offset = Offset.Unspecified
    var lastPointTwo: Offset = Offset.Unspecified
    var lastAngle: Float = Float.NaN
    var lastAngleOne: Float = Float.NaN
    var lastAngleTwo: Float = Float.NaN

    if (points.isEmpty())
        return

    val startPoints = mutableListOf<Offset>()
    val endPoints = mutableListOf<Offset>()

    for (i in points.indices) {
        val point = points[i]

        if (i == 0) {
            startPoints.add(Offset(point.x, point.y))
            path.moveTo(point.x, point.y)
            path2.moveTo(point.x, point.y)
        } else {
            path2.lineTo(point.x, point.y)
        }

        if (lastPoint != null) {
            val angle = calcLineAngleInRadian(lastPoint, point)
            val radius = point.pressure * brush.width / 2
            val lastRadius = lastPoint.pressure * brush.width / 2

            if (!lastAngle.isNaN()) {
                val midAngle = (angle + lastAngle) / 2
                val angleOne = midAngle + (PI / 2)
                val p1 = Offset(
                    (lastPoint.x + (lastRadius * cos(angleOne))).toFloat(),
                    (lastPoint.y + (lastRadius * sin(angleOne))).toFloat(),
                )

                val angleTwo = midAngle - (PI / 2)
                val p2 = Offset(
                    (lastPoint.x + (lastRadius * cos(angleTwo))).toFloat(),
                    (lastPoint.y + (lastRadius * sin(angleTwo))).toFloat(),
                )

                val pointOne: Offset
                val pointTwo: Offset

                if (!lastAngleOne.isNaN() && lastPointOne.isSpecified) {
                    val midAngleOne = calcLineAngle(lastPointOne, p1)
                    val midAngleTwo = calcLineAngle(lastPointOne, p2)

                    val diffOne = (toDegrees(angle) - midAngleOne).shrinkAngle()
                    val diffTwo = (toDegrees(angle) - midAngleTwo).shrinkAngle()

                    if (diffOne < diffTwo) {
                        pointOne = p1
                        pointTwo = p2
                    } else {
                        pointOne = p2
                        pointTwo = p1
                    }
                } else {
                    pointOne = p1
                    pointTwo = p2
                }

                path.lineTo(pointOne.x, pointOne.y)

                startPoints.add(pointOne)
                endPoints.add(pointTwo)

//                drawPoints(
//                    points = listOf(pointOne, pointTwo, Offset(lastPoint.x, lastPoint.y), Offset(point.x, point.y)),
//                    color = Color.Red,
//                    pointMode = PointMode.Points,
//                    strokeWidth = 5f
//                )
//
//                drawContext.canvas.nativeCanvas.drawString(
//                    "angle: ${toDegrees(angleOne.toFloat())}",
//                    pointOne.x,
//                    pointOne.y,
//                    font = Font(),
//                    paint = Paint()
//                )
//
//                drawContext.canvas.nativeCanvas.drawString(
//                    "angle: ${toDegrees(angleTwo.toFloat())}",
//                    pointTwo.x,
//                    pointTwo.y,
//                    font = Font(),
//                    paint = Paint()
//                )

                lastPointOne = pointOne
                lastPointTwo = pointTwo

                lastAngleOne = angleOne.toFloat()
                lastAngleTwo = angleTwo.toFloat()
            }

//            drawContext.canvas.nativeCanvas.drawString(
//                "angle: ${toDegrees(angle)}",
//                point.x,
//                point.y,
//                font = Font(),
//                paint = Paint()
//            )
//
//            drawCircle(
//                color = brush.color,
//                center = Offset(point.x, point.y),
//                radius = radius,
//                style = Stroke(2f)
//            )
//
//            drawCircle(
//                color = brush.color,
//                center = Offset(lastPoint.x, lastPoint.y),
//                radius = lastRadius,
//                style = Stroke(2f)
//            )

            lastAngle = angle
        }

        if (i == points.lastIndex) {
            startPoints.add(Offset(point.x, point.y))
            path.lineTo(point.x, point.y)
        }

        lastPoint = point
    }

    endPoints.fastForEachReversed {
        path.lineTo(it.x, it.y)
    }

    path.close()

    drawPath(
        path = path,
        color = Color.Black,
//        style = Stroke(1f)
    )

//    drawPath(
//        path = path2,
//        color = Color.Black,
//        style = Stroke(
//            width = 1f,
//        )
//    )
}

fun Float.shrinkAngle(): Float {
    return if (this > 180f) {
        (this % 180f) - 180f
    } else if (this < -180f) {
        (this % 180f) + 180f
    } else if (abs(this) == 180f) {
        0f
    } else {
        this
    }
}

fun calcLineAngle(
    p1: Offset,
    p2: Offset,
): Float {
    val angle = toDegrees(
        atan2(
            p2.y - p1.y,
            p2.x - p1.x
        )
    )

    return angle
}

private fun calcLineAngle(
    p1: StylusPoint,
    p2: StylusPoint,
): Float {
    val angle = toDegrees(
        atan2(
            p2.y - p1.y,
            p2.x - p1.x
        )
    )

    return if (angle < 0) angle + 360 else angle
}

private fun calcLineAngleInRadian(
    p1: StylusPoint,
    p2: StylusPoint,
): Float {
    return atan2(
        p2.y - p1.y,
        p2.x - p1.x
    )
}

private val Float.toRadians: Float
    get() = (this * DEGREES_TO_RADIANS).toFloat()

fun toDegrees(angrad: Float): Float {
    val angle = (angrad * RADIANS_TO_DEGREES).toFloat()
    return angle.shrinkAngle()
}

private const val RADIANS_TO_DEGREES = 57.29577951308232
private const val DEGREES_TO_RADIANS = 0.017453292519943295
