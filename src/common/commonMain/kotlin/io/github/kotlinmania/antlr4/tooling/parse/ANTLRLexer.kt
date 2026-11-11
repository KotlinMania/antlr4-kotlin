/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.CharStream
import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.IntStream
import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenSourceCharStream
import io.github.kotlinmania.antlr4.Vocabulary
import io.github.kotlinmania.antlr4.VocabularyImpl
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNType
import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType

/**
 * Pure-Kotlin ANTLR4 `.g4` grammar lexer implementing the complete lexical specification
 * of `ANTLRLexer.g` directly on top of [Lexer].
 */
open class ANTLRLexer : Lexer {
    var tokens: CommonTokenStream? = null
    var isLexerRule: Boolean = false
    var syntaxErrors: Int = 0

    private var currentLine: Int = 1
    private var currentCharPositionInLine: Int = 0
    private val emittedTokens: MutableList<Token> = ArrayList()

    constructor() : super()

    constructor(input: CharStream?) : super(input)

    override var line: Int
        get() = currentLine
        set(line) {
            currentLine = line
        }

    override var charPositionInLine: Int
        get() = currentCharPositionInLine
        set(charPositionInLine) {
            currentCharPositionInLine = charPositionInLine
        }

    override val grammarFileName: String
        get() = "ANTLRLexer.g4"

    override val ruleNames: List<String>
        get() = RULE_NAMES

    override val tokenNames: List<String>
        get() = ANTLRParserTypes.tokenNames

    override val vocabulary: Vocabulary
        get() = VOCABULARY

    override val atn: ATN
        get() = LEXER_ATN

    override fun reset() {
        super.reset()
        currentLine = 1
        currentCharPositionInLine = 0
        isLexerRule = false
        syntaxErrors = 0
        emittedTokens.clear()
    }

    open fun grammarError(
        etype: ErrorType,
        token: Token,
        vararg args: Any?,
    ) {
    }

    open fun displayRecognitionError(e: RecognitionException) {
    }

    fun displayRecognitionErrorForTokenNames(
        tokenNames: List<String>,
        e: RecognitionException,
    ) {
        displayRecognitionErrorWithTokenNames(tokenNames, e)
    }

    internal open fun displayRecognitionErrorWithTokenNames(
        tokenNames: List<String>,
        e: RecognitionException,
    ) {
        displayRecognitionError(e)
    }

    fun getErrorMessage(
        e: RecognitionException,
        tokenNames: List<String>,
    ): String = e.message ?: "syntax error"

    /**
     * Scan backwards from the current point in [tokens] (or [emittedTokens] if no
     * [CommonTokenStream] was attached) looking for the start of the rule or subrule.
     */
    fun getRuleOrSubruleStartToken(): Token? {
        val stream = tokens
        val size: Int
        var idx: Int
        if (stream != null && stream.size() > 0) {
            size = stream.size()
            idx = stream.index()
            if (idx >= size) {
                idx = size - 1
            }
        } else {
            size = emittedTokens.size
            idx = size - 1
        }
        var withinOptionsBlock = false
        while (idx in 0 until size) {
            val tok = stream?.get(idx) ?: emittedTokens[idx]
            val ttype = tok.type
            if (withinOptionsBlock) {
                if (ttype == ANTLRParserTypes.OPTIONS) {
                    withinOptionsBlock = false
                }
            } else {
                if (ttype == ANTLRParserTypes.RBRACE) {
                    withinOptionsBlock = true
                } else if (
                    ttype == ANTLRParserTypes.LPAREN ||
                    ttype == ANTLRParserTypes.TOKEN_REF ||
                    ttype == ANTLRParserTypes.RULE_REF
                ) {
                    return tok
                }
            }
            idx--
        }
        return null
    }

    override fun nextToken(): Token {
        val stream = checkNotNull(input) { "nextToken requires a non-null input stream." }
        val tokenStartMarker = stream.mark()
        try {
            while (true) {
                if (hitEOF || stream.LA(1) == IntStream.EOF) {
                    hitEOF = true
                    return checkNotNull(emitEOF()) { "emitEOF returned null" }
                }

                setToken(null)
                channel = Token.DEFAULT_CHANNEL
                tokenStartCharIndex = stream.index()
                tokenStartCharPositionInLine = currentCharPositionInLine
                tokenStartLine = currentLine
                text = ""
                resetCustomText()

                type = Token.INVALID_TYPE
                matchNextRule(stream)

                if (type == SKIP) {
                    continue
                }
                val out = token ?: checkNotNull(emit()) { "emit returned null" }
                emittedTokens.add(out)
                return out
            }
        } finally {
            stream.release(tokenStartMarker)
        }
    }

    private var hasCustomText: Boolean = false
    private var customTextValue: String? = null

    private fun resetCustomText() {
        hasCustomText = false
        customTextValue = null
    }

    private fun overrideTokenText(newText: String) {
        hasCustomText = true
        customTextValue = newText
        text = newText
    }

    private fun consumeChar(stream: CharStream): Int {
        val c = stream.LA(1)
        if (c == IntStream.EOF) {
            return IntStream.EOF
        }
        if (c == '\r'.code) {
            stream.consume()
            if (stream.LA(1) == '\n'.code) {
                // Keep position tracking accurate when caller consumes \n next;
                // if \r is standalone, advance line immediately.
                currentCharPositionInLine++
            } else {
                currentLine++
                currentCharPositionInLine = 0
            }
            return c
        }
        if (c == '\n'.code) {
            stream.consume()
            currentLine++
            currentCharPositionInLine = 0
            return c
        }
        stream.consume()
        currentCharPositionInLine++
        return c
    }

    private fun savePosition(stream: CharStream): LexerPosition =
        LexerPosition(stream.index(), currentLine, currentCharPositionInLine)

    private fun restorePosition(
        stream: CharStream,
        pos: LexerPosition,
    ) {
        stream.seek(pos.index)
        currentLine = pos.line
        currentCharPositionInLine = pos.charPositionInLine
    }

    private fun matchNextRule(stream: CharStream) {
        val c = stream.LA(1)
        when (c) {
            0xFEFF -> {
                consumeChar(stream)
                skip()
            }

            ' '.code, '\t'.code, '\r'.code, '\n'.code, '\u000C'.code -> {
                while (isWsNlChar(stream.LA(1))) {
                    consumeChar(stream)
                }
                type = ANTLRParserTypes.WS
                channel = HIDDEN
            }

            '/'.code -> {
                val c2 = stream.LA(2)
                if (c2 == '/'.code) {
                    matchSingleLineComment(stream)
                } else if (c2 == '*'.code) {
                    matchMultiLineComment(stream)
                } else {
                    // Standalone '/' followed by neither '/' nor '*': ANTLRLexer.g COMMENT has an empty 3rd alt!
                    // Wait: verify ANTLRLexer.g lines 270-284: '/' alone matches COMMENT on COMMENTS_CHANNEL!
                    consumeChar(stream)
                    type = ANTLRParserTypes.COMMENT
                    channel = COMMENTS_CHANNEL
                }
            }

            '['.code -> {
                if (isLexerRule) {
                    if (!tryMatchLexerCharSet(stream)) {
                        matchErrChar(stream)
                    }
                } else {
                    if (!tryMatchArgAction(stream)) {
                        matchErrChar(stream)
                    }
                }
            }

            '{'.code -> {
                matchActionOrSempred(stream)
            }

            '\''.code -> {
                matchStringLiteral(stream)
            }

            ':'.code -> {
                consumeChar(stream)
                if (stream.LA(1) == ':'.code) {
                    consumeChar(stream)
                    type = ANTLRParserTypes.COLONCOLON
                } else {
                    type = ANTLRParserTypes.COLON
                    val startTok = getRuleOrSubruleStartToken()
                    if (startTok != null) {
                        if (startTok.type == ANTLRParserTypes.RULE_REF) {
                            isLexerRule = false
                        } else if (startTok.type == ANTLRParserTypes.TOKEN_REF) {
                            isLexerRule = true
                        }
                    }
                }
            }

            ','.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.COMMA
            }

            ';'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.SEMI
            }

            '('.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.LPAREN
            }

            ')'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.RPAREN
            }

            '-'.code -> {
                if (stream.LA(2) == '>'.code) {
                    consumeChar(stream)
                    consumeChar(stream)
                    type = ANTLRParserTypes.RARROW
                } else {
                    matchErrChar(stream)
                }
            }

            '<'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.LT
            }

            '>'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.GT
            }

            '='.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.ASSIGN
            }

            '?'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.QUESTION
            }

            '*'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.STAR
            }

            '+'.code -> {
                consumeChar(stream)
                if (stream.LA(1) == '='.code) {
                    consumeChar(stream)
                    type = ANTLRParserTypes.PLUS_ASSIGN
                } else {
                    type = ANTLRParserTypes.PLUS
                }
            }

            '|'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.OR
            }

            '$'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.DOLLAR
            }

            '.'.code -> {
                consumeChar(stream)
                if (stream.LA(1) == '.'.code) {
                    consumeChar(stream)
                    type = ANTLRParserTypes.RANGE
                } else {
                    type = ANTLRParserTypes.DOT
                }
            }

            '@'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.AT
            }

            '#'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.POUND
            }

            '~'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.NOT
            }

            '}'.code -> {
                consumeChar(stream)
                type = ANTLRParserTypes.RBRACE
            }

            in '0'.code..'9'.code -> {
                while (stream.LA(1) in '0'.code..'9'.code) {
                    consumeChar(stream)
                }
                type = ANTLRParserTypes.INT
            }

            else -> {
                if (isNameStartChar(c)) {
                    matchIdentifierOrKeyword(stream)
                } else {
                    matchErrChar(stream)
                }
            }
        }

        if (!hasCustomText && type != SKIP) {
            val stopIdx = stream.index() - 1
            val raw =
                if (stopIdx >= tokenStartCharIndex) {
                    stream.getText(Interval.of(tokenStartCharIndex, stopIdx)) ?: ""
                } else {
                    ""
                }
            text = raw
        }
    }

    private fun matchSingleLineComment(stream: CharStream) {
        consumeChar(stream) // '/'
        consumeChar(stream) // '/'
        while (true) {
            val la = stream.LA(1)
            if (la == IntStream.EOF || la == '\r'.code || la == '\n'.code) {
                break
            }
            consumeChar(stream)
        }
        type = ANTLRParserTypes.COMMENT
        channel = COMMENTS_CHANNEL
    }

    private fun matchMultiLineComment(stream: CharStream) {
        consumeChar(stream) // '/'
        consumeChar(stream) // '*'
        var tokType = ANTLRParserTypes.COMMENT
        if (stream.LA(1) == '*'.code && stream.LA(2) != '/'.code) {
            consumeChar(stream)
            tokType = ANTLRParserTypes.DOC_COMMENT
        }
        while (stream.LA(1) != IntStream.EOF && !(stream.LA(1) == '*'.code && stream.LA(2) == '/'.code)) {
            consumeChar(stream)
        }
        if (stream.LA(1) == '*'.code && stream.LA(2) == '/'.code) {
            consumeChar(stream)
            consumeChar(stream)
        }
        type = tokType
        channel = COMMENTS_CHANNEL
    }

    private fun tryMatchLexerCharSet(stream: CharStream): Boolean {
        val saved = savePosition(stream)
        consumeChar(stream) // '['
        while (true) {
            val la = stream.LA(1)
            if (la == ']'.code) {
                consumeChar(stream)
                type = ANTLRParserTypes.LEXER_CHAR_SET
                return true
            }
            if (la == IntStream.EOF || la == '\r'.code || la == '\n'.code) {
                restorePosition(stream, saved)
                return false
            }
            if (la == '\\'.code) {
                consumeChar(stream)
                val esc = stream.LA(1)
                if (esc == IntStream.EOF || esc == '\r'.code || esc == '\n'.code) {
                    restorePosition(stream, saved)
                    return false
                }
                consumeChar(stream)
            } else {
                consumeChar(stream)
            }
        }
    }

    private fun tryMatchArgAction(stream: CharStream): Boolean {
        val saved = savePosition(stream)
        if (!consumeNestedArgAction(stream)) {
            restorePosition(stream, saved)
            return false
        }
        val innerStart = tokenStartCharIndex + 1
        val innerStop = stream.index() - 2
        val raw =
            if (innerStop >= innerStart) {
                stream.getText(Interval.of(innerStart, innerStop)) ?: ""
            } else {
                ""
            }
        type = ANTLRParserTypes.ARG_ACTION
        overrideTokenText(raw)
        return true
    }

    private fun consumeNestedArgAction(stream: CharStream): Boolean {
        if (stream.LA(1) != '['.code) {
            return false
        }
        consumeChar(stream) // '['
        while (true) {
            when (val la = stream.LA(1)) {
                IntStream.EOF -> return false
                ']'.code -> {
                    consumeChar(stream)
                    return true
                }
                '['.code -> {
                    if (!consumeNestedArgAction(stream)) {
                        return false
                    }
                }
                '"'.code -> {
                    if (!consumeActionStringLiteral(stream)) {
                        return false
                    }
                }
                '\''.code -> {
                    if (!consumeActionCharLiteral(stream)) {
                        return false
                    }
                }
                else -> {
                    if (la == '\\'.code && stream.LA(2) != IntStream.EOF) {
                        consumeChar(stream)
                    }
                    consumeChar(stream)
                }
            }
        }
    }

    private fun consumeActionStringLiteral(stream: CharStream): Boolean {
        consumeChar(stream) // '"'
        while (true) {
            val la = stream.LA(1)
            if (la == IntStream.EOF) {
                return false
            }
            if (la == '"'.code) {
                consumeChar(stream)
                return true
            }
            if (la == '\\'.code) {
                consumeChar(stream)
                if (stream.LA(1) == IntStream.EOF) {
                    return false
                }
                consumeChar(stream)
            } else {
                consumeChar(stream)
            }
        }
    }

    private fun consumeActionCharLiteral(stream: CharStream): Boolean {
        consumeChar(stream) // '\''
        while (true) {
            val la = stream.LA(1)
            if (la == IntStream.EOF) {
                return false
            }
            if (la == '\''.code) {
                consumeChar(stream)
                return true
            }
            if (la == '\\'.code) {
                consumeChar(stream)
                if (stream.LA(1) == IntStream.EOF) {
                    return false
                }
                consumeChar(stream)
            } else {
                consumeChar(stream)
            }
        }
    }

    private fun matchActionOrSempred(stream: CharStream) {
        consumeNestedAction(stream)
        if (stream.LA(1) == '?'.code) {
            consumeChar(stream)
            type = ANTLRParserTypes.SEMPRED
        } else {
            type = ANTLRParserTypes.ACTION
        }
    }

    private fun consumeNestedAction(stream: CharStream) {
        consumeChar(stream) // '{'
        while (true) {
            when (val la = stream.LA(1)) {
                IntStream.EOF -> return
                '}'.code -> {
                    consumeChar(stream)
                    return
                }
                '{'.code -> consumeNestedAction(stream)
                '\''.code -> {
                    consumeChar(stream)
                    while (stream.LA(1) != IntStream.EOF && stream.LA(1) != '\''.code) {
                        if (stream.LA(1) == '\\'.code) {
                            consumeChar(stream)
                            if (stream.LA(1) != IntStream.EOF) {
                                consumeChar(stream)
                            }
                        } else {
                            consumeChar(stream)
                        }
                    }
                    if (stream.LA(1) == '\''.code) {
                        consumeChar(stream)
                    }
                }
                '"'.code -> {
                    consumeChar(stream)
                    while (stream.LA(1) != IntStream.EOF && stream.LA(1) != '"'.code) {
                        if (stream.LA(1) == '\\'.code) {
                            consumeChar(stream)
                            if (stream.LA(1) != IntStream.EOF) {
                                consumeChar(stream)
                            }
                        } else {
                            consumeChar(stream)
                        }
                    }
                    if (stream.LA(1) == '"'.code) {
                        consumeChar(stream)
                    }
                }
                '/'.code -> {
                    val c2 = stream.LA(2)
                    if (c2 == '/'.code) {
                        consumeChar(stream)
                        consumeChar(stream)
                        while (
                            stream.LA(1) != IntStream.EOF &&
                            stream.LA(1) != '\r'.code &&
                            stream.LA(1) != '\n'.code
                        ) {
                            consumeChar(stream)
                        }
                    } else if (c2 == '*'.code) {
                        consumeChar(stream)
                        consumeChar(stream)
                        while (
                            stream.LA(1) != IntStream.EOF &&
                            !(stream.LA(1) == '*'.code && stream.LA(2) == '/'.code)
                        ) {
                            consumeChar(stream)
                        }
                        if (stream.LA(1) == '*'.code && stream.LA(2) == '/'.code) {
                            consumeChar(stream)
                            consumeChar(stream)
                        }
                    } else {
                        consumeChar(stream)
                    }
                }
                '\\'.code -> {
                    consumeChar(stream)
                    if (stream.LA(1) != IntStream.EOF) {
                        consumeChar(stream)
                    }
                }
                else -> consumeChar(stream)
            }
        }
    }

    private fun matchStringLiteral(stream: CharStream) {
        type = ANTLRParserTypes.STRING_LITERAL
        consumeChar(stream) // opening '\''
        while (true) {
            val la = stream.LA(1)
            if (la == IntStream.EOF || la == '\''.code || la == '\r'.code || la == '\n'.code) {
                break
            }
            if (la == '\\'.code) {
                matchEscapeSequenceInString(stream)
            } else {
                consumeChar(stream)
            }
        }
        if (stream.LA(1) == '\''.code) {
            consumeChar(stream)
        } else {
            val stopIdx = maxOf(tokenStartCharIndex, stream.index() - 1)
            val errTok =
                CommonToken(
                    TokenSourceCharStream(this, stream),
                    ANTLRParserTypes.STRING_LITERAL,
                    channel,
                    tokenStartCharIndex,
                    stopIdx,
                )
            errTok.line = tokenStartLine
            errTok.charPositionInLine = tokenStartCharPositionInLine
            errTok.text = stream.getText(Interval.of(tokenStartCharIndex, stopIdx)) ?: "'"
            grammarError(ErrorType.UNTERMINATED_STRING_LITERAL, errTok)
        }
    }

    private fun matchEscapeSequenceInString(stream: CharStream) {
        val escStartIdx = stream.index()
        val escStartLine = currentLine
        val escStartCol = currentCharPositionInLine
        consumeChar(stream) // '\\'
        val next = stream.LA(1)
        when (next) {
            'b'.code, 't'.code, 'n'.code, 'f'.code, 'r'.code, '\''.code, '\\'.code -> {
                consumeChar(stream)
            }
            'u'.code -> {
                if (stream.LA(2) == '{'.code && isExtendedUnicodeEscape(stream)) {
                    consumeChar(stream) // 'u'
                    consumeChar(stream) // '{'
                    var numDigits = 0
                    while (isHexDigit(stream.LA(1))) {
                        consumeChar(stream)
                        numDigits++
                    }
                    consumeChar(stream) // '}'
                    if (numDigits > 6) {
                        val stopIdx = stream.index() - 1
                        val bad = stream.getText(Interval.of(escStartIdx, stopIdx)) ?: ""
                        val errTok =
                            CommonToken(
                                TokenSourceCharStream(this, stream),
                                ANTLRParserTypes.STRING_LITERAL,
                                channel,
                                escStartIdx,
                                stopIdx,
                            )
                        errTok.line = escStartLine
                        errTok.charPositionInLine = escStartCol
                        errTok.text = bad
                        grammarError(ErrorType.INVALID_ESCAPE_SEQUENCE, errTok, bad)
                    }
                } else {
                    consumeChar(stream) // 'u'
                    var hexCount = 0
                    while (hexCount < 4 && isHexDigit(stream.LA(1))) {
                        consumeChar(stream)
                        hexCount++
                    }
                    if (hexCount < 4) {
                        val nextChar = stream.LA(1)
                        val badStop =
                            if (
                                nextChar == IntStream.EOF ||
                                nextChar == '\''.code ||
                                nextChar == '\r'.code ||
                                nextChar == '\n'.code
                            ) {
                                stream.index() - 1
                            } else {
                                stream.index()
                            }
                        val bad = stream.getText(Interval.of(escStartIdx, badStop)) ?: ""
                        val errTok =
                            CommonToken(
                                TokenSourceCharStream(this, stream),
                                ANTLRParserTypes.STRING_LITERAL,
                                channel,
                                escStartIdx,
                                badStop,
                            )
                        errTok.line = escStartLine
                        errTok.charPositionInLine = escStartCol
                        errTok.text = bad
                        grammarError(ErrorType.INVALID_ESCAPE_SEQUENCE, errTok, bad)
                    }
                }
            }
            IntStream.EOF, '\r'.code, '\n'.code -> {
                // Leave newline/EOF for outer UNTERMINATED_STRING_LITERAL check
            }
            else -> {
                consumeChar(stream)
                val stopIdx = stream.index() - 1
                val bad = stream.getText(Interval.of(escStartIdx, stopIdx)) ?: ""
                val errTok =
                    CommonToken(
                        TokenSourceCharStream(this, stream),
                        ANTLRParserTypes.STRING_LITERAL,
                        channel,
                        escStartIdx,
                        stopIdx,
                    )
                errTok.line = escStartLine
                errTok.charPositionInLine = escStartCol
                errTok.text = bad
                grammarError(ErrorType.INVALID_ESCAPE_SEQUENCE, errTok, bad)
            }
        }
    }

    private fun isExtendedUnicodeEscape(stream: CharStream): Boolean {
        // stream.LA(1) == 'u', stream.LA(2) == '{'
        var k = 3
        if (!isHexDigit(stream.LA(k))) {
            return false
        }
        while (isHexDigit(stream.LA(k))) {
            k++
        }
        return stream.LA(k) == '}'.code
    }

    private fun matchIdentifierOrKeyword(stream: CharStream) {
        val firstCharCode = consumeChar(stream)
        while (isNameChar(stream.LA(1))) {
            consumeChar(stream)
        }
        val ident = stream.getText(Interval.of(tokenStartCharIndex, stream.index() - 1)) ?: ""

        if (ident == "options" || ident == "tokens" || ident == "channels") {
            val afterIdent = savePosition(stream)
            while (isWsNlChar(stream.LA(1))) {
                consumeChar(stream)
            }
            if (stream.LA(1) == '{'.code) {
                consumeChar(stream)
                type =
                    when (ident) {
                        "options" -> ANTLRParserTypes.OPTIONS
                        "tokens" -> ANTLRParserTypes.TOKENS_SPEC
                        else -> ANTLRParserTypes.CHANNELS
                    }
                return
            }
            restorePosition(stream, afterIdent)
        }

        val keywordType = KEYWORDS[ident]
        if (keywordType != null) {
            type = keywordType
            return
        }

        val firstStr = codePointToString(firstCharCode)
        type =
            if (ANTLRParserTypes.isTokenName(firstStr)) {
                ANTLRParserTypes.TOKEN_REF
            } else {
                ANTLRParserTypes.RULE_REF
            }
    }

    private fun matchErrChar(stream: CharStream) {
        consumeChar(stream)
        val stopIdx = stream.index() - 1
        val errText = stream.getText(Interval.of(tokenStartCharIndex, stopIdx)) ?: ""
        val errTok =
            CommonToken(
                TokenSourceCharStream(this, stream),
                ANTLRParserTypes.ERRCHAR,
                Token.DEFAULT_CHANNEL,
                tokenStartCharIndex,
                stopIdx,
            )
        errTok.line = tokenStartLine
        errTok.charPositionInLine = tokenStartCharPositionInLine
        errTok.text = errText
        val msg = "${getTokenErrorDisplay(errTok)} came as a complete surprise to me"
        grammarError(ErrorType.SYNTAX_ERROR, errTok, msg)
        syntaxErrors++
        skip()
    }

    private data class LexerPosition(
        val index: Int,
        val line: Int,
        val charPositionInLine: Int,
    )

    companion object {
        const val COMMENTS_CHANNEL: Int = ANTLRParserTypes.COMMENTS_CHANNEL

        private val LEXER_ATN: ATN = ATN(ATNType.LEXER, ANTLRParserTypes.ERRCHAR)

        private val KEYWORDS: Map<String, Int> =
            mapOf(
                "import" to ANTLRParserTypes.IMPORT,
                "fragment" to ANTLRParserTypes.FRAGMENT,
                "lexer" to ANTLRParserTypes.LEXER,
                "parser" to ANTLRParserTypes.PARSER,
                "grammar" to ANTLRParserTypes.GRAMMAR,
                "returns" to ANTLRParserTypes.RETURNS,
                "locals" to ANTLRParserTypes.LOCALS,
                "throws" to ANTLRParserTypes.THROWS,
                "catch" to ANTLRParserTypes.CATCH,
                "finally" to ANTLRParserTypes.FINALLY,
                "mode" to ANTLRParserTypes.MODE,
            )

        private val RULE_NAMES: List<String> =
            listOf(
                "COMMENT",
                "ARG_OR_CHARSET",
                "ACTION",
                "OPTIONS",
                "TOKENS_SPEC",
                "CHANNELS",
                "IMPORT",
                "FRAGMENT",
                "LEXER",
                "PARSER",
                "GRAMMAR",
                "RETURNS",
                "LOCALS",
                "THROWS",
                "CATCH",
                "FINALLY",
                "MODE",
                "COLON",
                "COLONCOLON",
                "COMMA",
                "SEMI",
                "LPAREN",
                "RPAREN",
                "RARROW",
                "LT",
                "GT",
                "ASSIGN",
                "QUESTION",
                "STAR",
                "PLUS",
                "PLUS_ASSIGN",
                "OR",
                "DOLLAR",
                "DOT",
                "RANGE",
                "AT",
                "POUND",
                "NOT",
                "RBRACE",
                "ID",
                "INT",
                "STRING_LITERAL",
                "WS",
                "UNICODE_BOM",
                "ERRCHAR",
            )

        private val VOCABULARY: Vocabulary =
            VocabularyImpl(
                emptyList(),
                ANTLRParserTypes.tokenNames,
                emptyList(),
            )

        private fun isWsNlChar(c: Int): Boolean =
            c == ' '.code ||
                c == '\t'.code ||
                c == '\u000C'.code ||
                c == '\n'.code ||
                c == '\r'.code

        private fun isHexDigit(c: Int): Boolean =
            c in '0'.code..'9'.code ||
                c in 'a'.code..'f'.code ||
                c in 'A'.code..'F'.code

        private fun isNameStartChar(c: Int): Boolean =
            c in 'A'.code..'Z'.code ||
                c in 'a'.code..'z'.code ||
                c in 0x00C0..0x00D6 ||
                c in 0x00D8..0x00F6 ||
                c in 0x00F8..0x02FF ||
                c in 0x0370..0x037D ||
                c in 0x037F..0x1FFF ||
                c in 0x200C..0x200D ||
                c in 0x2070..0x218F ||
                c in 0x2C00..0x2FEF ||
                c in 0x3001..0xD7FF ||
                c in 0xF900..0xFDCF ||
                c in 0xFDF0..0xFEFE ||
                c in 0xFF00..0xFFFD ||
                c in 0x10000..0xEFFFF

        private fun isNameChar(c: Int): Boolean =
            isNameStartChar(c) ||
                c in '0'.code..'9'.code ||
                c == '_'.code ||
                c == 0x00B7 ||
                c in 0x0300..0x036F ||
                c in 0x203F..0x2040

        private fun codePointToString(cp: Int): String =
            if (cp <= 0xFFFF) {
                cp.toChar().toString()
            } else {
                val offset = cp - 0x10000
                val high = ((offset ushr 10) + 0xD800).toChar()
                val low = ((offset and 0x3FF) + 0xDC00).toChar()
                charArrayOf(high, low).concatToString()
            }
    }
}
