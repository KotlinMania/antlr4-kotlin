/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.ANTLRInputStream
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.tooling.gui.Trees
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.GrammarParserInterpreter
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LookaheadTreesTest {
    @Test
    fun testAlts() {
        val lg = LexerGrammar(LEXER_TEXT)
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : e SEMI EOF ;\n" +
                    "e : ID DOT ID\n" +
                    "  | ID LPAREN RPAREN\n" +
                    "  ;\n",
                lg,
            )

        testLookaheadTrees(
            lg = lg,
            g = g,
            input = "a.b;",
            startRuleName = "s",
            decision = 0,
            expectedTrees = arrayOf("(e:1 a . b)", "(e:2 a <error .>)"),
        )
    }

    @Test
    fun testAlts2() {
        val lg = LexerGrammar(LEXER_TEXT)
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : e? SEMI EOF ;\n" +
                    "e : ID\n" +
                    "  | e BANG" +
                    "  ;\n",
                lg,
            )

        testLookaheadTrees(
            lg = lg,
            g = g,
            input = "a;",
            startRuleName = "s",
            decision = 1,
            expectedTrees =
                arrayOf(
                    "(e:2 (e:1 a) <error ;>)",
                    "(s:1 (e:1 a) ; <EOF>)",
                ),
        )
    }

    @Test
    fun testIncludeEOF() {
        val lg = LexerGrammar(LEXER_TEXT)
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : e ;\n" +
                    "e : ID DOT ID EOF\n" +
                    "  | ID DOT ID EOF\n" +
                    "  ;\n",
                lg,
            )

        testLookaheadTrees(
            lg = lg,
            g = g,
            input = "a.b",
            startRuleName = "s",
            decision = 0,
            expectedTrees = arrayOf("(e:1 a . b <EOF>)", "(e:2 a . b <EOF>)"),
        )
    }

    @Test
    fun testCallLeftRecursiveRule() {
        val lg = LexerGrammar(LEXER_TEXT)
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : a BANG EOF;\n" +
                    "a : e SEMI \n" +
                    "  | ID SEMI \n" +
                    "  ;" +
                    "e : e MULT e\n" +
                    "  | e PLUS e\n" +
                    "  | e DOT e\n" +
                    "  | ID\n" +
                    "  | INT\n" +
                    "  ;\n",
                lg,
            )

        testLookaheadTrees(
            lg = lg,
            g = g,
            input = "x;!",
            startRuleName = "s",
            decision = 0,
            expectedTrees =
                arrayOf(
                    "(a:1 (e:4 x) ;)",
                    "(a:2 x ;)",
                ),
        )
        testLookaheadTrees(
            lg = lg,
            g = g,
            input = "x+1;!",
            startRuleName = "s",
            decision = 2,
            expectedTrees =
                arrayOf(
                    "(e:1 (e:4 x) <error +>)",
                    "(e:2 (e:4 x) + (e:5 1))",
                    "(e:3 (e:4 x) <error +>)",
                ),
        )
    }

    private fun testLookaheadTrees(
        lg: LexerGrammar,
        g: Grammar,
        input: String,
        startRuleName: String,
        decision: Int,
        expectedTrees: Array<String>,
    ) {
        val startRuleIndex = assertNotNull(g.getRule(startRuleName)).index
        val nodeTextProvider = InterpreterTreeTextProvider(g.ruleNames)

        val lexEngine = lg.createLexerInterpreter(ANTLRInputStream(input))
        val tokens = CommonTokenStream(lexEngine)
        val parser = g.createGrammarParserInterpreter(tokens)
        parser.setProfile(true)
        parser.parse(startRuleIndex)

        val parseInfo = assertNotNull(parser.parseInfo)
        val decisionInfo = parseInfo.decisionInfo[decision]
        val lookaheadEventInfo = assertNotNull(decisionInfo.SLL_MaxLookEvent)

        val lookaheadParseTrees =
            GrammarParserInterpreter.getLookaheadParseTrees(
                g,
                parser,
                tokens,
                startRuleIndex,
                lookaheadEventInfo.decision,
                lookaheadEventInfo.startIndex,
                lookaheadEventInfo.stopIndex,
            )

        assertEquals(expectedTrees.size, lookaheadParseTrees.size)
        for (i in lookaheadParseTrees.indices) {
            val lt = lookaheadParseTrees[i]
            assertEquals(expectedTrees[i], Trees.toStringTree(lt, nodeTextProvider))
        }
    }

    companion object {
        const val LEXER_TEXT: String =
            "lexer grammar L;\n" +
                "DOT  : '.' ;\n" +
                "SEMI : ';' ;\n" +
                "BANG : '!' ;\n" +
                "PLUS : '+' ;\n" +
                "LPAREN : '(' ;\n" +
                "RPAREN : ')' ;\n" +
                "MULT : '*' ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\t\\n]+ ;\n"
    }
}
