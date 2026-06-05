package io.github.kotlinmania.antlr4.misc

import io.github.kotlinmania.antlr4.RecognitionException

internal class ParseCancellationException(
    val recognitionException: RecognitionException?,
) : RuntimeException(recognitionException?.message)
