/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.CharStream
import io.github.kotlinmania.antlr4.IntStream
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType

/**
 * [ANTLRLexer] wired to an [ErrorManager] for `.g4` tool diagnostic reporting.
 */
class ToolANTLRLexer(
    input: CharStream?,
    val errorManager: ErrorManager?,
) : ANTLRLexer(input) {
    private val resolvedSourceName: String?
        get() {
            val name = inputStream?.sourceName
            return if (name.isNullOrEmpty() || name == IntStream.UNKNOWN_SOURCE_NAME) {
                null
            } else {
                name
            }
        }

    override fun displayRecognitionError(e: RecognitionException) {
        displayRecognitionErrorWithTokenNames(tokenNames, e)
    }

    override fun displayRecognitionErrorWithTokenNames(
        tokenNames: List<String>,
        e: RecognitionException,
    ) {
        val msg = getErrorMessage(e, tokenNames)
        errorManager?.syntaxError(ErrorType.SYNTAX_ERROR, resolvedSourceName, e.offendingToken, e, msg)
    }

    override fun grammarError(
        etype: ErrorType,
        token: Token,
        vararg args: Any?,
    ) {
        errorManager?.grammarError(etype, resolvedSourceName, token, *args)
    }
}
