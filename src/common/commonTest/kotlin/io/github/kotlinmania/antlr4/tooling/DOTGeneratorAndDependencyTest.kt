/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.ANTLRInputStream
import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.tooling.gui.Interpreter
import io.github.kotlinmania.antlr4.tooling.gui.TestRig
import io.github.kotlinmania.antlr4.tooling.tool.BuildDependencyGenerator
import io.github.kotlinmania.antlr4.tooling.tool.DOTGenerator
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DOTGeneratorAndDependencyTest {
    @Test
    fun testParserAndLexerATNDOTAndDFADOT() {
        val lg =
            LexerGrammar(
                "lexer grammar CalcLexer;\n" +
                    "PLUS : '+' ;\n" +
                    "INT  : [0-9]+ ;\n" +
                    "WS   : [ \\t\\r\\n]+ -> skip ;\n",
            )
        val g =
            Grammar(
                "parser grammar CalcParser;\n" +
                    "s : expr EOF ;\n" +
                    "expr : expr PLUS expr | INT ;\n",
                lg,
            )

        val parserDotGen = DOTGenerator(g)
        val exprRule = assertNotNull(g.getRule("expr"))
        val parserDot = assertNotNull(parserDotGen.getDOT(g.getATN().ruleToStartState[exprRule.index]))
        assertTrue(parserDot.contains("digraph ATN"))
        assertTrue(parserDot.contains("<expr"))

        val lexerDotGen = DOTGenerator(lg)
        val intRule = assertNotNull(lg.getRule("INT"))
        val lexerDot = assertNotNull(lexerDotGen.getDOT(lg.getATN().ruleToStartState[intRule.index], true))
        assertTrue(lexerDot.contains("digraph ATN"))
        assertTrue(lexerDot.contains("'0'..'9'"))

        val lexInterp = lg.createLexerInterpreter(ANTLRInputStream("10 + 20"))
        val tokens = CommonTokenStream(lexInterp)
        val parserInterp = g.createParserInterpreter(tokens)
        val tree = assertNotNull(parserInterp.parse(exprRule.index))
        assertEquals("(expr (expr 10) + (expr 20))", tree.toStringTree(parserInterp))

        val sim = assertNotNull(parserInterp.interpreter)
        val populatedDfa = sim.decisionToDFA.firstOrNull { it.s0 != null }
        val nonNullDfa = assertNotNull(populatedDfa)
        val dfaDot = assertNotNull(parserDotGen.getDOT(nonNullDfa, false))
        assertTrue(dfaDot.contains("digraph DFA"))
    }

    @Test
    fun testBuildDependencyGeneratorForCombinedAndSplitGrammars() {
        val tool = Tool(listOf("-visitor", "-listener", "-o", "gen"))
        tool.writeFilesToDisk = false
        val combinedRoot =
            assertNotNull(
                tool.parseGrammarFromString(
                    "grammar Expr;\n" +
                        "e : ID ;\n" +
                        "ID : [a-z]+ ;\n",
                ),
            )
        val combinedGrammar = tool.createGrammar(combinedRoot)
        combinedGrammar.fileName = "Expr.g4"

        val depGen = BuildDependencyGenerator(tool, combinedGrammar)
        val generated = assertNotNull(depGen.generatedFileList)
        assertTrue(generated.contains("gen/ExprParser.kt"))
        assertTrue(generated.contains("gen/ExprLexer.kt"))
        assertTrue(generated.contains("gen/Expr.tokens"))
        assertTrue(generated.contains("gen/ExprListener.kt"))
        assertTrue(generated.contains("gen/ExprBaseListener.kt"))
        assertTrue(generated.contains("gen/ExprVisitor.kt"))
        assertTrue(generated.contains("gen/ExprBaseVisitor.kt"))

        val rendered = depGen.renderDependencies()
        assertTrue(rendered.contains("gen/ExprParser.kt : Expr.g4"))
    }

    @Test
    fun testInterpreterAndTestRigDirectGrammarExecution() {
        val interp = Interpreter(listOf("Calc.g4", "expr", "-tree", "-tokens"))
        val parseInfo =
            assertNotNull(
                interp.interpGrammarText(
                    grammarText =
                        "grammar Calc;\n" +
                            "s : expr EOF ;\n" +
                            "expr : expr '*' expr | expr '+' expr | INT | ID ;\n" +
                            "ID : [a-z]+ ;\n" +
                            "INT : [0-9]+ ;\n" +
                            "WS : [ \\t]+ -> skip ;\n",
                    charStream = CharStreams.fromString("2 + 3 * 4"),
                    enableProfile = true,
                ),
            )
        assertEquals("(expr:2 (expr:3 2) + (expr:1 (expr:3 3) * (expr:3 4)))", interp.lastRenderedTree)
        assertTrue(interp.lastTokens.isNotEmpty())
        assertTrue(parseInfo.decisionInfo.isNotEmpty())
        val csv = interp.formatProfilerCSV(assertNotNull(interp.lastParser), parseInfo)
        assertTrue(csv.contains("Rule,Invocations,Time (ms)"))
        assertTrue(csv.contains("expr:"))

        val combined =
            Grammar(
                "grammar Mini;\n" +
                    "stat : ID '=' INT ;\n" +
                    "ID : [a-z]+ ;\n" +
                    "INT : [0-9]+ ;\n" +
                    "WS : [ \\t]+ -> skip ;\n",
            )
        val implicitLexer = assertNotNull(combined.implicitLexer)
        val rig = TestRig(listOf("Mini", "stat", "-tree", "-tokens"))
        val lexEngine = implicitLexer.createLexerInterpreter(CharStreams.fromString("x = 42"))
        val parseEngine = combined.createParserInterpreter(CommonTokenStream(lexEngine))
        val result =
            rig.processInput(
                lexer = implicitLexer.createLexerInterpreter(CharStreams.fromString("")),
                parser = parseEngine,
                input = CharStreams.fromString("x = 42"),
                emitStdout = false,
            )
        assertEquals("(stat x = 42)", result.treeText)
        assertEquals(4, result.tokens.size)
    }
}
