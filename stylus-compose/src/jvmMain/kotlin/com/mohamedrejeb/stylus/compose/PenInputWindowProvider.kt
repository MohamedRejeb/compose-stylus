package com.mohamedrejeb.stylus.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.FrameWindowScope

/**
 * CompositionLocal that exposes the host [ComposeWindow] to
 * `Modifier.penInput`, so the JVM modifier node can attach to the right AWT
 * window when it's added to composition.
 *
 * Wrap your `Window { … }` content with [ProvidePenInputWindow] to populate it,
 * or call `CompositionLocalProvider(LocalPenInputWindow provides window) { … }`
 * directly. If left unprovided, the modifier falls back to enumerating
 * [java.awt.Window.getWindows] which is fine for single-window apps but
 * unreliable when several windows coexist.
 */
val LocalPenInputWindow = staticCompositionLocalOf<ComposeWindow?> { null }

/**
 * Capture the enclosing [FrameWindowScope.window] and publish it through
 * [LocalPenInputWindow] for any descendant `Modifier.penInput`.
 *
 * ```
 * Window(onCloseRequest = ::exitApplication) {
 *     ProvidePenInputWindow {
 *         DrawApp()  // every Modifier.penInput inside resolves the window
 *     }
 * }
 * ```
 */
@Composable
fun FrameWindowScope.ProvidePenInputWindow(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPenInputWindow provides window) {
        content()
    }
}
