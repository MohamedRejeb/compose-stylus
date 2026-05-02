package com.mohamedrejeb.stylus.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/**
 * State holder for [PenInkSurface] — keeps the list of finished strokes and
 * exposes [clear] / [undo] operations. Survives recomposition via [remember].
 *
 * Held strokes are observable Compose state, so any composable that reads
 * [finishedStrokes] re-composes when a new stroke is added or undone.
 */
@Stable
class PenInkState internal constructor() {

    private val _finishedStrokes = mutableStateListOf<PenStroke>()

    /** Strokes that have been completed (pen lifted) since the last [clear]. */
    val finishedStrokes: List<PenStroke> get() = _finishedStrokes

    internal fun appendStrokes(strokes: List<PenStroke>) {
        if (strokes.isNotEmpty()) _finishedStrokes.addAll(strokes)
    }

    /** Remove all finished strokes. */
    fun clear() {
        _finishedStrokes.clear()
    }

    /** Remove the most-recently finished stroke, if any. */
    fun undo() {
        if (_finishedStrokes.isNotEmpty()) {
            _finishedStrokes.removeAt(_finishedStrokes.lastIndex)
        }
    }
}

/** Remember a [PenInkState] across recompositions. */
@Composable
fun rememberPenInkState(): PenInkState = remember { PenInkState() }
