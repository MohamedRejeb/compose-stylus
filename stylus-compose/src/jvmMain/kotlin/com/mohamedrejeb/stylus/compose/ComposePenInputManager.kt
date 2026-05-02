package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.mohamedrejeb.stylus.PenButton
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventCallback
import com.mohamedrejeb.stylus.PenEventType
import com.mohamedrejeb.stylus.PenInputSource
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM-only bridge between Compose modifier nodes and the native [PenInputSource].
 *
 * Tracks per-modifier (`key`) component state — top-left and size — so that
 * raw screen-space pen events from the native layer can be filtered to the
 * subset that fall inside the modifier's bounds, and forwarded to the user's
 * callback in component-local coordinates.
 */
internal class ComposePenInputManager private constructor() {

    private val states = ConcurrentHashMap<Any, ComponentState>()

    var windowTopLeft: Offset = Offset.Zero

    /**
     * Register [callback] under [key] in the manager's state map. Must run
     * synchronously from the modifier's `onAttach` — *before* the modifier's
     * `onPlaced`/`onRemeasured` would otherwise fire on the same EDT cycle
     * and silently no-op `updateTopLeft`/`updateSize` because the state
     * entry didn't exist yet. Pure in-memory work; safe to call off-EDT.
     */
    fun registerState(key: Any, callback: PenEventCallback) {
        val state = ComponentState()
        state.delegate = callback
        state.callback = ComponentScopedCallback(key, callback)
        states[key] = state
    }

    /**
     * Wire the previously [registerState]-ed callback to the native pen
     * source on [window]. Must run on the EDT — `PenInputSource.attach`
     * pokes platform input pipelines (Cocoa/X11/Win32) and is not thread-safe.
     */
    fun attachNative(key: Any, window: Window) {
        val state = states[key] ?: return
        val callback = state.callback ?: return
        PenInputSource.Default.attach(callback, window)
    }

    fun detach(key: Any, window: Window) {
        val state = states.remove(key) ?: return
        val callback = state.callback ?: return
        PenInputSource.Default.detach(callback, window)
    }

    fun setEnabled(key: Any, enabled: Boolean) {
        val state = states[key] ?: return
        val callback = state.callback ?: return
        PenInputSource.Default.setEnabled(callback, enabled)
    }

    fun updateTopLeft(key: Any, topLeft: Offset) {
        states[key]?.topLeft = topLeft
    }

    fun updateSize(key: Any, size: Size) {
        states[key]?.size = size
    }

    /**
     * Translate a native [PenEvent] (window-content-local, top-left origin,
     * produced by the native JNI bridge) into component-local coordinates:
     *
     *   componentLocal = nativeUnits − modifier.positionInWindow
     *
     * Both terms come from the same Compose Desktop layout space — `nativePx`
     * after the JNI's `* backingScaleFactor` matches what `positionInWindow()`
     * returns — so a direct subtraction yields component-local coordinates
     * Compose's DrawScope and pointer modifiers expect, with no density
     * division required (this matches the `returnValuesInPixel = true` mode
     * in the upstream stylus-compose port).
     *
     * Returns null if state for [key] hasn't been registered yet (which would
     * mean we shouldn't be dispatching anyway).
     */
    private fun translateToComponent(key: Any, event: PenEvent): PenEvent? {
        val state = states[key] ?: return null
        val xDp = event.x - state.topLeft.x
        val yDp = event.y - state.topLeft.y
        return event.translate(xDp, yDp)
    }

    private fun containsTranslated(key: Any, translated: PenEvent): Boolean {
        val state = states[key] ?: return false
        return translated.x in 0.0..state.size.width.toDouble() &&
            translated.y in 0.0..state.size.height.toDouble()
    }

    /**
     * Wrapper that translates each native event into component-local DPs and
     * tracks per-component "currently pressed" state so a drag that starts
     * inside the bounds keeps reporting until release, even if the cursor
     * leaves the component's bounds mid-stroke.
     */
    private inner class ComponentScopedCallback(
        private val key: Any,
        private val delegate: PenEventCallback,
    ) : PenEventCallback {
        private var pressed: Boolean = false

        override fun onEvent(event: PenEvent) {
            when (event.type) {
                PenEventType.Hover -> {
                    val translated = translateToComponent(key, event) ?: return
                    if (containsTranslated(key, translated)) delegate.onEvent(translated)
                }
                PenEventType.Move -> {
                    // Filter spurious "button down but callback wasn't the click target" events.
                    if (event.button != PenButton.None && !pressed) return
                    val translated = translateToComponent(key, event) ?: return
                    if (pressed || containsTranslated(key, translated)) {
                        delegate.onEvent(translated)
                    }
                }
                PenEventType.Press -> {
                    val translated = translateToComponent(key, event) ?: return
                    if (containsTranslated(key, translated)) {
                        pressed = true
                        delegate.onEvent(translated)
                    }
                }
                PenEventType.Release -> {
                    if (!pressed) return
                    pressed = false
                    val translated = translateToComponent(key, event) ?: return
                    delegate.onEvent(translated)
                }
            }
        }
    }

    // ─── Test-only entry points ─────────────────────────────────────────
    //
    // These let `:stylus-compose` integration tests drive the same callback
    // pipeline real native events take (translateToComponent → bounds filter
    // → ComponentScopedCallback → user callback) without depending on the
    // JNI swizzle, an actual NSEvent stream, or a visible AWT window — none
    // of which run reliably in a headless CI JVM test. Use [registerState]
    // to set up the per-key callback then [dispatchSynthetic] to inject events.

    /**
     * Inject [event] into the per-[key] callback pipeline as if it had been
     * dispatched from the native side. Coordinates should be in the same
     * space the native bridge produces: window-content-local **physical
     * pixels**, top-left origin.
     */
    internal fun dispatchSynthetic(key: Any, event: PenEvent) {
        val state = states[key] ?: return
        val callback = state.callback ?: return
        callback.onEvent(event)
    }

    /** Drop all per-key state. Used by tests to isolate cases. */
    internal fun resetForTest() {
        states.clear()
    }

    companion object {
        init {
            // Force AWT's JNI bridge (libjawt) to load before libstylus installs
            // the NSApplication swizzle — needed on JVMs where libjawt isn't
            // pulled in early enough on its own.
            runCatching { System.loadLibrary("jawt") }
                .onFailure { System.err.println("[stylus] could not load jawt: ${it.message}") }
        }

        val instance: ComposePenInputManager by lazy { ComposePenInputManager() }
    }
}

internal class ComponentState {
    var callback: PenEventCallback? = null
    var delegate: PenEventCallback? = null
    var topLeft: Offset = Offset.Zero
    var size: Size = Size.Zero
}
