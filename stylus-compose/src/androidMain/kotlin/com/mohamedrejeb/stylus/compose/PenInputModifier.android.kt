package com.mohamedrejeb.stylus.compose

import android.view.MotionEvent
import androidx.compose.ui.Modifier
import com.mohamedrejeb.stylus.PenEvent
import kotlin.math.cos
import kotlin.math.sin

internal actual fun platformPenInputModifier(
    key: Any,
    onEvent: (PenEvent) -> Unit,
): Modifier = pointerPenInputModifier(
    key = key,
    enrich = { penEvent, pointerEvent, change ->
        // `PointerInputChange` doesn't carry tilt, but the underlying
        // `MotionEvent` does — `AXIS_TILT` is the lean magnitude in radians,
        // `AXIS_ORIENTATION` is the azimuth around vertical. Convert the
        // polar pair to the Cartesian (tiltX, tiltY) shape `PenEvent` uses
        // so calligraphy / chisel-nib rendering downstream is platform-
        // neutral. The `motionEvent` accessor is documented as transient,
        // so we read the values here and let the [PenEvent] capture them.
        val motionEvent = pointerEvent.motionEvent
            ?: return@pointerPenInputModifier penEvent
        val pointerIndex = motionEvent.findPointerIndex(change.id.value.toInt())
            .takeIf { it >= 0 }
            ?: 0
        val tiltMag = motionEvent.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
        if (tiltMag == 0f) return@pointerPenInputModifier penEvent
        val orientation = motionEvent.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex)
        penEvent.copy(
            tiltX = (tiltMag * cos(orientation)).toDouble(),
            tiltY = (tiltMag * sin(orientation)).toDouble(),
        )
    },
    onEvent = onEvent,
)
