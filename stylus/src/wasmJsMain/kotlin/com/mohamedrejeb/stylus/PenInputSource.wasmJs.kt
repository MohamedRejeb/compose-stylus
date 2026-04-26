@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.mohamedrejeb.stylus

import org.w3c.dom.HTMLElement

/**
 * Web (wasmJs) implementation of [PenInputSource].
 *
 * Backed by `addEventListener("pointer*")` on a [HTMLElement] host. Events
 * arriving with `pointerType == "pen"`, `"touch"`, or `"mouse"` are forwarded
 * to the registered [PenEventCallback] as [PenEvent]s carrying the DOM
 * `PointerEvent`'s pressure / tilt / offset fields.
 *
 * Most consumers should prefer `Modifier.penInput { }` from `:stylus-compose`
 * — it routes through Compose's `PointerInputChange` which already carries the
 * same data with no JS-interop bridge in the way.
 */
actual class PenInputSource private constructor() {

    private val attachments = mutableMapOf<Pair<PenEventCallback, HTMLElement>, WebAttachment>()

    actual fun attach(callback: PenEventCallback, host: Any) {
        require(host is HTMLElement) {
            "Web PenInputSource requires host: HTMLElement, was ${host::class}"
        }
        val key = callback to host
        if (attachments.containsKey(key)) return
        val attachment = WebAttachment(callback, host).also { it.install() }
        attachments[key] = attachment
    }

    actual fun detach(callback: PenEventCallback, host: Any) {
        require(host is HTMLElement) {
            "Web PenInputSource requires host: HTMLElement, was ${host::class}"
        }
        val attachment = attachments.remove(callback to host) ?: return
        attachment.uninstall()
    }

    actual fun setEnabled(callback: PenEventCallback, enabled: Boolean) {
        attachments
            .filterKeys { it.first === callback }
            .values
            .forEach { it.enabled = enabled }
    }

    actual fun dispose() {
        attachments.values.forEach { it.uninstall() }
        attachments.clear()
    }

    actual companion object {
        private val INSTANCE: PenInputSource by lazy { PenInputSource() }
        actual val Default: PenInputSource get() = INSTANCE
    }
}

/**
 * Per-host DOM listener bundle. Uses a small JS interop bridge (`pointerEventInfo`)
 * to extract PointerEvent fields, because the `org.w3c.dom.events.PointerEvent`
 * binding shipped with kotlin-stdlib's wasmJs target doesn't expose pressure /
 * tilt / pointerType yet (as of Kotlin 2.2.x).
 */
private class WebAttachment(
    private val callback: PenEventCallback,
    private val host: HTMLElement,
) {
    var enabled: Boolean = true

    private val onDown: (JsAny) -> Unit = { ev ->
        if (enabled) deliver(ev, PenEventType.Press)
    }
    private val onMove: (JsAny) -> Unit = { ev ->
        if (enabled) {
            val event = pointerEventInfo(ev).toPenEvent(PenEventType.Move)
            // While not pressed, report Move events as Hover so callers can distinguish
            // contact-vs-hover cleanly without inspecting `button`.
            if (event.button == PenButton.None) {
                callback.onEvent(event.copy(type = PenEventType.Hover))
            } else {
                callback.onEvent(event)
            }
        }
    }
    private val onUp: (JsAny) -> Unit = { ev ->
        if (enabled) deliver(ev, PenEventType.Release)
    }
    private val onLeave: (JsAny) -> Unit = { ev ->
        if (enabled) deliver(ev, PenEventType.Hover)
    }

    fun install() {
        addPointerListener(host, "pointerdown", onDown)
        addPointerListener(host, "pointermove", onMove)
        addPointerListener(host, "pointerup", onUp)
        addPointerListener(host, "pointercancel", onUp)
        addPointerListener(host, "pointerleave", onLeave)
        addPointerListener(host, "pointerout", onLeave)
    }

    fun uninstall() {
        removePointerListener(host, "pointerdown", onDown)
        removePointerListener(host, "pointermove", onMove)
        removePointerListener(host, "pointerup", onUp)
        removePointerListener(host, "pointercancel", onUp)
        removePointerListener(host, "pointerleave", onLeave)
        removePointerListener(host, "pointerout", onLeave)
    }

    private fun deliver(ev: JsAny, type: PenEventType) {
        callback.onEvent(pointerEventInfo(ev).toPenEvent(type))
    }
}

/**
 * Plain data carrier for a DOM PointerEvent's fields, populated from JS via
 * [pointerEventInfo]. Avoids depending on any wasmJs PointerEvent binding.
 */
private class PointerEventInfo(
    val pointerType: String,
    val buttons: Int,
    val pressure: Double,
    val tangentialPressure: Double,
    val tiltX: Double,
    val tiltY: Double,
    val twist: Double,
    val offsetX: Double,
    val offsetY: Double,
    val timeStamp: Double,
)

private fun PointerEventInfo.toPenEvent(type: PenEventType): PenEvent {
    val tool = when (pointerType) {
        "pen" -> PenTool.Pen
        "touch" -> PenTool.Touch
        "mouse" -> PenTool.Mouse
        else -> PenTool.None
    }
    val button = if (buttons != 0) PenButton.Primary else PenButton.None
    return PenEvent(
        type = type,
        tool = tool,
        button = button,
        x = offsetX,
        y = offsetY,
        pressure = pressure,
        tangentPressure = tangentialPressure,
        tiltX = tiltX,
        tiltY = tiltY,
        rotation = twist,
        timestamp = timeStamp.toLong(),
    )
}

// ─── JS interop bridge ──────────────────────────────────────────────────────

// Drop down to JsAny for the JS-interop boundary; only `external`, primitive,
// String, and function types are allowed across the wasmJs ↔ JS bridge, so
// HTMLElement (which is a Kotlin class wrapper, not a primitive `external`)
// can't be a parameter of a `js("…")` function.
private fun addPointerListener(target: HTMLElement, type: String, callback: (JsAny) -> Unit) {
    addEventListenerJs(target as JsAny, type, callback)
}

private fun removePointerListener(target: HTMLElement, type: String, callback: (JsAny) -> Unit) {
    removeEventListenerJs(target as JsAny, type, callback)
}

private fun addEventListenerJs(target: JsAny, type: String, callback: (JsAny) -> Unit): Unit =
    js("target.addEventListener(type, callback)")

private fun removeEventListenerJs(target: JsAny, type: String, callback: (JsAny) -> Unit): Unit =
    js("target.removeEventListener(type, callback)")

private fun pointerEventInfo(event: JsAny): PointerEventInfo {
    return PointerEventInfo(
        pointerType = readPointerType(event),
        buttons = readButtons(event),
        pressure = readPressure(event),
        tangentialPressure = readTangentialPressure(event),
        tiltX = readTiltX(event),
        tiltY = readTiltY(event),
        twist = readTwist(event),
        offsetX = readOffsetX(event),
        offsetY = readOffsetY(event),
        timeStamp = readTimeStamp(event),
    )
}

private fun readPointerType(e: JsAny): String = js("e.pointerType || ''")
private fun readButtons(e: JsAny): Int = js("e.buttons | 0")
private fun readPressure(e: JsAny): Double = js("e.pressure || 0")
private fun readTangentialPressure(e: JsAny): Double = js("e.tangentialPressure || 0")
private fun readTiltX(e: JsAny): Double = js("e.tiltX || 0")
private fun readTiltY(e: JsAny): Double = js("e.tiltY || 0")
private fun readTwist(e: JsAny): Double = js("e.twist || 0")
private fun readOffsetX(e: JsAny): Double = js("e.offsetX || 0")
private fun readOffsetY(e: JsAny): Double = js("e.offsetY || 0")
private fun readTimeStamp(e: JsAny): Double = js("e.timeStamp || 0")
