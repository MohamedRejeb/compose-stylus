package com.mohamedrejeb.stylus

/**
 * The pen button (or barrel button) that produced the event.
 *
 * Ordinal layout is part of the JNI ABI — the native bridge maps its internal
 * `Button` enum to this one by ordinal index, so reordering or inserting
 * constants in front of existing ones silently miswires events on Desktop.
 */
enum class PenButton {
    None,
    Primary,
    Secondary,
    Tertiary,
}
