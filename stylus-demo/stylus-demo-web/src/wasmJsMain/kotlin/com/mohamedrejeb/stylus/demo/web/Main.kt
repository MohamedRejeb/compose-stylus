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
import com.mohamedrejeb.stylus.compose.PenInkSurface
import com.mohamedrejeb.stylus.compose.penInput
import com.mohamedrejeb.stylus.compose.rememberPenInkState
import kotlinx.browser.document

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
    PenInkSurface(
        modifier = Modifier.fillMaxSize(),
        state = state,
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Button(onClick = { state.undo() }) { Text("Undo") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { state.clear() }) { Text("Clear") }
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
