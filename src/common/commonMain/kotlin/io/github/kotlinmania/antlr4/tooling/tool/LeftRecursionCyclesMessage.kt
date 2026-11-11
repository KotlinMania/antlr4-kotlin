/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.Token

/**
 * Diagnostic message reporting mutually left-recursive rule cycles.
 */
class LeftRecursionCyclesMessage internal constructor(
    fileName: String?,
    cycles: Collection<Collection<ErrorTemplateObject>>,
) : ANTLRMessage(
        errorType = ErrorType.LEFT_RECURSION_CYCLES,
        cause = null,
        offendingToken = findStartTokenOfFirstRule(cycles),
        argsList = listOf(cycles),
    ) {
    constructor(
        fileName: String?,
        cycles: List<List<ErrorTemplateObject>>,
    ) : this(
        fileName = fileName,
        cycles = cycles as Collection<Collection<ErrorTemplateObject>>,
    )

    init {
        this.fileName = fileName
        val tok = offendingToken
        if (tok != null) {
            line = tok.line
            charPosition = tok.charPositionInLine
        }
    }

    companion object {
        internal fun findStartTokenOfFirstRule(cycles: Collection<Collection<ErrorTemplateObject>>): Token? {
            for (cycle in cycles) {
                for (rule in cycle) {
                    val tok = rule.startToken
                    if (tok != null) {
                        return tok
                    }
                }
            }
            return null
        }
    }
}
