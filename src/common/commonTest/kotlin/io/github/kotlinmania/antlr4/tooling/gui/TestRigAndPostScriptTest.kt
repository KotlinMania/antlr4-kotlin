package io.github.kotlinmania.antlr4.tooling.gui

import io.github.kotlinmania.antlr4.CharStream
import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.IntStream
import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.Parser
import io.github.kotlinmania.antlr4.ParserRuleContext
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.Vocabulary
import io.github.kotlinmania.antlr4.VocabularyImpl
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNType
import io.github.kotlinmania.antlr4.misc.CommonUtils
import io.github.kotlinmania.antlr4.tree.ErrorNodeImpl
import io.github.kotlinmania.antlr4.tree.TerminalNodeImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestRigAndPostScriptTest {
    private class SimpleExprLexer(
        input: CharStream?,
    ) : Lexer(input) {
        override val ruleNames: List<String> = listOf("ID", "PLUS", "WS")
        override val grammarFileName: String = "SimpleExpr.g4"
        override val vocabulary: Vocabulary =
            VocabularyImpl(
                listOf("", "", "'+'", ""),
                listOf("", "ID", "PLUS", "WS"),
            )
        override val atn: ATN = ATN(ATNType.LEXER, 3)
        override var line: Int = 1
        override var charPositionInLine: Int = 0

        override fun nextToken(): Token {
            val stream = inputStream ?: return CommonToken(Token.EOF, "<EOF>")
            while (true) {
                val ch = stream.LA(1)
                if (ch == IntStream.EOF) {
                    val eof = CommonToken(Token.EOF, "<EOF>")
                    eof.startIndex = stream.index()
                    eof.stopIndex = stream.index() - 1
                    eof.line = line
                    eof.charPositionInLine = charPositionInLine
                    return eof
                }
                if (ch == '\n'.code) {
                    stream.consume()
                    line += 1
                    charPositionInLine = 0
                    continue
                }
                if (ch == ' '.code || ch == '\t'.code) {
                    stream.consume()
                    charPositionInLine += 1
                    continue
                }
                if (ch == '+'.code) {
                    val idx = stream.index()
                    val col = charPositionInLine
                    stream.consume()
                    charPositionInLine += 1
                    val tok = CommonToken(2, "+")
                    tok.startIndex = idx
                    tok.stopIndex = idx
                    tok.line = line
                    tok.charPositionInLine = col
                    return tok
                }
                val start = stream.index()
                val col = charPositionInLine
                val text = StringBuilder()
                while (stream.LA(1).let { it != IntStream.EOF && it != ' '.code && it != '+'.code }) {
                    text.append(stream.LA(1).toChar())
                    stream.consume()
                    charPositionInLine += 1
                }
                val tok = CommonToken(1, text.toString())
                tok.startIndex = start
                tok.stopIndex = stream.index() - 1
                tok.line = line
                tok.charPositionInLine = col
                return tok
            }
        }
    }

    private class SimpleExprParser(
        input: TokenStream?,
    ) : Parser(input) {
        override val tokenNames: List<String> = listOf("<INVALID>", "ID", "'+'", "WS")
        override val ruleNames: List<String> = listOf("expr", "atom")
        override val grammarFileName: String = "SimpleExpr.g4"
        override val vocabulary: Vocabulary =
            VocabularyImpl(
                listOf("", "", "'+'", ""),
                listOf("", "ID", "PLUS", "WS"),
            )
        override val atn: ATN = ATN(ATNType.PARSER, 3)

        fun parseExpr(): ParserRuleContext {
            val root =
                object : ParserRuleContext(null, -1) {
                    override var ruleIndex: Int = 0
                }
            val stream = tokenStream
            root.start = stream.LT(1)
            val leftAtom = AtomContext(root)
            val first = requireNotNull(stream.LT(1))
            leftAtom.start = first
            leftAtom.stop = first
            leftAtom.addChild(TerminalNodeImpl(first))
            root.addChild(leftAtom)
            stream.consume()

            while (stream.LA(1) == 2) {
                val plus = requireNotNull(stream.LT(1))
                root.addChild(TerminalNodeImpl(plus))
                stream.consume()

                val rightAtom = AtomContext(root)
                val id = requireNotNull(stream.LT(1))
                rightAtom.start = id
                rightAtom.stop = id
                rightAtom.addChild(TerminalNodeImpl(id))
                root.addChild(rightAtom)
                stream.consume()
            }
            root.stop = stream.LT(-1)
            return root
        }

        private class AtomContext(
            parent: ParserRuleContext,
        ) : ParserRuleContext(parent, -1) {
            override var ruleIndex: Int = 1
        }
    }

    @Test
    fun generatesPostScriptWithTidyLayoutAndErrorHighlights() {
        val root =
            object : ParserRuleContext(null, -1) {
                override var ruleIndex: Int = 0
            }
        val left = TerminalNodeImpl(CommonToken(1, "alpha(1)"))
        val err = ErrorNodeImpl(CommonToken(2, "<missing ';'>"))
        root.addChild(left)
        root.addChild(err)

        val ps = Trees.getPS(root, listOf("stmt"), "CourierNew", 11)
        assertTrue(ps.startsWith("%!PS-Adobe-3.0 EPSF-3.0\n%%BoundingBox: 0 0 "))
        assertTrue(ps.contains("/CourierNewPSMT findfont 11 scalefont setfont"))
        assertTrue(ps.contains("(stmt) show"))
        assertTrue(ps.contains("(alpha\\(1\\)) show"))
        assertTrue(ps.contains("highlight\n"))
        assertTrue(ps.endsWith("%%Trailer\n"))

        val lisp = Trees.toStringTree(root, DefaultTreeTextProvider(listOf("stmt")))
        assertEquals("(stmt alpha(1) <missing ';'>)", lisp)
    }

    @Test
    fun testRigProcessesTokensAndTreeInMemory() {
        val rig = TestRig(listOf("SimpleExpr", "expr", "-tokens", "-tree"))
        val lexer = SimpleExprLexer(null)
        val parser = SimpleExprParser(CommonTokenStream(lexer))
        val result =
            rig.processInput(
                lexer = lexer,
                parser = parser,
                input = CharStreams.fromString("x + y"),
                startRule = StartRuleInvoker { p -> (p as SimpleExprParser).parseExpr() },
                emitStdout = false,
            )

        assertEquals(4, result.tokens.size)
        assertTrue(result.tokens[0].contains("'x'"))
        assertTrue(result.tokens[1].contains("'+'"))
        assertTrue(result.tokens[2].contains("'y'"))
        assertTrue(result.tokens[3].contains("<EOF>"))
        assertEquals("(expr (atom x) + (atom y))", result.treeText)
    }

    @Test
    fun decodesAndEncodesCharsetsViaEncodingRs() {
        val latinBytes = byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte())
        val decodedLatin = CommonUtils.decodeBytes(latinBytes, "ISO-8859-1")
        assertEquals("caf\u00E9", decodedLatin)
        assertTrue(latinBytes.contentEquals(CommonUtils.encodeString(decodedLatin, "ISO-8859-1")))

        val sjisEncoded = CommonUtils.encodeString("東京", "Shift_JIS")
        val sjisDecoded = CommonUtils.decodeBytes(sjisEncoded, "Shift_JIS")
        assertEquals("東京", sjisDecoded)
    }
}
