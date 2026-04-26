package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.Modifier
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType

/**
 * Observe pen / stylus events on the modified node.
 *
 * On Desktop the events come from a native (JNI) hook into Cocoa / X11 / Windows
 * RTS — that's the only way to get accurate pen pressure and tilt because AWT's
 * standard mouse pipeline strips that data.
 *
 * On Android, iOS, and Web the events are derived from Compose's own
 * `PointerInputChange`, which already carries `pressure`, `tiltX`, `tiltY`, and
 * a tool-type discriminator (mouse / touch / stylus / eraser).
 *
 * @param key Stable identifier so a callback attached on this node is distinct
 * from any siblings using the same modifier on other nodes.
 * @param onEvent Called for every [PenEvent] that occurs on this node.
 *   Dispatch on [PenEvent.type] to distinguish hover / move / press / release.
 */
fun Modifier.penInput(
    key: Any = PenInputKey,
    onEvent: (PenEvent) -> Unit,
): Modifier = this then platformPenInputModifier(key, onEvent)

/**
 * Granular overload of [penInput] — splits dispatch on [PenEvent.type] into
 * four typed callbacks. Mirrors the shape most apps want.
 */
fun Modifier.penInput(
    key: Any = PenInputKey,
    onHover: (PenEvent) -> Unit = {},
    onMove: (PenEvent) -> Unit = {},
    onPress: (PenEvent) -> Unit = {},
    onRelease: (PenEvent) -> Unit = {},
): Modifier = penInput(key) { event ->
    when (event.type) {
        PenEventType.Hover -> onHover(event)
        PenEventType.Move -> onMove(event)
        PenEventType.Press -> onPress(event)
        PenEventType.Release -> onRelease(event)
    }
}

private object PenInputKey

internal expect fun platformPenInputModifier(
    key: Any,
    onEvent: (PenEvent) -> Unit,
): Modifier
