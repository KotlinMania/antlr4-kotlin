package io.github.kotlinmania.antlr4.internal

internal actual inline fun <T> withHostFileSystemOrNull(block: () -> T): T? = block()
