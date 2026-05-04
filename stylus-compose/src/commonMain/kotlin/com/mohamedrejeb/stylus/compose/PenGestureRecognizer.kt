package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.geometry.Offset
import com.mohamedrejeb.stylus.PenButton
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType
import com.mohamedrejeb.stylus.PenTool

/**
 * Pure state machine that translates the raw [PenEvent] stream produced by
 * [Modifier.penInput] into higher-level gesture callbacks (click, double-click,
 * right-click, drag, hover, tool-change).
 *
 * Kept free of Compose's Modifier / pointer pipeline so it can be unit-tested
 * in `commonTest`. [Modifier.penGestures] simply pumps events into an instance
 * of this class.
 *
 * Mirrors the historical `Modifier.stylusListener` recognizer used by
 * Annodoc-class consumers: identical click-vs-drag heuristic, identical
 * "click also fires after a forced-drag tap" workaround, identical double-click
 * counting (first tap fires `onClick`, second fires `onDoubleClick`).
 */
internal class PenGestureRecognizer(
    private val config: PenGesturesConfig,
    private val isEnabled: () -> Boolean,
    private val forceDragOnPress: () -> Boolean,
    private val onClick: (PenEvent, Offset) -> Unit,
    private val onDoubleClick: (PenEvent, Offset) -> Unit,
    private val onRightClick: (PenEvent, Offset) -> Unit,
    private val onHover: (PenEvent, Offset) -> Unit,
    private val onToolChange: (PenTool) -> Unit,
    private val onDragStart: (PenEvent, Offset) -> Unit,
    private val onDrag: (PenEvent, Offset) -> Unit,
    private val onDragEnd: (PenEvent, Offset) -> Unit,
    private val onDragCancel: () -> Unit,
) {
    private var pressEvent: PenEvent? = null
    private var pressOffset: Offset = Offset.Zero
    private var lastUpTime: Long = 0L
    private var lastUpOffset: Offset = Offset.Zero
    private var clickCount: Int = 0
    private var isDragging: Boolean = false
    private var lastTool: PenTool? = null

    fun onPenEvent(event: PenEvent) {
        // Tool changes fire regardless of `isEnabled` so the consumer can
        // still react to a hardware flip (pen ↔ eraser tip) while the surface
        // is logically gated. Mirrors the legacy `onCursorChange` behavior.
        if (lastTool != event.tool) {
            lastTool = event.tool
            onToolChange(event.tool)
        }

        if (!isEnabled()) {
            cancel()
            return
        }

        val offset = Offset(event.x.toFloat(), event.y.toFloat())

        when (event.type) {
            PenEventType.Press -> handlePress(event, offset)
            PenEventType.Move -> handleMove(event, offset)
            PenEventType.Release -> handleRelease(event, offset)
            PenEventType.Hover -> onHover(event, offset)
        }
    }

    fun cancel() {
        if (isDragging) {
            onDragCancel()
        }
        clickCount = 0
        pressEvent = null
        isDragging = false
    }

    private fun handlePress(event: PenEvent, offset: Offset) {
        // A Secondary press is bookkept as a right-click that fires on
        // Release (matches the legacy contract), so press-time arming only
        // happens for the Primary button.
        if (event.button != PenButton.Primary) {
            pressEvent = event
            pressOffset = offset
            return
        }

        val withinDoubleClickWindow =
            (event.timestamp - lastUpTime) < config.doubleClickWindowMillis
        val withinDoubleClickSlop =
            (offset - lastUpOffset).getDistanceSquared() < tapSlopSquared()

        clickCount = if (withinDoubleClickWindow && withinDoubleClickSlop) clickCount + 1 else 1

        pressEvent = event
        pressOffset = offset
        isDragging = forceDragOnPress()

        if (isDragging) {
            onDragStart(event, offset)
            clickCount = 0
        }
    }

    private fun handleMove(event: PenEvent, offset: Offset) {
        val press = pressEvent ?: return
        if (press.button != PenButton.Primary && !isDragging) return

        val crossedDragSlop =
            (offset - pressOffset).getDistanceSquared() > dragSlopSquared()

        if (!isDragging && crossedDragSlop) {
            isDragging = true
            onDragStart(press, pressOffset)
            clickCount = 0
        }

        if (isDragging) {
            onDrag(event, offset)
        }
    }

    private fun handleRelease(event: PenEvent, offset: Offset) {
        // Some platforms drop the button id on Release (the input device
        // reports a generic "lift"); fall back to whatever the matching Press
        // recorded. Mirrors the legacy fallback to `state.startEvent.button`.
        val press = pressEvent
        val effectiveButton = when {
            event.button != PenButton.None -> event.button
            press != null -> press.button
            else -> PenButton.None
        }

        when (effectiveButton) {
            PenButton.Primary -> handlePrimaryRelease(event, offset, press)
            PenButton.Secondary -> {
                onRightClick(event, offset)
                pressEvent = null
                isDragging = false
            }
            PenButton.None,
            PenButton.Tertiary -> {
                pressEvent = null
                isDragging = false
            }
        }
    }

    private fun handlePrimaryRelease(event: PenEvent, offset: Offset, press: PenEvent?) {
        if (isDragging) {
            onDragEnd(event, offset)
            clickCount = 0

            // Forced-drag tap workaround: when `forceDragOnPress` arms a drag
            // immediately on Press, a quick tap-and-lift would otherwise look
            // like an empty drag. If it stays inside tap timeout + tap slop,
            // surface it as a click *in addition* to the dragEnd, so consumers
            // (e.g. a pencil tool that wants single-tap = dot stamp) still
            // hear it.
            if (
                press != null &&
                (event.timestamp - press.timestamp) < config.tapTimeoutMillis &&
                (offset - pressOffset).getDistanceSquared() < tapSlopSquared()
            ) {
                onClick(event, offset)
            }
        } else {
            if (clickCount == 2) {
                onDoubleClick(event, offset)
                clickCount = 0
            } else {
                onClick(event, offset)
            }
        }

        lastUpTime = event.timestamp
        lastUpOffset = offset
        isDragging = false
        pressEvent = null
    }

    private fun tapSlopSquared(): Float = config.tapSlopPx * config.tapSlopPx

    private fun dragSlopSquared(): Float = config.dragSlopPx * config.dragSlopPx
}
