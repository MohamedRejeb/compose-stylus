package com.mohamedrejeb.stylus.compose

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mohamedrejeb.stylus.PenEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenInkSurfaceTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun draw_appends_finished_stroke_and_emits_pen_events() {
        lateinit var state: PenInkState
        val events = mutableListOf<PenEvent>()

        rule.setContent {
            state = rememberPenInkState()
            PenInkSurface(
                modifier = Modifier
                    .size(SURFACE_SIZE.dp)
                    .testTag(TAG),
                state = state,
                onPenEvent = { events.add(it) },
            )
        }

        drawStroke(startX = 50f, endX = 200f)
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) { state.finishedStrokes.size == 1 }

        assertEquals(1, state.finishedStrokes.size)
        assertTrue(
            "Expected Modifier.penInput {} to fire alongside Ink, got ${events.size} events",
            events.isNotEmpty(),
        )
    }

    @Test
    fun clear_empties_finished_strokes() {
        lateinit var state: PenInkState

        rule.setContent {
            state = rememberPenInkState()
            PenInkSurface(
                modifier = Modifier.size(SURFACE_SIZE.dp).testTag(TAG),
                state = state,
            )
        }

        drawStroke(startX = 50f, endX = 200f)
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) { state.finishedStrokes.size == 1 }

        rule.runOnIdle { state.clear() }
        rule.runOnIdle { assertEquals(0, state.finishedStrokes.size) }
    }

    @Test
    fun undo_removes_only_the_last_finished_stroke() {
        lateinit var state: PenInkState

        rule.setContent {
            state = rememberPenInkState()
            PenInkSurface(
                modifier = Modifier.size(SURFACE_SIZE.dp).testTag(TAG),
                state = state,
            )
        }

        drawStroke(startX = 30f, endX = 90f)
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) { state.finishedStrokes.size == 1 }

        drawStroke(startX = 130f, endX = 230f)
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) { state.finishedStrokes.size == 2 }

        rule.runOnIdle { state.undo() }
        rule.runOnIdle { assertEquals(1, state.finishedStrokes.size) }
    }

    private fun drawStroke(startX: Float, endX: Float) {
        rule.onNodeWithTag(TAG).performTouchInput {
            down(Offset(startX, 50f))
            advanceEventTime(EVENT_INTERVAL_MS)
            moveTo(Offset((startX + endX) / 2f, 100f))
            advanceEventTime(EVENT_INTERVAL_MS)
            moveTo(Offset(endX, 150f))
            advanceEventTime(EVENT_INTERVAL_MS)
            up()
        }
    }

    companion object {
        private const val TAG = "ink-surface"
        private const val SURFACE_SIZE = 300
        private const val TIMEOUT_MS = 5_000L
        private const val EVENT_INTERVAL_MS = 16L
    }
}
