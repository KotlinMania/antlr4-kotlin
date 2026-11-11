/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.Token

/**
 * Diagnostic message representing a semantic error or warning at a grammar location.
 */
class GrammarSemanticsMessage internal constructor(
    errorType: ErrorType,
    fileName: String?,
    offendingToken: Token?,
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
    ) : this(
        errorType = errorType,
        fileName = fileName,
        offendingToken = offendingToken,
        argsList = emptyList(),
    )

    init {
        this.fileName = fileName
        if (offendingToken != null) {
            line = offendingToken.line
            charPosition = offendingToken.charPositionInLine
        }
    }
}
