package com.mohamedrejeb.stylus.internal

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads a native shared library from the JAR's `native/<platform>/<libname>` resource.
 *
 * Bundling layout (produced by `:stylus-jni:assembleNative` and copied into the JVM
 * jar by `:stylus`'s build script):
 *
 * ```
 * stylus.jar
 * └── native/
 *     ├── macos-aarch64/libstylus.dylib
 *     ├── macos-x86_64/libstylus.dylib
 *     ├── linux-x86_64/libstylus.so
 *     └── windows-x86_64/stylus.dll
 * ```
 */
internal object NativeLoader {

    private val loaded = ConcurrentHashMap.newKeySet<String>()

    fun loadLibrary(libName: String) {
        if (!loaded.add(libName)) return

        val mappedName = System.mapLibraryName(libName)
        val resourcePath = "/native/${platformClassifier()}/$mappedName"

        val temp: Path = Files.createTempFile(stripExtension(mappedName), extension(mappedName))
        try {
            javaClass.getResourceAsStream(resourcePath)
                ?.use { input -> Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING) }
                ?: throw IOException("Native library not found on classpath: $resourcePath")

            System.load(temp.toAbsolutePath().toString())
            println("[stylus] loaded native library $libName (classifier=${platformClassifier()}, path=${temp.toAbsolutePath()})")
        } catch (t: Throwable) {
            System.err.println("[stylus] failed to load native library $libName from $resourcePath: ${t.message}")
            runCatching { Files.deleteIfExists(temp) }
            loaded.remove(libName)
            throw t
        }

        // POSIX: file can be deleted after loading; on Windows the OS holds an exclusive
        // lock until the JVM exits, so defer cleanup to JVM shutdown.
        if (isPosix()) {
            runCatching { Files.deleteIfExists(temp) }
        } else {
            temp.toFile().deleteOnExit()
        }
    }

    private fun platformClassifier(): String {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()

        val os = when {
            osName.contains("mac") || osName.contains("darwin") -> "macos"
            osName.contains("win") -> "windows"
            osName.contains("nux") || osName.contains("nix") -> "linux"
            else -> error("Unsupported OS: $osName")
        }

        val arch = when (osArch) {
            "amd64", "x86_64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> error("Unsupported arch: $osArch")
        }

        return "$os-$arch"
    }

    private fun isPosix(): Boolean =
        java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    private fun stripExtension(fileName: String): String {
        val i = fileName.lastIndexOf('.')
        return if (i >= 0) fileName.substring(0, i) else fileName
    }

    private fun extension(fileName: String): String {
        val i = fileName.lastIndexOf('.')
        return if (i >= 0) fileName.substring(i) else ""
    }
}
