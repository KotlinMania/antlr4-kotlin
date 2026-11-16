package io.github.kotlinmania.antlr4.internal

/**
 * Executes [block] when the current runtime environment exposes a host OS file system (excluded in browser sandboxes).
 */
internal expect inline fun <T> withHostFileSystemOrNull(block: () -> T): T?
