package com.mohamedrejeb.stylus

import com.mohamedrejeb.stylus.internal.NativeLoader
import java.util.concurrent.ConcurrentHashMap

actual class PenInputSource private constructor() {

    private val wrappers = ConcurrentHashMap<PenEventCallback, JvmPenEventCallbackWrapper>()

    init {
        Runtime.getRuntime().addShutdownHook(Thread(::dispose))
    }

    actual fun attach(callback: PenEventCallback, host: Any) {
        val wrapper = wrappers.computeIfAbsent(callback) { JvmPenEventCallbackWrapper(callback) }
        attachCallback(wrapper, host)
    }

    actual fun detach(callback: PenEventCallback, host: Any) {
        val wrapper = wrappers.remove(callback) ?: return
        detachCallback(wrapper, host)
    }

    actual fun setEnabled(callback: PenEventCallback, enabled: Boolean) {
        val wrapper = wrappers[callback] ?: return
        setCallbackEnabled(wrapper, enabled)
    }

    actual fun dispose() {
        runCatching { destroy() }
    }

    private external fun destroy()
    private external fun attachCallback(callback: PenEventCallback, window: Any)
    private external fun detachCallback(callback: PenEventCallback, window: Any)
    private external fun setCallbackEnabled(callback: PenEventCallback, enable: Boolean)

    actual companion object {
        init {
            NativeLoader.loadLibrary("stylus")
        }

        private val INSTANCE: PenInputSource by lazy { PenInputSource() }

        actual val Default: PenInputSource get() = INSTANCE
    }
}

/**
 * Internal wrapper around a user-supplied [PenEventCallback]. The native code
 * stores its per-callback pointer in [nativeHandle] (via JNI SetHandle/GetHandle),
 * so we can't just hand the user's callback directly to the native side without
 * polluting their class with that field.
 */
internal class JvmPenEventCallbackWrapper(
    private val delegate: PenEventCallback,
) : PenEventCallback {

    // @JvmField pins this to a plain Java field literally named `nativeHandle`
    // with type J. The C++ side reads/writes it via `env->GetFieldID(cls,
    // "nativeHandle", "J")` to associate per-callback state across the JNI
    // boundary. Anything else (Kotlin private property, internal-name-mangled
    // accessor, etc.) breaks that lookup silently.
    @JvmField
    var nativeHandle: Long = 0L

    override fun onEvent(event: PenEvent) = delegate.onEvent(event)
}
