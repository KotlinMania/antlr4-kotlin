/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarASTErrorNode
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST

/**
 * Factory and tree manipulation helper for pure-Kotlin [GrammarAST] nodes.
 */
open class GrammarASTAdaptor {
    fun nil(): GrammarAST = GrammarAST()

    fun create(token: Token?): GrammarAST = GrammarAST(token)

    fun create(
        tokenType: Int,
        text: String?,
    ): GrammarAST =
        if (tokenType == ANTLRParserTypes.RULE) {
            RuleAST(CommonToken(ANTLRParserTypes.RULE, "RULE"))
        } else {
            GrammarAST(CommonToken(tokenType, text))
        }

    fun create(
        tokenType: Int,
        fromToken: Token?,
        text: String?,
    ): GrammarAST = GrammarAST(tokenType, fromToken, text)

    fun create(
        tokenType: Int,
        fromToken: Token?,
    ): GrammarAST = GrammarAST(tokenType, fromToken)

    fun dupNode(t: GrammarAST?): GrammarAST? = t?.dupNode()

    fun dupTree(t: GrammarAST?): GrammarAST? = t?.dupTree()

    fun errorNode(
        input: TokenStream?,
        start: Token?,
        stop: Token?,
        e: RecognitionException?,
    ): GrammarASTErrorNode = GrammarASTErrorNode(input, start, stop, e)

    fun setTokenBoundaries(
        t: GrammarAST?,
        startToken: Token?,
        stopToken: Token?,
    ) {
        if (t == null) {
            return
        }
        val start = startToken?.tokenIndex ?: -1
        val stop = stopToken?.tokenIndex ?: start
        if (t.isNil) {
            for (i in 0 until t.childCount) {
                val child = t.getChild(i) ?: continue
                if (child.tokenStartIndex < 0 && start >= 0) {
                    child.tokenStartIndex = start
                }
                if (child.tokenStopIndex < 0 && stop >= 0) {
                    child.tokenStopIndex = stop
                }
            }
        } else {
            if (start >= 0) {
                t.tokenStartIndex = start
            }
            if (stop >= 0) {
                t.tokenStopIndex = stop
            }
        }
    }
}
