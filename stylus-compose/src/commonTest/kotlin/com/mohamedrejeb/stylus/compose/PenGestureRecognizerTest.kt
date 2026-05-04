package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.geometry.Offset
import com.mohamedrejeb.stylus.PenButton
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType
import com.mohamedrejeb.stylus.PenTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PenGestureRecognizerTest {

    @Test
    fun `press and release inside tap slop fires single click`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(press(t = 0, x = 10f, y = 10f))
        r.onPenEvent(release(t = 50, x = 10f, y = 10f))

        assertEquals(listOf("click@10,10"), log.entries)
    }

    @Test
    fun `crossing drag slop promotes to drag and skips click`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(press(t = 0, x = 0f, y = 0f))
        r.onPenEvent(move(t = 16, x = 20f, y = 0f))
        r.onPenEvent(move(t = 32, x = 30f, y = 0f))
        r.onPenEvent(release(t = 48, x = 30f, y = 0f))

        assertEquals(
            listOf(
                "dragStart@0,0",
                "drag@20,0",
                "drag@30,0",
                "dragEnd@30,0",
            ),
            log.entries,
        )
    }

    @Test
    fun `dragStart fires once even after multiple sub-slop moves`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(press(t = 0, x = 0f, y = 0f))
        r.onPenEvent(move(t = 16, x = 1f, y = 0f))
        r.onPenEvent(move(t = 32, x = 2f, y = 0f))
        r.onPenEvent(move(t = 48, x = 100f, y = 0f))
        r.onPenEvent(release(t = 64, x = 100f, y = 0f))

        // dragStart should be reported with the original press offset, not
        // the last sub-slop position — that's how the legacy recognizer
        // surfaces the press point that the downstream tool wants.
        assertEquals(
            listOf("dragStart@0,0", "drag@100,0", "dragEnd@100,0"),
            log.entries,
        )
    }

    @Test
    fun `two taps inside double-click window fire click then double-click`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(press(t = 0, x = 5f, y = 5f))
        r.onPenEvent(release(t = 30, x = 5f, y = 5f))
        r.onPenEvent(press(t = 100, x = 5f, y = 5f))
        r.onPenEvent(release(t = 130, x = 5f, y = 5f))

        assertEquals(
            listOf("click@5,5", "doubleClick@5,5"),
            log.entries,
        )
    }

    @Test
    fun `second tap outside double-click window fires plain click`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(press(t = 0, x = 0f, y = 0f))
        r.onPenEvent(release(t = 20, x = 0f, y = 0f))
        r.onPenEvent(press(t = 1_000, x = 0f, y = 0f))
        r.onPenEvent(release(t = 1_020, x = 0f, y = 0f))

        assertEquals(listOf("click@0,0", "click@0,0"), log.entries)
    }

    @Test
    fun `triple tap is click double-click click`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(press(t = 0, x = 0f, y = 0f))
        r.onPenEvent(release(t = 20, x = 0f, y = 0f))
        r.onPenEvent(press(t = 100, x = 0f, y = 0f))
        r.onPenEvent(release(t = 120, x = 0f, y = 0f))
        r.onPenEvent(press(t = 200, x = 0f, y = 0f))
        r.onPenEvent(release(t = 220, x = 0f, y = 0f))

        assertEquals(
            listOf("click@0,0", "doubleClick@0,0", "click@0,0"),
            log.entries,
        )
    }

    @Test
    fun `secondary press release fires right-click only`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(press(t = 0, x = 7f, y = 8f, button = PenButton.Secondary))
        r.onPenEvent(release(t = 30, x = 7f, y = 8f, button = PenButton.Secondary))

        assertEquals(listOf("rightClick@7,8"), log.entries)
    }

    @Test
    fun `hover events forward to onHover`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(hover(t = 0, x = 1f, y = 2f))
        r.onPenEvent(hover(t = 16, x = 3f, y = 4f))

        assertEquals(listOf("hover@1,2", "hover@3,4"), log.entries)
    }

    @Test
    fun `force drag arms drag immediately and tap-and-lift also fires click`() {
        val log = GestureLog()
        val r = recognizer(log, forceDragOnPress = { true })

        r.onPenEvent(press(t = 0, x = 50f, y = 50f))
        r.onPenEvent(release(t = 30, x = 50f, y = 50f))

        assertEquals(
            listOf("dragStart@50,50", "dragEnd@50,50", "click@50,50"),
            log.entries,
        )
    }

    @Test
    fun `force drag with movement fires drag callbacks and no extra click`() {
        val log = GestureLog()
        val r = recognizer(log, forceDragOnPress = { true })

        r.onPenEvent(press(t = 0, x = 0f, y = 0f))
        r.onPenEvent(move(t = 16, x = 50f, y = 0f))
        r.onPenEvent(release(t = 32, x = 50f, y = 0f))

        assertEquals(
            listOf("dragStart@0,0", "drag@50,0", "dragEnd@50,0"),
            log.entries,
        )
    }

    @Test
    fun `disabled gating cancels in-flight drag and drops further events`() {
        val log = GestureLog()
        var enabled = true
        val r = recognizer(log, isEnabled = { enabled })

        r.onPenEvent(press(t = 0, x = 0f, y = 0f))
        r.onPenEvent(move(t = 16, x = 50f, y = 0f))
        assertEquals(
            listOf("dragStart@0,0", "drag@50,0"),
            log.entries,
        )

        enabled = false
        r.onPenEvent(move(t = 32, x = 60f, y = 0f))
        r.onPenEvent(release(t = 48, x = 60f, y = 0f))

        assertEquals(
            listOf("dragStart@0,0", "drag@50,0", "dragCancel"),
            log.entries,
        )
    }

    @Test
    fun `tool change fires even when disabled`() {
        val log = GestureLog()
        val r = recognizer(log, isEnabled = { false })

        r.onPenEvent(hover(t = 0, x = 0f, y = 0f, tool = PenTool.Pen))
        r.onPenEvent(hover(t = 16, x = 0f, y = 0f, tool = PenTool.Eraser))

        // Hover callbacks are gated, but tool changes are not — consumer
        // needs to know about the eraser flip even while the surface is
        // gated.
        assertEquals(listOf(PenTool.Pen, PenTool.Eraser), log.toolChanges)
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `tool change emitted only on transitions`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(hover(t = 0, x = 0f, y = 0f, tool = PenTool.Pen))
        r.onPenEvent(hover(t = 16, x = 1f, y = 0f, tool = PenTool.Pen))
        r.onPenEvent(hover(t = 32, x = 2f, y = 0f, tool = PenTool.Pen))

        assertEquals(listOf(PenTool.Pen), log.toolChanges)
    }

    @Test
    fun `release with NONE button after primary press still completes click`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(press(t = 0, x = 0f, y = 0f, button = PenButton.Primary))
        r.onPenEvent(release(t = 30, x = 0f, y = 0f, button = PenButton.None))

        assertEquals(listOf("click@0,0"), log.entries)
    }

    @Test
    fun `cancel during drag fires onDragCancel`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.onPenEvent(press(t = 0, x = 0f, y = 0f))
        r.onPenEvent(move(t = 16, x = 50f, y = 0f))
        r.cancel()

        assertEquals(
            listOf("dragStart@0,0", "drag@50,0", "dragCancel"),
            log.entries,
        )
    }

    @Test
    fun `cancel without active drag is a no-op`() {
        val log = GestureLog()
        val r = recognizer(log)

        r.cancel()

        assertTrue(log.entries.isEmpty())
    }

    private fun recognizer(
        log: GestureLog,
        isEnabled: () -> Boolean = { true },
        forceDragOnPress: () -> Boolean = { false },
        config: PenGesturesConfig = PenGesturesConfig.Default,
    ): PenGestureRecognizer = PenGestureRecognizer(
        config = config,
        isEnabled = isEnabled,
        forceDragOnPress = forceDragOnPress,
        onClick = { _, o -> log.add("click", o) },
        onDoubleClick = { _, o -> log.add("doubleClick", o) },
        onRightClick = { _, o -> log.add("rightClick", o) },
        onHover = { _, o -> log.add("hover", o) },
        onToolChange = { tool -> log.toolChanges.add(tool) },
        onDragStart = { _, o -> log.add("dragStart", o) },
        onDrag = { _, o -> log.add("drag", o) },
        onDragEnd = { _, o -> log.add("dragEnd", o) },
        onDragCancel = { log.entries.add("dragCancel") },
    )

    /**
     * Gesture callbacks land in [entries], tool changes in [toolChanges] —
     * keeping them separate means a click-vs-drag assertion isn't polluted
     * by the unconditional first-event tool-change (`null` → `Pen`).
     */
    private class GestureLog {
        val entries: MutableList<String> = mutableListOf()
        val toolChanges: MutableList<PenTool> = mutableListOf()

        fun add(name: String, offset: Offset) {
            entries.add("$name@${offset.x.toInt()},${offset.y.toInt()}")
        }
    }
}

private fun press(
    t: Long,
    x: Float,
    y: Float,
    button: PenButton = PenButton.Primary,
    tool: PenTool = PenTool.Pen,
): PenEvent = PenEvent(
    type = PenEventType.Press,
    tool = tool,
    button = button,
    x = x.toDouble(),
    y = y.toDouble(),
    pressure = 0.5,
    timestamp = t,
)

private fun move(
    t: Long,
    x: Float,
    y: Float,
    button: PenButton = PenButton.Primary,
    tool: PenTool = PenTool.Pen,
): PenEvent = PenEvent(
    type = PenEventType.Move,
    tool = tool,
    button = button,
    x = x.toDouble(),
    y = y.toDouble(),
    pressure = 0.5,
    timestamp = t,
)

private fun release(
    t: Long,
    x: Float,
    y: Float,
    button: PenButton = PenButton.Primary,
    tool: PenTool = PenTool.Pen,
): PenEvent = PenEvent(
    type = PenEventType.Release,
    tool = tool,
    button = button,
    x = x.toDouble(),
    y = y.toDouble(),
    pressure = 0.0,
    timestamp = t,
)

private fun hover(
    t: Long,
    x: Float,
    y: Float,
    tool: PenTool = PenTool.Pen,
): PenEvent = PenEvent(
    type = PenEventType.Hover,
    tool = tool,
    button = PenButton.None,
    x = x.toDouble(),
    y = y.toDouble(),
    pressure = 0.0,
    timestamp = t,
)
