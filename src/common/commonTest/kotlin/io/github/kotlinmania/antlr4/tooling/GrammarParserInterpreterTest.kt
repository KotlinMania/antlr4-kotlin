/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.ANTLRInputStream
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.InterpreterRuleContext
import io.github.kotlinmania.antlr4.tooling.gui.Trees
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GrammarParserInterpreterTest {
    @Test
    fun testAlts() {
        val lg = LexerGrammar(LEXER_TEXT)
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : ID\n" +
                    "  | INT{;}\n" +
                    "  ;\n",
                lg,
            )
        testInterp(lg, g, "s", "a", "(s:1 a)")
        testInterp(lg, g, "s", "3", "(s:2 3)")
    }

    @Test
    fun testAltsAsSet() {
        val lg = LexerGrammar(LEXER_TEXT)
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : ID\n" +
                    "  | INT\n" +
                    "  ;\n",
                lg,
            )
        testInterp(lg, g, "s", "a", "(s:1 a)")
        testInterp(lg, g, "s", "3", "(s:1 3)")
    }

    @Test
    fun testAltsWithLabels() {
        val lg = LexerGrammar(LEXER_TEXT)
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : ID  # foo\n" +
                    "  | INT # bar\n" +
                    "  ;\n",
                lg,
            )
        testInterp(lg, g, "s", "a", "(s:1 a)")
        testInterp(lg, g, "s", "3", "(s:2 3)")
    }

    @Test
    fun testOneAlt() {
        val lg = LexerGrammar(LEXER_TEXT)
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : ID\n" +
                    "  ;\n",
                lg,
            )
        testInterp(lg, g, "s", "a", "(s:1 a)")
    }

    @Test
    fun testLeftRecursionWithMultiplePrimaryAndRecursiveOps() {
        val lg = LexerGrammar(LEXER_TEXT)
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : e EOF ;\n" +
                    "e : e MULT e\n" +
                    "  | e PLUS e\n" +
                    "  | INT\n" +
                    "  | ID\n" +
                    "  ;\n",
                lg,
            )

        testInterp(lg, g, "s", "a", "(s:1 (e:4 a) <EOF>)")
        testInterp(lg, g, "e", "a", "(e:4 a)")
        testInterp(lg, g, "e", "34", "(e:3 34)")
        testInterp(lg, g, "e", "a+1", "(e:2 (e:4 a) + (e:3 1))")
        testInterp(lg, g, "e", "1+2*a", "(e:2 (e:3 1) + (e:1 (e:3 2) * (e:4 a)))")
    }

    private fun testInterp(
        lg: LexerGrammar,
        g: Grammar,
        startRule: String,
        input: String,
        expectedParseTree: String,
    ): InterpreterRuleContext? {
        val lexEngine = lg.createLexerInterpreter(ANTLRInputStream(input))
        val tokens = CommonTokenStream(lexEngine)
        val parser = g.createGrammarParserInterpreter(tokens)
        val rule = assertNotNull(g.getRule(startRule))
        val t = parser.parse(rule.index)
        val nodeTextProvider = InterpreterTreeTextProvider(g.ruleNames)
        val treeStr = Trees.toStringTree(t, nodeTextProvider)
        assertEquals(expectedParseTree, treeStr)
        return t as? InterpreterRuleContext
    }

    companion object {
        private const val LEXER_TEXT: String =
            "lexer grammar L;\n" +
                "PLUS : '+' ;\n" +
                "MULT : '*' ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\t\\n]+ ;\n"
    }
}
