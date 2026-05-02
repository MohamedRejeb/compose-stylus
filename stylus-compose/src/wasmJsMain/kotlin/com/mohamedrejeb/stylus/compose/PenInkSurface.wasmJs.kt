package com.mohamedrejeb.stylus.compose

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mohamedrejeb.stylus.PenEvent

@Composable
actual fun PenInkSurface(
    modifier: Modifier,
    state: PenInkState,
    brush: PenBrush,
    onStrokesFinished: (List<PenStroke>) -> Unit,
    onPenEvent: (PenEvent) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    ComposePenInkSurface(
        modifier = modifier,
        state = state,
        brush = brush,
        onStrokesFinished = onStrokesFinished,
        onPenEvent = onPenEvent,
        content = content,
    )
}
