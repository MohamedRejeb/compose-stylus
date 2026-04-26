package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.mohamedrejeb.stylus.PenButton
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventCallback
import com.mohamedrejeb.stylus.PenEventType
import com.mohamedrejeb.stylus.PenTool
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end test of the Compose-side pen input pipeline.
 *
 * `attachWithoutNative` + `dispatchSynthetic` drive the exact same callback
 * chain that real native events traverse on Desktop —
 * `JvmPenEventCallbackWrapper` → `ComponentScopedCallback` → `translateToComponent` →
 * `containsTranslated` → user callback — without depending on the JNI swizzle,
 * a real `NSEvent` stream, or a visible AWT window. That makes the test
 * reliable in a headless CI JVM environment while still exercising every line
 * of Compose-side code that runs in production.
 *
 * Catches regressions in:
 *   - the setEnabled-vs-attach race (fixed by removing the inner invokeLater)
 *   - HiDPI / density coordinate translation
 *   - bounds containment filtering
 *   - pressed-state tracking across drag-out-of-bounds
 *   - callback forwarding fidelity
 */
class ComposePenInputManagerPipelineTest {

    private val mgr = ComposePenInputManager.instance
    private val received = mutableListOf<PenEvent>()

    private val recorder = PenEventCallback { event -> received += event }

    @AfterTest
    fun cleanUp() {
        mgr.resetForTest()
        received.clear()
    }

    private fun primeFor(
        key: String,
        size: Size = Size(200f, 100f),
        topLeft: Offset = Offset(10f, 20f),
        density: Float = 2f,
    ) {
        mgr.attachWithoutNative(key, recorder)
        mgr.updateSize(key, size)
        mgr.updateTopLeft(key, topLeft)
        mgr.updateDensity(key, density)
    }

    private fun nativeEvent(
        x: Double,
        y: Double,
        type: PenEventType,
        button: PenButton = PenButton.Primary,
        pressure: Double = 0.5,
    ): PenEvent = PenEvent(
        type = type,
        tool = PenTool.Pen,
        button = button,
        x = x,
        y = y,
        pressure = pressure,
    )

    // ─── Translation: pixels → DPs → component-local ───────────────────

    @Test
    fun `down then move delivers correctly translated coordinates`() {
        // Modifier sits at (10dp, 20dp) inside a window with density=2.
        // Native pixel (100, 200) → DP (50, 100) → component (40, 80).
        primeFor("k", density = 2f, topLeft = Offset(10f, 20f))

        mgr.dispatchSynthetic("k", nativeEvent(x = 100.0, y = 200.0, type = PenEventType.Press))
        mgr.dispatchSynthetic("k", nativeEvent(x = 200.0, y = 100.0, type = PenEventType.Move))

        assertEquals(2, received.size)
        assertEquals(PenEventType.Press, received[0].type)
        assertEquals(40.0, received[0].x, "press x")
        assertEquals(80.0, received[0].y, "press y")
        assertEquals(PenEventType.Move, received[1].type)
        assertEquals(90.0, received[1].x, "move x")  // 200/2 - 10 = 90
        assertEquals(30.0, received[1].y, "move y")  // 100/2 - 20 = 30
    }

    @Test
    fun `pressure passes through translation unchanged`() {
        primeFor("k")
        mgr.dispatchSynthetic("k", nativeEvent(x = 50.0, y = 50.0, type = PenEventType.Press, pressure = 0.73))
        assertEquals(0.73, received.single().pressure)
    }

    @Test
    fun `density of 1 means native pixels equal component DPs after topLeft subtract`() {
        primeFor("k", density = 1f, topLeft = Offset.Zero, size = Size(500f, 500f))
        mgr.dispatchSynthetic("k", nativeEvent(x = 123.0, y = 456.0, type = PenEventType.Press))
        assertEquals(123.0, received.single().x)
        assertEquals(456.0, received.single().y)
    }

    // ─── Bounds containment ────────────────────────────────────────────

    @Test
    fun `events outside the component bounds are filtered`() {
        primeFor("k", density = 1f, topLeft = Offset.Zero, size = Size(100f, 100f))

        // Inside bounds — should arrive
        mgr.dispatchSynthetic("k", nativeEvent(x = 50.0, y = 50.0, type = PenEventType.Hover, button = PenButton.None))
        // Outside bounds — should be dropped
        mgr.dispatchSynthetic("k", nativeEvent(x = 500.0, y = 500.0, type = PenEventType.Hover, button = PenButton.None))

        assertEquals(1, received.size)
        assertEquals(50.0, received.single().x)
    }

    @Test
    fun `topLeft offset is correctly subtracted`() {
        primeFor("k", density = 1f, topLeft = Offset(50f, 50f), size = Size(100f, 100f))

        // Native (75, 75) → component (25, 25) — inside [0, 100]
        mgr.dispatchSynthetic("k", nativeEvent(x = 75.0, y = 75.0, type = PenEventType.Press))
        // Native (40, 40) → component (-10, -10) — outside the box's top-left
        mgr.dispatchSynthetic("k", nativeEvent(x = 40.0, y = 40.0, type = PenEventType.Press))

        assertEquals(1, received.size, "only the in-bounds event should arrive")
        assertEquals(25.0, received.single().x)
    }

    // ─── Drag-out-of-bounds: stays delivered until release ────────────

    @Test
    fun `pressed state keeps moves alive after dragging outside bounds`() {
        primeFor("k", density = 1f, topLeft = Offset.Zero, size = Size(100f, 100f))

        mgr.dispatchSynthetic("k", nativeEvent(x = 50.0, y = 50.0, type = PenEventType.Press))
        // Now drag *outside* the box. The native side keeps reporting; the
        // component should still receive moves because we're "captured".
        mgr.dispatchSynthetic("k", nativeEvent(x = 500.0, y = 500.0, type = PenEventType.Move))
        mgr.dispatchSynthetic("k", nativeEvent(x = 500.0, y = 500.0, type = PenEventType.Release, button = PenButton.None))

        assertEquals(3, received.size)
        assertEquals(
            listOf(PenEventType.Press, PenEventType.Move, PenEventType.Release),
            received.map { it.type },
        )
    }

    @Test
    fun `move events while not pressed are filtered when button is held elsewhere`() {
        primeFor("k", density = 1f, topLeft = Offset.Zero, size = Size(100f, 100f))

        // Mid-drag from a sibling component: button=Primary but we never received
        // a press ourselves (pressed=false). These should be ignored.
        mgr.dispatchSynthetic("k", nativeEvent(x = 50.0, y = 50.0, type = PenEventType.Move))
        assertTrue(received.isEmpty(), "moves with button held but never claimed should be dropped")
    }

    // ─── Listener detach / reset ──────────────────────────────────────

    @Test
    fun `resetForTest clears state so subsequent dispatches are no-ops`() {
        primeFor("k")
        mgr.resetForTest()
        mgr.dispatchSynthetic("k", nativeEvent(x = 100.0, y = 200.0, type = PenEventType.Press))
        assertTrue(received.isEmpty())
    }
}
