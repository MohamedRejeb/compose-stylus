package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import com.mohamedrejeb.stylus.PenButton
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType
import com.mohamedrejeb.stylus.PenTool

/**
 * Translate a Compose [PointerInputChange] into a [PenEvent].
 *
 * Used by the Android / iOS / Web actual implementations of `Modifier.penInput`
 * since on those platforms Compose's pointer pipeline already exposes pressure
 * and pointer type natively (forwarded from MotionEvent / UITouch / PointerEvent).
 */
internal fun PointerInputChange.toPenEvent(type: PenEventType): PenEvent {
    val tool = when (this.type) {
        PointerType.Stylus -> PenTool.Pen
        PointerType.Eraser -> PenTool.Eraser
        PointerType.Touch -> PenTool.Touch
        PointerType.Mouse -> PenTool.Mouse
        else -> PenTool.None
    }
    val button = if (pressed) PenButton.Primary else PenButton.None
    return PenEvent(
        type = type,
        tool = tool,
        button = button,
        x = position.x.toDouble(),
        y = position.y.toDouble(),
        pressure = pressure.toDouble(),
        timestamp = uptimeMillis,
    )
}
