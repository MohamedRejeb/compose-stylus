import org.gradle.internal.os.OperatingSystem

plugins {
    base
}

description = "Native JNI shared library for the stylus library (desktop only)"

// ─── Platform classifier ─────────────────────────────────────────────────────────
// Pick the classifier sub-directory to install the native artifact into.
// NativeLoader on the JVM side maps `os.name` × `os.arch` to the same string.
val classifier: String = run {
    val os = OperatingSystem.current()
    val arch = System.getProperty("os.arch").lowercase()

    val osName = when {
        os.isMacOsX -> "macos"
        os.isLinux -> "linux"
        os.isWindows -> "windows"
        else -> error("Unsupported OS: ${os.name}")
    }
    val archName = when (arch) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        else -> error("Unsupported arch: $arch")
    }
    "$osName-$archName"
}

// ─── Paths ───────────────────────────────────────────────────────────────────────
val cppDir = layout.projectDirectory.dir("src/main/cpp").asFile
val cmakeBuildDir = layout.buildDirectory.dir("cmake/$classifier")
val nativeInstallDir = layout.buildDirectory.dir("native/$classifier")

val javaHome: String = (System.getProperty("java.home")
    ?: System.getenv("JAVA_HOME")
    ?: error("JAVA_HOME is not set and java.home is unavailable"))
    // CMake parses -D values as CMake strings; backslashes in Windows paths
    // (e.g. C:\hostedtoolcache\...) are interpreted as escape sequences and
    // FindJNI.cmake fails with "Invalid character escape '\h'". Forward slashes
    // are valid on Windows for both CMake and the JDK include lookup.
    .replace('\\', '/')

// ─── Tasks ───────────────────────────────────────────────────────────────────────
val configureNative by tasks.registering(Exec::class) {
    group = "native"
    description = "Run cmake configure step for $classifier"

    inputs.dir(cppDir)
    inputs.property("classifier", classifier)
    inputs.property("javaHome", javaHome)
    outputs.dir(cmakeBuildDir)

    workingDir = projectDir
    val buildDir = cmakeBuildDir.get().asFile
    val installDir = nativeInstallDir.get().asFile
    doFirst {
        buildDir.mkdirs()
        installDir.mkdirs()
    }

    commandLine = buildList {
        add("cmake")
        add("-S"); add(cppDir.absolutePath)
        add("-B"); add(buildDir.absolutePath)
        add("-DCMAKE_BUILD_TYPE=Release")
        add("-DCMAKE_INSTALL_PREFIX=${installDir.absolutePath}")
        add("-DJAVA_HOME=$javaHome")
        if (OperatingSystem.current().isWindows) {
            add("-A"); add("x64")
        }
    }
}

val buildNative by tasks.registering(Exec::class) {
    group = "native"
    description = "Compile and install the native shared library for $classifier"

    dependsOn(configureNative)
    inputs.dir(cppDir)
    outputs.dir(nativeInstallDir)

    workingDir = projectDir
    commandLine(
        "cmake",
        "--build", cmakeBuildDir.get().asFile.absolutePath,
        "--config", "Release",
        "--target", "install",
    )
}

/**
 * Public entry point: produces the native shared library at
 * `stylus-jni/build/native/<classifier>/<libname>`.
 *
 * The `:stylus` JVM source set picks these up and bundles them as resources
 * under `native/<classifier>/<libname>` in the published JAR.
 */
val assembleNative by tasks.registering {
    group = "native"
    description = "Build the native JNI shared library for the host platform"
    dependsOn(buildNative)
}

tasks.named("assemble") {
    dependsOn(assembleNative)
}

tasks.named<Delete>("clean") {
    delete(layout.buildDirectory)
}
