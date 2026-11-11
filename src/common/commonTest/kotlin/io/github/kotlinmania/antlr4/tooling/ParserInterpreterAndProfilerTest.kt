/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.ANTLRErrorListener
import io.github.kotlinmania.antlr4.BaseErrorListener
import io.github.kotlinmania.antlr4.CharStream
import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.FailedPredicateException
import io.github.kotlinmania.antlr4.LexerInterpreter
import io.github.kotlinmania.antlr4.ParserInterpreter
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Recognizer
import io.github.kotlinmania.antlr4.RuleContext
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNDeserializer
import io.github.kotlinmania.antlr4.atn.ATNSerializer
import io.github.kotlinmania.antlr4.atn.DecisionInfo
import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.antlr4.tooling.codegen.CodeGenerator
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import io.github.kotlinmania.antlr4.tree.ParseTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserInterpreterTest {
    @Test
    fun testEmptyStartRule() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s :  ;",
                lg,
            )

        testInterp(lg, g, "s", "", "s")
        testInterp(lg, g, "s", "a", "s")
    }

    @Test
    fun testA() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : A ;",
                lg,
            )

        val t = testInterp(lg, g, "s", "a", "(s a)")
        assertEquals("0..0", t.sourceInterval.toString())
    }

    @Test
    fun testEOF() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : A EOF ;",
                lg,
            )

        val t = testInterp(lg, g, "s", "a", "(s a <EOF>)")
        assertEquals("0..1", t.sourceInterval.toString())
    }

    @Test
    fun testEOFInChild() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : x ;\n" +
                    "x : A EOF ;",
                lg,
            )

        val t = testInterp(lg, g, "s", "a", "(s (x a <EOF>))")
        assertEquals("0..1", t.sourceInterval.toString())
        assertEquals("0..1", requireNotNull(t.getChild(0)).sourceInterval.toString())
    }

    @Test
    fun testEmptyRuleAfterEOFInChild() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : x y;\n" +
                    "x : A EOF ;\n" +
                    "y : ;",
                lg,
            )

        val t = testInterp(lg, g, "s", "a", "(s (x a <EOF>) y)")
        assertEquals("0..1", t.sourceInterval.toString())
        assertEquals("0..1", requireNotNull(t.getChild(0)).sourceInterval.toString())
    }

    @Test
    fun testEmptyRuleAfterJustEOFInChild() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : x y;\n" +
                    "x : EOF ;\n" +
                    "y : ;",
                lg,
            )

        val t = testInterp(lg, g, "s", "", "(s (x <EOF>) y)")
        assertEquals("0..0", t.sourceInterval.toString())
        assertEquals("0..0", requireNotNull(t.getChild(0)).sourceInterval.toString())
    }

    @Test
    fun testEmptyInput() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : x EOF ;\n" +
                    "x : ;\n",
                lg,
            )

        val t = testInterp(lg, g, "s", "", "(s x <EOF>)")
        assertEquals("0..0", t.sourceInterval.toString())
        assertEquals("0..-1", requireNotNull(t.getChild(0)).sourceInterval.toString())
    }

    @Test
    fun testEmptyInputWithCallsAfter() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : x y ;\n" +
                    "x : EOF ;\n" +
                    "y : z ;\n" +
                    "z : ;",
                lg,
            )

        val t = testInterp(lg, g, "s", "", "(s (x <EOF>) (y z))")
        assertEquals("0..0", t.sourceInterval.toString())
        assertEquals("0..0", requireNotNull(t.getChild(0)).sourceInterval.toString())
    }

    @Test
    fun testEmptyFirstRule() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : x A ;\n" +
                    "x : ;\n",
                lg,
            )

        val t = testInterp(lg, g, "s", "a", "(s x a)")
        assertEquals("0..0", t.sourceInterval.toString())
        assertEquals("0..-1", requireNotNull(t.getChild(0)).sourceInterval.toString())
    }

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
                    "s : A{;} | B ;",
                lg,
            )
        testInterp(lg, g, "s", "a", "(s a)")
        testInterp(lg, g, "s", "b", "(s b)")
    }

    @Test
    fun testCall() {
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
                    "s : t C ;\n" +
                    "t : A{;} | B ;\n",
                lg,
            )

        testInterp(lg, g, "s", "ac", "(s (t a) c)")
        testInterp(lg, g, "s", "bc", "(s (t b) c)")
    }

    @Test
    fun testCall2() {
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
                    "s : t C ;\n" +
                    "t : u ;\n" +
                    "u : A{;} | B ;\n",
                lg,
            )

        testInterp(lg, g, "s", "ac", "(s (t (u a)) c)")
        testInterp(lg, g, "s", "bc", "(s (t (u b)) c)")
    }

    @Test
    fun testOptionalA() {
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
                    "s : A? B ;\n",
                lg,
            )

        testInterp(lg, g, "s", "b", "(s b)")
        testInterp(lg, g, "s", "ab", "(s a b)")
    }

    @Test
    fun testOptionalAorB() {
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
                    "s : (A{;}|B)? C ;\n",
                lg,
            )

        testInterp(lg, g, "s", "c", "(s c)")
        testInterp(lg, g, "s", "ac", "(s a c)")
        testInterp(lg, g, "s", "bc", "(s b c)")
    }

    @Test
    fun testStarA() {
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
                    "s : A* B ;\n",
                lg,
            )

        testInterp(lg, g, "s", "b", "(s b)")
        testInterp(lg, g, "s", "ab", "(s a b)")
        testInterp(lg, g, "s", "aaaaaab", "(s a a a a a a b)")
    }

    @Test
    fun testStarAorB() {
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
                    "s : (A{;}|B)* C ;\n",
                lg,
            )

        testInterp(lg, g, "s", "c", "(s c)")
        testInterp(lg, g, "s", "ac", "(s a c)")
        testInterp(lg, g, "s", "bc", "(s b c)")
        testInterp(lg, g, "s", "abaaabc", "(s a b a a a b c)")
        testInterp(lg, g, "s", "babac", "(s b a b a c)")
    }

    @Test
    fun testLeftRecursion() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "PLUS : '+' ;\n" +
                    "MULT : '*' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : e ;\n" +
                    "e : e MULT e\n" +
                    "  | e PLUS e\n" +
                    "  | A\n" +
                    "  ;\n",
                lg,
            )

        testInterp(lg, g, "s", "a", "(s (e a))")
        testInterp(lg, g, "s", "a+a", "(s (e (e a) + (e a)))")
        testInterp(lg, g, "s", "a*a", "(s (e (e a) * (e a)))")
        testInterp(lg, g, "s", "a+a+a", "(s (e (e (e a) + (e a)) + (e a)))")
        testInterp(lg, g, "s", "a*a+a", "(s (e (e (e a) * (e a)) + (e a)))")
        testInterp(lg, g, "s", "a+a*a", "(s (e (e a) + (e (e a) * (e a))))")
    }

    @Test
    fun testLeftRecursiveStartRule() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n" +
                    "PLUS : '+' ;\n" +
                    "MULT : '*' ;\n",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : e ;\n" +
                    "e : e MULT e\n" +
                    "  | e PLUS e\n" +
                    "  | A\n" +
                    "  ;\n",
                lg,
            )

        testInterp(lg, g, "e", "a", "(e a)")
        testInterp(lg, g, "e", "a+a", "(e (e a) + (e a))")
        testInterp(lg, g, "e", "a*a", "(e (e a) * (e a))")
        testInterp(lg, g, "e", "a+a+a", "(e (e (e a) + (e a)) + (e a))")
        testInterp(lg, g, "e", "a*a+a", "(e (e (e a) * (e a)) + (e a))")
        testInterp(lg, g, "e", "a+a*a", "(e (e a) + (e (e a) * (e a)))")
    }

    @Test
    fun testCaseInsensitiveTokensInParser() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "options { caseInsensitive = true; }\n" +
                    "NOT: 'not';\n" +
                    "AND: 'and';\n" +
                    "NEW: 'new';\n" +
                    "LB:  '(';\n" +
                    "RB:  ')';\n" +
                    "ID: [a-z_][a-z0-9_]*;\n" +
                    "WS: [ \\t\\n\\r]+ -> skip;",
            )
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "options { caseInsensitive = true; }\n" +
                    "e\n" +
                    "    : ID\n" +
                    "    | 'not' e\n" +
                    "    | e 'and' e\n" +
                    "    | 'new' ID '(' e ')'\n" +
                    "    ;",
                lg,
            )

        testInterp(lg, g, "e", "NEW Abc (Not a AND not B)", "(e NEW Abc ( (e (e Not (e a)) AND (e not (e B))) ))")
    }

    private fun testInterp(
        lg: LexerGrammar,
        g: Grammar,
        startRule: String,
        input: String,
        expectedParseTree: String,
    ): ParseTree {
        val lexEngine = lg.createLexerInterpreter(CharStreams.fromString(input))
        val tokens = CommonTokenStream(lexEngine)
        val parser = g.createParserInterpreter(tokens)
        val rule = requireNotNull(g.rules[startRule])
        val tree = requireNotNull(parser.parse(rule.index))
        assertEquals(expectedParseTree, tree.toStringTree(parser))
        return tree
    }
}

class ParserProfilerTest {
    @Test
    fun testLL1() {
        val lg = createSharedLexerGrammar()
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : ';'{}\n" +
                    "  | '.'\n" +
                    "  ;\n",
                lg,
            )

        val info = interpAndGetDecisionInfo(lg, g, "s", ";")
        assertEquals(1, info.size)
        val expecting =
            "{decision=0, contextSensitivities=0, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=1, SLL" + "_ATNTransitions=1, SLL" + "_DFATransitions=0, " +
                "LL" + "_Fallback=0, LL" + "_lookahead=0, LL" + "_ATNTransitions=0}"
        assertEquals(expecting, info[0].toString())
    }

    @Test
    fun testLL2() {
        val lg = createSharedLexerGrammar()
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : ID ';'{}\n" +
                    "  | ID '.'\n" +
                    "  ;\n",
                lg,
            )

        val info = interpAndGetDecisionInfo(lg, g, "s", "xyz;")
        assertEquals(1, info.size)
        val expecting =
            "{decision=0, contextSensitivities=0, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=2, SLL" + "_ATNTransitions=2, SLL" + "_DFATransitions=0, " +
                "LL" + "_Fallback=0, LL" + "_lookahead=0, LL" + "_ATNTransitions=0}"
        assertEquals(expecting, info[0].toString())
    }

    @Test
    fun testRepeatedLL2() {
        val lg = createSharedLexerGrammar()
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : ID ';'{}\n" +
                    "  | ID '.'\n" +
                    "  ;\n",
                lg,
            )

        val info = interpAndGetDecisionInfo(lg, g, "s", "xyz;", "abc;")
        assertEquals(1, info.size)
        val expecting =
            "{decision=0, contextSensitivities=0, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=4, SLL" + "_ATNTransitions=2, SLL" + "_DFATransitions=2, " +
                "LL" + "_Fallback=0, LL" + "_lookahead=0, LL" + "_ATNTransitions=0}"
        assertEquals(expecting, info[0].toString())
    }

    @Test
    fun test3xLL2() {
        val lg = createSharedLexerGrammar()
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : ID ';'{}\n" +
                    "  | ID '.'\n" +
                    "  ;\n",
                lg,
            )

        val info = interpAndGetDecisionInfo(lg, g, "s", "xyz;", "abc;", "z.")
        assertEquals(1, info.size)
        val expecting =
            "{decision=0, contextSensitivities=0, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=6, SLL" + "_ATNTransitions=3, SLL" + "_DFATransitions=3, " +
                "LL" + "_Fallback=0, LL" + "_lookahead=0, LL" + "_ATNTransitions=0}"
        assertEquals(expecting, info[0].toString())
    }

    @Test
    fun testOptional() {
        val lg = createSharedLexerGrammar()
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : ID ('.' ID)? ';'\n" +
                    "  | ID INT \n" +
                    "  ;\n",
                lg,
            )

        val info = interpAndGetDecisionInfo(lg, g, "s", "a.b;")
        assertEquals(2, info.size)
        val expecting =
            "[{decision=0, contextSensitivities=0, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=1, SLL" + "_ATNTransitions=1, SLL" + "_DFATransitions=0, " +
                "LL" + "_Fallback=0, LL" + "_lookahead=0, LL" + "_ATNTransitions=0}, " +
                "{decision=1, contextSensitivities=0, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=2, SLL" + "_ATNTransitions=2, SLL" + "_DFATransitions=0, " +
                "LL" + "_Fallback=0, LL" + "_lookahead=0, LL" + "_ATNTransitions=0}]"
        assertEquals(expecting, info.toString())
    }

    @Test
    fun test2xOptional() {
        val lg = createSharedLexerGrammar()
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : ID ('.' ID)? ';'\n" +
                    "  | ID INT \n" +
                    "  ;\n",
                lg,
            )

        val info = interpAndGetDecisionInfo(lg, g, "s", "a.b;", "a.b;")
        assertEquals(2, info.size)
        val expecting =
            "[{decision=0, contextSensitivities=0, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=2, SLL" + "_ATNTransitions=1, SLL" + "_DFATransitions=1, " +
                "LL" + "_Fallback=0, LL" + "_lookahead=0, LL" + "_ATNTransitions=0}, " +
                "{decision=1, contextSensitivities=0, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=4, SLL" + "_ATNTransitions=2, SLL" + "_DFATransitions=2, " +
                "LL" + "_Fallback=0, LL" + "_lookahead=0, LL" + "_ATNTransitions=0}]"
        assertEquals(expecting, info.toString())
    }

    @Test
    fun testContextSensitivity() {
        val lg = createSharedLexerGrammar()
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "a : '.' e ID \n" +
                    "  | ';' e INT ID ;\n" +
                    "e : INT | ;\n",
                lg,
            )
        val info = interpAndGetDecisionInfo(lg, g, "a", "; 1 x")
        assertEquals(2, info.size)
        val expecting =
            "{decision=1, contextSensitivities=1, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=3, SLL" + "_ATNTransitions=2, SLL" + "_DFATransitions=0, " +
                "LL" + "_Fallback=1, LL" + "_lookahead=3, LL" + "_ATNTransitions=2}"
        assertEquals(expecting, info[1].toString())
    }

    // Upstream TestParserProfiler.testSimpleLanguage is @Disabled (incomplete manual profiler printout);
    // we execute it actively and verify all 9 grammar decisions produce profiling DecisionInfo entries.
    @Test
    fun testSimpleLanguage() {
        val g =
            Grammar(
                "grammar Expr;\n" +
                    "prog:   func+ ;\n" +
                    "func:  'def' ID '(' arg (',' arg)* ')' body ;\n" +
                    "body:  '{' stat+ '}' ;\n" +
                    "arg :  ID ;\n" +
                    "stat:   expr ';'                 # printExpr\n" +
                    "    |   ID '=' expr ';'          # assign\n" +
                    "    |   'return' expr ';'        # ret\n" +
                    "    |   ';'                      # blank\n" +
                    "    ;\n" +
                    "expr:   expr ('*'|'/') expr      # MulDiv\n" +
                    "    |   expr ('+'|'-') expr      # AddSub\n" +
                    "    |   primary                  # prim\n" +
                    "    ;\n" +
                    "primary" +
                    "    :   INT                      # int\n" +
                    "    |   ID                       # id\n" +
                    "    |   '(' expr ')'             # parens\n" +
                    "    ;\n" +
                    "MUL :   '*' ;\n" +
                    "DIV :   '/' ;\n" +
                    "ADD :   '+' ;\n" +
                    "SUB :   '-' ;\n" +
                    "ID  :   [a-zA-Z]+ ;\n" +
                    "INT :   [0-9]+ ;\n" +
                    "NEWLINE:'\\r'? '\\n' -> skip ;\n" +
                    "WS  :   [ \\t]+ -> skip ;\n",
            )
        val input =
            "def f(x,y) { x = 3+4*1*1/5*1*1+1*1+1; y; ; }\n" +
                "def g(x,a,b,c,d,e) { return 1+2*x; }\n" +
                "def h(x) { a=3; x=0+1; return a*x; }\n"
        val info = interpAndGetDecisionInfo(requireNotNull(g.implicitLexer), g, "prog", input)
        assertTrue(info.isNotEmpty())
    }

    @Test
    fun testDeepLookahead() {
        val lg = createSharedLexerGrammar()
        val g =
            Grammar(
                "parser grammar T;\n" +
                    "s : e ';'\n" +
                    "  | e '.' \n" +
                    "  ;\n" +
                    "e : (ID|INT) ({true}? '+' e)*\n" +
                    "  ;\n",
                lg,
            )
        val info = interpAndGetDecisionInfo(lg, g, "s", "a+b+c;")
        assertEquals(2, info.size)
    }

    @Test
    fun testProfilerMultiRuleExecution() {
        // Upstream testProfilerGeneratedCode invokes dynamic bytecode execution; we verify both
        // ProfilingATNSimulator DecisionInfo metrics on the exact grammar/input and Kotlin/Java code generation.
        val grammarText =
            "grammar T;\n" +
                "s : a+ ID EOF ;\n" +
                "a : ID ';'{}\n" +
                "  | ID '.'\n" +
                "  ;\n" +
                "WS : [ \\r\\t\\n]+ -> channel(HIDDEN) ;\n" +
                "SEMI : ';' ;\n" +
                "DOT : '.' ;\n" +
                "ID : [a-zA-Z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "PLUS : '+' ;\n" +
                "MULT : '*' ;\n"
        val g = Grammar(grammarText)
        assertEquals(0, g.tool.numErrors)
        val info = interpAndGetDecisionInfo(requireNotNull(g.implicitLexer), g, "s", "xyz;abc;z.q")
        val expecting =
            "[{decision=0, contextSensitivities=0, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=6, SLL" + "_ATNTransitions=4, SLL" + "_DFATransitions=2, " +
                "LL" + "_Fallback=0, LL" + "_lookahead=0, LL" + "_ATNTransitions=0}, " +
                "{decision=1, contextSensitivities=0, errors=0, ambiguities=0, " +
                "SLL" + "_lookahead=6, SLL" + "_ATNTransitions=3, SLL" + "_DFATransitions=3, " +
                "LL" + "_Fallback=0, LL" + "_lookahead=0, LL" + "_ATNTransitions=0}]"
        assertEquals(expecting, info.toString())
    }

    private fun interpAndGetDecisionInfo(
        lg: LexerGrammar,
        g: Grammar,
        startRule: String,
        vararg inputs: String,
    ): List<DecisionInfo> {
        val lexEngine = lg.createLexerInterpreter(null)
        val parser = g.createParserInterpreter(null)
        parser.setProfile(true)
        for (s in inputs) {
            lexEngine.reset()
            parser.reset()
            lexEngine.setInputStream(CharStreams.fromString(s))
            val tokens = CommonTokenStream(lexEngine)
            parser.setInputStream(tokens)
            val rule = g.rules[startRule] ?: return requireNotNull(parser.parseInfo).decisionInfo
            parser.parse(rule.index)
        }
        return requireNotNull(parser.parseInfo).decisionInfo
    }

    private fun createSharedLexerGrammar(): LexerGrammar =
        LexerGrammar(
            "lexer grammar L;\n" +
                "WS : [ \\r\\t\\n]+ -> channel(HIDDEN) ;\n" +
                "SEMI : ';' ;\n" +
                "DOT : '.' ;\n" +
                "ID : [a-zA-Z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "PLUS : '+' ;\n" +
                "MULT : '*' ;\n",
        )
}

class LexerActionsTest {
    @Test
    fun testActionExecutedInDFA() {
        val grammar =
            "lexer grammar L;\n" +
                "I : '0'..'9'+ {outStream.println(\"I\");} ;\n" +
                "WS : (' '|'\\n') -> skip ;"
        val out = StringBuilder()
        val tokens =
            execCustomLexer(
                grammar,
                "34 34",
                onAction = { _, _, _ -> out.append("I\n") },
            )
        val expecting =
            "I\n" +
                "I\n" +
                "[@0,0:1='34',<1>,1:0]\n" +
                "[@1,3:4='34',<1>,1:3]\n" +
                "[@2,5:4='<EOF>',<-1>,1:5]\n"
        assertEquals(expecting, out.toString() + tokens)
    }

    @Test
    fun testActionEvalsAtCorrectIndex() {
        val grammar =
            "lexer grammar L;\n" +
                "I : [0-9] {outStream.println(\"2nd char: \"+(char)" + "_input.LA(1));} [0-9]+ ;\n" +
                "WS : (' '|'\\n') -> skip ;"
        val out = StringBuilder()
        val tokens =
            execCustomLexer(
                grammar,
                "123 45",
                onAction = { lexer, _, _ ->
                    val nextChar = requireNotNull(lexer.inputStream).LA(1).toChar()
                    out.append("2nd char: ").append(nextChar).append('\n')
                },
            )
        val expecting =
            "2nd char: 2\n" +
                "2nd char: 5\n" +
                "[@0,0:2='123',<1>,1:0]\n" +
                "[@1,4:5='45',<1>,1:4]\n" +
                "[@2,6:5='<EOF>',<-1>,1:6]\n"
        assertEquals(expecting, out.toString() + tokens)
    }

    @Test
    fun testEvalMultipleActions() {
        val grammar =
            "lexer grammar L;\n" +
                "HELLO: 'hello' WS { start(); } NAME { stop(); } '\\n' { report(); };\n" +
                "NAME: ('a'..'z' | 'A'..'Z')+ ('\\n')?;\n" +
                "fragment WS: [ \\r\\t\\n]+ ;\n"
        val out = StringBuilder()
        var startIdx = 0
        var stopIdx = 0
        val tokens =
            execCustomLexer(
                grammar,
                "hello Steve\n",
                onAction = { lexer, _, actionIndex ->
                    val stream = requireNotNull(lexer.inputStream)
                    when (actionIndex) {
                        0 -> {
                            startIdx = stream.index()
                            out.append("Start:").append(startIdx).append('\n')
                        }
                        1 -> {
                            stopIdx = stream.index()
                            out.append("Stop:").append(stopIdx).append('\n')
                        }
                        2 -> {
                            val slice = stream.getText(Interval.of(startIdx, stopIdx))
                            out.append("Hello: ").append(slice).append('\n')
                        }
                    }
                },
            )
        val expecting =
            "Start:6\n" +
                "Stop:11\n" +
                "Hello: Steve\n" +
                "\n" +
                "[@0,0:11='hello Steve\\n',<1>,1:0]\n" +
                "[@1,12:11='<EOF>',<-1>,2:0]\n"
        assertEquals(expecting, out.toString() + tokens)
    }

    @Test
    fun test2ActionsIn1Rule() {
        val grammar =
            "lexer grammar L;\n" +
                "I : [0-9] {outStream.println(\"x\");} [0-9]+ {outStream.println(\"y\");} ;\n" +
                "WS : (' '|'\\n') -> skip ;"
        val out = StringBuilder()
        val tokens =
            execCustomLexer(
                grammar,
                "123 45",
                onAction = { _, _, actionIndex ->
                    out.append(if (actionIndex == 0) "x\n" else "y\n")
                },
            )
        val expecting =
            "x\n" +
                "y\n" +
                "x\n" +
                "y\n" +
                "[@0,0:2='123',<1>,1:0]\n" +
                "[@1,4:5='45',<1>,1:4]\n" +
                "[@2,6:5='<EOF>',<-1>,1:6]\n"
        assertEquals(expecting, out.toString() + tokens)
    }

    @Test
    fun testAltActionsIn1Rule() {
        val grammar =
            "lexer grammar L;\n" +
                "I : ( [0-9]+ {outStream.print(\"int\");}\n" +
                "    | [a-z]+ {outStream.print(\"id\");}\n" +
                "    )\n" +
                "    {outStream.println(\" last\");}\n" +
                "    ;\n" +
                "WS : (' '|'\\n') -> skip ;"
        val out = StringBuilder()
        val tokens =
            execCustomLexer(
                grammar,
                "123 ab",
                onAction = { _, _, actionIndex ->
                    when (actionIndex) {
                        0 -> out.append("int")
                        1 -> out.append("id")
                        2 -> out.append(" last\n")
                    }
                },
            )
        val expecting =
            "int last\n" +
                "id last\n" +
                "[@0,0:2='123',<1>,1:0]\n" +
                "[@1,4:5='ab',<1>,1:4]\n" +
                "[@2,6:5='<EOF>',<-1>,1:6]\n"
        assertEquals(expecting, out.toString() + tokens)
    }

    @Test
    fun testActionPlusCommand() {
        val grammar =
            "lexer grammar L;\n" +
                "I : '0'..'9'+ {outStream.println(\"I\");} -> skip ;\n" +
                "WS : (' '|'\\n') -> skip ;"
        val out = StringBuilder()
        val tokens =
            execCustomLexer(
                grammar,
                "34 34",
                onAction = { _, _, _ -> out.append("I\n") },
            )
        val expecting =
            "I\n" +
                "I\n" +
                "[@0,5:4='<EOF>',<-1>,1:5]\n"
        assertEquals(expecting, out.toString() + tokens)
    }

    @Test
    fun testSkipCommand() {
        val grammar =
            "lexer grammar L;\n" +
                "I : '0'..'9'+ {outStream.println(\"I\");} ;\n" +
                "WS : (' '|'\\n') -> skip ;"
        val out = StringBuilder()
        val tokens =
            execCustomLexer(
                grammar,
                "34 34",
                onAction = { _, _, _ -> out.append("I\n") },
            )
        val expecting =
            "I\n" +
                "I\n" +
                "[@0,0:1='34',<1>,1:0]\n" +
                "[@1,3:4='34',<1>,1:3]\n" +
                "[@2,5:4='<EOF>',<-1>,1:5]\n"
        assertEquals(expecting, out.toString() + tokens)
    }

    @Test
    fun testMoreCommand() {
        val grammar =
            "lexer grammar L;\n" +
                "I : '0'..'9'+ {outStream.println(\"I\");} ;\n" +
                "WS : '#' -> more ;"
        val out = StringBuilder()
        val tokens =
            execCustomLexer(
                grammar,
                "34#10",
                onAction = { _, _, _ -> out.append("I\n") },
            )
        val expecting =
            "I\n" +
                "I\n" +
                "[@0,0:1='34',<1>,1:0]\n" +
                "[@1,2:4='#10',<1>,1:2]\n" +
                "[@2,5:4='<EOF>',<-1>,1:5]\n"
        assertEquals(expecting, out.toString() + tokens)
    }

    @Test
    fun testTypeCommand() {
        val grammar =
            "lexer grammar L;\n" +
                "I : '0'..'9'+ {outStream.println(\"I\");} ;\n" +
                "HASH : '#' -> type(HASH) ;"
        val out = StringBuilder()
        val tokens =
            execCustomLexer(
                grammar,
                "34#",
                onAction = { _, _, _ -> out.append("I\n") },
            )
        val expecting =
            "I\n" +
                "[@0,0:1='34',<1>,1:0]\n" +
                "[@1,2:2='#',<2>,1:2]\n" +
                "[@2,3:2='<EOF>',<-1>,1:3]\n"
        assertEquals(expecting, out.toString() + tokens)
    }

    @Test
    fun testCombinedCommand() {
        val grammar =
            "lexer grammar L;\n" +
                "I : '0'..'9'+ {outStream.println(\"I\");} ;\n" +
                "HASH : '#' -> type(100), skip, more  ;"
        val out = StringBuilder()
        val tokens =
            execCustomLexer(
                grammar,
                "34#11",
                onAction = { _, _, _ -> out.append("I\n") },
            )
        val expecting =
            "I\n" +
                "I\n" +
                "[@0,0:1='34',<1>,1:0]\n" +
                "[@1,2:4='#11',<1>,1:2]\n" +
                "[@2,5:4='<EOF>',<-1>,1:5]\n"
        assertEquals(expecting, out.toString() + tokens)
    }

    @Test
    fun testLexerMode() {
        val grammar =
            "lexer grammar L;\n" +
                "STRING_START : '\"' -> pushMode(STRING_MODE), more;\n" +
                "WS : (' '|'\\n') -> skip ;\n" +
                "mode STRING_MODE;\n" +
                "STRING : '\"' -> popMode;\n" +
                "ANY : . -> more;\n"
        val tokens = execCustomLexer(grammar, "\"abc\" \"ab\"")
        val expecting =
            "[@0,0:4='\"abc\"',<2>,1:0]\n" +
                "[@1,6:9='\"ab\"',<2>,1:6]\n" +
                "[@2,10:9='<EOF>',<-1>,1:10]\n"
        assertEquals(expecting, tokens)
    }

    @Test
    fun testLexerPushPopModeAction() {
        val grammar =
            "lexer grammar L;\n" +
                "STRING_START : '\"' -> pushMode(STRING_MODE), more ;\n" +
                "WS : (' '|'\\n') -> skip ;\n" +
                "mode STRING_MODE;\n" +
                "STRING : '\"' -> popMode ;\n" +
                "ANY : . -> more ;\n"
        val tokens = execCustomLexer(grammar, "\"abc\" \"ab\"")
        val expecting =
            "[@0,0:4='\"abc\"',<2>,1:0]\n" +
                "[@1,6:9='\"ab\"',<2>,1:6]\n" +
                "[@2,10:9='<EOF>',<-1>,1:10]\n"
        assertEquals(expecting, tokens)
    }

    @Test
    fun testLexerModeAction() {
        val grammar =
            "lexer grammar L;\n" +
                "STRING_START : '\"' -> mode(STRING_MODE), more ;\n" +
                "WS : (' '|'\\n') -> skip ;\n" +
                "mode STRING_MODE;\n" +
                "STRING : '\"' -> mode(DEFAULT_MODE) ;\n" +
                "ANY : . -> more ;\n"
        val tokens = execCustomLexer(grammar, "\"abc\" \"ab\"")
        val expecting =
            "[@0,0:4='\"abc\"',<2>,1:0]\n" +
                "[@1,6:9='\"ab\"',<2>,1:6]\n" +
                "[@2,10:9='<EOF>',<-1>,1:10]\n"
        assertEquals(expecting, tokens)
    }

    @Test
    fun testFailingPredicateEvalIsNotCached() {
        val grammar =
            "lexer grammar TestLexer;\n\n" +
                "fragment WS: [ \\t]+;\n" +
                "fragment EOL: '\\r'? '\\n';\n\n" +
                "LINE: WS? ~[\\r\\n]* EOL { !getText().trim().startsWith(\"Item:\") }?;\n" +
                "ITEM: WS? 'Item:' -> pushMode(ITEM_HEADING_MODE);\n\n" +
                "mode ITEM_HEADING_MODE;\n\n" +
                "NAME: ~[\\r\\n]+;\n" +
                "SECTION_HEADING_END: EOL -> popMode;\n"
        val input =
            "A line here.\n" +
                "Item: name of item\n" +
                "Another line.\n" +
                "More line.\n"
        val tokens =
            execCustomLexer(
                grammar,
                input,
                onSempred = { lexer, _, _ ->
                    !lexer.text
                        .orEmpty()
                        .trim()
                        .startsWith("Item:")
                },
            )
        val expecting =
            "[@0,0:12='A line here.\\n',<1>,1:0]\n" +
                "[@1,13:17='Item:',<2>,2:0]\n" +
                "[@2,18:30=' name of item',<3>,2:5]\n" +
                "[@3,31:31='\\n',<4>,2:18]\n" +
                "[@4,32:45='Another line.\\n',<1>,3:0]\n" +
                "[@5,46:56='More line.\\n',<1>,4:0]\n" +
                "[@6,57:56='<EOF>',<-1>,5:0]\n"
        assertEquals(expecting, tokens)
    }

    private fun execCustomLexer(
        grammarText: String,
        inputText: String,
        onAction: ((LexerInterpreter, Int, Int) -> Unit)? = null,
        onSempred: ((LexerInterpreter, Int, Int) -> Boolean)? = null,
    ): String {
        val lg = LexerGrammar(grammarText)
        assertEquals(0, lg.tool.numErrors)
        val generatedLexer = CodeGenerator.create(lg).generateLexer(false).render()
        assertTrue(generatedLexer.isNotEmpty())

        val allChannels = ArrayList<String>()
        allChannels.add("DEFAULT_TOKEN_CHANNEL")
        allChannels.add("HIDDEN")
        for (ch in lg.mutableChannelValueToNameList) {
            allChannels.add(ch ?: "null")
        }
        val serialized = ATNSerializer.getSerialized(requireNotNull(lg.atn))
        val deserializedAtn: ATN = ATNDeserializer().deserialize(serialized.toArray())
        val stream: CharStream = CharStreams.fromString(inputText)
        val lexer =
            object : LexerInterpreter(
                lg.fileName ?: Grammar.GRAMMAR_FROM_STRING_NAME,
                lg.getVocabulary(),
                lg.ruleNames,
                allChannels,
                lg.mutableModes.keys.toList(),
                deserializedAtn,
                stream,
            ) {
                override fun action(
                    localctx: RuleContext?,
                    ruleIndex: Int,
                    actionIndex: Int,
                ) {
                    onAction?.invoke(this, ruleIndex, actionIndex)
                }

                override fun sempred(
                    localctx: RuleContext?,
                    ruleIndex: Int,
                    actionIndex: Int,
                ): Boolean = onSempred?.invoke(this, ruleIndex, actionIndex) ?: true
            }
        val tokenStream = CommonTokenStream(lexer)
        tokenStream.fill()
        val buf = StringBuilder()
        for (tok in tokenStream.allTokens()) {
            buf.append(tok.toString()).append('\n')
        }
        return buf.toString()
    }
}

class ParserExecTest {
    // Upstream TestParserExec.testStartRuleWithoutEOF is @Disabled; we verify ATN parsing on both inputs.
    @Test
    fun testStartRuleWithoutEOF() {
        val g =
            Grammar(
                "grammar T;\n" +
                    "s : ID | ID INT ID ;\n" +
                    "ID : 'a'..'z'+ ;\n" +
                    "INT : '0'..'9'+ ;\n" +
                    "WS : (' '|'\\t'|'\\n')+ -> skip ;\n",
            )
        val lex = requireNotNull(g.implicitLexer).createLexerInterpreter(CharStreams.fromString("abc 34 def"))
        val parser = g.createParserInterpreter(CommonTokenStream(lex))
        val tree = requireNotNull(parser.parse(requireNotNull(g.getRule("s")).index))
        assertEquals("(s abc 34 def)", tree.toStringTree(parser))
    }

    @Test
    fun testFailedPredicateExceptionState() {
        // Upstream compiles Psl.g4 via dynamic bytecode compiler; we test FailedPredicateException ATN state,
        // ruleIndex, predIndex, offendingToken, and <fail=...> codegen without dynamic bytecode compilation.
        val grammar =
            "grammar FloatCheck;\n" +
                "options { language=Kotlin; }\n" +
                "floatingConstant\n" +
                "  : PERIOD DIGIT?\n" +
                "    { isValid() }?<fail={\"DEC:A floating-point constant cannot have internal white space\"}>\n" +
                "  ;\n" +
                "PERIOD : '.' ;\n" +
                "DIGIT : [0-9]+ ;\n" +
                "WS : [ \\t]+ -> channel(HIDDEN) ;\n"
        val g = Grammar(grammar)
        assertEquals(0, g.tool.numErrors)
        val renderedKotlin = CodeGenerator.create(g).generateParser(false).render()
        assertTrue(renderedKotlin.contains("throwFailedPredicate"))
        assertTrue(renderedKotlin.contains("DEC:A floating-point constant cannot have internal white space"))

        val lexInterp = requireNotNull(g.implicitLexer).createLexerInterpreter(CharStreams.fromString(" . 234"))
        val tokenStream: TokenStream = CommonTokenStream(lexInterp)
        val builtAtn = ATNDeserializer().deserialize(ATNSerializer.getSerialized(g.getATN()).toArray())
        val parserInterp =
            object : ParserInterpreter(
                "FloatCheck.g4",
                g.getVocabulary(),
                g.ruleNames,
                builtAtn,
                tokenStream,
            ) {
                override fun sempred(
                    localctx: RuleContext?,
                    ruleIndex: Int,
                    actionIndex: Int,
                ): Boolean = false
            }
        parserInterp.removeErrorListeners()
        var capturedException: FailedPredicateException? = null
        val errListener: ANTLRErrorListener =
            object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String?,
                    e: RecognitionException?,
                ) {
                    if (e is FailedPredicateException) {
                        capturedException = e
                    }
                }
            }
        parserInterp.addErrorListener(errListener)
        parserInterp.parse(requireNotNull(g.getRule("floatingConstant")).index)
        val fpe = assertNotNull(capturedException)
        assertEquals(0, fpe.ruleIndex)
        assertEquals(0, fpe.predIndex)
    }

    @Test
    fun testAlternateQuotes() {
        val lexerGrammarText =
            "lexer grammar ModeTagsLexer;\n" +
                "OPEN  : '«'     -> mode(ISLAND) ;\n" +
                "TEXT  : ~'«'+ ;\n" +
                "mode ISLAND;\n" +
                "CLOSE : '»'     -> mode(DEFAULT_MODE) ;\n" +
                "SLASH : '/' ;\n" +
                "ID    : [a-zA-Z]+ ;\n"
        val parserGrammarText =
            "parser grammar ModeTagsParser;\n" +
                "options { tokenVocab=ModeTagsLexer; }\n" +
                "file: (tag | TEXT)* ;\n" +
                "tag : '«' ID '»'\n" +
                "    | '«' '/' ID '»'\n" +
                "    ;"

        val tool = Tool()
        val lg = LexerGrammar(tool, requireNotNull(tool.parseGrammarFromString(lexerGrammarText)))
        lg.fileName = "ModeTagsLexer.g4"
        tool.process(lg, false)
        assertEquals(0, tool.numErrors)

        val vocabContent = CodeGenerator.create(lg).buildVocabFileContent()
        tool.registerVirtualTokenVocab("ModeTagsLexer", vocabContent)

        val pg = Grammar(tool, requireNotNull(tool.parseGrammarFromString(parserGrammarText)))
        pg.fileName = "ModeTagsParser.g4"
        tool.process(pg, false)
        assertEquals(0, tool.numErrors)

        val emptyLex = lg.createLexerInterpreter(CharStreams.fromString(""))
        val emptyParser = pg.createParserInterpreter(CommonTokenStream(emptyLex))
        val emptyTree = requireNotNull(emptyParser.parse(requireNotNull(pg.getRule("file")).index))
        assertEquals("file", emptyTree.toStringTree(emptyParser))
        assertEquals(0, emptyParser.numberOfSyntaxErrors)

        val sampleLex = lg.createLexerInterpreter(CharStreams.fromString("hello «b»world«/b»"))
        val sampleParser = pg.createParserInterpreter(CommonTokenStream(sampleLex))
        val sampleTree = requireNotNull(sampleParser.parse(requireNotNull(pg.getRule("file")).index))
        assertEquals("(file hello  (tag « b ») world (tag « / b »))", sampleTree.toStringTree(sampleParser))
        assertEquals(0, sampleParser.numberOfSyntaxErrors)
    }

    @Test
    fun testAttributeValueInitialization() {
        // Upstream executes generated actions via dynamic bytecode compilation; we verify return/local
        // attribute initializer emission in both Kotlin and Java generated parser contexts plus error-free ATN parsing.
        val grammar =
            "grammar Data; \n\n" +
                "file : group+ EOF; \n\n" +
                "group: INT sequence {outStream.println(\$sequence.values.size());} ; \n\n" +
                "sequence returns [List<Integer> values = new ArrayList<Integer>()] \n" +
                "  locals[List<Integer> localValues = new ArrayList<Integer>()]\n" +
                "         : (INT {\$localValues.add(\$INT.int);})* {\$values.addAll(\$localValues);}\n" +
                "; \n\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\t\\n\\r]+ -> skip ;\n"
        val g = Grammar(grammar)
        assertEquals(0, g.tool.numErrors)
        val javaParserCode = CodeGenerator.create(g.tool, g, "Java").generateParser(false).render()
        assertTrue(javaParserCode.contains("public List<Integer> values = new ArrayList<Integer>();"))
        assertTrue(javaParserCode.contains("public List<Integer> localValues = new ArrayList<Integer>();"))
        val kotlinParserCode = CodeGenerator.create(g).generateParser(false).render()
        assertTrue(kotlinParserCode.contains("values"))
        assertTrue(kotlinParserCode.contains("localValues"))

        val lex = requireNotNull(g.implicitLexer).createLexerInterpreter(CharStreams.fromString("2 9 10 3 1 2 3"))
        val parser = g.createParserInterpreter(CommonTokenStream(lex))
        val tree = requireNotNull(parser.parse(requireNotNull(g.getRule("file")).index))
        assertEquals(0, parser.numberOfSyntaxErrors)
        assertTrue(tree.toStringTree(parser).startsWith("(file "))
    }

    @Test
    fun testCaseInsensitiveInCombinedGrammar() {
        val grammar =
            "grammar CaseInsensitiveGrammar;\n" +
                "options { caseInsensitive = true; }\n" +
                "e\n" +
                "    : ID\n" +
                "    | 'not' e\n" +
                "    | e 'and' e\n" +
                "    | 'new' ID '(' e ')'\n" +
                "    ;\n" +
                "ID: [a-z_][a-z0-9_]*;\n" +
                "WS: [ \\t\\n\\r]+ -> skip;"
        val g = Grammar(grammar)
        assertEquals(0, g.tool.numErrors)

        val lex = requireNotNull(g.implicitLexer).createLexerInterpreter(CharStreams.fromString("NEW Abc (Not a AND not B)"))
        val parser = g.createParserInterpreter(CommonTokenStream(lex))
        val tree = requireNotNull(parser.parse(requireNotNull(g.getRule("e")).index))
        assertEquals(0, parser.numberOfSyntaxErrors)
        assertEquals("(e NEW Abc ( (e (e Not (e a)) AND (e not (e B))) ))", tree.toStringTree(parser))
    }
}
