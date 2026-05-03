package com.mohamedrejeb.stylus.compose

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mohamedrejeb.stylus.PenEvent

/**
 * Renderer used by [PenInkSurface] on Skia-backed targets (JVM, iOS, Web).
 *
 *  - [Tessellated] builds a feathered triangle mesh per stroke and feeds
 *    it to Skia's `drawVertices`. Cheap to redraw (zero CPU work after
 *    the first tessellation), but uses a polygonal join topology that
 *    can show as flat-cut corners or sub-pixel speckles on tight curves.
 *  - [SmoothPath] builds an outline `Path` of the stroke (left + right
 *    tracks stitched with quadratic Beziers, half-disc end caps) and
 *    hands it to `drawPath`. Skia's coverage AA gives genuinely smooth
 *    edges, sharp elbows are partitioned into clean sub-paths, and
 *    pressure modulation comes from a perfect-freehand-style pressure
 *    roll instead of velocity thinning. Slightly higher CPU cost per
 *    redraw than the cached mesh, but the visual quality is the point.
 *
 * Ignored on Android — the Android actual always routes finished strokes
 * through Jetpack Ink's `CanvasStrokeRenderer`, which has its own
 * native-tessellated topology and front-buffered in-progress rendering.
 */
enum class PenInkEngine {
    Tessellated,
    SmoothPath,
}

/**
 * Low-latency stylus drawing surface.
 *
 * On **Android** this is backed by [Jetpack Ink](https://developer.android.com/jetpack/androidx/releases/ink)
 * (`androidx.ink:ink-authoring-compose`), which renders in-progress strokes
 * through a front-buffered `SurfaceControl` for sub-frame latency, with
 * built-in motion prediction.
 *
 * On **Desktop / iOS / Web** the surface uses a Skia-backed pipeline:
 * Catmull-Rom smoothing for shape continuity, a Kalman-filter motion
 * predictor (a faithful port of `androidx.input.motionprediction` — same
 * algorithm and tuning as Jetpack Ink uses on Android), and `drawVertices`
 * with a pre-tessellated triangle strip per stroke so finished strokes
 * redraw with zero CPU work beyond the GPU upload.
 *
 * `Modifier.penInput {}` continues to fire alongside, so consumers that need
 * raw pen telemetry (pressure, tilt, hover) can still subscribe via
 * [onPenEvent] without losing the rendering benefits.
 *
 * ## Reducing latency further on Desktop
 *
 * Compose Desktop normally syncs draws with the display vsync, which adds
 * one frame (~16 ms at 60 Hz) of latency between a pen event and the
 * rendered stroke. For drawing-focused apps the trade-off is usually
 * worth flipping:
 *
 *  - Quick global switch: set `skiko.vsync.enabled` to `false` before
 *    the Compose application starts, e.g.
 *    `System.setProperty("skiko.vsync.enabled", "false")`.
 *  - Scoped per-panel: host the surface in a `ComposePanel` constructed
 *    with `RenderSettings(isVsyncEnabled = false)` (Compose Multiplatform
 *    1.8+, currently behind `useSwingGraphics = true`) so only the
 *    drawing panel goes vsync-free while the rest of the UI keeps
 *    smooth animation timing.
 *
 * Both reduce stylus latency to roughly one frame above input — close to
 * what Jetpack Ink achieves on Android. The trade-offs you accept in
 * return: visible **screen tearing** during fast strokes, **animation
 * judder** wherever the same compose host is running transitions /
 * spinners (Compose's recomposition timing assumes vsync-paced frames),
 * and **unbounded FPS** that keeps the GPU busy on idle redraws.
 * `PenInkSurface` itself does not change this setting — it's a host-app
 * concern that affects everything in the same Compose host.
 *
 * @param modifier Layout modifier applied to the surface's outer `Box`.
 * @param state State holder for finished strokes. Defaults to a freshly
 *   remembered [PenInkState].
 * @param brush Brush used for in-progress strokes. Defaults to [PenBrush.Default].
 * @param engine Renderer to use on Skia-backed targets. Defaults to the
 *   triangle-mesh renderer; flip to [PenInkEngine.SmoothPath] for the
 *   perfect-freehand-style outline renderer when corner / curve artefacts
 *   matter more than per-frame CPU cost. Ignored on Android.
 * @param onStrokesFinished Optional callback fired with the newly-completed
 *   strokes on each pen lift, in addition to them being appended to [state].
 * @param onPenEvent Optional raw [PenEvent] callback — fires on hover, move,
 *   press, and release.
 * @param content Content drawn on top of the ink surface (e.g. UI overlays).
 */
@Composable
expect fun PenInkSurface(
    modifier: Modifier = Modifier,
    state: PenInkState = rememberPenInkState(),
    brush: PenBrush = PenBrush.Default,
    engine: PenInkEngine = PenInkEngine.Tessellated,
    onStrokesFinished: (List<PenStroke>) -> Unit = {},
    onPenEvent: (PenEvent) -> Unit = {},
    content: @Composable BoxScope.() -> Unit = {},
)
