@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.kotlinmania.antlr4.internal

// Body wrapped in { } because wasmJs js(...) compiles to (args) => BODY.
@PublishedApi
internal fun wasmJsHasNodeFileSystem(): Boolean =
    js("{ return typeof process !== 'undefined' && process.versions != null && process.versions.node != null; }")

internal actual inline fun <T> withHostFileSystemOrNull(block: () -> T): T? =
    if (wasmJsHasNodeFileSystem()) block() else null
