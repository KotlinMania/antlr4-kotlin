/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.ANTLRErrorListener
import io.github.kotlinmania.antlr4.BaseErrorListener
import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.IntStream
import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.NoViableAltException
import io.github.kotlinmania.antlr4.Parser
import io.github.kotlinmania.antlr4.ParserRuleContext
import io.github.kotlinmania.antlr4.RecognitionControlException
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Recognizer
import io.github.kotlinmania.antlr4.RuleContext
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenSource
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNDeserializer
import io.github.kotlinmania.antlr4.atn.ATNSerializer
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.atn.ArrayPredictionContext
import io.github.kotlinmania.antlr4.atn.BlockStartState
import io.github.kotlinmania.antlr4.atn.DecisionState
import io.github.kotlinmania.antlr4.atn.EmptyPredictionContext
import io.github.kotlinmania.antlr4.atn.LexerATNSimulator
import io.github.kotlinmania.antlr4.atn.ParserATNSimulator
import io.github.kotlinmania.antlr4.atn.PredictionContext
import io.github.kotlinmania.antlr4.atn.PredictionContextCache
import io.github.kotlinmania.antlr4.atn.SingletonPredictionContext
import io.github.kotlinmania.antlr4.dfa.DFA
import io.github.kotlinmania.antlr4.misc.IntegerList
import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.antlr4.tooling.automata.ATNPrinter
import io.github.kotlinmania.antlr4.tooling.automata.LexerATNFactory
import io.github.kotlinmania.antlr4.tooling.automata.ParserATNFactory
import io.github.kotlinmania.antlr4.tooling.semantics.SemanticPipeline
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LeftRecursiveRule
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

internal class MockIntTokenStream(
    val types: IntegerList,
) : TokenStream {
    var p: Int = 0

    override fun consume() {
        p++
    }

    override fun LA(i: Int): Int = requireNotNull(LT(i)).type

    override fun mark(): Int = index()

    override fun index(): Int = p

    override fun release(marker: Int) {
        seek(marker)
    }

    override fun seek(index: Int) {
        p = index
    }

    override fun size(): Int = types.size()

    override val sourceName: String
        get() = IntStream.UNKNOWN_SOURCE_NAME

    override fun LT(k: Int): Token {
        val rawIndex = p + k - 1
        val token =
            if (rawIndex >= types.size()) {
                CommonToken(Token.EOF)
            } else {
                CommonToken(types.get(rawIndex))
            }
        token.tokenIndex = rawIndex
        return token
    }

    override fun get(index: Int): Token = CommonToken(types.get(index))

    override val tokenSource: TokenSource?
        get() = null

    override val text: String
        get() = throw UnsupportedOperationException("can't give strings")

    override fun getText(interval: Interval?): String =
        throw UnsupportedOperationException("can't give strings")

    override fun getText(ctx: RuleContext?): String =
        throw UnsupportedOperationException("can't give strings")

    override fun getText(
        start: Token?,
        stop: Token?,
    ): String = throw UnsupportedOperationException("can't give strings")
}

internal class ParserInterpreterForTesting(
    val g: Grammar,
    input: TokenStream?,
) {
    class DummyParser(
        val g: Grammar,
        private val builtAtn: ATN,
        input: TokenStream?,
    ) : Parser(input) {
        val decisionToDFA: List<DFA> =
            List(builtAtn.numberOfDecisions) { i ->
                DFA(requireNotNull(builtAtn.getDecisionState(i)), i)
            }
        val sharedContextCache: PredictionContextCache = PredictionContextCache()

        init {
            inputField = input
        }

        override val grammarFileName: String
            get() = g.fileName ?: Grammar.GRAMMAR_FROM_STRING_NAME

        override val ruleNames: List<String>
            get() = g.rules.keys.toList()

        override val tokenNames: List<String>
            get() = g.tokenNames

        override val atn: ATN
            get() = builtAtn
    }

    val parser: DummyParser
    val atnSimulator: ParserATNSimulator

    init {
        val antlr = Tool()
        antlr.process(g, false)
        val builtAtn = g.getATN()
        parser = DummyParser(g, builtAtn, input)
        atnSimulator = ParserATNSimulator(parser, builtAtn, parser.decisionToDFA, parser.sharedContextCache)
        parser.interpreter = atnSimulator
    }

    fun adaptivePredict(
        input: TokenStream,
        decision: Int,
        outerContext: ParserRuleContext?,
    ): Int = atnSimulator.adaptivePredict(input, decision, outerContext)

    fun matchATN(
        input: TokenStream,
        startState: ATNState,
    ): Int =
        when {
            startState.numberOfTransitions == 1 -> 1
            startState is DecisionState -> atnSimulator.adaptivePredict(input, startState.decision, null)
            startState.numberOfTransitions > 0 -> 1
            else -> -1
        }
}

private fun buildTestATN(
    g: Grammar,
    useSerializer: Boolean,
): ATN {
    var currentAtn = g.atn
    if (currentAtn == null) {
        semanticProcessForTest(g)
        assertEquals(0, g.tool.numErrors)
        val factory = if (g.isLexer) LexerATNFactory(g as LexerGrammar) else ParserATNFactory(g)
        currentAtn = factory.createATN()
        g.atn = currentAtn
        assertEquals(0, g.tool.numErrors)
    }
    if (useSerializer) {
        val serialized = ATNSerializer.getSerialized(currentAtn)
        return ATNDeserializer().deserialize(serialized.toArray())
    }
    return currentAtn
}

private fun semanticProcessForTest(g: Grammar) {
    val rootAst = g.ast
    if (rootAst != null && !rootAst.hasErrors) {
        val antlr = Tool()
        val sem = SemanticPipeline(g)
        sem.process()
        val imports = g.importedGrammars
        if (imports != null) {
            for (imp in imports) {
                antlr.processNonCombinedGrammar(imp, false)
            }
        }
    }
}

private fun tokenTypesViaATN(
    input: String,
    lexerATN: LexerATNSimulator,
): IntegerList {
    val stream = CharStreams.fromString(input)
    val tokenTypes = IntegerList()
    var ttype: Int
    do {
        ttype = lexerATN.match(stream, Lexer.DEFAULT_MODE)
        tokenTypes.add(ttype)
    } while (ttype != Token.EOF)
    return tokenTypes
}

private fun codePointString(cp: Int): String {
    if (cp <= 0xFFFF) return cp.toChar().toString()
    val offset = cp - 0x10000
    val high = ((offset ushr 10) + 0xD800).toChar()
    val low = ((offset and 0x3FF) + 0xDC00).toChar()
    return charArrayOf(high, low).concatToString()
}

private fun checkRuleATNText(
    g: Grammar,
    ruleName: String,
    expecting: String,
) {
    val rule = requireNotNull(g.getRule(ruleName))
    val startState = g.getATN().ruleToStartState[rule.index]
    val printer = ATNPrinter(g, startState)
    assertEquals(expecting, printer.asString())
}

class ATNInterpreterTest {
    @Test
    fun testSimpleNoBlock() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A B ;",
            )
        checkMatchedAlt(lg, g, "ab", 1)
    }

    @Test
    fun testSet() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "tokens {A,B,C}\n" +
                    "a : ~A ;",
            )
        checkMatchedAlt(lg, g, "b", 1)
    }

    @Test
    fun testPEGAchillesHeel() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A | A B ;",
            )
        checkMatchedAlt(lg, g, "a", 1)
        checkMatchedAlt(lg, g, "ab", 2)
        checkMatchedAlt(lg, g, "abc", 2)
    }

    @Test
    fun testMustTrackPreviousGoodAlt() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A | A B ;",
            )
        checkMatchedAlt(lg, g, "a", 1)
        checkMatchedAlt(lg, g, "ab", 2)
        checkMatchedAlt(lg, g, "ac", 1)
        checkMatchedAlt(lg, g, "abc", 2)
    }

    @Test
    fun testMustTrackPreviousGoodAltWithEOF() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : (A | A B) EOF;",
            )
        checkMatchedAlt(lg, g, "a", 1)
        checkMatchedAlt(lg, g, "ab", 2)

        val nvae = expectNoViableAlt { checkMatchedAlt(lg, g, "ac", 1) }
        assertEquals(1, requireNotNull(nvae.offendingToken).tokenIndex)
        assertEquals(3, requireNotNull(nvae.offendingToken).type)
    }

    @Test
    fun testMustTrackPreviousGoodAlt2() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "D : 'd' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A | A B | A B C ;",
            )
        checkMatchedAlt(lg, g, "a", 1)
        checkMatchedAlt(lg, g, "ab", 2)
        checkMatchedAlt(lg, g, "abc", 3)
        checkMatchedAlt(lg, g, "ad", 1)
        checkMatchedAlt(lg, g, "abd", 2)
        checkMatchedAlt(lg, g, "abcd", 3)
    }

    @Test
    fun testMustTrackPreviousGoodAlt2WithEOF() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "D : 'd' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : (A | A B | A B C) EOF;",
            )
        checkMatchedAlt(lg, g, "a", 1)
        checkMatchedAlt(lg, g, "ab", 2)
        checkMatchedAlt(lg, g, "abc", 3)

        val nvae = expectNoViableAlt { checkMatchedAlt(lg, g, "abd", 1) }
        assertEquals(2, requireNotNull(nvae.offendingToken).tokenIndex)
        assertEquals(4, requireNotNull(nvae.offendingToken).type)
    }

    @Test
    fun testMustTrackPreviousGoodAlt3() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "D : 'd' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A B | A | A B C ;",
            )
        checkMatchedAlt(lg, g, "a", 2)
        checkMatchedAlt(lg, g, "ab", 1)
        checkMatchedAlt(lg, g, "abc", 3)
        checkMatchedAlt(lg, g, "ad", 2)
        checkMatchedAlt(lg, g, "abd", 1)
        checkMatchedAlt(lg, g, "abcd", 3)
    }

    @Test
    fun testMustTrackPreviousGoodAlt3WithEOF() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "D : 'd' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : (A B | A | A B C) EOF;",
            )
        checkMatchedAlt(lg, g, "a", 2)
        checkMatchedAlt(lg, g, "ab", 1)
        checkMatchedAlt(lg, g, "abc", 3)

        val nvae = expectNoViableAlt { checkMatchedAlt(lg, g, "abd", 1) }
        assertEquals(2, requireNotNull(nvae.offendingToken).tokenIndex)
        assertEquals(4, requireNotNull(nvae.offendingToken).type)
    }

    @Test
    fun testAmbigAltChooseFirst() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "D : 'd' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A B | A B ;",
            )
        checkMatchedAlt(lg, g, "ab", 1)
        checkMatchedAlt(lg, g, "abc", 1)
    }

    @Test
    fun testAmbigAltChooseFirstWithFollowingToken() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "D : 'd' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : (A B | A B) C ;",
            )
        checkMatchedAlt(lg, g, "abc", 1)
        checkMatchedAlt(lg, g, "abcd", 1)
    }

    @Test
    fun testAmbigAltChooseFirstWithFollowingToken2() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "D : 'd' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : (A B | A B | C) D ;",
            )
        checkMatchedAlt(lg, g, "abd", 1)
        checkMatchedAlt(lg, g, "abdc", 1)
        checkMatchedAlt(lg, g, "cd", 3)
    }

    @Test
    fun testAmbigAltChooseFirst2() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "D : 'd' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A B | A B | A B C ;",
            )
        checkMatchedAlt(lg, g, "ab", 1)
        checkMatchedAlt(lg, g, "abc", 3)
        checkMatchedAlt(lg, g, "abd", 1)
        checkMatchedAlt(lg, g, "abcd", 3)
    }

    @Test
    fun testAmbigAltChooseFirst2WithEOF() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "D : 'd' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : (A B | A B | A B C) EOF;",
            )
        checkMatchedAlt(lg, g, "ab", 1)
        checkMatchedAlt(lg, g, "abc", 3)

        val nvae = expectNoViableAlt { checkMatchedAlt(lg, g, "abd", 1) }
        assertEquals(2, requireNotNull(nvae.offendingToken).tokenIndex)
        assertEquals(4, requireNotNull(nvae.offendingToken).type)
    }

    @Test
    fun testSimpleLoop() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "D : 'd' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A+ B ;",
            )
        checkMatchedAlt(lg, g, "ab", 1)
        checkMatchedAlt(lg, g, "aab", 1)
        checkMatchedAlt(lg, g, "aaaaaab", 1)
        checkMatchedAlt(lg, g, "aabd", 1)
    }

    @Test
    fun testCommonLeftPrefix() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A B | A C ;",
            )
        checkMatchedAlt(lg, g, "ab", 1)
        checkMatchedAlt(lg, g, "ac", 2)
    }

    @Test
    fun testArbitraryLeftPrefix() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A+ B | A+ C ;",
            )
        checkMatchedAlt(lg, g, "aac", 2)
    }

    @Test
    fun testRecursiveLeftPrefix() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "LP : '(' ;\n" +
                    "RP : ')' ;\n" +
                    "INT : '0'..'9'+ ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "tokens {A,B,C,LP,RP,INT}\n" +
                    "a : e B | e C ;\n" +
                    "e : LP e RP\n" +
                    "  | INT\n" +
                    "  ;",
            )
        checkMatchedAlt(lg, g, "34b", 1)
        checkMatchedAlt(lg, g, "34c", 2)
        checkMatchedAlt(lg, g, "(34)b", 1)
        checkMatchedAlt(lg, g, "(34)c", 2)
        checkMatchedAlt(lg, g, "((34))b", 1)
        checkMatchedAlt(lg, g, "((34))c", 2)
    }

    private fun expectNoViableAlt(block: () -> Unit): NoViableAltException {
        try {
            block()
        } catch (rce: RecognitionControlException) {
            val re = rce.recognitionException
            if (re is NoViableAltException) {
                return re
            }
            fail("Expected NoViableAltException, got $re")
        }
        fail("Expected NoViableAltException")
    }

    private fun checkMatchedAlt(
        lg: LexerGrammar,
        g: Grammar,
        inputString: String,
        expected: Int,
    ) {
        val lexAtn = buildTestATN(lg, true)
        val lexInterp = LexerATNSimulator(lexAtn, listOf(DFA(lexAtn.modeToStartState[Lexer.DEFAULT_MODE])), null)
        val types = tokenTypesViaATN(inputString, lexInterp)

        g.importVocab(lg)
        val factory = ParserATNFactory(g)
        val atn = factory.createATN()

        val input = MockIntTokenStream(types)
        val interp = ParserInterpreterForTesting(g, input)
        val ruleA = requireNotNull(g.getRule("a"))
        var startState: ATNState = requireNotNull(atn.ruleToStartState[ruleA.index])
        val firstTarget = startState.transition(0).target
        if (firstTarget is BlockStartState) {
            startState = firstTarget
        }
        val result = interp.matchATN(input, startState)
        assertEquals(expected, result)
    }
}

class ATNLexerInterpreterTest {
    @Test
    fun testLexerTwoRules() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n",
            )
        checkLexerMatches(lg, "abab", "A, B, A, B, EOF")
    }

    @Test
    fun testShortLongRule() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'xy'\n" +
                    "  | 'xyz'\n" +
                    "  ;\n" +
                    "Z : 'z'\n" +
                    "  ;\n",
            )
        checkLexerMatches(lg, "xy", "A, EOF")
        checkLexerMatches(lg, "xyz", "A, EOF")
    }

    @Test
    fun testShortLongRule2() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'xyz'\n" +
                    "  | 'xy'\n" +
                    "  ;\n",
            )
        checkLexerMatches(lg, "xy", "A, EOF")
        checkLexerMatches(lg, "xyz", "A, EOF")
    }

    @Test
    fun testWildOnEndFirstAlt() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'xy' .\n" +
                    "  | 'xy'\n" +
                    "  ;\n" +
                    "Z : 'z'\n" +
                    "  ;\n",
            )
        checkLexerMatches(lg, "xy", "A, EOF")
        checkLexerMatches(lg, "xyz", "A, EOF")
    }

    @Test
    fun testWildOnEndLastAlt() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'xy'\n" +
                    "  | 'xy' .\n" +
                    "  ;\n" +
                    "Z : 'z'\n" +
                    "  ;\n",
            )
        checkLexerMatches(lg, "xy", "A, EOF")
        checkLexerMatches(lg, "xyz", "A, EOF")
    }

    @Test
    fun testWildcardNonQuirkWhenSplitBetweenTwoRules() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'xy' ;\n" +
                    "B : 'xy' . 'z' ;\n",
            )
        checkLexerMatches(lg, "xy", "A, EOF")
        checkLexerMatches(lg, "xyqz", "B, EOF")
    }

    @Test
    fun testLexerLoops() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "INT : '0'..'9'+ ;\n" +
                    "ID : 'a'..'z'+ ;\n",
            )
        checkLexerMatches(lg, "a34bde3", "ID, INT, ID, INT, EOF")
    }

    @Test
    fun testLexerNotSet() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ~('a'|'b')\n ;",
            )
        checkLexerMatches(lg, "c", "ID, EOF")
    }

    @Test
    fun testLexerSetUnicodeBMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ('\u611B'|'\u611C')\n ;",
            )
        checkLexerMatches(lg, "\u611B", "ID, EOF")
    }

    @Test
    fun testLexerNotSetUnicodeBMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ~('\u611B'|'\u611C')\n ;",
            )
        checkLexerMatches(lg, "\u611D", "ID, EOF")
    }

    @Test
    fun testLexerNotSetUnicodeBMPMatchesSMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ~('\u611B'|'\u611C')\n ;",
            )
        checkLexerMatches(lg, codePointString(0x1F4A9), "ID, EOF")
    }

    @Test
    fun testLexerSetUnicodeSMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ('\\u{1F4A9}'|'\\u{1F4AA}')\n ;",
            )
        checkLexerMatches(lg, codePointString(0x1F4A9), "ID, EOF")
    }

    @Test
    fun testLexerNotBMPSetMatchesUnicodeSMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ~('a'|'b')\n ;",
            )
        checkLexerMatches(lg, codePointString(0x1F4A9), "ID, EOF")
    }

    @Test
    fun testLexerNotBMPSetMatchesBMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ~('a'|'b')\n ;",
            )
        checkLexerMatches(lg, "\u611B", "ID, EOF")
    }

    @Test
    fun testLexerNotBMPSetMatchesSMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ~('a'|'b')\n ;",
            )
        checkLexerMatches(lg, codePointString(0x1F4A9), "ID, EOF")
    }

    @Test
    fun testLexerNotSMPSetMatchesBMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ~('\\u{1F4A9}'|'\\u{1F4AA}')\n ;",
            )
        checkLexerMatches(lg, "\u611B", "ID, EOF")
    }

    @Test
    fun testLexerNotSMPSetMatchesSMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ~('\\u{1F4A9}'|'\\u{1F4AA}')\n ;",
            )
        checkLexerMatches(lg, codePointString(0x1D7C0), "ID, EOF")
    }

    @Test
    fun testLexerRangeUnicodeSMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ('\\u{1F4A9}'..'\\u{1F4B0}')\n ;",
            )
        checkLexerMatches(lg, codePointString(0x1F4AF), "ID, EOF")
    }

    @Test
    fun testLexerRangeUnicodeBMPToSMP() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ('\\u611B'..'\\u{1F4B0}')\n ;",
            )
        checkLexerMatches(lg, codePointString(0x12001), "ID, EOF")
    }

    @Test
    fun testLexerKeywordIDAmbiguity() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "KEND : 'end' ;\n" +
                    "ID : 'a'..'z'+ ;\n" +
                    "WS : (' '|'\\n')+ ;",
            )
        checkLexerMatches(lg, "end", "KEND, EOF")
        checkLexerMatches(lg, "ending", "ID, EOF")
        checkLexerMatches(lg, "a end bcd", "ID, WS, KEND, WS, ID, EOF")
    }

    @Test
    fun testLexerRuleRef() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "INT : DIGIT+ ;\n" +
                    "fragment DIGIT : '0'..'9' ;\n" +
                    "WS : (' '|'\\n')+ ;",
            )
        checkLexerMatches(lg, "32 99", "INT, WS, INT, EOF")
    }

    @Test
    fun testRecursiveLexerRuleRef() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "CMT : '/*' (CMT | ~'*')+ '*/' ;\n" +
                    "WS : (' '|'\\n')+ ;",
            )
        checkLexerMatches(lg, "/* ick */\n/* /*nested*/ */", "CMT, WS, CMT, EOF")
    }

    @Test
    fun testRecursiveLexerRuleRefWithWildcard() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "CMT : '/*' (CMT | .)*? '*/' ;\n" +
                    "WS : (' '|'\\n')+ ;",
            )
        checkLexerMatches(
            lg,
            "/* ick */\n" +
                "/* /* */\n" +
                "/* /*nested*/ */\n",
            "CMT, WS, CMT, WS, EOF",
        )
    }

    @Test
    fun testLexerWildcardGreedyLoopByDefault() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "CMT : '//' .* '\\n' ;\n",
            )
        checkLexerMatches(lg, "//x\n//y\n", "CMT, EOF")
    }

    @Test
    fun testLexerWildcardLoopExplicitNonGreedy() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "CMT : '//' .*? '\\n' ;\n",
            )
        checkLexerMatches(lg, "//x\n//y\n", "CMT, CMT, EOF")
    }

    @Test
    fun testLexerEscapeInString() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "STR : '[' ('~' ']' | .)* ']' ;\n",
            )
        checkLexerMatches(lg, "[a~]b]", "STR, EOF")
        checkLexerMatches(lg, "[a]", "STR, EOF")
    }

    @Test
    fun testLexerWildcardGreedyPlusLoopByDefault() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "CMT : '//' .+ '\\n' ;\n",
            )
        checkLexerMatches(lg, "//x\n//y\n", "CMT, EOF")
    }

    @Test
    fun testLexerWildcardExplicitNonGreedyPlusLoop() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "CMT : '//' .+? '\\n' ;\n",
            )
        checkLexerMatches(lg, "//x\n//y\n", "CMT, CMT, EOF")
    }

    @Test
    fun testLexerGreedyOptionalShouldWorkAsWeExpect() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "CMT : '/*' ('*/')? '*/' ;\n",
            )
        checkLexerMatches(lg, "/**/", "CMT, EOF")
    }

    @Test
    fun testGreedyBetweenRules() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : '<a>' ;\n" +
                    "B : '<' .+ '>' ;\n",
            )
        checkLexerMatches(lg, "<a><x>", "B, EOF")
    }

    @Test
    fun testNonGreedyBetweenRules() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : '<a>' ;\n" +
                    "B : '<' .+? '>' ;\n",
            )
        checkLexerMatches(lg, "<a><x>", "A, B, EOF")
    }

    @Test
    fun testEOFAtEndOfLineComment() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "CMT : '//' ~('\\n')* ;\n",
            )
        checkLexerMatches(lg, "//x", "CMT, EOF")
    }

    @Test
    fun testEOFAtEndOfLineComment2() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "CMT : '//' ~('\\n'|'\\r')* ;\n",
            )
        checkLexerMatches(lg, "//x", "CMT, EOF")
    }

    @Test
    fun testEOFInSetAtEndOfLineComment() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "CMT : '//' .* (EOF|'\\n') ;\n",
            )
        checkLexerMatches(lg, "//", "CMT, EOF")
    }

    @Test
    fun testEOFSuffixInSecondRule() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'a' EOF ;\n",
            )
        checkLexerMatches(lg, "a", "B, EOF")
    }

    @Test
    fun testEOFSuffixInFirstRule() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' EOF ;\n" +
                    "B : 'a';\n",
            )
        checkLexerMatches(lg, "a", "A, EOF")
    }

    @Test
    fun testEOFByItself() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "DONE : EOF ;\n" +
                    "A : 'a';\n",
            )
        checkLexerMatches(lg, "a", "A, DONE, EOF")
    }

    @Test
    fun testLexerCaseInsensitive() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n\n" +
                    "options { caseInsensitive = true; }\n\n" +
                    "WS:             [ \\t\\r\\n] -> skip;\n\n" +
                    "SIMPLE_TOKEN:           'and';\n" +
                    "TOKEN_WITH_SPACES:      'as' 'd' 'f';\n" +
                    "TOKEN_WITH_DIGITS:      'INT64';\n" +
                    "TOKEN_WITH_UNDERSCORE:  'TOKEN_WITH_UNDERSCORE';\n" +
                    "BOOL:                   'true' | 'FALSE';\n" +
                    "SPECIAL:                '==';\n" +
                    "SET:                    [a-z0-9]+;\n" +
                    "RANGE:                  ('а'..'я')+; ",
            )
        val inputString =
            "and AND aND\n" +
                "asdf ASDF\n" +
                "int64\n" +
                "token" + "_WITH_" + "underscore\n" +
                "TRUE FALSE\n" +
                "==\n" +
                "A0bcDE93\n" +
                "АБВабв\n"
        val expecting =
            listOf(
                "SIMPLE_TOKEN",
                "SIMPLE_TOKEN",
                "SIMPLE_TOKEN",
                "TOKEN_WITH_SPACES",
                "TOKEN_WITH_SPACES",
                "TOKEN_WITH_DIGITS",
                "TOKEN_WITH_UNDERSCORE",
                "BOOL",
                "BOOL",
                "SPECIAL",
                "SET",
                "RANGE",
                "EOF",
            ).joinToString(", WS, ")
        checkLexerMatches(lg, inputString, expecting)
    }

    @Test
    fun testLexerCaseInsensitiveLiteralWithNegation() {
        val grammar =
            "lexer grammar L;\n" +
                "options { caseInsensitive = true; }\n" +
                "LITERAL_WITH_NOT:   ~'f';\n"
        val errors = execLexerErrors(grammar, "F")
        assertEquals("line 1:0 token recognition error at: 'F'\n", errors)
    }

    @Test
    fun testLexerCaseInsensitiveSetWithNegation() {
        val grammar =
            "lexer grammar L;\n" +
                "options { caseInsensitive = true; }\n" +
                "SET_WITH_NOT: ~[a-c];\n"
        val errors = execLexerErrors(grammar, "B")
        assertEquals("line 1:0 token recognition error at: 'B'\n", errors)
    }

    @Test
    fun testLexerCaseInsensitiveFragments() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "options { caseInsensitive = true; }\n" +
                    "TOKEN_0:         FRAGMENT 'd'+;\n" +
                    "TOKEN_1:         FRAGMENT 'e'+;\n" +
                    "FRAGMENT:        'abc';\n",
            )
        checkLexerMatches(lg, "ABCDDD", "TOKEN_0, EOF")
    }

    @Test
    fun testLexerCaseInsensitiveWithDifferentCultures() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "options { caseInsensitive = true; }\n" +
                    "ENGLISH_TOKEN:   [a-z]+;\n" +
                    "GERMAN_TOKEN:    [äéöüß]+;\n" +
                    "FRENCH_TOKEN:    [àâæ-ëîïôœùûüÿ]+;\n" +
                    "CROATIAN_TOKEN:  [ćčđšž]+;\n" +
                    "ITALIAN_TOKEN:   [àèéìòù]+;\n" +
                    "SPANISH_TOKEN:   [áéíñóúü¡¿]+;\n" +
                    "GREEK_TOKEN:     [α-ω]+;\n" +
                    "RUSSIAN_TOKEN:   [а-я]+;\n" +
                    "WS:              [ ]+ -> skip;",
            )
        val inputString = "abcXYZ äéöüßÄÉÖÜß àâæçÙÛÜŸ ćčđĐŠŽ àèéÌÒÙ áéÚÜ¡¿ αβγΧΨΩ абвЭЮЯ "
        val expecting =
            listOf(
                "ENGLISH_TOKEN",
                "GERMAN_TOKEN",
                "FRENCH_TOKEN",
                "CROATIAN_TOKEN",
                "ITALIAN_TOKEN",
                "SPANISH_TOKEN",
                "GREEK_TOKEN",
                "RUSSIAN_TOKEN",
                "EOF",
            ).joinToString(", WS, ")
        checkLexerMatches(lg, inputString, expecting)
    }

    @Test
    fun testNotImpliedCharactersWithEnabledCaseInsensitiveOption() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "options { caseInsensitive = true; }\n" +
                    "TOKEN: ('A'..'z')+;\n",
            )
        val inputString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz"
        checkLexerMatches(lg, inputString, "TOKEN, EOF")
    }

    @Test
    fun testCaseInsensitiveInLexerRule() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "TOKEN1 options { caseInsensitive=true; } : [a-f]+;\n" +
                    "WS: [ ]+ -> skip;",
            )
        checkLexerMatches(lg, "ABCDEF", "TOKEN1, EOF")
    }

    @Test
    fun testCaseInsensitiveInLexerRuleOverridesGlobalValue() {
        val grammar =
            "lexer grammar L;\n" +
                "options { caseInsensitive=true; }\n" +
                "STRING options { caseInsensitive=false; } : 'N'? '\\'' (~'\\'' | '\\'\\'')* '\\'';\n"
        val errors = execLexerErrors(grammar, "n'sample'")
        assertEquals("line 1:0 token recognition error at: 'n'\n", errors)
    }

    private fun execLexerErrors(
        grammarText: String,
        inputText: String,
    ): String {
        val lg = LexerGrammar(grammarText)
        val antlr = Tool()
        antlr.process(lg, false)
        assertEquals(0, lg.tool.numErrors)
        val lexerInterp = lg.createLexerInterpreter(CharStreams.fromString(inputText))
        lexerInterp.removeErrorListeners()
        val errBuf = StringBuilder()
        val listener: ANTLRErrorListener =
            object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String?,
                    e: RecognitionException?,
                ) {
                    errBuf
                        .append("line ")
                        .append(line)
                        .append(':')
                        .append(charPositionInLine)
                        .append(' ')
                        .append(msg)
                        .append('\n')
                }
            }
        lexerInterp.addErrorListener(listener)
        val tokens = CommonTokenStream(lexerInterp)
        tokens.fill()
        return errBuf.toString()
    }

    private fun checkLexerMatches(
        lg: LexerGrammar,
        inputString: String,
        expecting: String,
    ) {
        val atn = buildTestATN(lg, true)
        val input = CharStreams.fromString(inputString)
        val interp = LexerATNSimulator(atn, listOf(DFA(atn.modeToStartState[Lexer.DEFAULT_MODE])), null)
        val tokenTypes = ArrayList<String?>()
        var hitEOF = false
        var ttype: Int
        do {
            if (hitEOF) {
                tokenTypes.add("EOF")
                break
            }
            val currentChar = input.LA(1)
            ttype = interp.match(input, Lexer.DEFAULT_MODE)
            if (ttype == Token.EOF) {
                tokenTypes.add("EOF")
            } else {
                tokenTypes.add(lg.typeToTokenList[ttype])
            }
            if (currentChar == IntStream.EOF) {
                hitEOF = true
            }
        } while (ttype != Token.EOF)
        assertEquals(expecting, tokenTypes.joinToString(", "))
    }
}

class ATNParserPredictionTest {
    @Test
    fun testAorB() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A{;} | B ;",
            )
        val decision = 0
        checkPredictedAlt(lg, g, decision, "a", 1)
        checkPredictedAlt(lg, g, decision, "b", 2)

        val inputs = listOf("a", "b", "a")
        val dfa =
            listOf(
                "s0-'a'->:s1=>1\n",
                "s0-'a'->:s1=>1\n" +
                    "s0-'b'->:s2=>2\n",
                "s0-'a'->:s1=>1\n" +
                    "s0-'b'->:s2=>2\n",
            )
        checkDFAConstruction(lg, g, decision, inputs, dfa)
    }

    @Test
    fun testEmptyInput() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A | ;",
            )
        val decision = 0
        checkPredictedAlt(lg, g, decision, "a", 1)
        checkPredictedAlt(lg, g, decision, "", 2)

        val inputs = listOf("a", "")
        val dfa =
            listOf(
                "s0-'a'->:s1=>1\n",
                "s0-EOF->:s2=>2\n" +
                    "s0-'a'->:s1=>1\n",
            )
        checkDFAConstruction(lg, g, decision, inputs, dfa)
    }

    @Test
    fun testPEGAchillesHeel() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A | A B ;",
            )
        checkPredictedAlt(lg, g, 0, "a", 1)
        checkPredictedAlt(lg, g, 0, "ab", 2)
        checkPredictedAlt(lg, g, 0, "abc", 2)

        val inputs = listOf("a", "ab", "abc")
        val dfa =
            listOf(
                "s0-'a'->s1\n" +
                    "s1-EOF->:s2=>1\n",
                "s0-'a'->s1\n" +
                    "s1-EOF->:s2=>1\n" +
                    "s1-'b'->:s3=>2\n",
                "s0-'a'->s1\n" +
                    "s1-EOF->:s2=>1\n" +
                    "s1-'b'->:s3=>2\n",
            )
        checkDFAConstruction(lg, g, 0, inputs, dfa)
    }

    @Test
    fun testRuleRefXOrY() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : x | y ;\n" +
                    "x : A ;\n" +
                    "y : B ;\n",
            )
        val decision = 0
        checkPredictedAlt(lg, g, decision, "a", 1)
        checkPredictedAlt(lg, g, decision, "b", 2)

        val inputs = listOf("a", "b", "a")
        val dfa =
            listOf(
                "s0-'a'->:s1=>1\n",
                "s0-'a'->:s1=>1\n" +
                    "s0-'b'->:s2=>2\n",
                "s0-'a'->:s1=>1\n" +
                    "s0-'b'->:s2=>2\n",
            )
        checkDFAConstruction(lg, g, decision, inputs, dfa)
    }

    @Test
    fun testOptionalRuleChasesGlobalFollow() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "tokens {A,B,C}\n" +
                    "a : x B ;\n" +
                    "b : x C ;\n" +
                    "x : A | ;\n",
            )
        val decision = 0
        checkPredictedAlt(lg, g, decision, "a", 1)
        checkPredictedAlt(lg, g, decision, "b", 2)
        checkPredictedAlt(lg, g, decision, "c", 2)

        val inputs = listOf("a", "b", "c", "c")
        val dfa =
            listOf(
                "s0-'a'->:s1=>1\n",
                "s0-'a'->:s1=>1\n" +
                    "s0-'b'->:s2=>2\n",
                "s0-'a'->:s1=>1\n" +
                    "s0-'b'->:s2=>2\n" +
                    "s0-'c'->:s3=>2\n",
                "s0-'a'->:s1=>1\n" +
                    "s0-'b'->:s2=>2\n" +
                    "s0-'c'->:s3=>2\n",
            )
        checkDFAConstruction(lg, g, decision, inputs, dfa)
    }

    @Test
    fun testLL1Ambig() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A | A | A B ;",
            )
        val decision = 0
        checkPredictedAlt(lg, g, decision, "a", 1)
        checkPredictedAlt(lg, g, decision, "ab", 3)

        val inputs = listOf("a", "ab", "ab")
        val dfa =
            listOf(
                "s0-'a'->s1\n" +
                    "s1-EOF->:s2^=>1\n",
                "s0-'a'->s1\n" +
                    "s1-EOF->:s2^=>1\n" +
                    "s1-'b'->:s3=>3\n",
                "s0-'a'->s1\n" +
                    "s1-EOF->:s2^=>1\n" +
                    "s1-'b'->:s3=>3\n",
            )
        checkDFAConstruction(lg, g, decision, inputs, dfa)
    }

    @Test
    fun testLL2Ambig() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : A B | A B | A B C ;",
            )
        val decision = 0
        checkPredictedAlt(lg, g, decision, "ab", 1)
        checkPredictedAlt(lg, g, decision, "abc", 3)

        val inputs = listOf("ab", "abc", "ab")
        val dfa =
            listOf(
                "s0-'a'->s1\n" +
                    "s1-'b'->s2\n" +
                    "s2-EOF->:s3^=>1\n",
                "s0-'a'->s1\n" +
                    "s1-'b'->s2\n" +
                    "s2-EOF->:s3^=>1\n" +
                    "s2-'c'->:s4=>3\n",
                "s0-'a'->s1\n" +
                    "s1-'b'->s2\n" +
                    "s2-EOF->:s3^=>1\n" +
                    "s2-'c'->:s4=>3\n",
            )
        checkDFAConstruction(lg, g, decision, inputs, dfa)
    }

    @Test
    fun testRecursiveLeftPrefix() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "LP : '(' ;\n" +
                    "RP : ')' ;\n" +
                    "INT : '0'..'9'+ ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "tokens {A,B,C,LP,RP,INT}\n" +
                    "a : e B | e C ;\n" +
                    "e : LP e RP\n" +
                    "  | INT\n" +
                    "  ;",
            )
        val decision = 0
        checkPredictedAlt(lg, g, decision, "34b", 1)
        checkPredictedAlt(lg, g, decision, "34c", 2)
        checkPredictedAlt(lg, g, decision, "((34))b", 1)
        checkPredictedAlt(lg, g, decision, "((34))c", 2)

        val inputs = listOf("34b", "34c", "((34))b", "((34))c")
        val dfa =
            listOf(
                "s0-INT->s1\n" +
                    "s1-'b'->:s2=>1\n",
                "s0-INT->s1\n" +
                    "s1-'b'->:s2=>1\n" +
                    "s1-'c'->:s3=>2\n",
                "s0-'('->s4\n" +
                    "s0-INT->s1\n" +
                    "s1-'b'->:s2=>1\n" +
                    "s1-'c'->:s3=>2\n" +
                    "s4-'('->s5\n" +
                    "s5-INT->s6\n" +
                    "s6-')'->s7\n" +
                    "s7-')'->s1\n",
                "s0-'('->s4\n" +
                    "s0-INT->s1\n" +
                    "s1-'b'->:s2=>1\n" +
                    "s1-'c'->:s3=>2\n" +
                    "s4-'('->s5\n" +
                    "s5-INT->s6\n" +
                    "s6-')'->s7\n" +
                    "s7-')'->s1\n",
            )
        checkDFAConstruction(lg, g, decision, inputs, dfa)
    }

    @Test
    fun testRecursiveLeftPrefixWithAorABIssue() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "LP : '(' ;\n" +
                    "RP : ')' ;\n" +
                    "INT : '0'..'9'+ ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "tokens {A,B,C,LP,RP,INT}\n" +
                    "a : e A | e A B ;\n" +
                    "e : LP e RP\n" +
                    "  | INT\n" +
                    "  ;",
            )
        val decision = 0
        checkPredictedAlt(lg, g, decision, "34a", 1)
        checkPredictedAlt(lg, g, decision, "34ab", 2)
        checkPredictedAlt(lg, g, decision, "((34))a", 1)
        checkPredictedAlt(lg, g, decision, "((34))ab", 2)

        val inputs = listOf("34a", "34ab", "((34))a", "((34))ab")
        val dfa =
            listOf(
                "s0-INT->s1\n" +
                    "s1-'a'->s2\n" +
                    "s2-EOF->:s3=>1\n",
                "s0-INT->s1\n" +
                    "s1-'a'->s2\n" +
                    "s2-EOF->:s3=>1\n" +
                    "s2-'b'->:s4=>2\n",
                "s0-'('->s5\n" +
                    "s0-INT->s1\n" +
                    "s1-'a'->s2\n" +
                    "s2-EOF->:s3=>1\n" +
                    "s2-'b'->:s4=>2\n" +
                    "s5-'('->s6\n" +
                    "s6-INT->s7\n" +
                    "s7-')'->s8\n" +
                    "s8-')'->s1\n",
                "s0-'('->s5\n" +
                    "s0-INT->s1\n" +
                    "s1-'a'->s2\n" +
                    "s2-EOF->:s3=>1\n" +
                    "s2-'b'->:s4=>2\n" +
                    "s5-'('->s6\n" +
                    "s6-INT->s7\n" +
                    "s7-')'->s8\n" +
                    "s8-')'->s1\n",
            )
        checkDFAConstruction(lg, g, decision, inputs, dfa)
    }

    @Test
    fun testContinuePrediction() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : 'a'..'z' ;\n" +
                    "SEMI : ';' ;\n" +
                    "INT : '0'..'9'+ ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "tokens {ID,SEMI,INT}\n" +
                    "a : (ID | ID ID?) SEMI ;",
            )
        val decision = 1
        checkPredictedAlt(lg, g, decision, "a;", 1)
        checkPredictedAlt(lg, g, decision, "ab;", 2)
    }

    @Test
    fun testContinuePrediction2() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : 'a'..'z' ;\n" +
                    "SEMI : ';' ;\n" +
                    "INT : '0'..'9'+ ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "tokens {ID,SEMI,INT}\n" +
                    "a : ID | ID | ID SEMI ;\n",
            )
        val decision = 0
        checkPredictedAlt(lg, g, decision, "a", 1)
        checkPredictedAlt(lg, g, decision, "a;", 3)
    }

    @Test
    fun testAltsForLRRuleComputation() {
        val g =
            Grammar(
                "grammar T;\n" +
                    "e : e '*' e\n" +
                    "  | INT\n" +
                    "  | e '+' e\n" +
                    "  | ID\n" +
                    "  ;\n" +
                    "ID : [a-z]+ ;\n" +
                    "INT : [0-9]+ ;\n" +
                    "WS : [ \\r\\t\\n]+ ;",
            )
        val e = g.getRule("e")
        assertTrue(e is LeftRecursiveRule)
        assertEquals("[0, 2, 4]", requireNotNull(e.primaryAlts).contentToString())
        assertEquals("[0, 1, 3]", requireNotNull(e.recursiveOpAlts).contentToString())
    }

    @Test
    fun testAltsForLRRuleComputation2() {
        val g =
            Grammar(
                "grammar T;\n" +
                    "e : INT\n" +
                    "  | e '*' e\n" +
                    "  | ID\n" +
                    "  ;\n" +
                    "ID : [a-z]+ ;\n" +
                    "INT : [0-9]+ ;\n" +
                    "WS : [ \\r\\t\\n]+ ;",
            )
        val e = g.getRule("e")
        assertTrue(e is LeftRecursiveRule)
        assertEquals("[0, 1, 3]", requireNotNull(e.primaryAlts).contentToString())
        assertEquals("[0, 2]", requireNotNull(e.recursiveOpAlts).contentToString())
    }

    @Test
    fun testAltsForLRRuleComputation3() {
        val g =
            Grammar(
                "grammar T;\n" +
                    "random : 'blort';\n" +
                    "e : '--' e\n" +
                    "  | e '*' e\n" +
                    "  | e '+' e\n" +
                    "  | e '--'\n" +
                    "  | ID\n" +
                    "  ;\n" +
                    "ID : [a-z]+ ;\n" +
                    "INT : [0-9]+ ;\n" +
                    "WS : [ \\r\\t\\n]+ ;",
            )
        val e = g.getRule("e")
        assertTrue(e is LeftRecursiveRule)
        assertEquals("[0, 1, 5]", requireNotNull(e.primaryAlts).contentToString())
        assertEquals("[0, 2, 3, 4]", requireNotNull(e.recursiveOpAlts).contentToString())
    }

    private fun checkPredictedAlt(
        lg: LexerGrammar,
        g: Grammar,
        decision: Int,
        inputString: String,
        expectedAlt: Int,
    ) {
        val lexAtn = buildTestATN(lg, true)
        val lexInterp =
            LexerATNSimulator(
                lexAtn,
                listOf(DFA(lexAtn.modeToStartState[Lexer.DEFAULT_MODE])),
                PredictionContextCache(),
            )
        val types = tokenTypesViaATN(inputString, lexInterp)

        semanticProcessForTest(lg)
        g.importVocab(lg)
        semanticProcessForTest(g)

        val input = MockIntTokenStream(types)
        val interp = ParserInterpreterForTesting(g, input)
        var alt = interp.adaptivePredict(input, decision, ParserRuleContext.EMPTY)
        assertEquals(expectedAlt, alt)

        input.seek(0)
        alt = interp.adaptivePredict(input, decision, null)
        assertEquals(expectedAlt, alt)

        input.seek(0)
        alt = interp.adaptivePredict(input, decision, null)
        assertEquals(expectedAlt, alt)
    }

    private fun checkDFAConstruction(
        lg: LexerGrammar,
        g: Grammar,
        decision: Int,
        inputStrings: List<String>,
        dfaStrings: List<String>,
    ) {
        val lexAtn = buildTestATN(lg, true)
        val lexInterp =
            LexerATNSimulator(
                lexAtn,
                listOf(DFA(requireNotNull(lexAtn.getDecisionState(Lexer.DEFAULT_MODE)))),
                PredictionContextCache(),
            )

        semanticProcessForTest(lg)
        g.importVocab(lg)
        semanticProcessForTest(g)

        val interp = ParserInterpreterForTesting(g, null)
        for (i in inputStrings.indices) {
            val types = tokenTypesViaATN(inputStrings[i], lexInterp)
            val input = MockIntTokenStream(types)
            interp.adaptivePredict(input, decision, ParserRuleContext.EMPTY)
            val dfa = interp.parser.decisionToDFA[decision]
            assertEquals(dfaStrings[i], dfa.toString(g.getVocabulary()))
        }
    }
}

class ExpectedTokensTest {
    @Test
    fun testEpsilonAltSubrule() {
        val gtext =
            "parser grammar T;\n" +
                "a : A (B | ) C ;\n"
        val g = Grammar(gtext)
        val atnText =
            "RuleStart" + "_a_0->s2\n" +
                "s2-A->BlockStart" + "_5\n" +
                "BlockStart" + "_5->s3\n" +
                "BlockStart" + "_5->s4\n" +
                "s3-B->BlockEnd" + "_6\n" +
                "s4->BlockEnd" + "_6\n" +
                "BlockEnd" + "_6->s7\n" +
                "s7-C->s8\n" +
                "s8->RuleStop" + "_a_1\n" +
                "RuleStop" + "_a_1-EOF->s9\n"
        checkRuleATNText(g, "a", atnText)

        val atn = g.getATN()
        val blkStartStateNumber = 5
        val tokens = atn.getExpectedTokens(blkStartStateNumber, null)
        assertEquals("{B, C}", tokens.toString(g.getVocabulary()))
    }

    @Test
    fun testOptionalSubrule() {
        val gtext =
            "parser grammar T;\n" +
                "a : A B? C ;\n"
        val g = Grammar(gtext)
        val atnText =
            "RuleStart" + "_a_0->s2\n" +
                "s2-A->BlockStart" + "_4\n" +
                "BlockStart" + "_4->s3\n" +
                "BlockStart" + "_4->BlockEnd" + "_5\n" +
                "s3-B->BlockEnd" + "_5\n" +
                "BlockEnd" + "_5->s6\n" +
                "s6-C->s7\n" +
                "s7->RuleStop" + "_a_1\n" +
                "RuleStop" + "_a_1-EOF->s8\n"
        checkRuleATNText(g, "a", atnText)

        val atn = g.getATN()
        val blkStartStateNumber = 4
        val tokens = atn.getExpectedTokens(blkStartStateNumber, null)
        assertEquals("{B, C}", tokens.toString(g.getVocabulary()))
    }

    @Test
    fun testFollowIncluded() {
        val gtext =
            "parser grammar T;\n" +
                "a : b A ;\n" +
                "b : B | ;"
        val g = Grammar(gtext)
        val atnTextA =
            "RuleStart" + "_a_0->s4\n" +
                "s4-b->RuleStart" + "_b_2\n" +
                "s5-A->s6\n" +
                "s6->RuleStop" + "_a_1\n" +
                "RuleStop" + "_a_1-EOF->s11\n"
        checkRuleATNText(g, "a", atnTextA)
        val atnTextB =
            "RuleStart" + "_b_2->BlockStart" + "_9\n" +
                "BlockStart" + "_9->s7\n" +
                "BlockStart" + "_9->s8\n" +
                "s7-B->BlockEnd" + "_10\n" +
                "s8->BlockEnd" + "_10\n" +
                "BlockEnd" + "_10->RuleStop" + "_b_3\n" +
                "RuleStop" + "_b_3->s5\n"
        checkRuleATNText(g, "b", atnTextB)

        val atn = g.getATN()
        val blkStartStateNumber = 9
        var tokens = atn.getExpectedTokens(blkStartStateNumber, ParserRuleContext.EMPTY)
        assertEquals("{<EOF>, B}", tokens.toString(g.getVocabulary()))

        tokens = atn.getExpectedTokens(blkStartStateNumber, ParserRuleContext(ParserRuleContext.EMPTY, 4))
        assertEquals("{A, B}", tokens.toString(g.getVocabulary()))
    }

    @Test
    fun testFollowIncludedInLeftRecursiveRule() {
        val gtext =
            "grammar T;\n" +
                "s : expr EOF ;\n" +
                "expr : L expr R\n" +
                "     | expr PLUS expr\n" +
                "     | ID\n" +
                "     ;\n"
        val g = Grammar(gtext)
        val atnText =
            "RuleStart" + "_expr_2->BlockStart" + "_13\n" +
                "BlockStart" + "_13->s7\n" +
                "BlockStart" + "_13->s12\n" +
                "s7-action" + "_1:-1->s8\n" +
                "s12-ID->BlockEnd" + "_14\n" +
                "s8-L->s9\n" +
                "BlockEnd" + "_14->StarLoopEntry" + "_20\n" +
                "s9-expr->RuleStart" + "_expr_2\n" +
                "StarLoopEntry" + "_20->StarBlockStart" + "_18\n" +
                "StarLoopEntry" + "_20->s21\n" +
                "s10-R->s11\n" +
                "StarBlockStart" + "_18->s15\n" +
                "s21->RuleStop" + "_expr_3\n" +
                "s11->BlockEnd" + "_14\n" +
                "s15-2 >= " + "_p->s16\n" +
                "RuleStop" + "_expr_3->s5\n" +
                "RuleStop" + "_expr_3->s10\n" +
                "RuleStop" + "_expr_3->BlockEnd" + "_19\n" +
                "s16-PLUS->s17\n" +
                "s17-expr->RuleStart" + "_expr_2\n" +
                "BlockEnd" + "_19->StarLoopBack" + "_22\n" +
                "StarLoopBack" + "_22->StarLoopEntry" + "_20\n"
        checkRuleATNText(g, "expr", atnText)

        val atn = g.getATN()
        val callStackFromS = ParserRuleContext(null, 4)
        var callStackFromExpr = ParserRuleContext(callStackFromS, 9)
        val afterID = 14
        var tokens = atn.getExpectedTokens(afterID, callStackFromExpr)
        assertEquals("{R, PLUS}", tokens.toString(g.getVocabulary()))

        callStackFromExpr = ParserRuleContext(null, 9)
        tokens = atn.getExpectedTokens(afterID, callStackFromExpr)
        assertEquals("{R, PLUS}", tokens.toString(g.getVocabulary()))
    }
}

class GraphNodesTest {
    private fun rootIsWildcard(): Boolean = true

    private fun fullCtx(): Boolean = false

    @Test
    fun testEmptyEmpty() {
        val r = PredictionContext.merge(EmptyPredictionContext.Instance, EmptyPredictionContext.Instance, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"*\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testEmptyEmptyFullCtx() {
        val r = PredictionContext.merge(EmptyPredictionContext.Instance, EmptyPredictionContext.Instance, fullCtx(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"$\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, fullCtx()))
    }

    @Test
    fun testXEmpty() {
        val r = PredictionContext.merge(x(), EmptyPredictionContext.Instance, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"*\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testXEmptyFullCtx() {
        val r = PredictionContext.merge(x(), EmptyPredictionContext.Instance, fullCtx(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>$\"];\n" +
                "  s1[label=\"$\"];\n" +
                "  s0:p0->s1[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, fullCtx()))
    }

    @Test
    fun testEmptyX() {
        val r = PredictionContext.merge(EmptyPredictionContext.Instance, x(), rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"*\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testEmptyXFullCtx() {
        val r = PredictionContext.merge(EmptyPredictionContext.Instance, x(), fullCtx(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>$\"];\n" +
                "  s1[label=\"$\"];\n" +
                "  s0:p0->s1[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, fullCtx()))
    }

    @Test
    fun testAA() {
        val r = PredictionContext.merge(a(), a(), rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAEmptyAx() {
        val a1 = a()
        val xNode = x()
        val a2 = createSingleton(xNode, 1)
        val r = PredictionContext.merge(a1, a2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAEmptyAxFullCtx() {
        val a1 = a()
        val xNode = x()
        val a2 = createSingleton(xNode, 1)
        val r = PredictionContext.merge(a1, a2, fullCtx(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[shape=record, label=\"<p0>|<p1>$\"];\n" +
                "  s2[label=\"$\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "  s1:p0->s2[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, fullCtx()))
    }

    @Test
    fun testAxEmptyAEmpty() {
        val xNode = x()
        val a1 = createSingleton(xNode, 1)
        val a2 = a()
        val r = PredictionContext.merge(a1, a2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAaEmptyAEmptyEmptyFullCtx() {
        val empty = EmptyPredictionContext.Instance
        val child1 = createSingleton(empty, 8)
        val right = PredictionContext.merge(empty, child1, false, null)
        val left = createSingleton(right, 8)
        val merged = PredictionContext.merge(left, right, false, null)
        val actual = toDOTString(merged, false)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>$\"];\n" +
                "  s1[shape=record, label=\"<p0>|<p1>$\"];\n" +
                "  s2[label=\"$\"];\n" +
                "  s0:p0->s1[label=\"8\"];\n" +
                "  s1:p0->s2[label=\"8\"];\n" +
                "}\n"
        assertEquals(expecting, actual)
    }

    @Test
    fun testAxEmptyAEmptyFullCtx() {
        val xNode = x()
        val a1 = createSingleton(xNode, 1)
        val a2 = a()
        val r = PredictionContext.merge(a1, a2, fullCtx(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[shape=record, label=\"<p0>|<p1>$\"];\n" +
                "  s2[label=\"$\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "  s1:p0->s2[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, fullCtx()))
    }

    @Test
    fun testAB() {
        val r = PredictionContext.merge(a(), b(), rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s1[label=\"2\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAxAxSame() {
        val xNode = x()
        val a1 = createSingleton(xNode, 1)
        val a2 = createSingleton(xNode, 1)
        val r = PredictionContext.merge(a1, a2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s2[label=\"*\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "  s1->s2[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAxAx() {
        val x1 = x()
        val x2 = x()
        val a1 = createSingleton(x1, 1)
        val a2 = createSingleton(x2, 1)
        val r = PredictionContext.merge(a1, a2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s2[label=\"*\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "  s1->s2[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAbxAbx() {
        val x1 = x()
        val x2 = x()
        val b1 = createSingleton(x1, 2)
        val b2 = createSingleton(x2, 2)
        val a1 = createSingleton(b1, 1)
        val a2 = createSingleton(b2, 1)
        val r = PredictionContext.merge(a1, a2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s2[label=\"2\"];\n" +
                "  s3[label=\"*\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "  s1->s2[label=\"2\"];\n" +
                "  s2->s3[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAbxAcx() {
        val x1 = x()
        val x2 = x()
        val bNode = createSingleton(x1, 2)
        val cNode = createSingleton(x2, 3)
        val a1 = createSingleton(bNode, 1)
        val a2 = createSingleton(cNode, 1)
        val r = PredictionContext.merge(a1, a2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s2[label=\"2\"];\n" +
                "  s3[label=\"*\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "  s1:p0->s2[label=\"2\"];\n" +
                "  s1:p1->s2[label=\"3\"];\n" +
                "  s2->s3[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAxBxSame() {
        val xNode = x()
        val aNode = createSingleton(xNode, 1)
        val bNode = createSingleton(xNode, 2)
        val r = PredictionContext.merge(aNode, bNode, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s2[label=\"*\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s1[label=\"2\"];\n" +
                "  s1->s2[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAxBx() {
        val x1 = x()
        val x2 = x()
        val aNode = createSingleton(x1, 1)
        val bNode = createSingleton(x2, 2)
        val r = PredictionContext.merge(aNode, bNode, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s2[label=\"*\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s1[label=\"2\"];\n" +
                "  s1->s2[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAxBy() {
        val aNode = createSingleton(x(), 1)
        val bNode = createSingleton(y(), 2)
        val r = PredictionContext.merge(aNode, bNode, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s2[label=\"2\"];\n" +
                "  s3[label=\"*\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s2[label=\"2\"];\n" +
                "  s2->s3[label=\"10\"];\n" +
                "  s1->s3[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAEmptyBx() {
        val x2 = x()
        val aNode = a()
        val bNode = createSingleton(x2, 2)
        val r = PredictionContext.merge(aNode, bNode, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s2[label=\"2\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s2[label=\"2\"];\n" +
                "  s2->s1[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testAEmptyBxFullCtx() {
        val x2 = x()
        val aNode = a()
        val bNode = createSingleton(x2, 2)
        val r = PredictionContext.merge(aNode, bNode, fullCtx(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s2[label=\"2\"];\n" +
                "  s1[label=\"$\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s2[label=\"2\"];\n" +
                "  s2->s1[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, fullCtx()))
    }

    // Upstream TestGraphNodes.testAexBfx is @Disabled because PredictionContext.merge intentionally leaves non-adjacent top nodes unmerged.

    @Test
    fun testArrayEmptyArrayEmptyFullCtx() {
        val arr1 = array(EmptyPredictionContext.Instance)
        val arr2 = array(EmptyPredictionContext.Instance)
        val r = PredictionContext.merge(arr1, arr2, fullCtx(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"$\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, fullCtx()))
    }

    @Test
    fun testArrayAbArrayC() {
        val arr1 = array(a(), b())
        val arr2 = array(c())
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>|<p2>\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s1[label=\"2\"];\n" +
                "  s0:p2->s1[label=\"3\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAArrayA() {
        val arr1 = array(a())
        val arr2 = array(a())
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAArrayBc() {
        val arr1 = array(a())
        val arr2 = array(b(), c())
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>|<p2>\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s1[label=\"2\"];\n" +
                "  s0:p2->s1[label=\"3\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAcArrayB() {
        val arr1 = array(a(), c())
        val arr2 = array(b())
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>|<p2>\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s1[label=\"2\"];\n" +
                "  s0:p2->s1[label=\"3\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAbArrayA() {
        val arr1 = array(a(), b())
        val arr2 = array(a())
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s1[label=\"2\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAbArrayB() {
        val arr1 = array(a(), b())
        val arr2 = array(b())
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s1[label=\"*\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s1[label=\"2\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAxArrayBy() {
        val arr1 = array(createSingleton(x(), 1))
        val arr2 = array(createSingleton(y(), 2))
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s2[label=\"2\"];\n" +
                "  s3[label=\"*\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s2[label=\"2\"];\n" +
                "  s2->s3[label=\"10\"];\n" +
                "  s1->s3[label=\"9\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAxArrayAy() {
        val arr1 = array(createSingleton(x(), 1))
        val arr2 = array(createSingleton(y(), 1))
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[label=\"0\"];\n" +
                "  s1[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s2[label=\"*\"];\n" +
                "  s0->s1[label=\"1\"];\n" +
                "  s1:p0->s2[label=\"9\"];\n" +
                "  s1:p1->s2[label=\"10\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAxcArrayAyd() {
        val arr1 = array(createSingleton(x(), 1), c())
        val arr2 = array(createSingleton(y(), 1), d())
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>|<p2>\"];\n" +
                "  s2[label=\"*\"];\n" +
                "  s1[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s2[label=\"3\"];\n" +
                "  s0:p2->s2[label=\"4\"];\n" +
                "  s1:p0->s2[label=\"9\"];\n" +
                "  s1:p1->s2[label=\"10\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAubvArrayCwdx() {
        val arr1 = array(createSingleton(u(), 1), createSingleton(v(), 2))
        val arr2 = array(createSingleton(w(), 3), createSingleton(x(), 4))
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>|<p2>|<p3>\"];\n" +
                "  s4[label=\"4\"];\n" +
                "  s5[label=\"*\"];\n" +
                "  s3[label=\"3\"];\n" +
                "  s2[label=\"2\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s2[label=\"2\"];\n" +
                "  s0:p2->s3[label=\"3\"];\n" +
                "  s0:p3->s4[label=\"4\"];\n" +
                "  s4->s5[label=\"9\"];\n" +
                "  s3->s5[label=\"8\"];\n" +
                "  s2->s5[label=\"7\"];\n" +
                "  s1->s5[label=\"6\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAubvArrayBvdx() {
        val arr1 = array(createSingleton(u(), 1), createSingleton(v(), 2))
        val arr2 = array(createSingleton(v(), 2), createSingleton(x(), 4))
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>|<p2>\"];\n" +
                "  s3[label=\"3\"];\n" +
                "  s4[label=\"*\"];\n" +
                "  s2[label=\"2\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s2[label=\"2\"];\n" +
                "  s0:p2->s3[label=\"4\"];\n" +
                "  s3->s4[label=\"9\"];\n" +
                "  s2->s4[label=\"7\"];\n" +
                "  s1->s4[label=\"6\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAubvArrayBwdx() {
        val arr1 = array(createSingleton(u(), 1), createSingleton(v(), 2))
        val arr2 = array(createSingleton(w(), 2), createSingleton(x(), 4))
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>|<p2>\"];\n" +
                "  s3[label=\"3\"];\n" +
                "  s4[label=\"*\"];\n" +
                "  s2[shape=record, label=\"<p0>|<p1>\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s2[label=\"2\"];\n" +
                "  s0:p2->s3[label=\"4\"];\n" +
                "  s3->s4[label=\"9\"];\n" +
                "  s2:p0->s4[label=\"7\"];\n" +
                "  s2:p1->s4[label=\"8\"];\n" +
                "  s1->s4[label=\"6\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAubvArrayBvdu() {
        val arr1 = array(createSingleton(u(), 1), createSingleton(v(), 2))
        val arr2 = array(createSingleton(v(), 2), createSingleton(u(), 4))
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>|<p2>\"];\n" +
                "  s2[label=\"2\"];\n" +
                "  s3[label=\"*\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s2[label=\"2\"];\n" +
                "  s0:p2->s1[label=\"4\"];\n" +
                "  s2->s3[label=\"7\"];\n" +
                "  s1->s3[label=\"6\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    @Test
    fun testArrayAubuArrayCudu() {
        val arr1 = array(createSingleton(u(), 1), createSingleton(u(), 2))
        val arr2 = array(createSingleton(u(), 3), createSingleton(u(), 4))
        val r = PredictionContext.merge(arr1, arr2, rootIsWildcard(), null)
        val expecting =
            "digraph G {\n" +
                "rankdir=LR;\n" +
                "  s0[shape=record, label=\"<p0>|<p1>|<p2>|<p3>\"];\n" +
                "  s1[label=\"1\"];\n" +
                "  s2[label=\"*\"];\n" +
                "  s0:p0->s1[label=\"1\"];\n" +
                "  s0:p1->s1[label=\"2\"];\n" +
                "  s0:p2->s1[label=\"3\"];\n" +
                "  s0:p3->s1[label=\"4\"];\n" +
                "  s1->s2[label=\"6\"];\n" +
                "}\n"
        assertEquals(expecting, toDOTString(r, rootIsWildcard()))
    }

    private fun a(): SingletonPredictionContext = createSingleton(EmptyPredictionContext.Instance, 1)

    private fun b(): SingletonPredictionContext = createSingleton(EmptyPredictionContext.Instance, 2)

    private fun c(): SingletonPredictionContext = createSingleton(EmptyPredictionContext.Instance, 3)

    private fun d(): SingletonPredictionContext = createSingleton(EmptyPredictionContext.Instance, 4)

    private fun u(): SingletonPredictionContext = createSingleton(EmptyPredictionContext.Instance, 6)

    private fun v(): SingletonPredictionContext = createSingleton(EmptyPredictionContext.Instance, 7)

    private fun w(): SingletonPredictionContext = createSingleton(EmptyPredictionContext.Instance, 8)

    private fun x(): SingletonPredictionContext = createSingleton(EmptyPredictionContext.Instance, 9)

    private fun y(): SingletonPredictionContext = createSingleton(EmptyPredictionContext.Instance, 10)

    private fun createSingleton(
        parent: PredictionContext?,
        payload: Int,
    ): SingletonPredictionContext = SingletonPredictionContext.create(parent, payload)

    private fun array(vararg nodes: SingletonPredictionContext): ArrayPredictionContext {
        val parents = arrayOfNulls<PredictionContext>(nodes.size)
        val invokingStates = IntArray(nodes.size)
        for (i in nodes.indices) {
            parents[i] = nodes[i].parent
            invokingStates[i] = nodes[i].returnState
        }
        return ArrayPredictionContext(parents, invokingStates)
    }

    companion object {
        private fun identityId(
            seen: MutableList<PredictionContext>,
            node: PredictionContext,
        ): Int = seen.indexOfFirst { it === node }

        fun toDOTString(
            context: PredictionContext?,
            rootIsWildcard: Boolean,
        ): String {
            assertNotNull(context)
            val nodes = StringBuilder()
            val edges = StringBuilder()
            val seen = ArrayList<PredictionContext>()
            val workList = ArrayDeque<PredictionContext>()
            seen.add(context)
            workList.addLast(context)
            while (workList.isNotEmpty()) {
                val current = workList.removeFirst()
                val currentId = identityId(seen, current)
                nodes.append("  s").append(currentId).append('[')
                if (current.size() > 1) {
                    nodes.append("shape=record, ")
                }
                nodes.append("label=\"")
                if (current.isEmpty) {
                    nodes.append(if (rootIsWildcard) '*' else '$')
                } else if (current.size() > 1) {
                    for (i in 0 until current.size()) {
                        if (i > 0) {
                            nodes.append('|')
                        }
                        nodes.append("<p").append(i).append('>')
                        if (current.getReturnState(i) == PredictionContext.EMPTY_RETURN_STATE) {
                            nodes.append(if (rootIsWildcard) '*' else '$')
                        }
                    }
                } else {
                    nodes.append(currentId)
                }
                nodes.append("\"];\n")

                if (current.isEmpty) {
                    continue
                }
                for (i in 0 until current.size()) {
                    if (current.getReturnState(i) == PredictionContext.EMPTY_RETURN_STATE) {
                        continue
                    }
                    val parent = requireNotNull(current.getParent(i))
                    var parentId = identityId(seen, parent)
                    if (parentId < 0) {
                        parentId = seen.size
                        seen.add(parent)
                        workList.addFirst(parent)
                    }
                    edges.append("  s").append(currentId)
                    if (current.size() > 1) {
                        edges.append(":p").append(i)
                    }
                    edges.append("->s").append(parentId)
                    edges.append("[label=\"").append(current.getReturnState(i)).append("\"];\n")
                }
            }
            val builder = StringBuilder()
            builder.append("digraph G {\n")
            builder.append("rankdir=LR;\n")
            builder.append(nodes)
            builder.append(edges)
            builder.append("}\n")
            return builder.toString()
        }
    }
}
