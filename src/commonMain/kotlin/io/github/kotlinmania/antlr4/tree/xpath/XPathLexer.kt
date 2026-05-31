package io.github.kotlinmania.antlr4.tree.xpath

import io.github.kotlinmania.antlr4.CharStream
import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.IntStream
import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.LexerNoViableAltException
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.Vocabulary
import io.github.kotlinmania.antlr4.VocabularyImpl
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNType
import io.github.kotlinmania.antlr4.misc.Interval

open class XPathLexer(
    input: CharStream,
) : Lexer(input) {
    override val grammarFileName: String = "XPathLexer.g4"

    override val ruleNames: Array<String>
        get() = Companion.ruleNames

    override val tokenNames: Array<String?>?
        get() = Array(Companion.tokenNames.size) { Companion.tokenNames[it] }

    override val vocabulary: Vocabulary
        get() = Companion.VOCABULARY

    override val atn: ATN = ATN(ATNType.LEXER, 8)

    override var line: Int = 1
    override var charPositionInLine: Int = 0

    override fun nextToken(): Token {
        _tokenStartCharIndex = _input!!.index()
        var t: CommonToken? = null
        while (t == null) {
            when (_input!!.LA(1)) {
                '/'.code -> {
                    consume()
                    if (_input!!.LA(1) == '/'.code) {
                        consume()
                        t = CommonToken(ANYWHERE, "//")
                    } else {
                        t = CommonToken(ROOT, "/")
                    }
                }
                '*'.code -> {
                    consume()
                    t = CommonToken(WILDCARD, "*")
                }
                '!'.code -> {
                    consume()
                    t = CommonToken(BANG, "!")
                }
                '\''.code -> {
                    val s = matchString()
                    t = CommonToken(STRING, s)
                }
                IntStream.EOF -> return CommonToken(EOF, "<EOF>")
                else -> {
                    if (isNameStartChar(_input!!.LA(1))) {
                        val id = matchID()
                        t = if (id[0].isUpperCase()) CommonToken(TOKEN_REF, id) else CommonToken(RULE_REF, id)
                    } else {
                        throw LexerNoViableAltException(this, _input, _tokenStartCharIndex, null)
                    }
                }
            }
        }
        t.startIndex = _tokenStartCharIndex
        t.charPositionInLine = _tokenStartCharIndex
        t.line = line
        return t
    }

    fun consume() {
        val curChar = _input!!.LA(1)
        if (curChar == '\n'.code) {
            line++
            charPositionInLine = 0
        } else {
            charPositionInLine++
        }
        _input!!.consume()
    }

    fun matchID(): String {
        val start = _input!!.index()
        consume()
        while (isNameChar(_input!!.LA(1))) {
            consume()
        }
        return _input!!.getText(Interval.of(start, _input!!.index() - 1)) ?: ""
    }

    fun matchString(): String {
        val start = _input!!.index()
        consume()
        while (_input!!.LA(1) != '\''.code) {
            consume()
        }
        consume()
        return _input!!.getText(Interval.of(start, _input!!.index() - 1)) ?: ""
    }

    fun isNameChar(c: Int): Boolean = c != IntStream.EOF && (c.toChar().isLetterOrDigit() || c.toChar() == '_')

    fun isNameStartChar(c: Int): Boolean = c != IntStream.EOF && (c.toChar().isLetter() || c.toChar() == '_')

    companion object {
        const val TOKEN_REF: Int = 1
        const val RULE_REF: Int = 2
        const val ANYWHERE: Int = 3
        const val ROOT: Int = 4
        const val WILDCARD: Int = 5
        const val BANG: Int = 6
        const val ID: Int = 7
        const val STRING: Int = 8

        val modeNames: Array<String> = arrayOf("DEFAULT_MODE")

        val ruleNames: Array<String> =
            arrayOf(
                "ANYWHERE",
                "ROOT",
                "WILDCARD",
                "BANG",
                "ID",
                "NameChar",
                "NameStartChar",
                "STRING",
            )

        private val _LITERAL_NAMES: Array<String?> = arrayOf(null, null, null, "'//'", "'/'", "'*'", "'!'")
        private val _SYMBOLIC_NAMES: Array<String?> =
            arrayOf(null, "TOKEN_REF", "RULE_REF", "ANYWHERE", "ROOT", "WILDCARD", "BANG", "ID", "STRING")

        val VOCABULARY: Vocabulary = VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES, null)

        val tokenNames: Array<String> =
            Array(_SYMBOLIC_NAMES.size) { i ->
                VOCABULARY.getLiteralName(i) ?: VOCABULARY.getSymbolicName(i) ?: "<INVALID>"
            }
    }
}
