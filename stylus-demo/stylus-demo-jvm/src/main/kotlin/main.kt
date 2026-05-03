import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Slider
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.awt.ComposeWindow
import com.mohamedrejeb.stylus.PenButton
import com.mohamedrejeb.stylus.compose.PenBrush
import com.mohamedrejeb.stylus.compose.PenInkEngine
import com.mohamedrejeb.stylus.compose.PenInkSurface
import com.mohamedrejeb.stylus.compose.ProvidePenInputWindow
import com.mohamedrejeb.stylus.compose.penInput
import com.mohamedrejeb.stylus.compose.rememberPenInkState
import stylus.calcDistance
import stylus.compose.demo.StylusBrush
import stylus.compose.demo.StylusPoint
import stylus.smoothStroke
import java.awt.geom.Ellipse2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

var initialDensity = Density(1f, 1f)

fun main() {
    System.setProperty("skiko.vsync.enabled", "false")

    application {
        val state = rememberWindowState()

        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Stylus demo (JVM)",
        ) {
            val composeWindow = window
            ProvidePenInputWindow {
                DemoSwitcher(
                    windowState = state,
                    composeWindow = composeWindow,
                )
            }
        }
    }
}

@Composable
private fun DemoSwitcher(
    windowState: WindowState,
    composeWindow: ComposeWindow,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Custom (legacy)") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("PenInkSurface") },
            )
        }
        Box(Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> DrawApp(windowState, composeWindow)
                else -> PenInkSurfaceDemo()
            }
        }
    }
}

@Composable
private fun PenInkSurfaceDemo() {
    val state = rememberPenInkState()
    var strokeWidth by remember { mutableStateOf(5f) }
    var engine by remember { mutableStateOf(PenInkEngine.Tessellated) }
    // Re-derive brush only when the slider value changes — without `remember`
    // each recomposition (e.g. while dragging the slider) would allocate a
    // fresh PenBrush, but the renderer is fine with that since width changes
    // are scoped to *new* strokes anyway.
    val brush = remember(strokeWidth) { PenBrush.pen(color = Color.Black, size = strokeWidth) }
    PenInkSurface(
        modifier = Modifier.fillMaxSize(),
        state = state,
        brush = brush,
        engine = engine,
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Row {
                Button(onClick = { state.undo() }) { Text("Undo") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { state.clear() }) { Text("Clear") }
            }
            Spacer(Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Width: ${strokeWidth.roundToInt()}")
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = strokeWidth,
                    onValueChange = { strokeWidth = it },
                    valueRange = 1f..40f,
                    modifier = Modifier.width(220.dp),
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Engine:")
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { engine = PenInkEngine.Tessellated },
                    enabled = engine != PenInkEngine.Tessellated,
                ) { Text("Tessellated") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { engine = PenInkEngine.SmoothPath },
                    enabled = engine != PenInkEngine.SmoothPath,
                ) { Text("SmoothPath") }
            }
        }
    }
}

@Composable
fun DrawApp(
    windowState: WindowState,
    composeWindow: ComposeWindow,
) {
    val pathList = remember { mutableListOf<Path>() }
    val pathPointList = remember { mutableListOf<List<StylusPoint>>() }

    val density = LocalDensity.current
    var windowInsets by remember { mutableStateOf(Offset.Zero) }
    val circlePath = remember { Path() }
    val tangentPath = remember { Path() }
    val points = remember { mutableListOf<StylusPoint>() }

    var previousPoint = remember { StylusPoint() }

    var update by remember { mutableIntStateOf(0) }

    var brush by remember {
        mutableStateOf(StylusBrush(width = 10f))
    }

    LaunchedEffect(
        windowState.position,
        windowInsets,
        windowState.placement,
        initialDensity,
    ) {
        // The Compose modifier auto-finds the host window now; the manager's
        // window-top-left bookkeeping is owned by the modifier's onPlaced callback.
    }

    var i = remember { 0 }

    val scope = rememberCoroutineScope()

    var zoom by remember {
        mutableStateOf(1f)
    }

    val textMeasurer = rememberTextMeasurer()

    val clipboardManager = LocalClipboardManager.current

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val jMenuBar = composeWindow.jMenuBar
                val jMenuBarSize =
                    if (jMenuBar != null && jMenuBar.isVisible && jMenuBar.size != null) {
                        jMenuBar.size.height + jMenuBar.insets.bottom
                    } else {
                        0
                    }

                windowInsets =
                    Offset(
                        x = composeWindow.insets.left * initialDensity.density,
                        y = (composeWindow.insets.top + jMenuBarSize) * initialDensity.density,
                    )
            }
            .penInput(
                key = "test",
                onHover = {
                    println("onHover: $it")
                },
                onMove = {
                    println("onMove: $it")
                    if (it.button == PenButton.Primary) {
                        val p1 =
                            StylusPoint(
                                x = it.x.toFloat(),
                                y = it.y.toFloat(),
                                pressure = (it.pressure.toFloat() * 3f + 1f) / 4f,
                            )
                        points.add(p1)
                        previousPoint = p1
                        update++
                    }
                },
                onPress = {
                    println("onPress: $it")
                    if (it.button == PenButton.Primary) {
                        previousPoint = StylusPoint(
                            x = it.x.toFloat(),
                            y = it.y.toFloat(),
                            pressure = (it.pressure.toFloat() * 3f + 1f) / 4f,
                        )
                        points.add(previousPoint)
                        circlePath.drawCircle(
                            brush = brush,
                            x = previousPoint.x,
                            y = previousPoint.y,
                            p = previousPoint.pressure
                        )
                        update++
                    }
                },
                onRelease = {
                    println("onRelease: $it")
                    if (it.button == PenButton.Primary) {
                        val pressure = (it.pressure.toFloat() * 3f + 1f) / 4f

                        if (pressure == 0f) {
                            val p1 =
                                StylusPoint(
                                    x = it.x.toFloat(),
                                    y = it.y.toFloat(),
                                    pressure = pressure,
                                )

                            points.add(p1)
                        }
                        pointsToJson(
                            points = points.toList(),
                            clipboardManager = clipboardManager,
                        )
                        pathPointList.add(points.toList())

                        points.clear()
                        update++
                    }
                },
            )
    ) {
        @Suppress("UNUSED_EXPRESSION")
        update

//        pathList.forEach {
//            drawPath(
//                path = it,
//                color = Color.Black,
//                style = Stroke(
//                    width = 5f,
//                    cap = StrokeCap.Round,
//                    join = StrokeJoin.Round,
//                )
//            )
//        }

        var lastPoint: StylusPoint? = null
//        for (i in points.indices) {
//            val point = points[i]
////        }
        scale(
            scale = zoom,
            pivot = center
        ) {
            pathPointList.fastForEach { stroke ->
                drawPoints2(
                    points = smoothStroke(stroke),
                    brush = brush,
                )
            }

            drawPoints2(
                points = smoothStroke(points.toList()),
                brush = brush,
            )
        }


//        pathList.fastForEach {
//            drawPath(
//                path = it,
//                color = Color.Black,
//            )
//        }

//        drawPath(
//            path = tangentPath,
//            color = Color.Black,
//        )

//        drawPath(
//            path = circlePath,
//            color = Color.Black,
//        )
    }

    val pickerLauncher = rememberFilePickerLauncher(
        type = FilePickerFileType.Image,
    ) {
        println("onImagePicked: $it")
    }

    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row {
            IconButton(
                onClick = {
                    pickerLauncher.launch()
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            IconButton(
                onClick = {
                    pathPointList.clear()
                    pathList.clear()
                    points.clear()
                    update++
                }
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear")
            }
        }
    }
}

private fun Path.drawLine(
    brush: StylusBrush,
    p0: StylusPoint,
    p1: StylusPoint
): Path {
//    val isDrawn = drawOuterTangents(
//        circle1Center = Offset(p0.x, p0.y),
//        radius1 = brush.width * p0.pressure / 2f,
//        circle2Center = Offset(p1.x, p1.y),
//        radius2 = brush.width * p1.pressure / 2f,
//    )
    drawOuterTangent2(
        c1 = Offset(p0.x, p0.y),
        r1 = brush.width * p0.pressure / 2f,
        c2 = Offset(p1.x, p1.y),
        r2 = brush.width * p1.pressure / 2f,
    )

    drawCircle(
        brush = brush,
        x = p1.x,
        y = p1.y,
        p = p1.pressure
    )

//    if (!isDrawn) {
//        drawCircle(
//            brush = brush,
//            x = p0.x,
//            y = p0.y,
//            p = p0.pressure
//        )
//

//    }
    return this

    val dist: Float = p0.distance(p1)
    val pDist: Float = (p1.pressure - p0.pressure) / dist
    val u: StylusPoint = p1.clone().subtract(p0).normalize()

    var x: Float
    var y: Float
    var p: Float

    var i = 0
    while (i < dist) {
        x = p0.x + u.x * i
        y = p0.y + u.y * i
        p = p0.pressure + pDist * i

        drawCircle(brush, x, y, p)
        i++
    }
}

private fun DrawScope.drawLine(
    brush: StylusBrush,
    p0: StylusPoint,
    p1: StylusPoint
) {
    val dist: Float = p0.distance(p1)
    val pDist: Float = (p1.pressure - p0.pressure) / dist
    val u: StylusPoint = p1.clone().subtract(p0).normalize()

    var x: Float
    var y: Float
    var p: Float

    var i = 0
    while (i < dist) {
        x = p0.x + u.x * i
        y = p0.y + u.y * i
        p = p0.pressure + pDist * i

        drawCircle(brush, x, y, p)
        i++
    }
}

internal fun Path.drawCircle(
    brush: StylusBrush,
    x: Float,
    y: Float,
    p: Float
): Path {
    val w: Float = p * brush.width
    val d = w / 2

    fastOval(
        topLeft = Offset(
            x = x - d,
            y = y - d,
        ),
        size = Size(
            width = w,
            height = w,
        )
    )
    return this
//    addOval(
//        oval = Rect(
//            offset = Offset(
//                x = x - d,
//                y = y - d,
//            ),
//            size = Size(
//                width = w,
//                height = w,
//            )
//        )
//    )
}

private fun DrawScope.drawPoints(
    points: List<StylusPoint>,
    brush: StylusBrush,
    zoom: Float = 1f,
) {
    var lastPoint: StylusPoint? = null

    points.fastForEachIndexed { i, point ->
        drawCircle(
            brush = brush,
            x = point.x,
            y = point.y,
            p = point.pressure
        )

        if (lastPoint != null) {
            val previousPointCenter = Offset(lastPoint!!.x, lastPoint!!.y)
            val previousPointRadius = brush.width * lastPoint!!.pressure / 2f

            val currentPointCenter = Offset(point.x, point.y)
            val currentPointRadius = brush.width * point.pressure / 2f

//            drawCircle(
//                color = Color.Black,
//                center = currentPointCenter,
//                radius = currentPointRadius,
//                style = Stroke(3f / zoom)
//            )

            val c1: Offset
            val r1: Float

            val c2: Offset
            val r2: Float

            if (previousPointRadius < currentPointRadius) {
                c1 = previousPointCenter
                r1 = previousPointRadius

                c2 = currentPointCenter
                r2 = currentPointRadius
            } else {
                c1 = currentPointCenter
                r1 = currentPointRadius

                c2 = previousPointCenter
                r2 = previousPointRadius
            }

            drawPath(
                path = Path().drawOuterTangent2(
                    c1 = c1,
                    r1 = r1,
                    c2 = c2,
                    r2 = r2,
//                    drawScope = this,
                    zoom = zoom,
                ),
//                color = Color.Red,
//                style = Stroke(3f / zoom),
                color = Color.Black
            )

//            drawText(
//                textMeasurer = textMeasurer,
//                text = currentPointRadius.toString().take(6),
//                topLeft = currentPointCenter + Offset(10f / zoom, 10f / zoom),
//                style = TextStyle(
//                    color = Color.Blue,
//                    fontSize = 12.sp / zoom,
//                ),
//            )
        }

        lastPoint = point
    }
}

private fun DrawScope.drawCircle(
    brush: StylusBrush,
    x: Float,
    y: Float,
    p: Float
) {
    val w: Float = p * brush.width

    drawCircle(
        color = Color.Black,
        radius = w / 2,
        center = Offset(x, y),
    )
}

fun Path.fastOval(
    topLeft: Offset,
    size: Size,
) {
    val oval = Ellipse2D.Float(
        topLeft.x,
        topLeft.y,
        size.width,
        size.height
    )
    moveTo(oval.x, oval.y + oval.height / 2)
    cubicTo(
        oval.x,
        oval.y,
        oval.x + oval.width,
        oval.y,
        oval.x + oval.width,
        oval.y + oval.height / 2
    )
    cubicTo(
        oval.x + oval.width,
        oval.y + oval.height,
        oval.x,
        oval.y + oval.height,
        oval.x,
        oval.y + oval.height / 2
    )
}

fun Path.roundRect(
    topLeft: Offset,
    topRight: Offset,
    bottomRight: Offset,
    bottomLeft: Offset,
    dx: Float,
    dy: Float,
) {
    moveTo(topLeft.x + dx, topLeft.y)
    lineTo(topRight.x - dx, topRight.y)
    arcTo(
        rect =
        Rect(
            offset =
            Offset(
                x = topRight.x - dx * 2f,
                y = topRight.y,
            ),
            size =
            Size(
                width = dx * 2f,
                height = dy * 2f,
            ),
        ),
        startAngleDegrees = 270f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(bottomRight.x, bottomRight.y - dy)
    arcTo(
        rect =
        Rect(
            offset =
            Offset(
                x = bottomRight.x - dx * 2f,
                y = bottomRight.y - dy * 2f,
            ),
            size =
            Size(
                width = dx * 2f,
                height = dy * 2f,
            ),
        ),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(bottomLeft.x + dx, bottomLeft.y)
    arcTo(
        rect =
        Rect(
            offset =
            Offset(
                x = bottomLeft.x,
                y = bottomLeft.y - dy * 2f,
            ),
            size =
            Size(
                width = dx * 2f,
                height = dy * 2f,
            ),
        ),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    lineTo(topLeft.x, topLeft.y + dy)
    arcTo(
        rect =
        Rect(
            offset =
            Offset(
                x = topLeft.x,
                y = topLeft.y,
            ),
            size =
            Size(
                width = dx * 2f,
                height = dy * 2f,
            ),
        ),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false,
    )
    close()
}

fun Path.drawOuterTangents(
    circle1Center: Offset,
    radius1: Float,
    circle2Center: Offset,
    radius2: Float,
): Boolean {
    val dx = circle2Center.x - circle1Center.x
    val dy = circle2Center.y - circle1Center.y
    val dist = sqrt(dx * dx + dy * dy)

    if (dist <= abs(radius1 - radius2)) return false

    val a1 = atan2(dy, dx)
    val a2 = acos((radius1 - radius2) / dist)

    // Line 1
    moveTo(
        circle2Center.x + radius2 * cos(a1 + a2),
        circle2Center.y + radius2 * sin(a1 + a2),
    )
    lineTo(
        circle1Center.x + radius1 * cos(a1 + a2),
        circle1Center.y + radius1 * sin(a1 + a2),
    )

    // Arcs
    val from = (a2 + a1).toDegree()
    val to = (a1 - a2).toDegree()

    // Arc 1
    arcTo(
        rect =
        Rect(
            offset = circle1Center - Offset(radius1, radius1),
            size = Size(radius1 * 2, radius1 * 2),
        ),
        startAngleDegrees = from,
        sweepAngleDegrees = to - from + 360f,
        forceMoveTo = false,
    )

    // Line 2
    lineTo(
        circle2Center.x + radius2 * cos(a1 - a2),
        circle2Center.y + radius2 * sin(a1 - a2),
    )

    // Arc 2
    arcTo(
        rect =
        Rect(
            offset = circle2Center - Offset(radius2, radius2),
            size = Size(radius2 * 2, radius2 * 2),
        ),
        startAngleDegrees = to,
        sweepAngleDegrees = from - to + 360f,
        forceMoveTo = false,
    )

    return true
}

fun Path.drawOuterTangent2(
    c1: Offset,
    r1: Float,
    c2: Offset,
    r2: Float,
    drawScope: DrawScope? = null,
    zoom: Float = 1f
): Path {
    val centerMidPoint = Offset(
        (c2.x + c1.x) / 2,
        (c2.y + c1.y) / 2
    )

    val centersDistance = sqrt(
        (c2.x - c1.x).pow(2) + (c2.y - c1.y).pow(2)
    )

    if (centersDistance + min(r1, r2) <= max(r1, r2))
        return this.also {
            println("centersDistance + min(r1, r2) <= max(r1, r2)")
        }

    val midCircleRadius = centersDistance / 2f
    val radiusDiff = r2 - r1
    val innerCircleRadius =
        radiusDiff.coerceAtLeast(r2 / 100f)


    val intersection = calcIntersectionBetweenTwoCircles(
        r = midCircleRadius,
        c1 = c2,
        r1 = innerCircleRadius,
        c2 = centerMidPoint,
        r2 = midCircleRadius
    )

    if (intersection.isUnspecified)
        return this.also {
            println("intersection.isUnspecified")
        }

    val plusMinus = calcIntersectionPlusMinusBetweenTwoCircles(
        r = midCircleRadius,
        c1 = c2,
        r1 = innerCircleRadius,
        c2 = centerMidPoint,
        r2 = midCircleRadius
    )

    if (plusMinus.isUnspecified)
        return this.also {
            println("plusMinus.isUnspecified")
        }

    val p = intersection + plusMinus
    val q = intersection - plusMinus

    val radiusProduct = r2 / innerCircleRadius
    val t1 = c2 + (p - c2) * radiusProduct
    val t3 = c2 + (q - c2) * radiusProduct

    val secondCircleRadiusProduct = r1 / innerCircleRadius
    val t2 = c1 + (p - c2) * secondCircleRadiusProduct
    val t4 = c1 + (q - c2) * secondCircleRadiusProduct

    if (calcDistance(t1, c2).roundToInt() != r2.roundToInt())
        println("Should be equal t1: ${calcDistance(t1, c2)} != $r2; infos: c1: $c1, c2: $c2, r1: $r1, r2: $r2, p: $p, q: $q, innerCircleRadius: $innerCircleRadius, radiusProduct: $radiusProduct")

    if (calcDistance(t2, c1).roundToInt() != r1.roundToInt())
        println("Should be equal t2: ${calcDistance(t2, c1)} != $r1; infos: c1: $c1, c2: $c2, r1: $r1, r2: $r2, p: $p, q: $q, innerCircleRadius: $innerCircleRadius, radiusProduct: $radiusProduct")

    if (calcDistance(t3, c2).roundToInt() != r2.roundToInt())
        println("Should be equal t3: ${calcDistance(t3, c2)} != $r2; infos: c1: $c1, c2: $c2, r1: $r1, r2: $r2, p: $p, q: $q, innerCircleRadius: $innerCircleRadius, radiusProduct: $radiusProduct")

    if (calcDistance(t4, c1).roundToInt() != r1.roundToInt())
        println("Should be equal t4: ${calcDistance(t4, c1)} != $r1; infos: c1: $c1, c2: $c2, r1: $r1, r2: $r2, p: $p, q: $q, innerCircleRadius: $innerCircleRadius, radiusProduct: $radiusProduct")

    if (drawScope != null)
        drawScope.apply {
            drawCircle(
                color = Color.Black,
                center = centerMidPoint,
                radius = centersDistance / 2f,
                style = Stroke(1f / zoom)
            )

            drawCircle(
                color = Color.Black,
                center = c1,
                radius = innerCircleRadius,
                style = Stroke(1f / zoom)
            )

            drawLine(
                start = c2,
                end = c1,
                color = Color.Black,
                strokeWidth = 1f / zoom,
            )

            drawLine(
                start = t1,
                end = t2,
                color = Color.Black,
                strokeWidth = 1f / zoom,
            )

            drawLine(
                start = t3,
                end = t4,
                color = Color.Black,
                strokeWidth = 1f / zoom,
            )

            drawPoints(
                points = listOf(c2, c1, centerMidPoint, p, q, t1, t2, t3, t4),
                pointMode = PointMode.Points,
                color = Color.Red,
                strokeWidth = 5f / zoom,
                cap = StrokeCap.Round,
            )
        }

    moveTo(t1.x, t1.y)
    lineTo(t2.x, t2.y)
    lineTo(c1.x, c1.y)
    lineTo(t4.x, t4.y)
    lineTo(t3.x, t3.y)
    lineTo(c2.x, c2.y)
    lineTo(t1.x, t1.y)

    return this
}

fun Float.toDegree(): Float {
    return (this * 180f / PI).toFloat()
}

fun drawPointsOnPath(
    points: List<StylusPoint>,
    brush: StylusBrush,
): Path {
    var path = Path()

    var lastPoint: StylusPoint? = null

    points.fastForEach { point ->
        path = Path.combine(
            operation = PathOperation.Union,
            path1 = path,
            path2 = Path().apply {
                addOval(
                    Rect(
                        center = Offset(point.x, point.y),
                        radius = brush.width * point.pressure / 2f
                    )
                )
            },
        )

        if (lastPoint != null) {
            val previousPointCenter = Offset(lastPoint!!.x, lastPoint!!.y)
            val previousPointRadius = brush.width * lastPoint!!.pressure / 2f

            val currentPointCenter = Offset(point.x, point.y)
            val currentPointRadius = brush.width * point.pressure / 2f

            val c1: Offset
            val r1: Float

            val c2: Offset
            val r2: Float

            if (previousPointRadius < currentPointRadius) {
                c1 = previousPointCenter
                r1 = previousPointRadius

                c2 = currentPointCenter
                r2 = currentPointRadius
            } else {
                c1 = currentPointCenter
                r1 = currentPointRadius

                c2 = previousPointCenter
                r2 = previousPointRadius
            }

            path = Path.combine(
                operation = PathOperation.Union,
                path1 = path,
                path2 = Path().drawOuterTangent2(
                    c1 = c1,
                    r1 = r1,
                    c2 = c2,
                    r2 = r2,
                ),
            )
        }

        lastPoint = point
    }

    return path
}