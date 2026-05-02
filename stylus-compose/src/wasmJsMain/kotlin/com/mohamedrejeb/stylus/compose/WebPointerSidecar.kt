@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.mohamedrejeb.stylus.compose

import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenTool
import kotlinx.browser.document

/**
 * Web-only sidecar that fills in the pen telemetry Compose's wasmJs pointer
 * pipeline drops on the floor — and recovers the high-frequency stylus
 * samples the browser hides behind `pointermove` coalescing.
 *
 * Two responsibilities:
 *
 * 1. **Augmentation** — Compose Multiplatform on wasmJs only listens to
 *    `mouse*` / `touch*` events (see `ComposeWindow.w3c.kt`), so for a stylus
 *    the `PointerInputChange` we receive carries a default mouse pressure
 *    (~1.0 when pressed) and a `Mouse` pointer-type discriminator. Real pen
 *    pressure, tilt, and the `pen / touch / mouse` distinction only live on
 *    the DOM `PointerEvent`. The sidecar attaches capture-phase listeners on
 *    `document` that run **before** the synthesized `mouse*` event Compose
 *    listens to, stashing the latest values. The wasmJs
 *    `platformPenInputModifier` calls [augment] on each event to overwrite
 *    pressure / tilt / tool with the real values.
 *
 * 2. **Coalesced sample emission** — browsers cap `pointermove` dispatch to
 *    the display refresh rate but accumulate the full hardware sample stream
 *    (240+ Hz on stylus digitisers) inside `getCoalescedEvents()`. Without
 *    these, fast pen strokes look polygonal because every other sample is
 *    missing. Subscribers ([subscribe]) receive a [PointerSample] for each
 *    coalesced sub-event (excluding the last — Compose's mousemove will emit
 *    the final one through the standard pipeline) so the modifier can fan
 *    out synthetic [PenEvent]s, matching what tldraw, excalidraw, and other
 *    drawing apps do.
 */
internal object WebPointerSidecar {
    private var attached: Boolean = false

    private var pressure: Double = 0.0
    private var pointerType: String = ""
    private var tiltX: Double = 0.0
    private var tiltY: Double = 0.0
    private var twist: Double = 0.0

    private val subscribers: MutableList<Subscriber> = mutableListOf()

    fun ensureAttached() {
        if (attached) return
        attached = true
        val target = document.body ?: return
        for (type in PASSIVE_TYPES) {
            addCapturingPointerListener(target as JsAny, type) { ev ->
                updateCache(ev)
            }
        }
        addCapturingPointerListener(target as JsAny, "pointermove") { ev ->
            updateCache(ev)
            dispatchCoalesced(ev)
        }
    }

    fun augment(event: PenEvent): PenEvent {
        val tool = when (pointerType) {
            "pen" -> PenTool.Pen
            "touch" -> PenTool.Touch
            "mouse" -> PenTool.Mouse
            else -> event.tool
        }
        return event.copy(
            tool = tool,
            pressure = pressure,
            tiltX = tiltX,
            tiltY = tiltY,
            rotation = twist,
        )
    }

    fun subscribe(subscriber: Subscriber) {
        subscribers.add(subscriber)
    }

    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
    }

    private fun updateCache(ev: JsAny) {
        pointerType = readPointerType(ev)
        pressure = readPressure(ev)
        tiltX = readTiltX(ev)
        tiltY = readTiltY(ev)
        twist = readTwist(ev)
    }

    /**
     * Walk `getCoalescedEvents()` and forward every pressed sample except the
     * last to subscribers. The last sample is what Compose will dispatch
     * through its `mousemove` listener, so emitting it here would duplicate.
     *
     * **Hover-leak filter** — `getCoalescedEvents()` on a `pointermove`
     * returns the merged samples since the *previous dispatched
     * `pointermove`*, not the previous event of any kind. The first
     * `pointermove` after a `pointerdown` therefore includes the entire
     * hover trajectory leading up to the press in addition to the post-press
     * drag samples. Per MDN those sub-events expose their own per-sample
     * `pressure`, `clientX/Y`, `tilt*` values, but `buttons` inherits from
     * the dispatched event — so a `buttons` filter is useless here, and we
     * key off `pressure == 0` instead. Stylus + mouse hover both report
     * pressure 0; pressed contact reports > 0 (mouse: 0.5, stylus: device
     * value). Without this filter, every new stroke draws a long straight
     * line from its starting point back to wherever the pen was hovering.
     *
     * Skipped on iOS: Safari's coalesced events were unreliable when tldraw
     * tried this in PR #5554 — the API was missing in some embedding
     * contexts and they had to revert + null-check. We do both: feature
     * detection per-call AND a global iOS skip.
     *
     * Also skipped during the outer hover (no buttons pressed) — drawing is
     * the only place 240 Hz matters; spinning up extra event work for cursor
     * moves just burns CPU.
     */
    private fun dispatchCoalesced(ev: JsAny) {
        if (subscribers.isEmpty()) return
        if (isIosWeb()) return
        if (!hasGetCoalescedEvents(ev)) return
        if (readButtons(ev) == 0) return
        val length = coalescedLength(ev)
        if (length <= 1) return
        for (i in 0 until length - 1) {
            val sub = coalescedAt(ev, i)
            // Per-sample pressure — see the hover-leak filter note in the
            // KDoc above. `<= 0` covers both genuinely-not-pressed samples
            // and the rare digitiser report of an exact 0 mid-stroke (the
            // worst case is losing a single point, well within the
            // smoothing tolerance).
            val pressure = readPressure(sub)
            if (pressure <= 0.0) continue
            val sample = PointerSample(
                clientX = readClientX(sub),
                clientY = readClientY(sub),
                pressure = pressure,
                pointerType = readPointerType(sub),
                tiltX = readTiltX(sub),
                tiltY = readTiltY(sub),
                twist = readTwist(sub),
                timeStamp = readTimeStamp(sub),
                pressed = true,
            )
            // Snapshot the subscriber list so a callback that unsubscribes
            // (e.g. on dispose) doesn't ConcurrentModificationException us.
            for (s in subscribers.toList()) s.onCoalescedSample(sample)
        }
    }

    private val PASSIVE_TYPES: List<String> = listOf(
        "pointerdown",
        "pointerup",
        "pointercancel",
    )

    /** Snapshot of a single DOM PointerEvent in viewport coordinates. */
    internal class PointerSample(
        val clientX: Double,
        val clientY: Double,
        val pressure: Double,
        val pointerType: String,
        val tiltX: Double,
        val tiltY: Double,
        val twist: Double,
        val timeStamp: Double,
        val pressed: Boolean,
    )

    internal fun interface Subscriber {
        fun onCoalescedSample(sample: PointerSample)
    }
}

private fun addCapturingPointerListener(
    target: JsAny,
    type: String,
    callback: (JsAny) -> Unit,
): Unit = js("target.addEventListener(type, callback, true)")

private fun readPointerType(e: JsAny): String = js("e.pointerType || ''")
private fun readPressure(e: JsAny): Double = js("e.pressure || 0")
private fun readTiltX(e: JsAny): Double = js("e.tiltX || 0")
private fun readTiltY(e: JsAny): Double = js("e.tiltY || 0")
private fun readTwist(e: JsAny): Double = js("e.twist || 0")
private fun readClientX(e: JsAny): Double = js("e.clientX || 0")
private fun readClientY(e: JsAny): Double = js("e.clientY || 0")
private fun readButtons(e: JsAny): Int = js("e.buttons | 0")
private fun readTimeStamp(e: JsAny): Double = js("e.timeStamp || 0")

private fun hasGetCoalescedEvents(e: JsAny): Boolean =
    js("typeof e.getCoalescedEvents === 'function'")

private fun coalescedLength(e: JsAny): Int =
    js("(e.getCoalescedEvents() || []).length | 0")

private fun coalescedAt(e: JsAny, index: Int): JsAny =
    js("e.getCoalescedEvents()[index]")

private fun isIosWeb(): Boolean =
    js("(function(){try{var ua=navigator.userAgent||'';return /iP(ad|hone|od)/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);}catch(e){return false;}})()")
