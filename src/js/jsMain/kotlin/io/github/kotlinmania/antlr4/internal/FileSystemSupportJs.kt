package io.github.kotlinmania.antlr4.internal

@PublishedApi
internal fun jsHasNodeFileSystem(): Boolean =
    js("typeof process !== 'undefined' && process.versions != null && process.versions.node != null") as Boolean

internal actual inline fun <T> withHostFileSystemOrNull(block: () -> T): T? =
    if (jsHasNodeFileSystem()) block() else null
