package com.mohamedrejeb.stylus

/**
 * Receives [PenEvent]s from a [PenInputSource].
 *
 * Single-method `fun interface` so callers can pass a lambda:
 *
 * ```
 * PenInputSource.Default.attach({ event -> /* … */ }, host)
 * ```
 *
 * The callback's identity (object reference) is the key used by [PenInputSource]
 * to track attachments — pass the same instance to [PenInputSource.detach] /
 * [PenInputSource.setEnabled] later.
 */
fun interface PenEventCallback {
    fun onEvent(event: PenEvent)
}
