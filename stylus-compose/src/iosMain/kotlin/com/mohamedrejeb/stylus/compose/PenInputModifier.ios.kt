package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.Modifier
import com.mohamedrejeb.stylus.PenEvent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import platform.UIKit.UIEvent
import platform.UIKit.UITouch
import platform.UIKit.UITouchTypeStylus

internal actual fun platformPenInputModifier(
    key: Any,
    onEvent: (PenEvent) -> Unit,
): Modifier = pointerPenInputModifier(
    key = key,
    enrich = { penEvent, pointerEvent, _ ->
        // `PointerInputChange` doesn't carry tilt, but the source `UIEvent`
        // (passed through as `nativeEvent` by Compose's iOS scene mediator)
        // does. Find the active Pencil touch and convert its polar
        // (altitudeAngle, azimuthAngleInView) into the Cartesian
        // (tiltX, tiltY) shape `PenEvent` uses so calligraphy rendering
        // downstream stays platform-neutral. We pick the first stylus
        // touch — multi-Pencil sessions aren't a real product use case
        // for this library and matching by `change.id` would require
        // hashing every UITouch, which we want to avoid in the hot path.
        val uiEvent = pointerEvent.nativeEvent as? UIEvent
            ?: return@pointerPenInputModifier penEvent
        val touches = uiEvent.allTouches() ?: return@pointerPenInputModifier penEvent
        var stylus: UITouch? = null
        for (obj in touches) {
            val touch = obj as? UITouch ?: continue
            if (touch.type == UITouchTypeStylus) {
                stylus = touch
                break
            }
        }
        val touch = stylus ?: return@pointerPenInputModifier penEvent
        // altitudeAngle: 0 (parallel to surface) → π/2 (perpendicular).
        // tilt magnitude is the complement, clamped to non-negative since
        // values past perpendicular have no physical meaning here.
        val tiltMag = (PI / 2.0 - touch.altitudeAngle).coerceAtLeast(0.0)
        val azimuth = touch.azimuthAngleInView(null)
        penEvent.copy(
            tiltX = tiltMag * cos(azimuth),
            tiltY = tiltMag * sin(azimuth),
        )
    },
    onEvent = onEvent,
)
