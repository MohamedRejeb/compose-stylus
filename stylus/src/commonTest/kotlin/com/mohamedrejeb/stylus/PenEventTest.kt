package com.mohamedrejeb.stylus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class PenEventTest {

    @Test
    fun `primary constructor preserves passed timestamp`() {
        val event = PenEvent(
            type = PenEventType.Press,
            tool = PenTool.Pen,
            button = PenButton.Primary,
            x = 5.0,
            y = 7.0,
            pressure = 0.3,
            timestamp = 1_000L,
        )
        assertEquals(PenEventType.Press, event.type)
        assertEquals(PenTool.Pen, event.tool)
        assertEquals(PenButton.Primary, event.button)
        assertEquals(5.0, event.x)
        assertEquals(7.0, event.y)
        assertEquals(0.3, event.pressure)
        assertEquals(1_000L, event.timestamp)
    }

    @Test
    fun `default timestamp is monotonically positive`() {
        val event = PenEvent(
            type = PenEventType.Hover,
            tool = PenTool.None,
            button = PenButton.None,
            x = 0.0,
            y = 0.0,
            pressure = 0.0,
        )
        // currentTimeMillis() expect/actual is implemented per platform; whatever
        // it returns, it should be a positive non-zero millisecond timestamp.
        assertTrue(event.timestamp > 0L, "timestamp=${event.timestamp}")
    }

    @Test
    fun `JNI-bridge DoubleArray constructor wraps axes correctly`() {
        val raw = doubleArrayOf(1.0, 2.0, 0.5, 0.0, 0.0, 0.0, 0.0)
        val event = PenEvent(PenEventType.Press, PenTool.Pen, PenButton.Primary, raw)
        assertEquals(1.0, event.x)
        assertEquals(2.0, event.y)
        assertEquals(0.5, event.pressure)
        assertEquals(PenEventType.Press, event.type)
        assertEquals(PenTool.Pen, event.tool)
        assertEquals(PenButton.Primary, event.button)
    }

    @Test
    fun `JNI-bridge DoubleArray constructor tolerates short arrays`() {
        // Some platforms only deliver x, y, pressure — defaults must kick in.
        val raw = doubleArrayOf(1.0, 2.0, 0.5)
        val event = PenEvent(PenEventType.Move, PenTool.Pen, PenButton.Primary, raw)
        assertEquals(1.0, event.x)
        assertEquals(2.0, event.y)
        assertEquals(0.5, event.pressure)
        assertEquals(0.0, event.tangentPressure)
        assertEquals(0.0, event.tiltX)
        assertEquals(0.0, event.tiltY)
        assertEquals(0.0, event.rotation)
    }

    @Test
    fun `translate returns a new event with the same metadata`() {
        val original = PenEvent(
            type = PenEventType.Move,
            tool = PenTool.Pen,
            button = PenButton.Primary,
            x = 5.0,
            y = 7.0,
            pressure = 0.5,
            timestamp = 100L,
        )
        val translated = original.translate(x = 50.0, y = 70.0)
        assertEquals(50.0, translated.x)
        assertEquals(70.0, translated.y)
        assertEquals(0.5, translated.pressure)
        assertEquals(PenEventType.Move, translated.type)
        assertEquals(PenTool.Pen, translated.tool)
        assertEquals(PenButton.Primary, translated.button)
        assertEquals(100L, translated.timestamp)
        assertNotSame(original, translated)
        assertEquals(5.0, original.x) // original untouched
    }
}
