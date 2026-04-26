package com.mohamedrejeb.stylus

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIEvent
import platform.UIKit.UITouch
import platform.UIKit.UITouchTypeDirect
import platform.UIKit.UITouchTypeStylus
import platform.UIKit.UIView
import kotlin.math.PI

/**
 * iOS implementation of [PenInputSource].
 *
 * Programmatic non-Compose use: the supplied `host` must be a [PenInputView] —
 * adding it to your UIView hierarchy and routing touches through it lets us
 * observe pen / finger events without colliding with Kotlin/Native's lack of
 * public bindings for `UIGestureRecognizerSubclass.h`.
 *
 * Most consumers should use `Modifier.penInput { }` from `:stylus-compose`
 * instead — that path already feeds off Compose's `PointerInputChange`, which
 * carries pressure / tilt / pointer type forwarded from the underlying [UITouch].
 */
@OptIn(ExperimentalForeignApi::class)
actual class PenInputSource private constructor() {

    private val attachments = mutableMapOf<Pair<PenEventCallback, PenInputView>, Unit>()

    actual fun attach(callback: PenEventCallback, host: Any) {
        require(host is PenInputView) {
            "iOS PenInputSource requires host: PenInputView (a UIView subclass " +
                "exposed by this library). Add a PenInputView to your hierarchy " +
                "and pass it as the host. Was: ${host::class}"
        }
        host.callback = callback
        host.enabled = true
        attachments[callback to host] = Unit
    }

    actual fun detach(callback: PenEventCallback, host: Any) {
        require(host is PenInputView) {
            "iOS PenInputSource requires host: PenInputView, was ${host::class}"
        }
        host.callback = null
        attachments.remove(callback to host)
    }

    actual fun setEnabled(callback: PenEventCallback, enabled: Boolean) {
        attachments.keys
            .filter { it.first === callback }
            .forEach { it.second.enabled = enabled }
    }

    actual fun dispose() {
        attachments.keys.forEach { it.second.callback = null }
        attachments.clear()
    }

    actual companion object {
        private val INSTANCE: PenInputSource by lazy { PenInputSource() }
        actual val Default: PenInputSource get() = INSTANCE
    }
}

/**
 * UIView subclass that surfaces pen events to a [PenEventCallback].
 *
 * Override of `touchesBegan/Moved/Ended/Cancelled` works on UIView (UIResponder
 * methods are publicly overridable), unlike on UIGestureRecognizer where the
 * subclass header isn't part of the default Kotlin/Native binding.
 *
 * Add an instance of this view to your iOS UI, size it over the input area
 * you want to track, and pass it to [PenInputSource.attach] as the host.
 * `userInteractionEnabled` is on by default.
 */
@OptIn(ExperimentalForeignApi::class)
class PenInputView : UIView {

    constructor() : super(frame = CGRectZero.readValue())

    @Suppress("MemberVisibilityCanBePrivate")
    var callback: PenEventCallback? = null

    @Suppress("MemberVisibilityCanBePrivate")
    var enabled: Boolean = true

    override fun touchesBegan(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesBegan(touches, withEvent)
        if (!enabled) return
        forEachTouch(touches) {
            callback?.onEvent(it.toPenEvent(this, PenEventType.Press, PenButton.Primary))
        }
    }

    override fun touchesMoved(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesMoved(touches, withEvent)
        if (!enabled) return
        forEachTouch(touches) {
            callback?.onEvent(it.toPenEvent(this, PenEventType.Move, PenButton.Primary))
        }
    }

    override fun touchesEnded(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesEnded(touches, withEvent)
        if (!enabled) return
        forEachTouch(touches) {
            callback?.onEvent(it.toPenEvent(this, PenEventType.Release, PenButton.None))
        }
    }

    override fun touchesCancelled(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesCancelled(touches, withEvent)
        if (!enabled) return
        forEachTouch(touches) {
            callback?.onEvent(it.toPenEvent(this, PenEventType.Release, PenButton.None))
        }
    }

    private inline fun forEachTouch(touches: Set<*>, block: (UITouch) -> Unit) {
        for (touch in touches) {
            if (touch is UITouch) block(touch)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UITouch.toPenEvent(
    view: UIView?,
    type: PenEventType,
    button: PenButton,
): PenEvent {
    val location = locationInView(view).useContents { x to y }
    val tool = when (this.type) {
        UITouchTypeStylus -> PenTool.Pen
        UITouchTypeDirect -> PenTool.Touch
        else -> PenTool.Mouse
    }

    val maxForce = if (maximumPossibleForce > 0.0) maximumPossibleForce else 1.0
    val pressure = (force / maxForce).coerceIn(0.0, 1.0)

    // iOS reports tilt as polar coords:
    //   altitudeAngle: 0 (parallel to surface) → π/2 (perpendicular)
    //   azimuthAngle:  0..2π (compass direction)
    // Project to a Cartesian (tiltX, tiltY) tilt magnitude in radians.
    val tiltMagnitude = (PI / 2.0) - altitudeAngle
    val azimuth = azimuthAngleInView(view)
    val tiltX = tiltMagnitude * kotlin.math.cos(azimuth)
    val tiltY = tiltMagnitude * kotlin.math.sin(azimuth)

    return PenEvent(
        type = type,
        tool = tool,
        button = button,
        x = location.first,
        y = location.second,
        pressure = pressure,
        tiltX = tiltX,
        tiltY = tiltY,
        timestamp = (timestamp * 1000.0).toLong(),
    )
}
