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

    fun attach(key: Any, callback: PenEventCallback, window: Window) {
        val state = ComponentState()
        state.delegate = callback
        state.callback = ComponentScopedCallback(key, callback)
        states[key] = state

        // Run synchronously: callers (the modifier's onAttach) are already on
        // the EDT — they wrap the call in SwingUtilities.invokeLater. Adding
        // another nested invokeLater here used to race with the modifier's
        // immediate setEnabled(true) call: setEnabled would run before the
        // wrapper was registered, silently leaving the native callback
        // disabled and dropping every event.
        PenInputSource.Default.attach(state.callback!!, window)
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

    fun updateDensity(key: Any, density: Float) {
        states[key]?.density = density
    }

    /**
     * Translate a native [PenEvent] (window-content-local **physical pixels**,
     * top-left origin — produced by the native JNI bridge) into
     * component-local DP coordinates that match what Compose draws in:
     *
     *   componentDp = (nativePx / density) − modifier.positionInWindow
     *
     * Returns null if state for [key] hasn't been registered yet (which would
     * mean we shouldn't be dispatching anyway).
     */
    private fun translateToComponent(key: Any, event: PenEvent): PenEvent? {
        val state = states[key] ?: return null
        val density = state.density.toDouble().takeIf { it > 0.0 } ?: 1.0
        val xDp = event.x / density - state.topLeft.x
        val yDp = event.y / density - state.topLeft.y
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
    // of which run reliably in a headless CI JVM test.

    /**
     * Register [callback] under [key] without touching [PenInputSource] or the
     * native bridge. After this call, [updateSize], [updateTopLeft],
     * [updateDensity], and [dispatchSynthetic] behave the same as if a real
     * native attach had succeeded.
     */
    internal fun attachWithoutNative(key: Any, callback: PenEventCallback) {
        val state = ComponentState()
        state.delegate = callback
        state.callback = ComponentScopedCallback(key, callback)
        states[key] = state
    }

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
    var density: Float = 1f
}
