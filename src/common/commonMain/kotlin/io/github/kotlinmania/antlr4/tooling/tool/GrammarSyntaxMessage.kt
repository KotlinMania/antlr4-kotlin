/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Token

/**
 * Diagnostic message representing a syntax error in an ANTLR `.g4` grammar.
 */
class GrammarSyntaxMessage internal constructor(
    errorType: ErrorType,
    fileName: String?,
    offendingToken: Token?,
    val recognitionException: RecognitionException?,
    argsList: List<Any?>,
) : ANTLRMessage(
        errorType = errorType,
        cause = null,
        offendingToken = offendingToken,
        argsList = argsList,
    ) {
    constructor(
        errorType: ErrorType,
        fileName: String?,
        offendingToken: Token?,
        detail: String,
    ) : this(
        errorType = errorType,
        fileName = fileName,
        offendingToken = offendingToken,
        recognitionException = null,
        argsList = listOf(detail),
    )

    init {
        this.fileName = fileName
        if (offendingToken != null) {
            line = offendingToken.line
            charPosition = offendingToken.charPositionInLine
        }
    }
}
