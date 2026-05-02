package com.mohamedrejeb.stylus.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventCallback
import java.awt.Window
import javax.swing.SwingUtilities

internal actual fun platformPenInputModifier(
    key: Any,
    onEvent: (PenEvent) -> Unit,
): Modifier = PenInputNodeElement(key = key, onEvent = onEvent)

private data class PenInputNodeElement(
    val key: Any,
    val onEvent: (PenEvent) -> Unit,
) : ModifierNodeElement<PenInputNode>() {

    override fun create(): PenInputNode = PenInputNode(key = key, onEvent = onEvent)

    override fun update(node: PenInputNode) {
        node.key = key
        node.onEvent = onEvent
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "penInput"
        properties["key"] = key
    }
}

private class PenInputNode(
    var key: Any,
    var onEvent: (PenEvent) -> Unit,
) : Modifier.Node(),
    LayoutAwareModifierNode,
    PointerInputModifierNode,
    CompositionLocalConsumerModifierNode {

    private var attachedWindow: Window? = null

    private val callback = PenEventCallback { event -> onEvent(event) }

    override fun onAttach() {
        // Read the host ComposeWindow at compose time. Reading from LocalPenInputWindow
        // avoids the race that Window.getWindows() runs into — onAttach() can fire
        // before AWT has registered the new ComposeWindow.
        val providedWindow: Window? = currentValueOf(LocalPenInputWindow)

        SwingUtilities.invokeLater {
            val window = providedWindow ?: locateAwtWindow()
            if (window == null) {
                System.err.println(
                    "[stylus] onAttach: no AWT Window available — wrap your Window { } content " +
                        "with ProvidePenInputWindow { } so LocalPenInputWindow can resolve."
                )
                return@invokeLater
            }
            attachedWindow = window
            val source = if (providedWindow != null) "LocalPenInputWindow" else "Window.getWindows() fallback"
            println("[stylus] onAttach: registering key=$key on ${window.javaClass.simpleName} (via $source)")
            ComposePenInputManager.instance.attach(key, callback, window)
            ComposePenInputManager.instance.updateDensity(key, window.nativeScale())
            // Enable immediately. We can't rely on Compose's PointerEventType.Enter
            // because real-pen hover events on macOS don't always propagate
            // through the AWT mouse pipeline that drives Compose pointer events.
            ComposePenInputManager.instance.setEnabled(key, true)
        }
    }

    override fun onDetach() {
        val window = attachedWindow ?: return
        ComposePenInputManager.instance.setEnabled(key, false)
        ComposePenInputManager.instance.detach(key, window)
        attachedWindow = null
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        ComposePenInputManager.instance.updateTopLeft(key, coordinates.positionInWindow())
        attachedWindow?.let {
            ComposePenInputManager.instance.updateDensity(key, it.nativeScale())
        }
    }

    override fun onRemeasured(size: IntSize) {
        ComposePenInputManager.instance.updateSize(key, size.toSize())
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) = Unit

    override fun onCancelPointerInput() = Unit

    /**
     * Resolve the AWT [Window] this composition is hosted in.
     *
     * On Compose Desktop the only top-level visible windows are AWT windows
     * created by `Window { }`; picking the focused one is a pragmatic stand-in
     * for a Compose-Desktop-internal API to traverse the layout owner up to its
     * container. If there are multiple compose windows simultaneously, callers
     * should provide an explicit `key` per window to keep their callbacks
     * separate.
     */
    private fun locateAwtWindow(): Window? =
        Window.getWindows().firstOrNull { it.isFocused && it.isVisible }
            ?: Window.getWindows().firstOrNull { it.isVisible }
}

/**
 * Native HiDPI scale of the AWT [Window] — matches the value the JNI bridge
 * uses when multiplying NSEvent / X / Win32 coordinates into physical pixels
 * (e.g. `backingScaleFactor` on macOS Retina). Read from
 * `GraphicsConfiguration.defaultTransform.scaleX`, NOT from `LocalDensity`:
 * Compose Desktop reports `density = 1.0` even on Retina, while the native
 * pen events have already been scaled by the OS factor, so dividing by
 * `LocalDensity.density` would leave the values in physical px and the
 * stroke would land down-and-right of the cursor by `nativeScale - 1`.
 */
private fun Window.nativeScale(): Float =
    graphicsConfiguration?.defaultTransform?.scaleX?.toFloat() ?: 1f
