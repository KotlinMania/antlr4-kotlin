/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.InputMismatchException
import io.github.kotlinmania.antlr4.NoViableAltException
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.misc.Interval

/**
 * AST node representing an erroneous token range in a `.g4` token stream.
 */
class GrammarASTErrorNode(
    val input: TokenStream?,
    val start: Token?,
    val stop: Token?,
    internal val trappedException: RecognitionException?,
) : GrammarAST() {
    override val isNil: Boolean
        get() = false

    override var type: Int
        get() = Token.INVALID_TYPE
        set(_) {}

    override var text: String
        get() {
            val startTok = start ?: return ""
            val stopTok = stop
            val stream = input ?: return "<bad token>"
            val begin = startTok.tokenIndex
            var end =
                when {
                    stopTok == null -> begin
                    stopTok.type == Token.EOF -> stream.size() - 1
                    else -> stopTok.tokenIndex
                }
            if (end < begin) {
                end = begin
            }
            if (begin < 0 || begin >= stream.size()) {
                return "<EOF>"
            }
            return stream.getText(Interval.of(begin, end)) ?: ""
        }
        set(_) {}

    override fun toString(): String {
        if (start?.type == Token.EOF) {
            return "<EOF>"
        }
        val ex = trappedException
        return when (ex) {
            is InputMismatchException -> "<mismatched token: $start, resync=$text>"
            is NoViableAltException -> "<unexpected: $start, resync=$text>"
            null -> "<error: $text>"
            else -> "<unexpected: $start, resync=$text>"
        }
    }
}
