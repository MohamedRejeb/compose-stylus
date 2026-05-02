# Compose Stylus

Pressure-sensitive stylus / pen input for **Kotlin Multiplatform** and **Compose Multiplatform**.

`compose-stylus` exposes a unified `PenEvent` API across Desktop (JVM), Android, iOS, and Web (wasmJs).
On Desktop it taps native pen events through a small JNI layer (Cocoa / X11+XInput2 / Windows RTS) — all
other targets read pressure, tilt, and rotation directly from the platform pointer APIs.

## Targets

| Target           | Source                                                              |
|------------------|---------------------------------------------------------------------|
| Desktop (JVM)    | Native JNI: Cocoa (macOS), X11 + XInput2 (Linux), RTS (Windows)     |
| Android          | `MotionEvent` axes (`TOOL_TYPE_STYLUS`, pressure, tilt, orientation) |
| iOS              | `UITouch` (`force`, `azimuthAngle`, `altitudeAngle`)                |
| Web (wasmJs)     | DOM `PointerEvent` (`pointerType="pen"`, `pressure`, `tiltX/Y`)     |

## Installation

Artifacts are published to Maven Central under `com.mohamedrejeb.stylus`.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    // Core API (PenInputSource, PenEvent, PenEventCallback)
    implementation("com.mohamedrejeb.stylus:stylus:<version>")

    // Compose Multiplatform integration (Modifier.penInput)
    implementation("com.mohamedrejeb.stylus:stylus-compose:<version>")
}
```

## Quick start — Compose

Most users only need the Compose integration:

```kotlin
import com.mohamedrejeb.stylus.compose.penInput

@Composable
fun Canvas() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .penInput(
                onHover   = { event -> /* pen entered / exited / hovered */ },
                onMove    = { event -> /* pen moved while in contact */ },
                onPress   = { event -> /* pen pressed against the surface */ },
                onRelease = { event -> /* pen lifted */ },
            )
    )
}
```

Or, if you'd rather route a single stream of events yourself:

```kotlin
Modifier.penInput { event ->
    when (event.type) {
        PenEventType.Hover -> /* … */
        PenEventType.Move -> /* … */
        PenEventType.Press -> /* … */
        PenEventType.Release -> /* … */
    }
}
```

`PenEvent` carries:

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
    val timestamp: Long = currentTimeMillis(),
)
```

## Android: low-latency drawing with `PenInkSurface`

On Android, `stylus-compose` ships an additional composable, `PenInkSurface`, backed by
[Jetpack Ink](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/about-ink-api).
It uses a front-buffered `SurfaceControl` and built-in motion prediction to render strokes with sub-frame
latency — visibly tighter than drawing on a regular Compose `Canvas`.

```kotlin
import com.mohamedrejeb.stylus.compose.PenBrush
import com.mohamedrejeb.stylus.compose.PenInkSurface
import com.mohamedrejeb.stylus.compose.rememberPenInkState

@Composable
fun Notes() {
    val state = rememberPenInkState()
    PenInkSurface(
        modifier = Modifier.fillMaxSize(),
        state = state,
        brush = PenBrush.pen(Color.Black, size = 5f),
    ) {
        // Overlays — e.g. an undo/clear toolbar
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Button(onClick = { state.undo() }) { Text("Undo") }
            Button(onClick = { state.clear() }) { Text("Clear") }
        }
    }
}
```

`PenBrush` exposes three stock brushes — `pen(color, size)`, `marker(color, size)`, and
`highlighter(color, size)` — plus `PenBrush.Default`.

`PenInkSurface` is currently Android-only. On other targets keep using `Modifier.penInput {}` over your own
`Canvas`. The surrounding `Modifier.penInput {}` continues to fire alongside Ink, so callbacks like
`onPenEvent` still receive every hover / move / press / release event.

## Core API (no Compose)

If you need to attach callbacks outside Compose (e.g. to a `Window`, `View`, `UIView`, or `HTMLElement`):

```kotlin
val source = PenInputSource.Default

val callback = PenEventCallback { event ->
    // dispatch on event.type
}

source.attach(callback, host)   // host: Window / View / UIView / HTMLElement
// …
source.detach(callback, host)
```

`host: Any` accepts the platform-specific surface:

| Platform | Host type                                  |
|----------|--------------------------------------------|
| Desktop  | `java.awt.Window` (e.g. `ComposeWindow`)   |
| Android  | `android.view.View`                        |
| iOS      | `PenInputView` (a `UIView` shipped with the iOS source set) |
| Web      | `HTMLElement` (e.g. canvas)                |

Compose users should prefer `Modifier.penInput {}` from `stylus-compose` over calling `PenInputSource` directly.

## Modules

| Module           | Purpose                                                                                  |
|------------------|------------------------------------------------------------------------------------------|
| `stylus`         | Public KMP API: `PenEvent`, `PenEventCallback`, `PenInputSource`                         |
| `stylus-compose` | Compose Multiplatform integration: `Modifier.penInput {}`. Android also ships `PenInkSurface` (Jetpack Ink, low-latency). |
| `stylus-jni`     | JVM-only — builds the native shared library used by the Desktop target                  |

## Build prerequisites

JVM build itself only needs JDK 17 + Gradle. To rebuild the **native** Desktop library locally:

| OS      | Toolchain                                                                       |
|---------|---------------------------------------------------------------------------------|
| macOS   | Xcode 15+ (clang + Cocoa / AppKit / IOKit)                                      |
| Linux   | gcc, g++, libstdc++, `libX11-dev`, `libxi-dev`, `libxext-dev`                   |
| Windows | Visual Studio 2019+ (MSVC v142+)                                                |

```bash
# Build everything
./gradlew build

# Build only the core library
./gradlew :stylus:build

# Rebuild the native JNI lib for the current host
./gradlew :stylus-jni:assembleNative

# Run the desktop demo
./gradlew :stylus-demo-jvm:run

# Run the web demo
./gradlew :stylus-demo-web:wasmJsBrowserDevelopmentRun

# Install the Android demo on a connected device
./gradlew :stylus-demo-android:installDebug
```

CI ships prebuilt native binaries for `macos-aarch64`, `macos-x86_64`, `linux-x86_64`, and `windows-x86_64`,
so consumers do **not** need a C++ toolchain — only contributors who want to rebuild the native code do.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
