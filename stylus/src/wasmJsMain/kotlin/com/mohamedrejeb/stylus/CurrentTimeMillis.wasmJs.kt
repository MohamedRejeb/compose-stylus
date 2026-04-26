@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.mohamedrejeb.stylus

internal actual fun currentTimeMillis(): Long = jsDateNow().toLong()

private fun jsDateNow(): Double = js("Date.now()")
