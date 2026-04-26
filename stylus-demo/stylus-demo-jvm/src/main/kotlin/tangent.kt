import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.onDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import stylus.compose.demo.StylusBrush
import stylus.compose.demo.StylusPoint
import kotlin.math.pow
import kotlin.math.sqrt

fun main() {
    System.setProperty("skiko.vsync.enabled", "false")

    application {
        val state = rememberWindowState()

        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Geometry Playground",
        ) {
            TangentApp()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TangentApp() {
    val circleList = remember {
        listOf(
            CircleState(center = Offset(300f, 500f), radius = 120f),
            CircleState(center = Offset(1000f, 400f), radius = 200f),
            CircleState(center = Offset(1500f, 500f), radius = 100f),
            CircleState(center = Offset(1500f, 500f), radius = 120f),
            CircleState(center = Offset(1500f, 500f), radius = 110f),
            CircleState(center = Offset(1500f, 500f), radius = 100f),
        )
    }

    var path = remember { Path() }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            drawPoints2(
                points = circleList.map {
                    StylusPoint(
                        x = it.center.x,
                        y = it.center.y,
                        pressure = (it.radius / circleList.maxOf { it.radius }).coerceIn(0f, 1f)
                    )
                },
                brush = StylusBrush(
                    color = Color.Black,
                    width = circleList.maxOf { it.radius } * 2f
                )
            )
        }

        val density = LocalDensity.current

        circleList.forEach { circle ->
            Box(
                modifier = Modifier
                    .size(with(density) { (circle.radius * 2).toDp() })
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        translationX = circle.center.x - size.width / 2
                        translationY = circle.center.y - size.height / 2
                    }
                    .clip(CircleShape)
                    .onDrag {
                        circle.center += Offset(it.x, it.y)
                    }
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
        ) {
            circleList.forEach { circle ->
                TextField(
                    value = circle.radius.toString(),
                    onValueChange = {
                        circle.radius = it.toFloatOrNull() ?: circle.radius
                    }
                )
            }
        }
    }
}

fun calcIntersectionBetweenTwoCircles(
    r: Float,
    c1: Offset,
    r1: Float,
    c2: Offset,
    r2: Float
): Offset {
    if (r == 0f)
        return Offset.Zero

    val x = (c1.x + c2.x) / 2 + ((r1.pow(2) - r2.pow(2)) / (2 * r.pow(2))) * (c2.x - c1.x)
    val y = (c1.y + c2.y) / 2 + ((r1.pow(2) - r2.pow(2)) / (2 * r.pow(2))) * (c2.y - c1.y)

    val offset = Offset(x, y)

    return offset
//    return if (offset.isSpecified) offset else Offset.Zero
}

fun calcIntersectionPlusMinusBetweenTwoCircles(
    r: Float,
    c1: Offset,
    r1: Float,
    c2: Offset,
    r2: Float
): Offset {
    if (r == 0f)
        return Offset.Zero

    val x =
        (sqrt((2 * (r1.pow(2) + r2.pow(2))) / r.pow(2) - (r1.pow(2) - r2.pow(2)).pow(2) / r.pow(4) - 1) / 2) * (c2.y - c1.y)
    val y =
        (sqrt((2 * (r1.pow(2) + r2.pow(2))) / r.pow(2) - (r1.pow(2) - r2.pow(2)).pow(2) / r.pow(4) - 1) / 2) * (c1.x - c2.x)

    if (x.isNaN() || y.isNaN())
        return Offset.Zero

    val offset = Offset(x, y)

    return offset
//    return if (offset.isSpecified) offset else Offset.Zero
}

@Immutable
class CircleState(
    center: Offset,
    radius: Float
) {
    var center by mutableStateOf(center)

    var radius by mutableStateOf(radius)
}