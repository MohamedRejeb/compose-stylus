package com.mohamedrejeb.stylus.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenTool

/**
 * Tunable thresholds for [Modifier.penGestures] and [rememberPenGesturesHandler].
 *
 * Slop / timeout defaults are deliberately tighter than Compose's touch slop
 * because a stylus is much steadier than a finger — using touch defaults makes
 * pen taps feel mushy. Override per-app if your tools need a different feel
 * (e.g. a coarse "lasso" tool can bump [dragSlopPx] up to dampen jitter).
 *
 * @property tapSlopPx Maximum movement (in px) between a Press and Release for
 *   the gesture to still count as a click. Also reused as the slop window for
 *   double-click detection.
 * @property dragSlopPx Movement (in px) past the press point that promotes the
 *   gesture from "pending click" to "drag". Typically smaller than [tapSlopPx]
 *   so dragging starts feeling responsive before the tap window closes.
 * @property tapTimeoutMillis Maximum elapsed time between Press and Release
 *   for a forced-drag tap to also surface as `onClick`. Has no effect on
 *   normal taps (those are already gated by [dragSlopPx]).
 * @property doubleClickWindowMillis Maximum elapsed time between a Release and
 *   the next Press for the second tap to count as a double-click.
 */
data class PenGesturesConfig(
    val tapSlopPx: Float = 8f,
    val dragSlopPx: Float = 4f,
    val tapTimeoutMillis: Long = 300L,
    val doubleClickWindowMillis: Long = 300L,
) {
    companion object {
        val Default: PenGesturesConfig = PenGesturesConfig()
    }
}

/**
 * Build a stateful pen-gesture handler — a `(PenEvent) -> Unit` sink that
 * recognises clicks, double-clicks, right-clicks, drags, hover, and tool
 * changes from a raw [PenEvent] stream.
 *
 * Use this when you have an existing pen-event source that is not a Modifier:
 * - [PenInkSurface]'s `onPenEvent` callback (so freehand ink rendering and
 *   higher-level gestures can be driven from the same pipeline without
 *   stacking modifiers — important on Android where Jetpack Ink consumes
 *   touch events on its own SurfaceView).
 * - A custom subscription to [com.mohamedrejeb.stylus.PenInputSource].
 * - Tests that feed synthetic [PenEvent]s.
 *
 * For the more common case of recognising gestures on a regular Compose node,
 * use [Modifier.penGestures] — it's a thin wrapper around this factory.
 *
 * The recogniser instance is held inside [remember], so it survives
 * recompositions; every callback is wrapped in `rememberUpdatedState` so
 * passing fresh lambdas on each composition (e.g. when the active tool
 * changes) doesn't reset the gesture state machine.
 *
 * Behaviour notes — see [Modifier.penGestures] kdoc for the full contract.
 *
 * @param key Stable identifier for the recogniser instance. The recogniser is
 *   re-created if [key] or [config] change — useful when you want a clean
 *   gesture state on tool switch.
 */
@Composable
fun rememberPenGesturesHandler(
    key: Any = PenGesturesKey,
    isEnabled: () -> Boolean = AlwaysEnabled,
    forceDragOnPress: () -> Boolean = NeverForceDrag,
    config: PenGesturesConfig = PenGesturesConfig.Default,
    onClick: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onDoubleClick: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onRightClick: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onHover: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onToolChange: (PenTool) -> Unit = NoOpTool,
    onDragStart: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onDrag: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onDragEnd: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onDragCancel: () -> Unit = NoOpUnit,
): (PenEvent) -> Unit {
    val currentIsEnabled by rememberUpdatedState(isEnabled)
    val currentForceDrag by rememberUpdatedState(forceDragOnPress)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
    val currentOnRightClick by rememberUpdatedState(onRightClick)
    val currentOnHover by rememberUpdatedState(onHover)
    val currentOnToolChange by rememberUpdatedState(onToolChange)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    val recognizer = remember(key, config) {
        PenGestureRecognizer(
            config = config,
            isEnabled = { currentIsEnabled() },
            forceDragOnPress = { currentForceDrag() },
            onClick = { e, o -> currentOnClick(e, o) },
            onDoubleClick = { e, o -> currentOnDoubleClick(e, o) },
            onRightClick = { e, o -> currentOnRightClick(e, o) },
            onHover = { e, o -> currentOnHover(e, o) },
            onToolChange = { tool -> currentOnToolChange(tool) },
            onDragStart = { e, o -> currentOnDragStart(e, o) },
            onDrag = { e, o -> currentOnDrag(e, o) },
            onDragEnd = { e, o -> currentOnDragEnd(e, o) },
            onDragCancel = { currentOnDragCancel() },
        )
    }

    return remember(recognizer) { { event: PenEvent -> recognizer.onPenEvent(event) } }
}

/**
 * Higher-level pen gesture recognizer layered on top of [Modifier.penInput].
 *
 * Mirrors `androidx.compose.foundation.gestures.detectTapGestures` +
 * `detectDragGestures` but pen-aware: every callback receives the originating
 * [PenEvent], so consumers retain pressure / tilt / tool / button data on
 * recognized gestures (which is lost when going through the regular
 * `pointerInput` pipeline).
 *
 * Behavior:
 * - **Click vs drag** — a Press followed by movement under [PenGesturesConfig.dragSlopPx]
 *   and a Release fires [onClick]; crossing the slop promotes to
 *   [onDragStart] / [onDrag] / [onDragEnd].
 * - **Double-click** — two clicks within [PenGesturesConfig.doubleClickWindowMillis]
 *   and within tap-slop fire [onDoubleClick]. The first click still fires its
 *   own [onClick]; the second fires [onDoubleClick] *instead of* a second
 *   [onClick]. Matches the historical `stylusListener` semantics.
 * - **Right-click** — fired on Release when the originating button was
 *   `PenButton.Secondary` (hardware barrel button or right mouse button).
 * - **Hover** — every `PenEventType.Hover` is forwarded to [onHover].
 * - **Tool change** — fires whenever consecutive events report a different
 *   [PenTool] (the canonical case is a Wacom-style pen flipped to its eraser
 *   end mid-stroke). Fires *regardless* of [isEnabled] so the consumer can
 *   still switch the active tool while the surface is gated.
 * - **Forced drag** — when [forceDragOnPress] returns `true`, a Press starts
 *   a drag immediately rather than waiting for slop. A subsequent Release
 *   inside tap-timeout + tap-slop also surfaces as [onClick] so a single tap
 *   on a freehand tool still produces a "dot".
 * - **Disabled gating** — when [isEnabled] returns `false` the recognizer
 *   cancels any in-flight drag (firing [onDragCancel]) and drops further
 *   callbacks until it goes back to `true`. Reads the lambda lazily, so
 *   toggling cheap flags does not re-key the gesture loop.
 *
 * Use [Modifier.penInput] directly if you only need raw events. Use
 * [PenInkSurface] for low-latency stroke rendering — `penGestures` is for
 * "user intent" tools (select / text / rect / pan / measurement). To combine
 * both — freehand ink **and** gesture recognition on the same surface — wire
 * [rememberPenGesturesHandler] into [PenInkSurface]'s `onPenEvent`.
 *
 * @param key Stable identifier for the underlying pointer-input loop. Keep it
 *   stable across recompositions to avoid resetting in-flight gesture state.
 * @param isEnabled Lazily-read gate. When `false`, in-flight drags are
 *   cancelled and further callbacks (other than [onToolChange]) are skipped.
 * @param forceDragOnPress When `true` at Press time, the gesture starts a
 *   drag immediately instead of waiting for [PenGesturesConfig.dragSlopPx].
 * @param config Slop / timeout thresholds. See [PenGesturesConfig].
 */
fun Modifier.penGestures(
    key: Any = PenGesturesKey,
    isEnabled: () -> Boolean = AlwaysEnabled,
    forceDragOnPress: () -> Boolean = NeverForceDrag,
    config: PenGesturesConfig = PenGesturesConfig.Default,
    onClick: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onDoubleClick: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onRightClick: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onHover: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onToolChange: (PenTool) -> Unit = NoOpTool,
    onDragStart: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onDrag: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onDragEnd: (event: PenEvent, offset: Offset) -> Unit = NoOpEventOffset,
    onDragCancel: () -> Unit = NoOpUnit,
): Modifier = composed {
    val handler = rememberPenGesturesHandler(
        key = key,
        isEnabled = isEnabled,
        forceDragOnPress = forceDragOnPress,
        config = config,
        onClick = onClick,
        onDoubleClick = onDoubleClick,
        onRightClick = onRightClick,
        onHover = onHover,
        onToolChange = onToolChange,
        onDragStart = onDragStart,
        onDrag = onDrag,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
    )
    Modifier.penInput(key = key) { event -> handler(event) }
}

private object PenGesturesKey

private val AlwaysEnabled: () -> Boolean = { true }
private val NeverForceDrag: () -> Boolean = { false }
private val NoOpEventOffset: (PenEvent, Offset) -> Unit = { _, _ -> }
private val NoOpTool: (PenTool) -> Unit = { }
private val NoOpUnit: () -> Unit = { }
