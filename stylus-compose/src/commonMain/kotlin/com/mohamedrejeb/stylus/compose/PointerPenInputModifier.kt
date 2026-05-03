package com.mohamedrejeb.stylus.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType

/**
 * Reusable pointer-event-driven implementation of `Modifier.penInput` for
 * Android / iOS / Web. Each platform's `platformPenInputModifier` actual
 * delegates here.
 *
 * Compose's pointer pipeline on these platforms already routes pressure and
 * the `Stylus` / `Eraser` discriminator through `PointerInputChange`, so we
 * forward each change as a [PenEvent]. Tilt does *not* travel with
 * `PointerInputChange`, so platform actuals pass an [enrich] callback that
 * pulls it out of the native event ([PointerEvent.motionEvent] on Android,
 * [PointerEvent.nativeEvent] cast to `UIEvent` on iOS).
 *
 * Press / Release are detected via the change's pressed-state transition rather
 * than the raw `event.type`. On the Web target Compose only listens to
 * `mouse*` / `touch*` (no `pointer*`) events, so an explicit Release event for
 * a stylus lift can go missing — relying on the transition keeps press/release
 * symmetric. A `Move` while not pressed is reported as `Hover` so callers can
 * distinguish hovering pen telemetry from in-contact drawing without having to
 * inspect `event.button` themselves.
 */
internal fun pointerPenInputModifier(
    key: Any,
    enrich: (PenEvent, PointerEvent, PointerInputChange) -> PenEvent = NoEnrich,
    onEvent: (PenEvent) -> Unit,
): Modifier = Modifier.composed {
    // `Modifier.pointerInput(key)` launches its suspend block once and never
    // re-launches as long as the key is stable. The block is a coroutine,
    // not a recomposition-driven function, so it captures the [onEvent] /
    // [enrich] references it sees on first launch and keeps using them
    // forever — every subsequent caller passing fresh lambdas (e.g. when
    // a brush state changes) is silently ignored. The visible symptom is
    // a finished `PenStroke` getting stamped with whichever brush was
    // current when the surface first composed.
    //
    // `rememberUpdatedState` swaps the tracked value out on every
    // composition; reading `.value` inside the suspend body picks up the
    // latest closure on the next pointer event without restarting the
    // gesture loop (so `wasPressed` and any pending press/release state
    // survive across brush changes).
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentEnrich by rememberUpdatedState(enrich)
    Modifier.pointerInput(key) {
        awaitPointerEventScope {
            var wasPressed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change: PointerInputChange = event.changes.firstOrNull() ?: continue
                val isPressed = change.pressed

                val penEvent: PenEvent? = when {
                    !wasPressed && isPressed -> change.toPenEvent(PenEventType.Press)
                    wasPressed && !isPressed -> change.toPenEvent(PenEventType.Release)
                    else -> when (event.type) {
                        PointerEventType.Enter,
                        PointerEventType.Exit -> change.toPenEvent(PenEventType.Hover)

                        PointerEventType.Move -> {
                            val penType = if (isPressed) PenEventType.Move else PenEventType.Hover
                            change.toPenEvent(penType)
                        }

                        else -> null
                    }
                }

                if (penEvent != null) currentOnEvent(currentEnrich(penEvent, event, change))

                wasPressed = isPressed
            }
        }
    }
}

private val NoEnrich: (PenEvent, PointerEvent, PointerInputChange) -> PenEvent =
    { event, _, _ -> event }
