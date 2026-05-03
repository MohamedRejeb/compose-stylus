package com.mohamedrejeb.stylus.demo.web

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.mohamedrejeb.stylus.compose.PenBrush
import com.mohamedrejeb.stylus.compose.PenInkEngine
import com.mohamedrejeb.stylus.compose.PenInkSurface
import com.mohamedrejeb.stylus.compose.penInput
import com.mohamedrejeb.stylus.compose.rememberPenInkState
import kotlinx.browser.document
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize().background(Color.White)) {
                DemoSwitcher()
            }
        }
    }
}

@Composable
private fun DemoSwitcher() {
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
                0 -> StylusDemoCanvas()
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
    // Re-derive the brush only when the slider value changes — the renderer
    // reads `brush.size` for new strokes only, so allocating per
    // recomposition would be wasteful but harmless.
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
private fun StylusDemoCanvas() {
    val strokes = remember { mutableStateListOf<List<Pair<Offset, Float>>>() }
    var current by remember { mutableStateOf<List<Pair<Offset, Float>>>(emptyList()) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .penInput(
                key = "web-canvas",
                onPress = { event ->
                    current = listOf(Offset(event.x.toFloat(), event.y.toFloat()) to event.pressure.toFloat())
                },
                onMove = { event ->
                    current = current + (Offset(event.x.toFloat(), event.y.toFloat()) to event.pressure.toFloat())
                },
                onRelease = {
                    if (current.isNotEmpty()) strokes.add(current)
                    current = emptyList()
                },
            )
    ) {
        (strokes + listOf(current)).forEach { stroke ->
            for (i in 1 until stroke.size) {
                val (p0, pr) = stroke[i - 1]
                val (p1, _) = stroke[i]
                drawLine(
                    color = Color.Black,
                    start = p0,
                    end = p1,
                    strokeWidth = (1f + pr * 12f),
                )
            }
        }
    }
}
