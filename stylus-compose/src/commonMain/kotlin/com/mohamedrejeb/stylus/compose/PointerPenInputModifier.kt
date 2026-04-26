package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType

/**
 * Reusable pointer-event-driven implementation of `Modifier.penInput` for
 * Android / iOS / Web. Each platform's `platformPenInputModifier` actual
 * delegates here.
 *
 * Compose's pointer pipeline on these platforms already routes pressure, tilt
 * and the `Stylus` / `Eraser` discriminator through `PointerInputChange`, so we
 * just forward each change as a [PenEvent].
 */
internal fun pointerPenInputModifier(
    key: Any,
    onEvent: (PenEvent) -> Unit,
): Modifier = Modifier.pointerInput(key) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change: PointerInputChange = event.changes.firstOrNull() ?: continue

            when (event.type) {
                PointerEventType.Enter,
                PointerEventType.Exit -> onEvent(change.toPenEvent(PenEventType.Hover))

                PointerEventType.Press -> {
                    if (change.changedToDown()) onEvent(change.toPenEvent(PenEventType.Press))
                }

                PointerEventType.Release -> {
                    if (change.changedToUp()) onEvent(change.toPenEvent(PenEventType.Release))
                }

                PointerEventType.Move -> onEvent(change.toPenEvent(PenEventType.Move))
            }
        }
    }
}
