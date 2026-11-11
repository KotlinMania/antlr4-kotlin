/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.automata.ATNPrinter
import io.github.kotlinmania.antlr4.tooling.automata.LexerATNFactory
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ATNConstructionTest {
    @Test
    fun testA() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : A;",
            )
        val expecting =
            "RuleStart_a_0->s2\n" +
                "s2-A->s3\n" +
                "s3->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s4\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testAB() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : A B ;",
            )
        val expecting =
            "RuleStart_a_0->s2\n" +
                "s2-A->s3\n" +
                "s3-B->s4\n" +
                "s4->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s5\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testAorB() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : A | B {;} ;",
            )
        val expecting =
            "RuleStart_a_0->BlockStart_5\n" +
                "BlockStart_5->s2\n" +
                "BlockStart_5->s3\n" +
                "s2-A->BlockEnd_6\n" +
                "s3-B->s4\n" +
                "BlockEnd_6->RuleStop_a_1\n" +
                "s4-action_0:-1->BlockEnd_6\n" +
                "RuleStop_a_1-EOF->s7\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testSetAorB() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : A | B ;",
            )
        val expecting =
            "RuleStart_a_0->s2\n" +
                "s2-{A, B}->s3\n" +
                "s3->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s4\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testLexerIsntSetMultiCharString() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : ('0x' | '0X') ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->BlockStart_7\n" +
                "BlockStart_7->s3\n" +
                "BlockStart_7->s5\n" +
                "s3-'0'->s4\n" +
                "s5-'0'->s6\n" +
                "s4-'x'->BlockEnd_8\n" +
                "s6-'X'->BlockEnd_8\n" +
                "BlockEnd_8->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testRange() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : 'a'..'c' ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-'a'..'c'->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testCharSet() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : [abc] ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-{97..99}->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testCharSetRange() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : [a-c] ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-{97..99}->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testCharSetUnicodeBMPEscape() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : [\\uABCD] ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-43981->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testCharSetUnicodeBMPEscapeRange() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : [a-c\\uABCD-\\uABFF] ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-{97..99, 43981..44031}->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testCharSetUnicodeSMPEscape() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : [\\u{10ABCD}] ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-1092557->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testCharSetUnicodeSMPEscapeRange() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : [a-c\\u{10ABCD}-\\u{10ABFF}] ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-{97..99, 1092557..1092607}->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testCharSetUnicodePropertyEscape() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : [\\p{Gothic}] ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-{66352..66378}->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testCharSetUnicodePropertyInvertEscape() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : [\\P{Gothic}] ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-{0..66351, 66379..1114111}->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testCharSetUnicodeMultiplePropertyEscape() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : [\\p{Gothic}\\p{Mahajani}] ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-{66352..66378, 69968..70006}->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testCharSetUnicodePropertyOverlap() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : [\\p{ASCII_Hex_Digit}\\p{Hex_Digit}] ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->s3\n" +
                "s3-{48..57, 65..70, 97..102, 65296..65305, 65313..65318, 65345..65350}->s4\n" +
                "s4->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testRangeOrRange() {
        val g =
            LexerGrammar(
                "lexer grammar P;\n" +
                    "A : ('a'..'c' 'h' | 'q' 'j'..'l') ;",
            )
        val expecting =
            "s0->RuleStart_A_1\n" +
                "RuleStart_A_1->BlockStart_7\n" +
                "BlockStart_7->s3\n" +
                "BlockStart_7->s5\n" +
                "s3-'a'..'c'->s4\n" +
                "s5-'q'->s6\n" +
                "s4-'h'->BlockEnd_8\n" +
                "s6-'j'..'l'->BlockEnd_8\n" +
                "BlockEnd_8->RuleStop_A_2\n"
        checkTokensRule(g, null, expecting)
    }

    @Test
    fun testStringLiteralInParser() {
        val g =
            Grammar(
                "grammar P;\n" +
                    "a : A|'b' ;",
            )
        val expecting =
            "RuleStart_a_0->s2\n" +
                "s2-{'b', A}->s3\n" +
                "s3->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s4\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testABorCD() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : A B | C D;",
            )
        val expecting =
            "RuleStart_a_0->BlockStart_6\n" +
                "BlockStart_6->s2\n" +
                "BlockStart_6->s4\n" +
                "s2-A->s3\n" +
                "s4-C->s5\n" +
                "s3-B->BlockEnd_7\n" +
                "s5-D->BlockEnd_7\n" +
                "BlockEnd_7->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s8\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testbA() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : b A ;\n" +
                    "b : B ;",
            )
        var expecting =
            "RuleStart_a_0->s4\n" +
                "s4-b->RuleStart_b_2\n" +
                "s5-A->s6\n" +
                "s6->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s9\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
        expecting =
            "RuleStart_b_2->s7\n" +
                "s7-B->s8\n" +
                "s8->RuleStop_b_3\n" +
                "RuleStop_b_3->s5\n"
        ToolTestSupport.checkRuleATN(g, "b", expecting)
    }

    @Test
    fun testFollow() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : b A ;\n" +
                    "b : B ;\n" +
                    "c : b C;",
            )
        val expecting =
            "RuleStart_b_2->s9\n" +
                "s9-B->s10\n" +
                "s10->RuleStop_b_3\n" +
                "RuleStop_b_3->s7\n" +
                "RuleStop_b_3->s12\n"
        ToolTestSupport.checkRuleATN(g, "b", expecting)
    }

    @Test
    fun testAorEpsilon() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : A | ;",
            )
        val expecting =
            "RuleStart_a_0->BlockStart_4\n" +
                "BlockStart_4->s2\n" +
                "BlockStart_4->s3\n" +
                "s2-A->BlockEnd_5\n" +
                "s3->BlockEnd_5\n" +
                "BlockEnd_5->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s6\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testAOptional() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : A?;",
            )
        val expecting =
            "RuleStart_a_0->BlockStart_3\n" +
                "BlockStart_3->s2\n" +
                "BlockStart_3->BlockEnd_4\n" +
                "s2-A->BlockEnd_4\n" +
                "BlockEnd_4->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s5\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testAorBoptional() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : (A{;}|B)?;",
            )
        val expecting =
            "RuleStart_a_0->BlockStart_5\n" +
                "BlockStart_5->s2\n" +
                "BlockStart_5->s4\n" +
                "BlockStart_5->BlockEnd_6\n" +
                "s2-A->s3\n" +
                "s4-B->BlockEnd_6\n" +
                "BlockEnd_6->RuleStop_a_1\n" +
                "s3-action_0:-1->BlockEnd_6\n" +
                "RuleStop_a_1-EOF->s7\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testSetAorBoptional() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : (A|B)?;",
            )
        val expecting =
            "RuleStart_a_0->BlockStart_3\n" +
                "BlockStart_3->s2\n" +
                "BlockStart_3->BlockEnd_4\n" +
                "s2-{A, B}->BlockEnd_4\n" +
                "BlockEnd_4->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s5\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testAorBthenC() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : (A | B) C;",
            )
        val expecting =
            "RuleStart_a_0->s2\n" +
                "s2-{A, B}->s3\n" +
                "s3-C->s4\n" +
                "s4->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s5\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testAplus() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : A+;",
            )
        val expecting =
            "RuleStart_a_0->PlusBlockStart_3\n" +
                "PlusBlockStart_3->s2\n" +
                "s2-A->BlockEnd_4\n" +
                "BlockEnd_4->PlusLoopBack_5\n" +
                "PlusLoopBack_5->PlusBlockStart_3\n" +
                "PlusLoopBack_5->s6\n" +
                "s6->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s7\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testAplusSingleAltHasPlusASTPointingAtLoopBackState() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "s : a B ;\n" +
                    "a : A+;",
            )
        val expecting =
            "RuleStart_a_2->PlusBlockStart_8\n" +
                "PlusBlockStart_8->s7\n" +
                "s7-A->BlockEnd_9\n" +
                "BlockEnd_9->PlusLoopBack_10\n" +
                "PlusLoopBack_10->PlusBlockStart_8\n" +
                "PlusLoopBack_10->s11\n" +
                "s11->RuleStop_a_3\n" +
                "RuleStop_a_3->s5\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
        val rootAst = assertNotNull(g.ast)
        val ruleNodes = rootAst.getNodesWithType(ANTLRParserTypes.RULE)
        val a = ruleNodes[1] as RuleAST
        val nodesInRule = a.getNodesWithType(null)
        val covered = LinkedHashMap<GrammarAST, ATNState>()
        for (node in nodesInRule) {
            val state = node.atnState
            if (state != null) {
                covered[node] = state
            }
        }
        assertEquals("{RULE=2, BLOCK=8, +=10, BLOCK=8, A=7}", covered.toString())
    }

    @Test
    fun testAorBplus() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : (A|B{;})+;",
            )
        val expecting =
            "RuleStart_a_0->PlusBlockStart_5\n" +
                "PlusBlockStart_5->s2\n" +
                "PlusBlockStart_5->s3\n" +
                "s2-A->BlockEnd_6\n" +
                "s3-B->s4\n" +
                "BlockEnd_6->PlusLoopBack_7\n" +
                "s4-action_0:-1->BlockEnd_6\n" +
                "PlusLoopBack_7->PlusBlockStart_5\n" +
                "PlusLoopBack_7->s8\n" +
                "s8->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s9\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testAorBorEmptyPlus() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : (A | B | )+ ;",
            )
        val expecting =
            "RuleStart_a_0->PlusBlockStart_5\n" +
                "PlusBlockStart_5->s2\n" +
                "PlusBlockStart_5->s3\n" +
                "PlusBlockStart_5->s4\n" +
                "s2-A->BlockEnd_6\n" +
                "s3-B->BlockEnd_6\n" +
                "s4->BlockEnd_6\n" +
                "BlockEnd_6->PlusLoopBack_7\n" +
                "PlusLoopBack_7->PlusBlockStart_5\n" +
                "PlusLoopBack_7->s8\n" +
                "s8->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s9\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testEmptyOrEmpty() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : | ;",
            )
        val expecting =
            "RuleStart_a_0->BlockStart_4\n" +
                "BlockStart_4->s2\n" +
                "BlockStart_4->s3\n" +
                "s2->BlockEnd_5\n" +
                "s3->BlockEnd_5\n" +
                "BlockEnd_5->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s6\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testAStar() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : A*;",
            )
        val expecting =
            "RuleStart_a_0->StarLoopEntry_5\n" +
                "StarLoopEntry_5->StarBlockStart_3\n" +
                "StarLoopEntry_5->s6\n" +
                "StarBlockStart_3->s2\n" +
                "s6->RuleStop_a_1\n" +
                "s2-A->BlockEnd_4\n" +
                "RuleStop_a_1-EOF->s8\n" +
                "BlockEnd_4->StarLoopBack_7\n" +
                "StarLoopBack_7->StarLoopEntry_5\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testNestedAstar() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : (COMMA ID*)*;",
            )
        val expecting =
            "RuleStart_a_0->StarLoopEntry_11\n" +
                "StarLoopEntry_11->StarBlockStart_9\n" +
                "StarLoopEntry_11->s12\n" +
                "StarBlockStart_9->s2\n" +
                "s12->RuleStop_a_1\n" +
                "s2-COMMA->StarLoopEntry_6\n" +
                "RuleStop_a_1-EOF->s14\n" +
                "StarLoopEntry_6->StarBlockStart_4\n" +
                "StarLoopEntry_6->s7\n" +
                "StarBlockStart_4->s3\n" +
                "s7->BlockEnd_10\n" +
                "s3-ID->BlockEnd_5\n" +
                "BlockEnd_10->StarLoopBack_13\n" +
                "BlockEnd_5->StarLoopBack_8\n" +
                "StarLoopBack_13->StarLoopEntry_11\n" +
                "StarLoopBack_8->StarLoopEntry_6\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testAorBstar() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : (A | B{;})* ;",
            )
        val expecting =
            "RuleStart_a_0->StarLoopEntry_7\n" +
                "StarLoopEntry_7->StarBlockStart_5\n" +
                "StarLoopEntry_7->s8\n" +
                "StarBlockStart_5->s2\n" +
                "StarBlockStart_5->s3\n" +
                "s8->RuleStop_a_1\n" +
                "s2-A->BlockEnd_6\n" +
                "s3-B->s4\n" +
                "RuleStop_a_1-EOF->s10\n" +
                "BlockEnd_6->StarLoopBack_9\n" +
                "s4-action_0:-1->BlockEnd_6\n" +
                "StarLoopBack_9->StarLoopEntry_7\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testPredicatedAorB() {
        val g =
            Grammar(
                "parser grammar P;\n" +
                    "a : {p1}? A | {p2}? B ;",
            )
        val expecting =
            "RuleStart_a_0->BlockStart_6\n" +
                "BlockStart_6->s2\n" +
                "BlockStart_6->s4\n" +
                "s2-pred_0:0->s3\n" +
                "s4-pred_0:1->s5\n" +
                "s3-A->BlockEnd_7\n" +
                "s5-B->BlockEnd_7\n" +
                "BlockEnd_7->RuleStop_a_1\n" +
                "RuleStop_a_1-EOF->s8\n"
        ToolTestSupport.checkRuleATN(g, "a", expecting)
    }

    @Test
    fun testParserRuleRefInLexerRule() {
        val tool = Tool()
        tool.writeFilesToDisk = false
        val errorQueue = ToolErrorQueue(tool)
        tool.removeListeners()
        tool.addListener(errorQueue)
        assertEquals(0, errorQueue.size())

        val gstr =
            "grammar U;\n" +
                "a : A;\n" +
                "A : a;\n"
        val grammarRootAST = assertNotNull(tool.parseGrammarFromString(gstr))
        assertEquals(0, errorQueue.size())
        val g = tool.createGrammar(grammarRootAST)
        assertEquals(0, errorQueue.size())
        g.fileName = "<string>"
        tool.process(g, false)

        assertEquals(1, errorQueue.errors.size)
        assertEquals(ErrorType.PARSER_RULE_REF_IN_LEXER_RULE, errorQueue.errors[0].errorType)
        assertEquals("[a, A]", errorQueue.errors[0].getArgs().toString())
    }

    @Test
    fun testForRepeatedTransitionsToStopState() {
        val gstr =
            "grammar T;\n" +
                "\t s : e EOF;\n" +
                "\t e :<assoc=right> e '*' e\n" +
                "\t   |<assoc=right> e '+' e\n" +
                "\t   |<assoc=right> e '?' e ':' e\n" +
                "\t   |<assoc=right> e '=' e\n" +
                "\t   | ID\n" +
                "\t   ;\n" +
                "\t ID : 'a'..'z'+ ;\n" +
                "\t WS : (' '|'\\n') -> skip ;"
        val g = Grammar(gstr)
        val expecting =
            "RuleStart_e_2->s7\n" +
                "s7-action_1:-1->s8\n" +
                "s8-ID->s9\n" +
                "s9->StarLoopEntry_27\n" +
                "StarLoopEntry_27->StarBlockStart_25\n" +
                "StarLoopEntry_27->s28\n" +
                "StarBlockStart_25->s10\n" +
                "StarBlockStart_25->s13\n" +
                "StarBlockStart_25->s16\n" +
                "StarBlockStart_25->s22\n" +
                "s28->RuleStop_e_3\n" +
                "s10-5 >= _p->s11\n" +
                "s13-4 >= _p->s14\n" +
                "s16-3 >= _p->s17\n" +
                "s22-2 >= _p->s23\n" +
                "RuleStop_e_3->s5\n" +
                "RuleStop_e_3->BlockEnd_26\n" +
                "RuleStop_e_3->s19\n" +
                "RuleStop_e_3->s21\n" +
                "s11-'*'->s12\n" +
                "s14-'+'->s15\n" +
                "s17-'?'->s18\n" +
                "s23-'='->s24\n" +
                "s12-e->RuleStart_e_2\n" +
                "s15-e->RuleStart_e_2\n" +
                "s18-e->RuleStart_e_2\n" +
                "s24-e->RuleStart_e_2\n" +
                "BlockEnd_26->StarLoopBack_29\n" +
                "s19-':'->s20\n" +
                "StarLoopBack_29->StarLoopEntry_27\n" +
                "s20-e->RuleStart_e_2\n" +
                "s21->BlockEnd_26\n"
        ToolTestSupport.checkRuleATN(g, "e", expecting)
    }

    @Test
    fun testDefaultMode() {
        val g =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "X : 'x' ;\n" +
                    "mode FOO;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val expecting =
            "s0->RuleStart_A_2\n" +
                "s0->RuleStart_X_4\n" +
                "RuleStart_A_2->s10\n" +
                "RuleStart_X_4->s12\n" +
                "s10-'a'->s11\n" +
                "s12-'x'->s13\n" +
                "s11->RuleStop_A_3\n" +
                "s13->RuleStop_X_5\n"
        checkTokensRule(g, "DEFAULT_MODE", expecting)
    }

    @Test
    fun testMode() {
        val g =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a' ;\n" +
                    "X : 'x' ;\n" +
                    "mode FOO;\n" +
                    "B : 'b' ;\n" +
                    "C : 'c' ;\n",
            )
        val expecting =
            "s1->RuleStart_B_6\n" +
                "s1->RuleStart_C_8\n" +
                "RuleStart_B_6->s14\n" +
                "RuleStart_C_8->s16\n" +
                "s14-'b'->s15\n" +
                "s16-'c'->s17\n" +
                "s15->RuleStop_B_7\n" +
                "s17->RuleStop_C_9\n"
        checkTokensRule(g, "FOO", expecting)
    }

    private fun checkTokensRule(
        g: LexerGrammar,
        modeName: String?,
        expecting: String,
    ) {
        val targetMode = modeName ?: "DEFAULT_MODE"
        assertNotNull(g.modes[targetMode], "no such mode $targetMode")
        val factory = LexerATNFactory(g)
        val nfa = factory.createATN()
        val startState = nfa.modeNameToStartState[targetMode]
        val serializer = ATNPrinter(g, startState)
        val result = serializer.asString()
        assertEquals(expecting, result)
    }
}
