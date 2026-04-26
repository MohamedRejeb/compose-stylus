package com.mohamedrejeb.stylus

import android.view.MotionEvent
import android.view.View
import java.util.concurrent.ConcurrentHashMap

/**
 * Android implementation of [PenInputSource].
 *
 * Backed by [View.setOnTouchListener] / [View.setOnHoverListener] which observe
 * [MotionEvent]s with `TOOL_TYPE_STYLUS` / `TOOL_TYPE_ERASER`.
 *
 * Most consumers should prefer `Modifier.penInput { }` from `:stylus-compose`
 * — Compose's `PointerInputChange` already exposes pressure, tilt, and tool type.
 */
actual class PenInputSource private constructor() {

    private val attachments = ConcurrentHashMap<Pair<PenEventCallback, View>, AndroidAttachment>()

    actual fun attach(callback: PenEventCallback, host: Any) {
        require(host is View) { "Android PenInputSource requires host: View, was ${host::class}" }
        val key = callback to host
        if (attachments.containsKey(key)) return
        val attachment = AndroidAttachment(callback)
        host.setOnTouchListener(attachment)
        host.setOnHoverListener(attachment)
        attachments[key] = attachment
    }

    actual fun detach(callback: PenEventCallback, host: Any) {
        require(host is View) { "Android PenInputSource requires host: View, was ${host::class}" }
        val key = callback to host
        attachments.remove(key) ?: return
        host.setOnTouchListener(null)
        host.setOnHoverListener(null)
    }

    actual fun setEnabled(callback: PenEventCallback, enabled: Boolean) {
        attachments
            .filterKeys { it.first === callback }
            .values
            .forEach { it.enabled = enabled }
    }

    actual fun dispose() {
        attachments.clear()
    }

    actual companion object {
        private val INSTANCE: PenInputSource by lazy { PenInputSource() }
        actual val Default: PenInputSource get() = INSTANCE
    }
}

private class AndroidAttachment(
    private val callback: PenEventCallback,
) : View.OnTouchListener, View.OnHoverListener {

    @Volatile var enabled: Boolean = true

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!enabled) return false
        return dispatch(event)
    }

    override fun onHover(v: View, event: MotionEvent): Boolean {
        if (!enabled) return false
        return dispatch(event)
    }

    private fun dispatch(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        if (toolType != MotionEvent.TOOL_TYPE_STYLUS && toolType != MotionEvent.TOOL_TYPE_ERASER) {
            return false
        }
        val type = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> PenEventType.Press
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> PenEventType.Release
            MotionEvent.ACTION_MOVE -> PenEventType.Move
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_EXIT,
            MotionEvent.ACTION_HOVER_MOVE -> PenEventType.Hover
            else -> return false
        }
        callback.onEvent(event.toPenEvent(type))
        return true
    }
}

private fun MotionEvent.toPenEvent(type: PenEventType): PenEvent {
    val tool = when (getToolType(0)) {
        MotionEvent.TOOL_TYPE_ERASER -> PenTool.Eraser
        MotionEvent.TOOL_TYPE_STYLUS -> PenTool.Pen
        else -> PenTool.Mouse
    }
    val button = when {
        actionMasked == MotionEvent.ACTION_DOWN ||
            actionMasked == MotionEvent.ACTION_MOVE -> PenButton.Primary
        else -> PenButton.None
    }
    return PenEvent(
        type = type,
        tool = tool,
        button = button,
        x = x.toDouble(),
        y = y.toDouble(),
        pressure = getPressure(0).toDouble(),
        tiltX = getAxisValue(MotionEvent.AXIS_TILT, 0).toDouble(),
        rotation = getAxisValue(MotionEvent.AXIS_ORIENTATION, 0).toDouble(),
        timestamp = eventTime,
    )
}
