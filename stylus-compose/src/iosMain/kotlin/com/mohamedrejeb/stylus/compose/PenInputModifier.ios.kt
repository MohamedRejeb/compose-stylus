package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.Modifier
import com.mohamedrejeb.stylus.PenEvent

internal actual fun platformPenInputModifier(
    key: Any,
    onEvent: (PenEvent) -> Unit,
): Modifier = pointerPenInputModifier(key, onEvent)
