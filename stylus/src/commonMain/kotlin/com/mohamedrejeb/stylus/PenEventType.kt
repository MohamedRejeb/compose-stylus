package com.mohamedrejeb.stylus

/**
 * What kind of pen action a [PenEvent] represents.
 *
 * Ordinal layout is part of the JNI ABI — the native bridge converts its
 * dispatch site to this enum by ordinal index, so reordering or inserting
 * constants in front of existing ones silently miswires events on Desktop.
 *
 * Mapping to platform pointer concepts:
 * - [Hover] — pen entered, exited, or moved while not in contact with the surface.
 * - [Move] — pen moved while in contact (or in contact with a button held).
 * - [Press] — pen first contacted the surface (or a barrel button was pressed).
 * - [Release] — pen lifted off the surface (or a barrel button was released).
 */
enum class PenEventType {
    Hover,
    Move,
    Press,
    Release,
}
