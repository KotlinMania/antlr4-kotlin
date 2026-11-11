/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.tool.GrammarASTOwner

/**
 * A [CommonToken] extracted from within an embedded action or argument block whose
 * line, column, and character indices are projected back onto the owning grammar's
 * original token stream.
 */
class GrammarToken : CommonToken {
    val g: GrammarASTOwner
    var originalTokenIndex: Int = -1

    constructor(
        g: GrammarASTOwner,
        oldToken: Token,
    ) : super(oldToken) {
        this.g = g
    }

    constructor(
        g: GrammarASTOwner,
        type: Int,
        text: String?,
    ) : super(type, text) {
        this.g = g
    }

    override var charPositionInLine: Int
        get() {
            if (originalTokenIndex == -1) {
                return super.charPositionInLine
            }
            val original = g.originalTokenStream?.get(originalTokenIndex) ?: return super.charPositionInLine
            return if (super.line == 1) {
                original.charPositionInLine + super.charPositionInLine
            } else {
                super.charPositionInLine
            }
        }
        set(value) {
            super.charPositionInLine = value
        }

    override var line: Int
        get() {
            if (originalTokenIndex == -1) {
                return super.line
            }
            val original = g.originalTokenStream?.get(originalTokenIndex) ?: return super.line
            return original.line + super.line - 1
        }
        set(value) {
            super.line = value
        }

    override var tokenIndex: Int
        get() = if (originalTokenIndex >= 0) originalTokenIndex else super.tokenIndex
        set(value) {
            super.tokenIndex = value
        }

    override var startIndex: Int
        get() {
            if (originalTokenIndex >= 0) {
                val original = g.originalTokenStream?.get(originalTokenIndex)
                if (original != null) {
                    return original.startIndex + super.startIndex
                }
            }
            return super.startIndex
        }
        set(value) {
            super.startIndex = value
        }

    override var stopIndex: Int
        get() {
            val n = super.stopIndex - super.startIndex + 1
            return startIndex + n - 1
        }
        set(value) {
            super.stopIndex = value
        }

    override fun toString(): String {
        var channelStr = ""
        if (channel > 0) {
            channelStr = ",channel=$channel"
        }
        var txt = text
        txt =
            if (txt != null) {
                txt
                    .replace("\n", "\\\\n")
                    .replace("\r", "\\\\r")
                    .replace("\t", "\\\\t")
            } else {
                "<no text>"
            }
        return "[@$tokenIndex,$startIndex:$stopIndex='$txt',<$type>$channelStr,$line:$charPositionInLine]"
    }
}
