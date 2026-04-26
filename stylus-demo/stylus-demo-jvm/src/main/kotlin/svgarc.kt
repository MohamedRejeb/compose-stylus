import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathEllipseArc
import stylus.calcDistance
import java.io.IOException
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


@Preview
@Composable
private fun VectorPreview() {
    Column {
        Image(SvgviewerOutput2, null)

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(320.dp)
        ) {
            val path = Path()

            path.apply {
//                drawSlice(
//                    center = Offset(100f, 200f),
//                    radius = 100f,
//                    startDeg = 0f,
//                    endDeg = 80f
//                )

//                circlePath2(
//                    x = 100f,
//                    y = 200f,
//                    r = 100f,
//                )
                drawSlice(
                    drawScope = this@Canvas,
                    start = Offset(133.2f, 214.0f),
                    end = Offset(148.2f, 201.9f),
                )
//                moveTo(10f, 315f)
//                lineTo(110f, 215f)
//                arcTo(
//                    rx = 30f,
//                    ry = 50f,
//                    xAxisRotate = 0f,
//                    x1 = 162.55f,
//                    y1 = 162.45f,
//                )
//                lineTo(172.55f, 152.45f)
//                arcTo(
//                    rx = 30f,
//                    ry = 50f,
//                    xAxisRotate = -45f,
////                    theta = -45f, isMoreThanHalf = false, isPositiveArc = true,
//                    x1 = 215.1f,
//                    y1 = 109.9f
//                )
//                lineTo(315f, 10f)
            }

            translate(
                left = -1000f,
                top = -1000f
            ) {
                scale(10f, pivot = Offset.Zero) {
                    drawPath(
                        path = path,
                        color = Color(0xFF008000),
                    )

                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(
                            width = 2f,
                            cap = StrokeCap.Butt,
                            join = StrokeJoin.Miter,
                            miter = 1.0f
                        ),
                    )
                }
            }
        }
    }
}

private var _SvgviewerOutput: ImageVector? = null

public val SvgviewerOutput2: ImageVector
    get() {
        if (_SvgviewerOutput != null) {
            return _SvgviewerOutput!!
        }
        _SvgviewerOutput = ImageVector.Builder(
            name = "SvgviewerOutput",
            defaultWidth = 320.dp,
            defaultHeight = 320.dp,
            viewportWidth = 320f,
            viewportHeight = 320f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF008000)),
                fillAlpha = 1.0f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1.0f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(10f, 315f)
                lineTo(110f, 215f)
                arcTo(
                    horizontalEllipseRadius = 30f,
                    verticalEllipseRadius = 50f,
                    theta = 0f, isMoreThanHalf = false, isPositiveArc = true,
                    x1 = 162.55f,
                    y1 = 162.45f
                )
                lineTo(172.55f, 152.45f)
                arcTo(
                    horizontalEllipseRadius = 30f,
                    verticalEllipseRadius = 50f,
                    theta = -45f, isMoreThanHalf = false, isPositiveArc = true,
                    x1 = 215.1f,
                    y1 = 109.9f
                )
                lineTo(315f, 10f)
            }
        }.build()
        return _SvgviewerOutput!!
    }

fun Path.arcTo(
    rx: Float,
    ry: Float,
    xAxisRotate: Float,
    x1: Float,
    y1: Float,
) {
    this.asSkiaPath().ellipticalArcTo(
        rx = rx,
        ry = ry,
        xAxisRotate = xAxisRotate,
        arc = PathEllipseArc.SMALLER,
        direction = PathDirection.CLOCKWISE,
        x = x1,
        y = y1
    )
}

internal fun Offset.rotAround(
    other: Offset,
    r: Double,
): Offset {
    if (r == 0.0) return this

    val sin = sin(r)
    val cos = cos(r)

    val px = x - other.x
    val py = y - other.y

    val nx = other.x + px * cos - py * sin
    val ny = other.y + px * sin + py * cos

    return Offset(nx.toFloat(), ny.toFloat())
}

private fun Path.drawSlice(
    drawScope: DrawScope? = null,
    start: Offset,
    end: Offset,
    clockwise: Boolean = true,
) {
    val center = Offset((start.x + end.x) / 2, (start.y + end.y) / 2)
    val radius = calcDistance(start, end) / 2f
    val thirdPoint = start.rotAround(center, Math.toRadians(if (clockwise) 90.0 else -90.0))
    val const = 0.5522848f

    val angle = calcLineAngle(start, end)
    val startPointNormal = (start).rotAround(center, Math.toRadians((-angle).toDouble()))
    val endPointNormal = (end).rotAround(center, Math.toRadians((-angle).toDouble()))
    val thirdPointNormal = (thirdPoint).rotAround(center, Math.toRadians((-angle).toDouble()))
    val cp1Normal1 = startPointNormal.copy(y = startPointNormal.y + radius * const)

    val cp1Normal2 = startPointNormal.copy(y = startPointNormal.y - radius * const)
    val cp1Normal =
        if (calcDistance(thirdPointNormal, cp1Normal1) < calcDistance(thirdPointNormal, cp1Normal2))
            cp1Normal1
        else
            cp1Normal2

    val cp2Normal1 = thirdPointNormal.copy(x = thirdPointNormal.x + radius * const)
    val cp2Normal2 = thirdPointNormal.copy(x = thirdPointNormal.x - radius * const)
    val cp2Normal =
        if (calcDistance(startPointNormal, cp2Normal1) < calcDistance(startPointNormal, cp2Normal2))
            cp2Normal1
        else
            cp2Normal2

    val cp3Normal1 = thirdPointNormal.copy(x = thirdPointNormal.x + radius * const)
    val cp3Normal2 = thirdPointNormal.copy(x = thirdPointNormal.x - radius * const)
    val cp3Normal =
        if (calcDistance(endPointNormal, cp3Normal1) < calcDistance(endPointNormal, cp3Normal2))
            cp3Normal1
        else
            cp3Normal2

    val cp4Normal1 = endPointNormal.copy(y = startPointNormal.y + radius * const)
    val cp4Normal2 = endPointNormal.copy(y = startPointNormal.y - radius * const)
    val cp4Normal =
        if (calcDistance(thirdPointNormal, cp4Normal1) < calcDistance(thirdPointNormal, cp4Normal2))
            cp4Normal1
        else
            cp4Normal2

    val cp1 = cp1Normal.rotAround(center, Math.toRadians(angle.toDouble()))
    val cp2 = cp2Normal.rotAround(center, Math.toRadians(angle.toDouble()))
    val cp3 = cp3Normal.rotAround(center, Math.toRadians(angle.toDouble()))
    val cp4 = cp4Normal.rotAround(center, Math.toRadians(angle.toDouble()))

//    drawScope?.drawPoints(
//        points = listOf(
//            center,
//            start,
//            startPointNormal,
////            cp1,
//            cp1Normal,
////            cp2,
//            cp2Normal,
//            thirdPoint,
//            thirdPointNormal,
////            cp3,
//            cp3Normal,
////            cp4,
//            cp4Normal,
//            end,
//            endPointNormal,
//        ),
//        pointMode = PointMode.Points,
//        color = Color.Red,
//        strokeWidth = 10f
//    )

    moveTo(start.x, start.y)
    cubicTo(
        cp1.x,
        cp1.y,
        cp2.x,
        cp2.y,
        thirdPoint.x,
        thirdPoint.y
    )
    cubicTo(
        cp3.x,
        cp3.y,
        cp4.x,
        cp4.y,
        end.x,
        end.y
    )
}

fun Path.circlePath2(
    x: Float,
    y: Float,
    r: Float,
) {
    moveTo(x, y)
    moveTo(x - r, y)

    drawSlice(
        start = Offset(x - r, y),
        end = Offset(x + r, y),
    )

    drawSlice(
        start = Offset(x + r, y),
        end = Offset(x - r, y),
    )
}

private fun Path.drawSlice(
    center: Offset,
    radius: Float,
    startDeg: Float,
    endDeg: Float,
) {
//    moveTo(0f, 0f)
    val smallArc =
        createSmallArc(radius.toDouble(), Math.toRadians(startDeg.toDouble()), Math.toRadians(endDeg.toDouble()))
//    lineTo(smallArc[0], smallArc[1])
    moveTo(center.x + smallArc[0], center.y + smallArc[1])
    cubicTo(
        center.x + smallArc[2],
        center.y + smallArc[3],
        center.x + smallArc[4],
        center.y + smallArc[5],
        center.x + smallArc[6],
        center.y + smallArc[7]
    )
//    close()
}

private fun createSmallArc(r: Double, a1: Double, a2: Double): List<Float> {
    // Compute all four points for an arc that subtends the same total angle
    // but is centered on the X-axis
    val a = (a2 - a1) / 2
    val x4 = r * cos(a)
    val y4 = r * sin(a)
    val x1 = x4
    val y1 = -y4
    val q1 = x1 * x1 + y1 * y1

    val q2 = q1 + x1 * x4 + y1 * y4
    val k2 = 4 / 3.0 * (sqrt(2 * q1 * q2) - q2) / (x1 * y4 - y1 * x4)
    val x2 = x1 - k2 * y1
    val y2 = y1 + k2 * x1
    val x3 = x2
    val y3 = -y2


    // Find the arc points' actual locations by computing x1,y1 and x4,y4
    // and rotating the control points by a + a1
    val ar = a + a1
    val cos_ar = cos(ar)
    val sin_ar = sin(ar)

    val list: MutableList<Float> = ArrayList()
    list.add((r * cos(a1)).toFloat())
    list.add((r * sin(a1)).toFloat())
    list.add((x2 * cos_ar - y2 * sin_ar).toFloat())
    list.add((x2 * sin_ar + y2 * cos_ar).toFloat())
    list.add((x3 * cos_ar - y3 * sin_ar).toFloat())
    list.add((x3 * sin_ar + y3 * cos_ar).toFloat())
    list.add((r * cos(a2)).toFloat())
    list.add((r * sin(a2)).toFloat())
    return list
}