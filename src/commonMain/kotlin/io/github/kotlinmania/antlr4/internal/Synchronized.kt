package io.github.kotlinmania.antlr4.internal

/**
 * Common synchronization placeholder used by generated runtime code paths.
 * Platform monitor primitives are only needed when shared mutable state
 * crosses a real thread boundary.
 */
inline fun <T> synchronized(
    lock: Any,
    block: () -> T,
): T = block()
