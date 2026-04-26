package com.mohamedrejeb.stylus

/**
 * The hardware tool currently producing input.
 *
 * Ordinals 0..3 (None / Mouse / Eraser / Pen) are part of the JNI ABI — the
 * native bridge maps its internal `Cursor` enum to this one by ordinal.
 * `Touch` is added on the Kotlin side for non-JVM platforms (Android touch,
 * iOS direct touch, Web touch pointers) and must come last so the
 * ordinal-based mapping from the JVM JNI layer keeps working.
 */
enum class PenTool {
    None,
    Mouse,
    Eraser,
    Pen,
    Touch,
}
