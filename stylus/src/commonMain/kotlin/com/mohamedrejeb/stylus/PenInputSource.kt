package com.mohamedrejeb.stylus

/**
 * Cross-platform source of pen / stylus events.
 *
 * On JVM (Desktop) this is backed by a native shared library loaded through
 * JNI that taps into Cocoa / X11+XInput2 / Windows RTS — that's the only way
 * to get accurate pen pressure and tilt because AWT's standard mouse pipeline
 * strips that data.
 *
 * On Android / iOS / Web, the platform exposes pen axes (pressure, tilt, ...)
 * directly through standard input events; the corresponding actuals are
 * thinner wrappers around `MotionEvent` / `UITouch` / `PointerEvent`.
 *
 * Most consumers should prefer `Modifier.penInput { }` from the `:stylus-compose`
 * module — that hides the host-attachment plumbing and routes through Compose's
 * own pointer pipeline on every target except Desktop.
 */
expect class PenInputSource {

    /**
     * Attach [callback] so it receives events while [host] is visible and focused.
     *
     * `host` is the platform-specific surface to observe:
     * - Desktop: `java.awt.Window` (typically the `ComposeWindow`)
     * - Android: `android.view.View` (the Compose host view)
     * - iOS: a `UIView` (specifically a `PenInputView` exposed by the iOS actual)
     * - Web: an `HTMLElement` (e.g. the `<canvas>` Compose renders into)
     */
    fun attach(callback: PenEventCallback, host: Any)

    /** Detach a previously [attach]ed callback from [host]. */
    fun detach(callback: PenEventCallback, host: Any)

    /** Toggle a previously attached callback on/off without unregistering it. */
    fun setEnabled(callback: PenEventCallback, enabled: Boolean)

    /** Release all native resources. Idempotent. */
    fun dispose()

    companion object {
        /** Process-wide default [PenInputSource]. */
        val Default: PenInputSource
    }
}
