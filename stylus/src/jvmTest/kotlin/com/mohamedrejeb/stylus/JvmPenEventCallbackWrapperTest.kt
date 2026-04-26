package com.mohamedrejeb.stylus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmPenEventCallbackWrapperTest {

    /**
     * The C++ JNI bridge writes a per-callback pointer into a Java field
     * named `nativeHandle` of type `J` (long), via
     * `env->GetFieldID(cls, "nativeHandle", "J")`. If Kotlin name-mangles or
     * relocates the field, that lookup returns null and every callback stays
     * in its disabled-by-default state — the silent failure mode we hit
     * before adding `@JvmField`.
     *
     * This test pins the field's existence + name + JVM type signature.
     */
    @Test
    fun `nativeHandle field is reflectively reachable as a long`() {
        val noopDelegate = PenEventCallback { }
        val wrapper = JvmPenEventCallbackWrapper(noopDelegate)

        val field = wrapper.javaClass.getDeclaredField("nativeHandle")
        field.isAccessible = true
        assertEquals(java.lang.Long.TYPE, field.type, "field type should be primitive long (J)")
        assertEquals(0L, field.getLong(wrapper), "default value should be zero")

        // Simulate what the JNI bridge does: stash a pointer-as-long and read it back.
        field.setLong(wrapper, 0xDEADBEEFL)
        assertEquals(0xDEADBEEFL, field.getLong(wrapper))
    }

    @Test
    fun `wrapper forwards onEvent calls to the delegate`() {
        val received = mutableListOf<PenEvent>()
        val delegate = PenEventCallback { event -> received += event }
        val wrapper = JvmPenEventCallbackWrapper(delegate)
        val event = PenEvent(
            type = PenEventType.Press,
            tool = PenTool.Pen,
            button = PenButton.Primary,
            x = 0.0,
            y = 0.0,
            pressure = 0.5,
        )

        wrapper.onEvent(event)
        wrapper.onEvent(event.copy(type = PenEventType.Move))
        wrapper.onEvent(event.copy(type = PenEventType.Release))

        assertEquals(3, received.size)
        assertEquals(listOf(PenEventType.Press, PenEventType.Move, PenEventType.Release), received.map { it.type })
    }

    @Test
    fun `currentTimeMillis returns wall-clock millis on JVM`() {
        val before = System.currentTimeMillis()
        val measured = currentTimeMillis()
        val after = System.currentTimeMillis()
        assertTrue(measured in before..after, "expected $measured ∈ [$before, $after]")
    }
}
