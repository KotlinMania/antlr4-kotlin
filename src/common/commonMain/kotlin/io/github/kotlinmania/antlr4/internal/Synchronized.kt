package io.github.kotlinmania.antlr4.internal

/**
 * Reentrant monitor synchronization for shared ATN/DFA caches across platforms.
 */
internal expect inline fun <T> synchronized(
    lock: Any,
    block: () -> T,
): T
