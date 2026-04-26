package com.mohamedrejeb.stylus.demo.web

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.ComposeViewport
import com.mohamedrejeb.stylus.compose.penInput
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize().background(Color.White)) {
                StylusDemoCanvas()
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
