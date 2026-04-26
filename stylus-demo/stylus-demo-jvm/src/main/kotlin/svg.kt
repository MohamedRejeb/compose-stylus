import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathEllipseArc


internal fun average(
    a: Float,
    b: Float,
) = (a + b) / 2

fun Path.circlePath(
    x: Float,
    y: Float,
    r: Float,
) {
    moveTo(x, y)
    relativeMoveTo(-r, 0f)
    asSkiaPath().rEllipticalArcTo(
        rx = r,
        ry = r,
        xAxisRotate = 0f,
        arc = PathEllipseArc.SMALLER,
        direction = PathDirection.CLOCKWISE,
        dx = r * 2,
        dy = 0f,
    )
    asSkiaPath().rEllipticalArcTo(
        rx = r,
        ry = r,
        xAxisRotate = 0f,
        arc = PathEllipseArc.SMALLER,
        direction = PathDirection.CLOCKWISE,
        dx = -r * 2,
        dy = 0f,
    )
}

@Preview
@Composable
private fun VectorPreview() {
    Column(
        modifier = Modifier.padding(20.dp)
    ) {
        Image(SvgviewerOutput, null)

        Canvas(
            modifier = Modifier
                .size(400.dp)
        ) {
            var x1 = 1.4308f
            var y1 = -4.1547f
            var x = average(1.4308f, 7.3025f)
            var y = average(-4.1547f, -7.985f)

            val path = Path()
            path.moveTo(-1.5525f, -2.3858f)

            path.quadraticBezierTo(
                x1 = x1,
                y1 = y1,
                x2 = x,
                y2 = y,
            )

            var lastControlPointX = x1
            var lastControlPointY = y1
            var lastX = x
            var lastY = y

            fun reflectiveQuadTo(xx: Float, yy: Float) {
                x = xx
                y = yy
                x1 = lastX + (lastX - lastControlPointX)
                y1 = lastY + (lastY - lastControlPointY)

                path.quadraticBezierTo(x1, y1, x, y)

                lastControlPointX = x1
                lastControlPointY = y1
                lastX = x
                lastY = y
            }

            path.apply {

//                reflectiveQuadTo(1.4308f, -4.1547f)
                // convert it to relativeQuadraticBezierTo


//                reflectiveQuadTo(7.3025f, -7.985f)

                reflectiveQuadTo(12.9203f, -12.1462f)
                reflectiveQuadTo(18.0875f, -16.4644f)
                reflectiveQuadTo(22.5084f, -20.935f)
                reflectiveQuadTo(26.2636f, -25.4983f)
                reflectiveQuadTo(29.4499f, -30.5623f)
                reflectiveQuadTo(31.6364f, -35.4904f)
                reflectiveQuadTo(32.7675f, -39.3848f)
                reflectiveQuadTo(33.1334f, -43.6336f)
                reflectiveQuadTo(31.5834f, -47.6357f)
                reflectiveQuadTo(27.8223f, -49.9659f)
                reflectiveQuadTo(23.7761f, -50.9999f)
                reflectiveQuadTo(19.5747f, -50.0046f)
                reflectiveQuadTo(15.6586f, -47.612f)
                reflectiveQuadTo(12.359f, -44.9012f)
                reflectiveQuadTo(9.0486f, -41.604f)
                reflectiveQuadTo(6.4037f, -37.8607f)
                reflectiveQuadTo(4.6024f, -33.5515f)
                reflectiveQuadTo(3.1591f, -28.0932f)
                reflectiveQuadTo(2.0514f, -21.6376f)
                reflectiveQuadTo(1.4432f, -14.6661f)
                reflectiveQuadTo(1.1f, -7.1746f)
                reflectiveQuadTo(1.1795f, 0.0013f)
                reflectiveQuadTo(1.906f, 6.439f)
                reflectiveQuadTo(2.9489f, 12.0289f)
                reflectiveQuadTo(3.9153f, 16.3495f)
                reflectiveQuadTo(5.1038f, 20.0916f)
                reflectiveQuadTo(7.1325f, 21.1817f)
                reflectiveQuadTo(9.8215f, 19.5225f)
                reflectiveQuadTo(12.4151f, 17.8778f)
                reflectiveQuadTo(15.1307f, 16.2628f)
                reflectiveQuadTo(17.9034f, 15.1343f)
                reflectiveQuadTo(19.1525f, 14.8662f)

//                arcTo(2.874f, 2.874f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.799f, 20.5777f)
//
                arcTo(2.874f, 2.874f, 0f, 19.799f, 20.5777f)
                reflectiveQuadTo(18.8159f, 20.6609f)
                reflectiveQuadTo(15.8556f, 21.7668f)
                reflectiveQuadTo(12.3132f, 23.7945f)
                reflectiveQuadTo(8.487f, 25.7932f)
                reflectiveQuadTo(4.758f, 26.3793f)
                reflectiveQuadTo(2.4588f, 24.6184f)
                reflectiveQuadTo(1.1885f, 21.1273f)
                reflectiveQuadTo(0.4052f, 17.0929f)
                reflectiveQuadTo(-0.4805f, 12.681f)
                reflectiveQuadTo(-1.5765f, 6.8374f)
                reflectiveQuadTo(-2.3508f, 0.0614f)
                reflectiveQuadTo(-2.4651f, -7.329f)
                reflectiveQuadTo(-2.1586f, -14.9707f)
                reflectiveQuadTo(-1.5728f, -22.2465f)
                reflectiveQuadTo(-0.4855f, -29.0467f)
                reflectiveQuadTo(1.0249f, -35.0304f)
                reflectiveQuadTo(3.052f, -40.111f)
                reflectiveQuadTo(5.9442f, -44.5418f)
                reflectiveQuadTo(9.3086f, -48.3325f)
                reflectiveQuadTo(12.8218f, -51.6853f)
                reflectiveQuadTo(16.4656f, -54.3979f)
                reflectiveQuadTo(21.4472f, -55.8062f)
                reflectiveQuadTo(27.1918f, -55.3567f)
                reflectiveQuadTo(31.2763f, -53.7f)
                reflectiveQuadTo(34.6367f, -51.3097f)
                reflectiveQuadTo(37.0358f, -48.0829f)
                reflectiveQuadTo(37.5568f, -44.6955f)
                reflectiveQuadTo(37.2027f, -41.5873f)
                reflectiveQuadTo(36.3496f, -38.3288f)
                reflectiveQuadTo(34.9972f, -34.0534f)
                reflectiveQuadTo(32.6182f, -28.5906f)
                reflectiveQuadTo(29.2731f, -23.0869f)
                reflectiveQuadTo(25.406f, -18.1656f)
                reflectiveQuadTo(20.8616f, -13.2821f)
                reflectiveQuadTo(15.7009f, -8.5947f)
                reflectiveQuadTo(10.1124f, -3.9804f)
                reflectiveQuadTo(4.3786f, 0.3745f)
                reflectiveQuadTo(1.5525f, 2.3858f)
                arcTo(2.8464f, 2.8464f, 0f, -1.5525f, -2.3858f)
                close()
                moveTo(21.8502f, 16.1027f)
                reflectiveQuadTo(22.5932f, 17.1779f)
                reflectiveQuadTo(24.0704f, 19.7311f)
                reflectiveQuadTo(25.6012f, 22.8333f)
                reflectiveQuadTo(27.6897f, 26.8471f)
                reflectiveQuadTo(30.1925f, 30.9446f)
                reflectiveQuadTo(32.6427f, 33.5161f)
                reflectiveQuadTo(35.278f, 34.8069f)
                reflectiveQuadTo(38.4532f, 35.5334f)
                reflectiveQuadTo(42.1019f, 36.0141f)
                reflectiveQuadTo(45.7998f, 35.8267f)
                reflectiveQuadTo(49.7192f, 34.6387f)
                reflectiveQuadTo(53.8905f, 32.7323f)
                reflectiveQuadTo(57.4841f, 30.5985f)
                reflectiveQuadTo(60.7127f, 27.9165f)
                reflectiveQuadTo(63.2545f, 24.9015f)
                reflectiveQuadTo(64.8413f, 20.82f)
                reflectiveQuadTo(65.6813f, 16.166f)
                reflectiveQuadTo(63.4586f, 16.3226f)
                reflectiveQuadTo(59.7514f, 20.9075f)
                reflectiveQuadTo(56.9962f, 26.2658f)
                reflectiveQuadTo(54.6323f, 31.9051f)
                reflectiveQuadTo(52.7808f, 37.2326f)
                reflectiveQuadTo(51.3545f, 42.2138f)
                reflectiveQuadTo(50.4738f, 46.6173f)
                reflectiveQuadTo(50.0759f, 50.1996f)
                reflectiveQuadTo(51.3364f, 53.8296f)
                reflectiveQuadTo(54.5648f, 56.4373f)
                reflectiveQuadTo(58.0056f, 57.1748f)
                reflectiveQuadTo(61.1955f, 57.4423f)
                reflectiveQuadTo(65.7464f, 56.6504f)
                reflectiveQuadTo(70.2734f, 55.2325f)
                reflectiveQuadTo(73.9331f, 54.0686f)
                reflectiveQuadTo(77.5519f, 53.3225f)
                reflectiveQuadTo(80.6829f, 53.625f)
                reflectiveQuadTo(82.9203f, 51.9693f)
                reflectiveQuadTo(84.1591f, 47.9169f)
                reflectiveQuadTo(84.6927f, 44.6952f)
                reflectiveQuadTo(84.6403f, 43.4566f)
                arcTo(3.2541f, 3.2541f, 0f, 91.1197f, 42.8434f)
                reflectiveQuadTo(91.1559f, 44.1086f)
                reflectiveQuadTo(90.7397f, 47.5164f)
                reflectiveQuadTo(89.467f, 52.0048f)
                reflectiveQuadTo(87.8837f, 55.772f)
                reflectiveQuadTo(85.631f, 58.4144f)
                reflectiveQuadTo(82.2356f, 59.7351f)
                reflectiveQuadTo(78.7678f, 59.2102f)
                reflectiveQuadTo(75.2749f, 58.9732f)
                reflectiveQuadTo(71.8744f, 59.8057f)
                reflectiveQuadTo(68.3901f, 61.0396f)
                reflectiveQuadTo(64.452f, 62.1379f)
                reflectiveQuadTo(60.7846f, 62.3287f)
                reflectiveQuadTo(57.0759f, 61.9851f)
                reflectiveQuadTo(53.5252f, 61.2228f)
                reflectiveQuadTo(50.6099f, 59.7246f)
                reflectiveQuadTo(48.1256f, 57.1707f)
                reflectiveQuadTo(46.4322f, 53.5501f)
                reflectiveQuadTo(46.125f, 49.7798f)
                reflectiveQuadTo(46.6834f, 45.9012f)
                reflectiveQuadTo(47.5607f, 41.2004f)
                reflectiveQuadTo(48.8714f, 35.9669f)
                reflectiveQuadTo(50.5757f, 30.3299f)
                reflectiveQuadTo(52.7619f, 24.2632f)
                reflectiveQuadTo(55.244f, 18.4638f)
                reflectiveQuadTo(58.1797f, 12.7452f)
                reflectiveQuadTo(61.1267f, 8.9367f)
                reflectiveQuadTo(64.7901f, 8.0096f)
                reflectiveQuadTo(68.5063f, 9.3185f)
                reflectiveQuadTo(70.2577f, 12.5304f)
                reflectiveQuadTo(70.3259f, 16.6727f)
                reflectiveQuadTo(69.4784f, 20.9287f)
                reflectiveQuadTo(68.2266f, 24.2708f)
                reflectiveQuadTo(66.2768f, 27.3752f)
                reflectiveQuadTo(63.1616f, 30.9375f)
                reflectiveQuadTo(59.5119f, 33.9504f)
                reflectiveQuadTo(55.5748f, 36.2918f)
                reflectiveQuadTo(50.9155f, 38.5717f)
                reflectiveQuadTo(46.0625f, 40.1083f)
                reflectiveQuadTo(41.5728f, 40.4448f)
                reflectiveQuadTo(37.3377f, 39.9355f)
                reflectiveQuadTo(33.1874f, 38.8421f)
                reflectiveQuadTo(29.5237f, 36.7875f)
                reflectiveQuadTo(26.2877f, 33.6452f)
                reflectiveQuadTo(23.1493f, 29.4273f)
                reflectiveQuadTo(20.0572f, 24.2758f)
                reflectiveQuadTo(17.8286f, 20.422f)
                reflectiveQuadTo(17.1013f, 19.3411f)
                arcTo(2.874f, 2.874f, 0f, 21.8502f, 16.1027f)
                close()
                moveTo(91.134f, 43.121f)
                reflectiveQuadTo(91.1757f, 44.1261f)
                reflectiveQuadTo(91.2055f, 47.1253f)
                reflectiveQuadTo(91.592f, 51.4766f)
                reflectiveQuadTo(92.5825f, 55.5673f)
                reflectiveQuadTo(94.083f, 59.3553f)
                reflectiveQuadTo(96.022f, 63.68f)
                reflectiveQuadTo(98.2847f, 68.4411f)
                reflectiveQuadTo(100.8477f, 73.3762f)
                reflectiveQuadTo(103.6092f, 78.4488f)
                reflectiveQuadTo(106.3489f, 83.5857f)
                reflectiveQuadTo(109.1352f, 89.2703f)
                reflectiveQuadTo(111.65f, 94.8957f)
                reflectiveQuadTo(113.3281f, 99.2823f)
                reflectiveQuadTo(114.3726f, 102.8466f)
                reflectiveQuadTo(114.8679f, 107.0452f)
                reflectiveQuadTo(112.6921f, 111.5123f)
                reflectiveQuadTo(107.511f, 114.3997f)
                reflectiveQuadTo(101.4163f, 115.8795f)
                reflectiveQuadTo(94.5799f, 116.6104f)
                reflectiveQuadTo(87.1911f, 116.9756f)
                reflectiveQuadTo(80.1951f, 117.159f)
                reflectiveQuadTo(74.0108f, 116.9806f)
                reflectiveQuadTo(69.1433f, 116.5696f)
                reflectiveQuadTo(65.7959f, 116.1985f)
                reflectiveQuadTo(63.0984f, 115.1113f)
                reflectiveQuadTo(61.0343f, 112.5385f)
                reflectiveQuadTo(60.2776f, 108.5228f)
                reflectiveQuadTo(61.281f, 103.8108f)
                reflectiveQuadTo(64.0609f, 98.867f)
                reflectiveQuadTo(68.1644f, 94.0285f)
                reflectiveQuadTo(73.4248f, 89.7586f)
                reflectiveQuadTo(79.7674f, 86.0955f)
                reflectiveQuadTo(87.3259f, 82.8294f)
                reflectiveQuadTo(94.6509f, 80.2419f)
                reflectiveQuadTo(99.8813f, 78.6103f)
                reflectiveQuadTo(103.7585f, 77.3484f)
                reflectiveQuadTo(107.7651f, 76.0563f)
                reflectiveQuadTo(109.9604f, 75.3855f)
                arcTo(1.8511f, 1.8511f, 0f, 111.0796f, 78.9145f)
                reflectiveQuadTo(108.899f, 79.6315f)
                reflectiveQuadTo(104.8617f, 80.8905f)
                reflectiveQuadTo(100.9517f, 82.0503f)
                reflectiveQuadTo(95.8493f, 83.6464f)
                reflectiveQuadTo(88.8072f, 86.1873f)
                reflectiveQuadTo(81.6655f, 89.331f)
                reflectiveQuadTo(75.8897f, 92.7314f)
                reflectiveQuadTo(71.2627f, 96.5924f)
                reflectiveQuadTo(67.7861f, 100.8914f)
                reflectiveQuadTo(65.6095f, 104.7813f)
                reflectiveQuadTo(64.919f, 108.7589f)
                reflectiveQuadTo(66.2746f, 111.8763f)
                reflectiveQuadTo(69.4963f, 112.9077f)
                reflectiveQuadTo(74.1089f, 113.3744f)
                reflectiveQuadTo(80.0788f, 113.4852f)
                reflectiveQuadTo(86.9757f, 113.2194f)
                reflectiveQuadTo(94.1192f, 112.7591f)
                reflectiveQuadTo(100.4256f, 111.9723f)
                reflectiveQuadTo(105.5564f, 110.6625f)
                reflectiveQuadTo(109.3066f, 107.5606f)
                reflectiveQuadTo(110.5407f, 103.8015f)
                reflectiveQuadTo(109.836f, 100.5577f)
                reflectiveQuadTo(108.2989f, 96.4111f)
                reflectiveQuadTo(105.7748f, 90.9691f)
                reflectiveQuadTo(102.945f, 85.4628f)
                reflectiveQuadTo(100.0886f, 80.4558f)
                reflectiveQuadTo(97.1063f, 75.4482f)
                reflectiveQuadTo(94.224f, 70.5628f)
                reflectiveQuadTo(91.5903f, 65.9725f)
                reflectiveQuadTo(89.0718f, 61.6739f)
                reflectiveQuadTo(86.8544f, 57.5566f)
                reflectiveQuadTo(85.4077f, 53.8516f)
                reflectiveQuadTo(84.7322f, 50.6095f)
                reflectiveQuadTo(84.5769f, 47.0768f)
                reflectiveQuadTo(84.6016f, 44.1114f)
                reflectiveQuadTo(84.626f, 43.179f)
                arcTo(3.2541f, 3.2541f, 0f, 91.134f, 43.121f)
                close()

            }

            drawPath(
                path = path,
                color = Color.Black,
            )
        }
    }
}

private var _SvgviewerOutput: ImageVector? = null

public val SvgviewerOutput: ImageVector
    get() {
        if (_SvgviewerOutput != null) {
            return _SvgviewerOutput!!
        }
        _SvgviewerOutput = ImageVector.Builder(
            name = "SvgviewerOutput",
            defaultWidth = 377.6125363150678.dp,
            defaultHeight = 433.02132500199855.dp,
            viewportWidth = 377.6125363150678f,
            viewportHeight = 433.02132500199855f
        ).apply {
            group(
                scaleX = 1f,
                scaleY = 1f,
                translationX = 0f,
                translationY = 0f,
                pivotX = 0f,
                pivotY = 0f,
            ) {
                group(
                    scaleX = 1f,
                    scaleY = 1f,
                    translationX = 100f,
                    translationY = 100f,
                    pivotX = 0f,
                    pivotY = 0f,
                ) {
                    path(
                        fill = SolidColor(Color(0xFF1D1D1D)),
                        fillAlpha = 1f,
                        stroke = null,
                        strokeAlpha = 1f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(-1.5525f, -2.3858f)
                        reflectiveQuadTo(1.4308f, -4.1547f)
                        reflectiveQuadTo(7.3025f, -7.985f)
                        reflectiveQuadTo(12.9203f, -12.1462f)
                        reflectiveQuadTo(18.0875f, -16.4644f)
                        reflectiveQuadTo(22.5084f, -20.935f)
                        reflectiveQuadTo(26.2636f, -25.4983f)
                        reflectiveQuadTo(29.4499f, -30.5623f)
                        reflectiveQuadTo(31.6364f, -35.4904f)
                        reflectiveQuadTo(32.7675f, -39.3848f)
                        reflectiveQuadTo(33.1334f, -43.6336f)
                        reflectiveQuadTo(31.5834f, -47.6357f)
                        reflectiveQuadTo(27.8223f, -49.9659f)
                        reflectiveQuadTo(23.7761f, -50.9999f)
                        reflectiveQuadTo(19.5747f, -50.0046f)
                        reflectiveQuadTo(15.6586f, -47.612f)
                        reflectiveQuadTo(12.359f, -44.9012f)
                        reflectiveQuadTo(9.0486f, -41.604f)
                        reflectiveQuadTo(6.4037f, -37.8607f)
                        reflectiveQuadTo(4.6024f, -33.5515f)
                        reflectiveQuadTo(3.1591f, -28.0932f)
                        reflectiveQuadTo(2.0514f, -21.6376f)
                        reflectiveQuadTo(1.4432f, -14.6661f)
                        reflectiveQuadTo(1.1f, -7.1746f)
                        reflectiveQuadTo(1.1795f, 0.0013f)
                        reflectiveQuadTo(1.906f, 6.439f)
                        reflectiveQuadTo(2.9489f, 12.0289f)
                        reflectiveQuadTo(3.9153f, 16.3495f)
                        reflectiveQuadTo(5.1038f, 20.0916f)
                        reflectiveQuadTo(7.1325f, 21.1817f)
                        reflectiveQuadTo(9.8215f, 19.5225f)
                        reflectiveQuadTo(12.4151f, 17.8778f)
                        reflectiveQuadTo(15.1307f, 16.2628f)
                        reflectiveQuadTo(17.9034f, 15.1343f)
                        reflectiveQuadTo(19.1525f, 14.8662f)
                        arcTo(2.874f, 2.874f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.799f, 20.5777f)
                        reflectiveQuadTo(18.8159f, 20.6609f)
                        reflectiveQuadTo(15.8556f, 21.7668f)
                        reflectiveQuadTo(12.3132f, 23.7945f)
                        reflectiveQuadTo(8.487f, 25.7932f)
                        reflectiveQuadTo(4.758f, 26.3793f)
                        reflectiveQuadTo(2.4588f, 24.6184f)
                        reflectiveQuadTo(1.1885f, 21.1273f)
                        reflectiveQuadTo(0.4052f, 17.0929f)
                        reflectiveQuadTo(-0.4805f, 12.681f)
                        reflectiveQuadTo(-1.5765f, 6.8374f)
                        reflectiveQuadTo(-2.3508f, 0.0614f)
                        reflectiveQuadTo(-2.4651f, -7.329f)
                        reflectiveQuadTo(-2.1586f, -14.9707f)
                        reflectiveQuadTo(-1.5728f, -22.2465f)
                        reflectiveQuadTo(-0.4855f, -29.0467f)
                        reflectiveQuadTo(1.0249f, -35.0304f)
                        reflectiveQuadTo(3.052f, -40.111f)
                        reflectiveQuadTo(5.9442f, -44.5418f)
                        reflectiveQuadTo(9.3086f, -48.3325f)
                        reflectiveQuadTo(12.8218f, -51.6853f)
                        reflectiveQuadTo(16.4656f, -54.3979f)
                        reflectiveQuadTo(21.4472f, -55.8062f)
                        reflectiveQuadTo(27.1918f, -55.3567f)
                        reflectiveQuadTo(31.2763f, -53.7f)
                        reflectiveQuadTo(34.6367f, -51.3097f)
                        reflectiveQuadTo(37.0358f, -48.0829f)
                        reflectiveQuadTo(37.5568f, -44.6955f)
                        reflectiveQuadTo(37.2027f, -41.5873f)
                        reflectiveQuadTo(36.3496f, -38.3288f)
                        reflectiveQuadTo(34.9972f, -34.0534f)
                        reflectiveQuadTo(32.6182f, -28.5906f)
                        reflectiveQuadTo(29.2731f, -23.0869f)
                        reflectiveQuadTo(25.406f, -18.1656f)
                        reflectiveQuadTo(20.8616f, -13.2821f)
                        reflectiveQuadTo(15.7009f, -8.5947f)
                        reflectiveQuadTo(10.1124f, -3.9804f)
                        reflectiveQuadTo(4.3786f, 0.3745f)
                        reflectiveQuadTo(1.5525f, 2.3858f)
                        arcTo(2.8464f, 2.8464f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.5525f, -2.3858f)
                        close()
                        moveTo(21.8502f, 16.1027f)
                        reflectiveQuadTo(22.5932f, 17.1779f)
                        reflectiveQuadTo(24.0704f, 19.7311f)
                        reflectiveQuadTo(25.6012f, 22.8333f)
                        reflectiveQuadTo(27.6897f, 26.8471f)
                        reflectiveQuadTo(30.1925f, 30.9446f)
                        reflectiveQuadTo(32.6427f, 33.5161f)
                        reflectiveQuadTo(35.278f, 34.8069f)
                        reflectiveQuadTo(38.4532f, 35.5334f)
                        reflectiveQuadTo(42.1019f, 36.0141f)
                        reflectiveQuadTo(45.7998f, 35.8267f)
                        reflectiveQuadTo(49.7192f, 34.6387f)
                        reflectiveQuadTo(53.8905f, 32.7323f)
                        reflectiveQuadTo(57.4841f, 30.5985f)
                        reflectiveQuadTo(60.7127f, 27.9165f)
                        reflectiveQuadTo(63.2545f, 24.9015f)
                        reflectiveQuadTo(64.8413f, 20.82f)
                        reflectiveQuadTo(65.6813f, 16.166f)
                        reflectiveQuadTo(63.4586f, 16.3226f)
                        reflectiveQuadTo(59.7514f, 20.9075f)
                        reflectiveQuadTo(56.9962f, 26.2658f)
                        reflectiveQuadTo(54.6323f, 31.9051f)
                        reflectiveQuadTo(52.7808f, 37.2326f)
                        reflectiveQuadTo(51.3545f, 42.2138f)
                        reflectiveQuadTo(50.4738f, 46.6173f)
                        reflectiveQuadTo(50.0759f, 50.1996f)
                        reflectiveQuadTo(51.3364f, 53.8296f)
                        reflectiveQuadTo(54.5648f, 56.4373f)
                        reflectiveQuadTo(58.0056f, 57.1748f)
                        reflectiveQuadTo(61.1955f, 57.4423f)
                        reflectiveQuadTo(65.7464f, 56.6504f)
                        reflectiveQuadTo(70.2734f, 55.2325f)
                        reflectiveQuadTo(73.9331f, 54.0686f)
                        reflectiveQuadTo(77.5519f, 53.3225f)
                        reflectiveQuadTo(80.6829f, 53.625f)
                        reflectiveQuadTo(82.9203f, 51.9693f)
                        reflectiveQuadTo(84.1591f, 47.9169f)
                        reflectiveQuadTo(84.6927f, 44.6952f)
                        reflectiveQuadTo(84.6403f, 43.4566f)
                        arcTo(3.2541f, 3.2541f, 0f, isMoreThanHalf = false, isPositiveArc = true, 91.1197f, 42.8434f)
                        reflectiveQuadTo(91.1559f, 44.1086f)
                        reflectiveQuadTo(90.7397f, 47.5164f)
                        reflectiveQuadTo(89.467f, 52.0048f)
                        reflectiveQuadTo(87.8837f, 55.772f)
                        reflectiveQuadTo(85.631f, 58.4144f)
                        reflectiveQuadTo(82.2356f, 59.7351f)
                        reflectiveQuadTo(78.7678f, 59.2102f)
                        reflectiveQuadTo(75.2749f, 58.9732f)
                        reflectiveQuadTo(71.8744f, 59.8057f)
                        reflectiveQuadTo(68.3901f, 61.0396f)
                        reflectiveQuadTo(64.452f, 62.1379f)
                        reflectiveQuadTo(60.7846f, 62.3287f)
                        reflectiveQuadTo(57.0759f, 61.9851f)
                        reflectiveQuadTo(53.5252f, 61.2228f)
                        reflectiveQuadTo(50.6099f, 59.7246f)
                        reflectiveQuadTo(48.1256f, 57.1707f)
                        reflectiveQuadTo(46.4322f, 53.5501f)
                        reflectiveQuadTo(46.125f, 49.7798f)
                        reflectiveQuadTo(46.6834f, 45.9012f)
                        reflectiveQuadTo(47.5607f, 41.2004f)
                        reflectiveQuadTo(48.8714f, 35.9669f)
                        reflectiveQuadTo(50.5757f, 30.3299f)
                        reflectiveQuadTo(52.7619f, 24.2632f)
                        reflectiveQuadTo(55.244f, 18.4638f)
                        reflectiveQuadTo(58.1797f, 12.7452f)
                        reflectiveQuadTo(61.1267f, 8.9367f)
                        reflectiveQuadTo(64.7901f, 8.0096f)
                        reflectiveQuadTo(68.5063f, 9.3185f)
                        reflectiveQuadTo(70.2577f, 12.5304f)
                        reflectiveQuadTo(70.3259f, 16.6727f)
                        reflectiveQuadTo(69.4784f, 20.9287f)
                        reflectiveQuadTo(68.2266f, 24.2708f)
                        reflectiveQuadTo(66.2768f, 27.3752f)
                        reflectiveQuadTo(63.1616f, 30.9375f)
                        reflectiveQuadTo(59.5119f, 33.9504f)
                        reflectiveQuadTo(55.5748f, 36.2918f)
                        reflectiveQuadTo(50.9155f, 38.5717f)
                        reflectiveQuadTo(46.0625f, 40.1083f)
                        reflectiveQuadTo(41.5728f, 40.4448f)
                        reflectiveQuadTo(37.3377f, 39.9355f)
                        reflectiveQuadTo(33.1874f, 38.8421f)
                        reflectiveQuadTo(29.5237f, 36.7875f)
                        reflectiveQuadTo(26.2877f, 33.6452f)
                        reflectiveQuadTo(23.1493f, 29.4273f)
                        reflectiveQuadTo(20.0572f, 24.2758f)
                        reflectiveQuadTo(17.8286f, 20.422f)
                        reflectiveQuadTo(17.1013f, 19.3411f)
                        arcTo(2.874f, 2.874f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21.8502f, 16.1027f)
                        close()
                        moveTo(91.134f, 43.121f)
                        reflectiveQuadTo(91.1757f, 44.1261f)
                        reflectiveQuadTo(91.2055f, 47.1253f)
                        reflectiveQuadTo(91.592f, 51.4766f)
                        reflectiveQuadTo(92.5825f, 55.5673f)
                        reflectiveQuadTo(94.083f, 59.3553f)
                        reflectiveQuadTo(96.022f, 63.68f)
                        reflectiveQuadTo(98.2847f, 68.4411f)
                        reflectiveQuadTo(100.8477f, 73.3762f)
                        reflectiveQuadTo(103.6092f, 78.4488f)
                        reflectiveQuadTo(106.3489f, 83.5857f)
                        reflectiveQuadTo(109.1352f, 89.2703f)
                        reflectiveQuadTo(111.65f, 94.8957f)
                        reflectiveQuadTo(113.3281f, 99.2823f)
                        reflectiveQuadTo(114.3726f, 102.8466f)
                        reflectiveQuadTo(114.8679f, 107.0452f)
                        reflectiveQuadTo(112.6921f, 111.5123f)
                        reflectiveQuadTo(107.511f, 114.3997f)
                        reflectiveQuadTo(101.4163f, 115.8795f)
                        reflectiveQuadTo(94.5799f, 116.6104f)
                        reflectiveQuadTo(87.1911f, 116.9756f)
                        reflectiveQuadTo(80.1951f, 117.159f)
                        reflectiveQuadTo(74.0108f, 116.9806f)
                        reflectiveQuadTo(69.1433f, 116.5696f)
                        reflectiveQuadTo(65.7959f, 116.1985f)
                        reflectiveQuadTo(63.0984f, 115.1113f)
                        reflectiveQuadTo(61.0343f, 112.5385f)
                        reflectiveQuadTo(60.2776f, 108.5228f)
                        reflectiveQuadTo(61.281f, 103.8108f)
                        reflectiveQuadTo(64.0609f, 98.867f)
                        reflectiveQuadTo(68.1644f, 94.0285f)
                        reflectiveQuadTo(73.4248f, 89.7586f)
                        reflectiveQuadTo(79.7674f, 86.0955f)
                        reflectiveQuadTo(87.3259f, 82.8294f)
                        reflectiveQuadTo(94.6509f, 80.2419f)
                        reflectiveQuadTo(99.8813f, 78.6103f)
                        reflectiveQuadTo(103.7585f, 77.3484f)
                        reflectiveQuadTo(107.7651f, 76.0563f)
                        reflectiveQuadTo(109.9604f, 75.3855f)
                        arcTo(1.8511f, 1.8511f, 0f, isMoreThanHalf = false, isPositiveArc = true, 111.0796f, 78.9145f)
                        reflectiveQuadTo(108.899f, 79.6315f)
                        reflectiveQuadTo(104.8617f, 80.8905f)
                        reflectiveQuadTo(100.9517f, 82.0503f)
                        reflectiveQuadTo(95.8493f, 83.6464f)
                        reflectiveQuadTo(88.8072f, 86.1873f)
                        reflectiveQuadTo(81.6655f, 89.331f)
                        reflectiveQuadTo(75.8897f, 92.7314f)
                        reflectiveQuadTo(71.2627f, 96.5924f)
                        reflectiveQuadTo(67.7861f, 100.8914f)
                        reflectiveQuadTo(65.6095f, 104.7813f)
                        reflectiveQuadTo(64.919f, 108.7589f)
                        reflectiveQuadTo(66.2746f, 111.8763f)
                        reflectiveQuadTo(69.4963f, 112.9077f)
                        reflectiveQuadTo(74.1089f, 113.3744f)
                        reflectiveQuadTo(80.0788f, 113.4852f)
                        reflectiveQuadTo(86.9757f, 113.2194f)
                        reflectiveQuadTo(94.1192f, 112.7591f)
                        reflectiveQuadTo(100.4256f, 111.9723f)
                        reflectiveQuadTo(105.5564f, 110.6625f)
                        reflectiveQuadTo(109.3066f, 107.5606f)
                        reflectiveQuadTo(110.5407f, 103.8015f)
                        reflectiveQuadTo(109.836f, 100.5577f)
                        reflectiveQuadTo(108.2989f, 96.4111f)
                        reflectiveQuadTo(105.7748f, 90.9691f)
                        reflectiveQuadTo(102.945f, 85.4628f)
                        reflectiveQuadTo(100.0886f, 80.4558f)
                        reflectiveQuadTo(97.1063f, 75.4482f)
                        reflectiveQuadTo(94.224f, 70.5628f)
                        reflectiveQuadTo(91.5903f, 65.9725f)
                        reflectiveQuadTo(89.0718f, 61.6739f)
                        reflectiveQuadTo(86.8544f, 57.5566f)
                        reflectiveQuadTo(85.4077f, 53.8516f)
                        reflectiveQuadTo(84.7322f, 50.6095f)
                        reflectiveQuadTo(84.5769f, 47.0768f)
                        reflectiveQuadTo(84.6016f, 44.1114f)
                        reflectiveQuadTo(84.626f, 43.179f)
                        arcTo(3.2541f, 3.2541f, 0f, isMoreThanHalf = false, isPositiveArc = true, 91.134f, 43.121f)
                        close()
                    }
                }
            }
        }.build()
        return _SvgviewerOutput!!
    }

