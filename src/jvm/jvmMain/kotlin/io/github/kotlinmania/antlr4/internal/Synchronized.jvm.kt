package io.github.kotlinmania.antlr4.internal

internal actual inline fun <T> synchronized(
    lock: Any,
    block: () -> T,
): T = kotlin.synchronized(lock, block)
