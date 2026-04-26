package com.mohamedrejeb.stylus

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The native (C++) side of the JNI bridge passes [PenButton] / [PenTool] /
 * [PenEventType] enum values by **ordinal index**. Reordering or inserting
 * constants in front of existing ones silently miswires events on Desktop.
 *
 * These tests pin the ordinal layout so any reorder breaks the build instead.
 */
class PenEnumsTest {

    @Test
    fun `PenButton ordinals match native Button enum`() {
        assertEquals(0, PenButton.None.ordinal)
        assertEquals(1, PenButton.Primary.ordinal)
        assertEquals(2, PenButton.Secondary.ordinal)
        assertEquals(3, PenButton.Tertiary.ordinal)
        assertEquals(4, PenButton.entries.size)
    }

    @Test
    fun `PenTool ordinals 0-3 match native Cursor enum`() {
        // Native side has only None, Mouse, Eraser, Pen. Touch is added on the
        // Kotlin side for non-JVM platforms (Android touch / iOS direct touch /
        // Web touch pointers) and must come after the native-known values so
        // ordinal-based mapping from the JVM JNI layer keeps working.
        assertEquals(0, PenTool.None.ordinal)
        assertEquals(1, PenTool.Mouse.ordinal)
        assertEquals(2, PenTool.Eraser.ordinal)
        assertEquals(3, PenTool.Pen.ordinal)
        assertEquals(4, PenTool.Touch.ordinal)
    }

    @Test
    fun `PenEventType ordinals match native PenEventTypeOrdinal enum`() {
        assertEquals(0, PenEventType.Hover.ordinal)
        assertEquals(1, PenEventType.Move.ordinal)
        assertEquals(2, PenEventType.Press.ordinal)
        assertEquals(3, PenEventType.Release.ordinal)
        assertEquals(4, PenEventType.entries.size)
    }
}
