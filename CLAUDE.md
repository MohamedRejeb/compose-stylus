# compose-stylus — Kotlin Multiplatform stylus input library

Multiplatform pressure-sensitive stylus input library for Kotlin and Compose Multiplatform.
Targets: **Desktop (JVM)**, **Android**, **iOS**, **Web (wasmJs)**.

The `stylus` core exposes a unified `PenEvent` API; `stylus-compose` exposes a
`Modifier.penInput {}` for Compose Multiplatform consumers. Desktop is backed by a
small JNI layer (`stylus-jni`); the other targets read pen data straight from the platform
pointer APIs.

---

## Architecture

### Source of truth per platform

| Platform | Stylus event source                                   | Notes |
|----------|-------------------------------------------------------|-------|
| Desktop  | Native JNI lib (Cocoa / X11+XInput2 / Windows RTS)    | Built by `stylus-jni` via Gradle CMake. Required because Swing/AWT does not expose pressure/tilt natively. |
| Android  | `MotionEvent` (`TOOL_TYPE_STYLUS`, axes for pressure/tilt/orientation) | No JNI needed — Android exposes stylus axes through the public framework. |
| iOS      | `UITouch` (`type`, `force`, `azimuthAngle`, `altitudeAngle`) | No JNI needed — UIKit exposes Pencil data directly. |
| Web      | DOM `PointerEvent` (`pointerType=='pen'`, `pressure`, `tiltX`, `tiltY`) | No JNI — browsers expose pen data on PointerEvent. |

### Module layout

```
compose-stylus/
├── stylus/                    KMP library — public API (commonMain) + platform actuals
│   ├── commonMain/            PenEvent, PenEventType, PenButton, PenTool,
│   │                          PenEventCallback (fun interface), expect PenInputSource
│   ├── jvmMain/               actual PenInputSource → JNI (depends on :stylus-jni runtime resources)
│   ├── androidMain/           actual PenInputSource → MotionEvent
│   ├── iosMain/               actual PenInputSource → UITouch (PenInputView)
│   └── wasmJsMain/            actual PenInputSource → PointerEvent
│
├── stylus-jni/                JVM-only Gradle module — builds C++ JNI shared library via CMake
│   └── src/main/cpp/          Native sources (Cocoa / X11+XInput2 / Windows RTS)
│
├── stylus-compose/            Compose Multiplatform integration — Modifier.penInput {}
│   ├── commonMain/            expect Modifier.penInput(...)
│   ├── jvmMain/               actual — uses ComposePenInputManager bridging to JNI
│   ├── androidMain/           actual — derives PenEvent from PointerInputChange
│   ├── iosMain/               actual — derives PenEvent from PointerInputChange
│   └── wasmJsMain/            actual — derives PenEvent from PointerInputChange
│
└── stylus-demo/
    ├── stylus-demo-jvm/       Compose Desktop demo
    ├── stylus-demo-android/   Android app demo
    └── stylus-demo-web/       Compose for Web demo
```

### Public API (commonMain)

```kotlin
enum class PenEventType { Hover, Move, Press, Release }
enum class PenButton { None, Primary, Secondary, Tertiary }
enum class PenTool { None, Mouse, Eraser, Pen, Touch }

data class PenEvent(
    val type: PenEventType,
    val tool: PenTool,
    val button: PenButton,
    val x: Double,
    val y: Double,
    val pressure: Double,
    val tangentPressure: Double = 0.0,
    val tiltX: Double = 0.0,
    val tiltY: Double = 0.0,
    val rotation: Double = 0.0,
    val timestamp: Long,
)

fun interface PenEventCallback {
    fun onEvent(event: PenEvent)
}

expect class PenInputSource {
    fun attach(callback: PenEventCallback, host: Any)
    fun detach(callback: PenEventCallback, host: Any)
    fun setEnabled(callback: PenEventCallback, enabled: Boolean)
    fun dispose()
    companion object { val Default: PenInputSource }
}
```

`host: Any` is the platform-specific surface to attach to:
- Desktop: `java.awt.Window` (typically `ComposeWindow`)
- Android: `android.view.View` (the Compose host view)
- iOS: `PenInputView` (a `UIView` shipped with the iOS source set)
- Web: `HTMLElement` / canvas

Compose users should never call `PenInputSource` directly — use `Modifier.penInput {}`
from `:stylus-compose` instead.

### Low-latency drawing — `PenInkSurface`

`stylus-compose` ships a `PenInkSurface` composable that handles in-progress
stylus rendering and persists finished strokes. The public API lives in
`commonMain`; `PenInkSurface` itself is `expect`/`actual` so each target uses
the best available engine.

| Target           | Engine                                                                                                                |
|------------------|-----------------------------------------------------------------------------------------------------------------------|
| Android          | [Jetpack Ink](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/about-ink-api) — front-buffered `SurfaceControl`, native motion prediction. Finished strokes drawn via `CanvasStrokeRenderer`. |
| JVM / iOS / Web  | Shared `ComposePenInkSurface` — pure-Compose pipeline with Catmull-Rom smoothing (8 subdivisions / segment) and linear motion prediction (~16 ms ahead). |

File layout:

```
stylus-compose/src/
├── commonMain/.../PenInkSurface.kt        expect fun + ComposePenInkSurface (shared impl)
├── commonMain/.../PenStroke.kt            PenStroke, PenStrokePoint, PenBrush, PenBrushFamily
├── commonMain/.../PenInkState.kt          PenInkState, rememberPenInkState
├── androidMain/.../PenInkSurface.android.kt   actual — Ink + brush/stroke conversions
├── jvmMain/.../PenInkSurface.jvm.kt        actual — delegates to ComposePenInkSurface
├── iosMain/.../PenInkSurface.ios.kt        actual — delegates to ComposePenInkSurface
└── wasmJsMain/.../PenInkSurface.wasmJs.kt  actual — delegates to ComposePenInkSurface
```

Public API (common):

```kotlin
@Composable
expect fun PenInkSurface(
    modifier: Modifier = Modifier,
    state: PenInkState = rememberPenInkState(),
    brush: PenBrush = PenBrush.Default,
    onStrokesFinished: (List<PenStroke>) -> Unit = {},
    onPenEvent: (PenEvent) -> Unit = {},
    content: @Composable BoxScope.() -> Unit = {},
)

class PenBrush {                              // concrete, common
    val color: Color; val size: Float; val family: PenBrushFamily
    companion object {
        val Default: PenBrush
        fun pen(color: Color, size: Float = 5f): PenBrush
        fun marker(color: Color, size: Float = 10f): PenBrush
        fun highlighter(color: Color, size: Float = 20f): PenBrush
    }
}
enum class PenBrushFamily { Pen, Marker, Highlighter }

data class PenStrokePoint(val x: Float, val y: Float, val pressure: Float, val elapsedMillis: Long)
class PenStroke {                             // platform-neutral
    val brush: PenBrush
    val points: List<PenStrokePoint>
    val bounds: Rect                          // lazy
}

class PenInkState {                           // remembered via rememberPenInkState()
    val finishedStrokes: List<PenStroke>
    fun clear()
    fun undo()
}
```

Conventions:

- **`PenStroke` is portable data**, not an opaque wrapper. A stroke captured on
  any platform can be re-rendered on any other. The Android actual round-trips
  through `MutableStrokeInputBatch` to feed `CanvasStrokeRenderer`.
- `Modifier.penInput {}` keeps firing while `PenInkSurface` is active, so
  consumers can still receive raw `PenEvent`s alongside the rendered output.
- Use `PenInkSurface` only when **rendering** strokes; for UX that just observes
  pen events (cursor, palm rejection, gesture recognition), use `Modifier.penInput {}`
  over a regular `Canvas`.
- Ink artifacts (`androidx.ink:ink-authoring-compose`, `ink-brush`,
  `ink-rendering`, `ink-strokes`) are declared in `gradle/libs.versions.toml`
  and pulled into `stylus-compose`'s `androidMain` only. They never reach
  `commonMain` — the cross-platform `actual`s use Compose's own `Canvas` and
  pointer pipeline.

### JNI naming conventions

The JNI bridge files live under `stylus-jni/src/main/cpp/` and are named `JNI_Pen*`
(`JNI_Pen.h`, `JNI_PenInputSource.h/cpp`, `JNI_PenEvent.h/cpp`, `JNI_PenEventCallback.h/cpp`,
`JNI_PenContext.h/cpp`). Symbol names follow the JNI convention exactly — e.g.
`Java_com_mohamedrejeb_stylus_PenInputSource_attachCallback`.

The deeper C++ implementation under `dependencies/core/` and `dependencies/{macos,linux,windows}/`
keeps its original `stylus::*` namespace and `StylusManager` / `StylusListener` / `StylusEvent`
class names — those reflect the platform input concepts (Cocoa stylus, X11 stylus, Win32 RTS)
and aren't part of the user-visible API.

---

## Build conventions

### Tooling

- **Gradle** 8.13+ (see `gradle/wrapper/gradle-wrapper.properties`)
- **Kotlin** 2.2.21
- **Compose Multiplatform** 1.9.3
- **Android Gradle Plugin** 9.0.x
- **JDK** 17 for the build, **JVM target 11** for the `stylus`/`stylus-jni` runtime
- **CMake** 3.22+ for the JNI build

All versions live in `gradle/libs.versions.toml`; never hardcode versions in `build.gradle.kts`.

### JNI build (Desktop only)

The `stylus-jni` module builds the native shared library via Gradle CMake and stages the
binary into `stylus-jni/build/native/<platform>/`. The `stylus` JVM source set bundles the
binary as a runtime resource at `lib/<platform>/<libname>` and `NativeLoader` extracts +
`System.load`s it on first use. **Never** hardcode an absolute path in
`PenInputSource.jvm.kt` — always go through `NativeLoader`.

Platform classifiers:
- `macos-aarch64`, `macos-x86_64`
- `linux-x86_64`
- `windows-x86_64`

To rebuild the native lib only:
```bash
./gradlew :stylus-jni:assembleNative
```

### Multiplatform conventions

- Use **expect/actual** for platform-specific implementations.
- Keep `commonMain` free of `java.*`, `android.*`, `platform.*`, `org.w3c.*` symbols.
- Public types are immutable `data class`es; mutate via `.copy(...)`.
- `enum class` for closed sets (axes, buttons, cursor types).
- Use `kotlinx.coroutines` `Flow` for streaming events when wrapping the listener API.

### Coding style

See `.claude/rules/coding-style.md` (Kotlin style — `val` over `var`, no `!!`, sealed types,
scope functions sparingly).

### Testing

See `.claude/rules/testing.md`. `kotlin.test` for `commonTest`; platform-specific tests under
`jvmTest`, `androidUnitTest`, `iosTest`. Prefer hand-written fakes over mocks. 80% coverage target.

---

## Common commands

```bash
# Build everything
./gradlew build

# Build only the stylus core (all targets it supports)
./gradlew :stylus:build

# Run desktop demo
./gradlew :stylus-demo-jvm:run

# Build native JNI lib for current host
./gradlew :stylus-jni:assembleNative

# Run common tests
./gradlew :stylus:allTests

# Install Android demo on connected device
./gradlew :stylus-demo-android:installDebug

# Run Web demo (Compose for Web, wasmJs)
./gradlew :stylus-demo-web:wasmJsBrowserDevelopmentRun

# Publish to local Maven cache (~/.m2) for testing in another project
./gradlew :stylus:publishToMavenLocal :stylus-compose:publishToMavenLocal -PskipNativeBuild
```

## Publishing

Publishing is handled by `com.vanniktech.maven.publish` and routes through the
Sonatype Central Portal. Coordinates: `com.mohamedrejeb.stylus:{stylus|stylus-compose}:<version>`.

### Local snapshot install

```bash
./gradlew :stylus:publishToMavenLocal :stylus-compose:publishToMavenLocal
```

Local publishes skip signing automatically (vanniktech 0.30+).

### Release flow

Releases are driven by `.github/workflows/release.yml`, triggered by either:

- **Pushing a `vX.Y.Z` tag** → reads version from the tag, publishes, and creates a GitHub release.
- **Manual `workflow_dispatch`** with the version typed in.

The workflow:
1. Runs the four-platform native build matrix (same as `build.yml`).
2. On `macos-latest` so iOS publications can be built, downloads every native artifact, lays them out under `stylus-jni/build/native/<classifier>/`.
3. Runs `./gradlew :stylus:publishAllPublicationsToMavenCentralRepository :stylus-compose:publishAllPublicationsToMavenCentralRepository -PskipNativeBuild`.

### Required GitHub secrets

Names match the convention used across `MohamedRejeb/*` repos (e.g. Calf),
so the same secrets work for every project.

| Secret | What it is |
|---|---|
| `OSSRH_USERNAME` | Sonatype Central Portal user token name |
| `OSSRH_PASSWORD` | Sonatype Central Portal user token password |
| `OSSRH_GPG_SECRET_KEY` | GPG private key (ASCII-armored, full block including BEGIN/END lines) |
| `OSSRH_GPG_SECRET_KEY_ID` | Last 8 chars of the GPG key fingerprint |
| `OSSRH_GPG_SECRET_KEY_PASSWORD` | GPG passphrase |

Both the release (`release.yml`) and snapshot (`build.yml` on push to `main`)
jobs expose these to Gradle as `ORG_GRADLE_PROJECT_*` env vars, which the
vanniktech plugin picks up.

### `-PskipNativeBuild`

Both `build.yml` and `release.yml` pass `-PskipNativeBuild` to the JAR/publish
Gradle invocations. This disables the `:stylus-jni:assembleNative` dependency
on `:stylus`'s `copyNativeLib` task, so the publish job uses the matrix-built
native libs already staged under `stylus-jni/build/native/<classifier>/`
instead of rebuilding (which would only produce a single-OS lib on whatever
runner the publish job lives on).

### Native build prerequisites

| OS      | Toolchain |
|---------|-----------|
| macOS   | Xcode 15+ (provides clang + Cocoa/AppKit/IOKit frameworks) |
| Linux   | gcc, g++, libstdc++, libX11-dev, libxi-dev, libxext-dev |
| Windows | Visual Studio 2019+ (MSVC v142+) |

---

## When in doubt

- For Compose Multiplatform patterns (state mgmt, navigation, theming): use the
  `compose-multiplatform-patterns` skill.
- For Kotlin idioms (coroutines, Flow, sealed types): use the `kotlin-patterns` skill.
- For Gradle build issues: use the `gradle-build` slash command / `kotlin-build-resolver` agent.
- For up-to-date library docs: prefer Context7 over guessing.
