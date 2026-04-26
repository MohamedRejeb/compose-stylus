package com.mohamedrejeb.stylus.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `NativeLoader.platformClassifier` (private) is exercised indirectly here:
 * we drive `os.name` / `os.arch` through every supported and unsupported
 * combination and assert the classifier we'd use to look up
 * `/native/<classifier>/<libname>` in the JAR.
 *
 * The classifier mapping has to stay in lock-step with the directory layout
 * the CI `package-jar` job lays out and with the cmake install path on each
 * `:stylus-jni:assembleNative` host — getting it wrong means the JAR ships
 * the binaries but the runtime can't find them.
 */
class NativeLoaderClassifierTest {

    private fun classify(osName: String, osArch: String): String {
        val originalName = System.getProperty("os.name")
        val originalArch = System.getProperty("os.arch")
        System.setProperty("os.name", osName)
        System.setProperty("os.arch", osArch)
        try {
            val method = NativeLoader::class.java
                .getDeclaredMethod("platformClassifier")
                .also { it.isAccessible = true }
            return try {
                method.invoke(NativeLoader) as String
            } catch (e: java.lang.reflect.InvocationTargetException) {
                // Surface the real exception so assertFailsWith<IllegalStateException>
                // can see it, instead of the InvocationTargetException wrapper.
                throw e.targetException
            }
        } finally {
            System.setProperty("os.name", originalName)
            System.setProperty("os.arch", originalArch)
        }
    }

    @Test fun `mac on apple silicon maps to macos-aarch64`() {
        assertEquals("macos-aarch64", classify("Mac OS X", "aarch64"))
        assertEquals("macos-aarch64", classify("Mac OS X", "arm64"))
        assertEquals("macos-aarch64", classify("Darwin", "aarch64"))
    }

    @Test fun `mac on intel maps to macos-x86_64`() {
        assertEquals("macos-x86_64", classify("Mac OS X", "x86_64"))
        assertEquals("macos-x86_64", classify("Mac OS X", "amd64"))
    }

    @Test fun `linux on x86_64 maps to linux-x86_64`() {
        assertEquals("linux-x86_64", classify("Linux", "amd64"))
        assertEquals("linux-x86_64", classify("Linux", "x86_64"))
    }

    @Test fun `windows on x86_64 maps to windows-x86_64`() {
        assertEquals("windows-x86_64", classify("Windows 11", "amd64"))
        assertEquals("windows-x86_64", classify("Windows 10", "x86_64"))
    }

    @Test fun `unsupported os throws`() {
        assertFailsWith<IllegalStateException> { classify("FreeBSD", "x86_64") }
    }

    @Test fun `unsupported arch throws`() {
        assertFailsWith<IllegalStateException> { classify("Linux", "ppc64le") }
    }
}
