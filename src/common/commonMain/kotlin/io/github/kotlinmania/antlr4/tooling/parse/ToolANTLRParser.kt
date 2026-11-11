/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.IntStream
import io.github.kotlinmania.antlr4.NoViableAltException
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType

/**
 * [ANTLRParser] wired to an [ErrorManager] for `.g4` tool diagnostic reporting,
 * appending active rule paraphrases (`while matching ...`, `while looking for ...`)
 * identically to upstream ANTLR4.
 */
class ToolANTLRParser(
    input: TokenStream?,
    val errorManager: ErrorManager?,
) : ANTLRParser(input) {
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
        var msg =
            when (e) {
                is NoViableAltException -> "${getTokenErrorDisplay(e.offendingToken)} came as a complete surprise to me"
                is V4ParserException -> e.msg
                else -> getErrorMessage(e, tokenNames)
            }
        if (paraphrases.isNotEmpty()) {
            val paraphrase = paraphrases.last()
            msg = "$msg while $paraphrase"
        }
        errorManager?.syntaxError(ErrorType.SYNTAX_ERROR, resolvedSourceName, e.offendingToken, e, msg)
    }

    fun getParserErrorMessage(
        parser: ANTLRParser,
        e: RecognitionException,
    ): String =
        when (e) {
            is NoViableAltException -> "${parser.getTokenErrorDisplay(e.offendingToken)} came as a complete surprise to me"
            is V4ParserException -> e.msg
            else -> parser.getErrorMessage(e, parser.tokenNames)
        }

    override fun grammarError(
        etype: ErrorType,
        token: Token?,
        vararg args: Any?,
    ) {
        errorManager?.grammarError(etype, resolvedSourceName, token, *args)
    }
}
