/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.ANTLRInputStream
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.atn.BasicBlockStartState
import io.github.kotlinmania.antlr4.atn.DecisionState
import io.github.kotlinmania.antlr4.atn.PredictionMode
import io.github.kotlinmania.antlr4.tooling.gui.Trees
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.GrammarParserInterpreter
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AmbigParseTreesTest {
    @Test
    fun testParseDecisionWithinAmbiguousStartRule() {
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
                    "s : A x C" +
                    "  | A B C" +
                    "  ;" +
                    "x : B ; \n",
                lg,
            )

        testInterpAtSpecificAlt(lg, g, "s", 1, "abc", "(s:1 a (x:1 b) c)")
        testInterpAtSpecificAlt(lg, g, "s", 2, "abc", "(s:2 a b c)")
    }

    @Test
    fun testAmbigAltsAtRoot() {
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
                    "s : A x C" +
                    "  | A B C" +
                    "  ;" +
                    "x : B ; \n",
                lg,
            )

        testAmbiguousTrees(
            lg = lg,
            g = g,
            startRule = "s",
            input = "abc",
            decision = 0,
            expectedAmbigAlts = "{1, 2}",
            overallTree = "(s:1 a (x:1 b) c)",
            expectedParseTrees = arrayOf("(s:1 a (x:1 b) c)", "(s:2 a b c)"),
        )
    }

    @Test
    fun testAmbigAltsNotAtRoot() {
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
                    "s : x ;" +
                    "x : y ;" +
                    "y : A z C" +
                    "  | A B C" +
                    "  ;" +
                    "z : B ; \n",
                lg,
            )

        testAmbiguousTrees(
            lg = lg,
            g = g,
            startRule = "s",
            input = "abc",
            decision = 0,
            expectedAmbigAlts = "{1, 2}",
            overallTree = "(s:1 (x:1 (y:1 a (z:1 b) c)))",
            expectedParseTrees = arrayOf("(y:1 a (z:1 b) c)", "(y:2 a b c)"),
        )
    }

    @Test
    fun testAmbigAltDipsIntoOuterContextToRoot() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "SELF : 'self' ;\n" +
                    "ID : [a-z]+ ;\n" +
                    "DOT : '.' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "e : p (DOT ID)* ;\n" +
                    "p : SELF" +
                    "  | SELF DOT ID" +
                    "  ;",
                lg,
            )

        testAmbiguousTrees(
            lg = lg,
            g = g,
            startRule = "e",
            input = "self.x",
            decision = 1,
            expectedAmbigAlts = "{1, 2}",
            overallTree = "(e:1 (p:1 self) . x)",
            expectedParseTrees = arrayOf("(e:1 (p:1 self) . x)", "(p:2 self . x)"),
        )
    }

    @Test
    fun testAmbigAltDipsIntoOuterContextBelowRoot() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "SELF : 'self' ;\n" +
                    "ID : [a-z]+ ;\n" +
                    "DOT : '.' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : e ;\n" +
                    "e : p (DOT ID)* ;\n" +
                    "p : SELF" +
                    "  | SELF DOT ID" +
                    "  ;",
                lg,
            )

        testAmbiguousTrees(
            lg = lg,
            g = g,
            startRule = "s",
            input = "self.x",
            decision = 1,
            expectedAmbigAlts = "{1, 2}",
            overallTree = "(s:1 (e:1 (p:1 self) . x))",
            expectedParseTrees = arrayOf("(e:1 (p:1 self) . x)", "(p:2 self . x)"),
        )
    }

    @Test
    fun testAmbigAltInLeftRecursiveBelowStartRule() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "SELF : 'self' ;\n" +
                    "ID : [a-z]+ ;\n" +
                    "DOT : '.' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : e ;\n" +
                    "e : p | e DOT ID ;\n" +
                    "p : SELF" +
                    "  | SELF DOT ID" +
                    "  ;",
                lg,
            )

        testAmbiguousTrees(
            lg = lg,
            g = g,
            startRule = "s",
            input = "self.x",
            decision = 1,
            expectedAmbigAlts = "{1, 2}",
            overallTree = "(s:1 (e:2 (e:1 (p:1 self)) . x))",
            expectedParseTrees = arrayOf("(e:2 (e:1 (p:1 self)) . x)", "(p:2 self . x)"),
        )
    }

    @Test
    fun testAmbigAltInLeftRecursiveStartRule() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "SELF : 'self' ;\n" +
                    "ID : [a-z]+ ;\n" +
                    "DOT : '.' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "e : p | e DOT ID ;\n" +
                    "p : SELF" +
                    "  | SELF DOT ID" +
                    "  ;",
                lg,
            )

        testAmbiguousTrees(
            lg = lg,
            g = g,
            startRule = "e",
            input = "self.x",
            decision = 1,
            expectedAmbigAlts = "{1, 2}",
            overallTree = "(e:2 (e:1 (p:1 self)) . x)",
            expectedParseTrees = arrayOf("(e:2 (e:1 (p:1 self)) . x)", "(p:2 self . x)"),
        )
    }

    private fun testAmbiguousTrees(
        lg: LexerGrammar,
        g: Grammar,
        startRule: String,
        input: String,
        decision: Int,
        expectedAmbigAlts: String,
        overallTree: String,
        expectedParseTrees: Array<String>,
    ) {
        val nodeTextProvider = InterpreterTreeTextProvider(g.ruleNames)

        val lexEngine = lg.createLexerInterpreter(ANTLRInputStream(input))
        val tokens = CommonTokenStream(lexEngine)
        val parser = g.createGrammarParserInterpreter(tokens)
        parser.setProfile(true)
        assertNotNull(parser.interpreter).setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION)

        val ruleIndex = assertNotNull(g.getRule(startRule)).index
        val parseTree = parser.parse(ruleIndex)
        assertEquals(overallTree, Trees.toStringTree(parseTree, nodeTextProvider))

        val parseInfo = assertNotNull(parser.parseInfo)
        val decisionInfo = parseInfo.decisionInfo
        val ambiguities = decisionInfo[decision].ambiguities
        assertEquals(1, ambiguities.size)
        val ambiguityInfo = ambiguities[0]
        val ambigAlts = assertNotNull(ambiguityInfo.ambigAlts)

        val ambiguousParseTrees =
            GrammarParserInterpreter.getAllPossibleParseTrees(
                g,
                parser,
                tokens,
                decision,
                ambigAlts,
                ambiguityInfo.startIndex,
                ambiguityInfo.stopIndex,
                ruleIndex,
            )
        assertEquals(expectedAmbigAlts, ambigAlts.toString())
        assertEquals(ambigAlts.cardinality(), ambiguousParseTrees.size)

        for (i in ambiguousParseTrees.indices) {
            val t = ambiguousParseTrees[i]
            assertEquals(expectedParseTrees[i], Trees.toStringTree(t, nodeTextProvider))
        }
    }

    private fun testInterpAtSpecificAlt(
        lg: LexerGrammar,
        g: Grammar,
        startRule: String,
        startAlt: Int,
        input: String,
        expectedParseTree: String,
    ) {
        val lexEngine = lg.createLexerInterpreter(ANTLRInputStream(input))
        val tokens = CommonTokenStream(lexEngine)
        val parser = g.createGrammarParserInterpreter(tokens)
        val rule = assertNotNull(g.getRule(startRule))
        val ruleStartState = assertNotNull(g.getATN().ruleToStartState[rule.index])
        val tr = ruleStartState.transition(0)
        val t2 = tr.target
        require(t2 is BasicBlockStartState) { "rule has no decision: $startRule" }
        parser.addDecisionOverride((t2 as DecisionState).decision, 0, startAlt)
        val t = parser.parse(rule.index)
        val nodeTextProvider = InterpreterTreeTextProvider(g.ruleNames)
        assertEquals(expectedParseTree, Trees.toStringTree(t, nodeTextProvider))
    }
}
