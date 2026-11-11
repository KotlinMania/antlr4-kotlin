/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.CharStream
import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.CommonTokenFactory
import io.github.kotlinmania.antlr4.IntStream
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenFactory
import io.github.kotlinmania.antlr4.TokenSource
import io.github.kotlinmania.antlr4.TokenSourceCharStream
import io.github.kotlinmania.antlr4.misc.Interval

/**
 * Pure-Kotlin scanner implementing `ActionSplitter.g` to decompose embedded action code
 * and semantic predicates into attribute references (`$x`, `$x.y`, `$x::y`), attribute
 * assignments (`$x = ...;`, `$x::y = ...;`), comments, and verbatim text chunks.
 */
class ActionSplitter(
    private val input: CharStream,
    private val delegate: ActionSplitterListener,
    initialLine: Int = 1,
    initialCharPositionInLine: Int = 0,
) : TokenSource {
    override var line: Int = initialLine
    override var charPositionInLine: Int = initialCharPositionInLine
    override val inputStream: CharStream
        get() = input
    override val sourceName: String
        get() = input.sourceName ?: IntStream.UNKNOWN_SOURCE_NAME
    private var currentTokenFactory: TokenFactory<*> = CommonTokenFactory.DEFAULT
    override val tokenFactory: TokenFactory<*>
        get() = currentTokenFactory

    override fun setTokenFactory(factory: TokenFactory<*>?) {
        currentTokenFactory = factory ?: CommonTokenFactory.DEFAULT
    }

    fun getActionTokens(): List<Token> {
        val chunks = ArrayList<Token>()
        var t = nextToken()
        while (t.type != Token.EOF) {
            chunks.add(t)
            t = nextToken()
        }
        return chunks
    }

    override fun nextToken(): Token {
        val c1 = input.LA(1)
        if (c1 == IntStream.EOF) {
            return makeToken(Token.EOF, null, input.index(), input.index() - 1, line, charPositionInLine)
        }

        val startIdx = input.index()
        val startLine = line
        val startCol = charPositionInLine

        // 1. COMMENT: '/*' .*? '*/'
        if (c1 == '/'.code && input.LA(2) == '*'.code) {
            val saved = saveCursor()
            val commentToken = tryMatchBlockComment(startIdx, startLine, startCol)
            if (commentToken != null) {
                return commentToken
            }
            restoreCursor(saved)
        }

        // 2. LINE_COMMENT: '//' ~('\n'|'\r')* '\r'? '\n'
        if (c1 == '/'.code && input.LA(2) == '/'.code) {
            val saved = saveCursor()
            val lineCommentToken = tryMatchLineComment(startIdx, startLine, startCol)
            if (lineCommentToken != null) {
                return lineCommentToken
            }
            restoreCursor(saved)
        }

        // 3. '$' attribute references and assignments
        if (c1 == '$'.code && isIdStartChar(input.LA(2))) {
            val dollarToken = matchDollarConstruct(startIdx, startLine, startCol)
            if (dollarToken != null) {
                return dollarToken
            }
        }

        // 4. TEXT: (c=~('\\'|'$') | '\\$' | '\\' c=~('$') | '$' {!isIDStartChar(LA(1))}?)+
        return matchTextChunk(startIdx, startLine, startCol)
    }

    private fun tryMatchBlockComment(
        startIdx: Int,
        startLine: Int,
        startCol: Int,
    ): Token? {
        consumeChar() // '/'
        consumeChar() // '*'
        while (input.LA(1) != IntStream.EOF) {
            if (input.LA(1) == '*'.code && input.LA(2) == '/'.code) {
                consumeChar()
                consumeChar()
                val stopIdx = input.index() - 1
                val raw = input.getText(Interval.of(startIdx, stopIdx)).orEmpty()
                delegate.text(raw)
                return makeToken(COMMENT, null, startIdx, stopIdx, startLine, startCol)
            }
            consumeChar()
        }
        return null
    }

    private fun tryMatchLineComment(
        startIdx: Int,
        startLine: Int,
        startCol: Int,
    ): Token? {
        consumeChar() // '/'
        consumeChar() // '/'
        while (input.LA(1) != IntStream.EOF && input.LA(1) != '\n'.code && input.LA(1) != '\r'.code) {
            consumeChar()
        }
        if (input.LA(1) == '\r'.code) {
            consumeChar()
        }
        if (input.LA(1) != '\n'.code) {
            return null
        }
        consumeChar() // '\n'
        val stopIdx = input.index() - 1
        val raw = input.getText(Interval.of(startIdx, stopIdx)).orEmpty()
        delegate.text(raw)
        return makeToken(LINE_COMMENT, null, startIdx, stopIdx, startLine, startCol)
    }

    private fun matchDollarConstruct(
        startIdx: Int,
        startLine: Int,
        startCol: Int,
    ): Token? {
        consumeChar() // '$'
        val xTok = matchIdentifierToken() ?: return null

        // SET_NONLOCAL_ATTR or NONLOCAL_ATTR: '$' x=ID '::' y=ID (WS? '=' expr=ATTR_VALUE_EXPR ';')?
        if (input.LA(1) == ':'.code && input.LA(2) == ':'.code) {
            val afterX = saveCursor()
            consumeChar()
            consumeChar()
            if (isIdStartChar(input.LA(1))) {
                val yTok = matchIdentifierToken()
                if (yTok != null) {
                    val afterY = saveCursor()
                    skipOptionalWhitespace()
                    if (input.LA(1) == '='.code && input.LA(2) != '='.code) {
                        consumeChar() // '='
                        val exprTok = tryMatchAttrValueExpr()
                        if (exprTok != null && input.LA(1) == ';'.code) {
                            consumeChar() // ';'
                            val stopIdx = input.index() - 1
                            val raw = input.getText(Interval.of(startIdx, stopIdx)).orEmpty()
                            delegate.setNonLocalAttr(raw, xTok, yTok, exprTok)
                            return makeToken(SET_NONLOCAL_ATTR, null, startIdx, stopIdx, startLine, startCol)
                        }
                    }
                    restoreCursor(afterY)
                    val stopIdx = input.index() - 1
                    val raw = input.getText(Interval.of(startIdx, stopIdx)).orEmpty()
                    delegate.nonLocalAttr(raw, xTok, yTok)
                    return makeToken(NONLOCAL_ATTR, null, startIdx, stopIdx, startLine, startCol)
                }
            }
            restoreCursor(afterX)
        }

        // QUALIFIED_ATTR: '$' x=ID '.' y=ID {input.LA(1)!='('}?
        if (input.LA(1) == '.'.code && isIdStartChar(input.LA(2))) {
            val afterX = saveCursor()
            consumeChar() // '.'
            val yTok = matchIdentifierToken()
            if (yTok != null && input.LA(1) != '('.code) {
                val stopIdx = input.index() - 1
                val raw = input.getText(Interval.of(startIdx, stopIdx)).orEmpty()
                delegate.qualifiedAttr(raw, xTok, yTok)
                return makeToken(QUALIFIED_ATTR, null, startIdx, stopIdx, startLine, startCol)
            }
            restoreCursor(afterX)
        }

        // SET_ATTR: '$' x=ID WS? '=' expr=ATTR_VALUE_EXPR ';'
        val afterX = saveCursor()
        skipOptionalWhitespace()
        if (input.LA(1) == '='.code && input.LA(2) != '='.code) {
            consumeChar() // '='
            val exprTok = tryMatchAttrValueExpr()
            if (exprTok != null && input.LA(1) == ';'.code) {
                consumeChar() // ';'
                val stopIdx = input.index() - 1
                val raw = input.getText(Interval.of(startIdx, stopIdx)).orEmpty()
                delegate.setAttr(raw, xTok, exprTok)
                return makeToken(SET_ATTR, null, startIdx, stopIdx, startLine, startCol)
            }
        }
        restoreCursor(afterX)

        // ATTR: '$' x=ID
        val stopIdx = input.index() - 1
        val raw = input.getText(Interval.of(startIdx, stopIdx)).orEmpty()
        delegate.attr(raw, xTok)
        return makeToken(ATTR, null, startIdx, stopIdx, startLine, startCol)
    }

    private fun matchIdentifierToken(): CommonToken? {
        if (!isIdStartChar(input.LA(1))) {
            return null
        }
        val idStart = input.index()
        val idLine = line
        val idCol = charPositionInLine
        consumeChar()
        while (isIdPartChar(input.LA(1))) {
            consumeChar()
        }
        val idStop = input.index() - 1
        return makeCommonToken(ID, null, idStart, idStop, idLine, idCol)
    }

    /**
     * `fragment ATTR_VALUE_EXPR : ~'=' (~';')* ;`
     */
    private fun tryMatchAttrValueExpr(): CommonToken? {
        val first = input.LA(1)
        if (first == IntStream.EOF || first == '='.code || first == ';'.code) {
            return null
        }
        val exprStart = input.index()
        val exprLine = line
        val exprCol = charPositionInLine
        consumeChar()
        while (input.LA(1) != IntStream.EOF && input.LA(1) != ';'.code) {
            consumeChar()
        }
        if (input.LA(1) != ';'.code) {
            return null
        }
        val exprStop = input.index() - 1
        return makeCommonToken(ATTR_VALUE_EXPR, null, exprStart, exprStop, exprLine, exprCol)
    }

    private fun skipOptionalWhitespace() {
        while (true) {
            val c = input.LA(1)
            if (c == ' '.code || c == '\t'.code || c == '\n'.code || c == '\r'.code) {
                consumeChar()
            } else {
                break
            }
        }
    }

    private fun matchTextChunk(
        startIdx: Int,
        startLine: Int,
        startCol: Int,
    ): Token {
        val buf = StringBuilder()
        while (input.LA(1) != IntStream.EOF) {
            val c = input.LA(1)
            if (buf.isNotEmpty() && c == '/'.code && hasCompleteCommentAhead()) {
                break
            }
            if (c != '\\'.code && c != '$'.code) {
                consumeChar()
                buf.append(c.toChar())
            } else if (c == '\\'.code) {
                val next = input.LA(2)
                if (next == '$'.code) {
                    consumeChar()
                    consumeChar()
                    buf.append('$')
                } else if (next != IntStream.EOF) {
                    consumeChar()
                    consumeChar()
                    buf.append('\\').append(next.toChar())
                } else {
                    consumeChar()
                    buf.append('\\')
                }
            } else {
                // c == '$'
                if (!isIdStartChar(input.LA(2))) {
                    consumeChar()
                    buf.append('$')
                } else {
                    break
                }
            }
        }
        val stopIdx = input.index() - 1
        delegate.text(buf.toString())
        return makeToken(TEXT, null, startIdx, stopIdx, startLine, startCol)
    }

    private fun hasCompleteCommentAhead(): Boolean {
        val c2 = input.LA(2)
        if (c2 == '/'.code) {
            var k = 3
            while (true) {
                val la = input.LA(k)
                if (la == IntStream.EOF) return false
                if (la == '\n'.code) return true
                k++
            }
        }
        if (c2 == '*'.code) {
            var k = 3
            while (true) {
                val la = input.LA(k)
                if (la == IntStream.EOF) return false
                if (la == '*'.code && input.LA(k + 1) == '/'.code) return true
                k++
            }
        }
        return false
    }

    private fun consumeChar() {
        val c = input.LA(1)
        if (c == IntStream.EOF) {
            return
        }
        input.consume()
        if (c == '\n'.code) {
            line++
            charPositionInLine = 0
        } else {
            charPositionInLine++
        }
    }

    private class CursorState(
        val index: Int,
        val line: Int,
        val col: Int,
    )

    private fun saveCursor(): CursorState = CursorState(input.index(), line, charPositionInLine)

    private fun restoreCursor(state: CursorState) {
        input.seek(state.index)
        line = state.line
        charPositionInLine = state.col
    }

    private fun makeCommonToken(
        type: Int,
        overrideText: String?,
        startIdx: Int,
        stopIdx: Int,
        tokLine: Int,
        tokCol: Int,
    ): CommonToken {
        val tok =
            CommonToken(
                TokenSourceCharStream(this, input),
                type,
                Token.DEFAULT_CHANNEL,
                startIdx,
                stopIdx,
            )
        if (overrideText != null) {
            tok.text = overrideText
        }
        tok.line = tokLine
        tok.charPositionInLine = tokCol
        return tok
    }

    private fun makeToken(
        type: Int,
        overrideText: String?,
        startIdx: Int,
        stopIdx: Int,
        tokLine: Int,
        tokCol: Int,
    ): Token =
        tokenFactory.create(
            TokenSourceCharStream(this, input),
            type,
            overrideText,
            Token.DEFAULT_CHANNEL,
            startIdx,
            stopIdx,
            tokLine,
            tokCol,
        ) ?: makeCommonToken(type, overrideText, startIdx, stopIdx, tokLine, tokCol)

    private fun isIdStartChar(c: Int): Boolean {
        if (c == IntStream.EOF) return false
        val ch = c.toChar()
        return ch == '_' || ch in 'a'..'z' || ch in 'A'..'Z' || (c > 0x7F && ch.isLetter())
    }

    private fun isIdPartChar(c: Int): Boolean {
        if (c == IntStream.EOF) return false
        val ch = c.toChar()
        return ch == '_' || ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z' || (c > 0x7F && ch.isLetterOrDigit())
    }

    companion object {
        const val COMMENT: Int = 4
        const val LINE_COMMENT: Int = 5
        const val SET_NONLOCAL_ATTR: Int = 6
        const val NONLOCAL_ATTR: Int = 7
        const val QUALIFIED_ATTR: Int = 8
        const val SET_ATTR: Int = 9
        const val ATTR: Int = 10
        const val TEXT: Int = 11
        const val ATTR_VALUE_EXPR: Int = 12
        const val ID: Int = 13
        const val WS: Int = 14
    }
}
