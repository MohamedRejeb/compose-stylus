package com.mohamedrejeb.stylus.compose

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.mohamedrejeb.stylus.PenButton
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType
import com.mohamedrejeb.stylus.PenTool

internal actual fun platformPenInputModifier(
    key: Any,
    onEvent: (PenEvent) -> Unit,
): Modifier = Modifier.composed {
    // Compose Multiplatform on wasmJs scales DOM offsetX/offsetY by the
    // scene density before dispatching pointer events (see
    // `MouseEvent.offset` in ComposeWindow.w3c.kt). That means every
    // Compose-internal coordinate — including the rect from
    // `boundsInWindow()` and the `position` on `PointerInputChange` — is in
    // **device pixels**, not CSS pixels. DOM `clientX/clientY` are CSS
    // pixels. We pass the live density into the subscriber so it can lift
    // the coalesced samples into the same coordinate system before
    // emitting; otherwise on a 2× display every synthetic point lands at
    // half its true position and the renderer draws a phantom stroke at
    // half the canvas's offset.
    val density = LocalDensity.current.density
    // The subscriber survives across recompositions, but `onEvent` is
    // re-allocated on every composition — the lambda from
    // `Modifier.penInput { … }` captures the latest brush / state inside
    // `PenInkSurface`, and stamping a finished stroke uses whichever
    // `onEvent` we last forwarded. If we left the subscriber holding the
    // lambda from the very first composition (the original
    // `remember { CoalescedSubscriber(onEvent) }` pattern), changing the
    // brush would only affect in-flight rendering — every finished
    // stroke would still be captured with the brush that was active
    // when the surface first composed. Push the latest lambda through
    // each frame, same way we already update `density`.
    val subscriber = remember { CoalescedSubscriber() }
    subscriber.density = density
    subscriber.onEvent = onEvent

    DisposableEffect(Unit) {
        WebPointerSidecar.ensureAttached()
        WebPointerSidecar.subscribe(subscriber)
        onDispose { WebPointerSidecar.unsubscribe(subscriber) }
    }

    // Compose's pointer pipeline still drives press/release/hover transitions
    // and the dispatched (last-of-frame) move — see WebPointerSidecar for why
    // we leave the final sample to Compose and only emit the in-between ones
    // ourselves. `augment` overwrites pressure/tilt/tool with real DOM data
    // so a stylus on web doesn't show up as a 1.0-pressure mouse.
    Modifier
        .onGloballyPositioned { coords ->
            subscriber.bounds = coords.boundsInWindow()
        }
        .then(
            pointerPenInputModifier(key) { event ->
                onEvent(WebPointerSidecar.augment(event))
            },
        )
}

private class CoalescedSubscriber : WebPointerSidecar.Subscriber {
    // Re-assigned by the modifier every composition so this subscriber
    // always forwards events through the latest closure. See the comment
    // at the assignment site for why the initial-only capture was wrong.
    var onEvent: (PenEvent) -> Unit = {}
    var bounds: Rect = Rect.Zero
    var density: Float = 1f

    override fun onCoalescedSample(sample: WebPointerSidecar.PointerSample) {
        val rect = bounds
        if (rect.width <= 0f || rect.height <= 0f) return
        // CSS-pixel `clientX/Y` lifted into Compose's device-pixel space,
        // then translated by the modifier's window-pixel rect to get
        // modifier-local position. Bounds-aligned for full-viewport
        // ComposeViewport setups; embedded canvases with viewport offsets
        // would additionally need their canvas bounding rect subtracted,
        // but that path needs a stable canvas reference — defer until a
        // user actually hits it.
        val d = density.toDouble()
        val localX = sample.clientX * d - rect.left
        val localY = sample.clientY * d - rect.top
        if (localX < 0.0 || localY < 0.0 || localX > rect.width || localY > rect.height) return

        val tool = when (sample.pointerType) {
            "pen" -> PenTool.Pen
            "touch" -> PenTool.Touch
            "mouse" -> PenTool.Mouse
            else -> PenTool.None
        }
        onEvent(
            PenEvent(
                type = PenEventType.Move,
                tool = tool,
                button = if (sample.pressed) PenButton.Primary else PenButton.None,
                x = localX,
                y = localY,
                pressure = sample.pressure,
                tiltX = sample.tiltX,
                tiltY = sample.tiltY,
                rotation = sample.twist,
                timestamp = sample.timeStamp.toLong(),
            ),
        )
    }
}
