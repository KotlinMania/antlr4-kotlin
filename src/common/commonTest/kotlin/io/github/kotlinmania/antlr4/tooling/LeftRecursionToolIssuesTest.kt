/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.tooling.ToolTestSupport.testErrors
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import kotlin.test.Test

class LeftRecursionToolIssuesTest {
    @Test
    fun testCheckForNonLeftRecursiveRule() {
        val grammar =
            "grammar T;\n" +
                "s @after {System.out.println(\$ctx.toStringTree(this));} : a ;\n" +
                "a : a ID\n" +
                "  ;\n" +
                "ID : 'a'..'z'+ ;\n" +
                "WS : (' '|'\\n') -> skip ;\n"
        val expected =
            "error(" + ErrorType.NO_NON_LR_ALTS.code +
                "): T.g4:3:0: left recursive rule a must contain an alternative which is not left recursive\n"
        testErrors(arrayOf(grammar, expected), false)
    }

    @Test
    fun testCheckForLeftRecursiveEmptyFollow() {
        val grammar =
            "grammar T;\n" +
                "s @after {System.out.println(\$ctx.toStringTree(this));} : a ;\n" +
                "a : a ID?\n" +
                "  | ID\n" +
                "  ;\n" +
                "ID : 'a'..'z'+ ;\n" +
                "WS : (' '|'\\n') -> skip ;\n"
        val expected =
            "error(" + ErrorType.EPSILON_LR_FOLLOW.code +
                "): T.g4:3:0: left recursive rule a contains a left recursive alternative which can be followed by the empty string\n"
        testErrors(arrayOf(grammar, expected), false)
    }

    @Test
    fun testLeftRecursiveRuleRefWithArg() {
        val grammar =
            "grammar T;\n" +
                "statement\n" +
                "locals[Scope scope]\n" +
                "    : expressionA[\$scope] ';'\n" +
                "    ;\n" +
                "expressionA[Scope scope]\n" +
                "    : atom[\$scope]\n" +
                "    | expressionA[\$scope] '[' expressionA[\$scope] ']'\n" +
                "    ;\n" +
                "atom[Scope scope]\n" +
                "    : 'dummy'\n" +
                "    ;\n"
        val expected =
            "error(" + ErrorType.NONCONFORMING_LR_RULE.code +
                "): T.g4:6:0: rule expressionA is left recursive but doesn't conform to a pattern ANTLR can handle\n"
        testErrors(arrayOf(grammar, expected), false)
    }

    @Test
    fun testLeftRecursiveRuleRefWithArg2() {
        val grammar =
            "grammar T;\n" +
                "a[int i] : 'x'\n" +
                "  | a[3] 'y'\n" +
                "  ;"
        val expected =
            "error(" + ErrorType.NONCONFORMING_LR_RULE.code +
                "): T.g4:2:0: rule a is left recursive but doesn't conform to a pattern ANTLR can handle\n"
        testErrors(arrayOf(grammar, expected), false)
    }

    @Test
    fun testLeftRecursiveRuleRefWithArg3() {
        val grammar =
            "grammar T;\n" +
                "a : 'x'\n" +
                "  | a[3] 'y'\n" +
                "  ;"
        val expected =
            "error(" + ErrorType.NONCONFORMING_LR_RULE.code +
                "): T.g4:2:0: rule a is left recursive but doesn't conform to a pattern ANTLR can handle\n"
        testErrors(arrayOf(grammar, expected), false)
    }

    @Test
    fun testIsolatedLeftRecursiveRuleRef() {
        val grammar =
            "grammar T;\n" +
                "a : a | b ;\n" +
                "b : 'B' ;\n"
        val expected =
            "error(" + ErrorType.NONCONFORMING_LR_RULE.code +
                "): T.g4:2:0: rule a is left recursive but doesn't conform to a pattern ANTLR can handle\n"
        testErrors(arrayOf(grammar, expected), false)
    }

    @Test
    fun testArgOnPrimaryRuleInLeftRecursiveRule() {
        val grammar =
            "grammar T;\n" +
                "val: dval[1]\n" +
                "   | val '*' val\n" +
                "   ;\n" +
                "dval[int  x]: '.';\n"
        val expected = ""
        testErrors(arrayOf(grammar, expected), false)
    }
}
