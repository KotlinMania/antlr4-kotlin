/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TokenTypeAssignmentTest {
    @Test
    fun testParserSimpleTokens() {
        val g =
            Grammar(
                "parser grammar t;\n" +
                    "a : A | B;\n" +
                    "b : C ;",
            )
        checkSymbols(g, "a, b", "A, B, C")
    }

    @Test
    fun testParserTokensSection() {
        val g =
            Grammar(
                "parser grammar t;\n" +
                    "tokens {\n" +
                    "  C,\n" +
                    "  D" +
                    "}\n" +
                    "a : A | B;\n" +
                    "b : C ;",
            )
        checkSymbols(g, "a, b", "A, B, C, D")
    }

    @Test
    fun testLexerTokensSection() {
        val g =
            LexerGrammar(
                "lexer grammar t;\n" +
                    "tokens {\n" +
                    "  C,\n" +
                    "  D" +
                    "}\n" +
                    "A : 'a';\n" +
                    "C : 'c' ;",
            )
        checkSymbols(g, "A, C", "A, C, D")
    }

    @Test
    fun testCombinedGrammarLiterals() {
        val g =
            Grammar(
                "grammar t;\n" +
                    "a : 'begin' b 'end';\n" +
                    "b : C ';' ;\n" +
                    "ID : 'a' ;\n" +
                    "FOO : 'foo' ;\n" +
                    "C : 'c' ;\n",
            )
        checkSymbols(g, "a, b", "C, FOO, ID, 'begin', 'end', ';'")
    }

    @Test
    fun testLiteralInParserAndLexer() {
        val g =
            Grammar(
                "grammar t;\n" +
                    "a : 'x' E ; \n" +
                    "E: 'x' '0' ;\n",
            )

        assertEquals("['x']", g.stringLiteralToTypeMap.keys.toString())
        val implicitLexer = assertNotNull(g.implicitLexer)
        assertEquals("['x']", implicitLexer.stringLiteralToTypeMap.keys.toString())

        val typeToTokenName = g.tokenDisplayNames
        val tokens = LinkedHashSet<String>()
        tokens.addAll(typeToTokenName)
        assertEquals("[<INVALID>, 'x', E]", tokens.toString())
    }

    @Test
    fun testPredDoesNotHideNameToLiteralMapInLexer() {
        val g =
            Grammar(
                "grammar t;\n" +
                    "a : 'x' X ; \n" +
                    "X: 'x' {true}?;\n",
            )

        assertEquals("{'x'=1}", g.stringLiteralToTypeMap.toString())
        assertEquals("{EOF=-1, X=1}", g.tokenNameToTypeMap.toString())

        val implicitLexer = assertNotNull(g.implicitLexer)
        assertEquals("{'x'=1}", implicitLexer.stringLiteralToTypeMap.toString())
        assertEquals("{EOF=-1, X=1}", implicitLexer.tokenNameToTypeMap.toString())
    }

    @Test
    fun testCombinedGrammarWithRefToLiteralButNoTokenIDRef() {
        val g =
            Grammar(
                "grammar t;\n" +
                    "a : 'a' ;\n" +
                    "A : 'a' ;\n",
            )
        checkSymbols(g, "a", "A, 'a'")
    }

    @Test
    fun testSetDoesNotMissTokenAliases() {
        val g =
            Grammar(
                "grammar t;\n" +
                    "a : 'a'|'b' ;\n" +
                    "A : 'a' ;\n" +
                    "B : 'b' ;\n",
            )
        checkSymbols(g, "a", "A, 'a', B, 'b'")
    }

    @Test
    fun testParserCharLiteralWithEscape() {
        val g =
            Grammar(
                "grammar t;\n" +
                    "a : '\\n';\n",
            )
        val literals = g.stringLiteralToTypeMap.keys.toList()
        assertEquals("'\\n'", literals[0])
    }

    @Test
    fun testParserCharLiteralWithBasicUnicodeEscape() {
        val g =
            Grammar(
                "grammar t;\n" +
                    "a : '\\uABCD';\n",
            )
        val literals = g.stringLiteralToTypeMap.keys.toList()
        assertEquals("'\\uABCD'", literals[0])
    }

    @Test
    fun testParserCharLiteralWithExtendedUnicodeEscape() {
        val g =
            Grammar(
                "grammar t;\n" +
                    "a : '\\u{1ABCD}';\n",
            )
        val literals = g.stringLiteralToTypeMap.keys.toList()
        assertEquals("'\\u{1ABCD}'", literals[0])
    }

    private fun checkSymbols(
        g: Grammar,
        rulesStr: String,
        allValidTokensStr: String,
    ) {
        val typeToTokenName = g.tokenNames
        val tokens = HashSet<String>()
        for (i in typeToTokenName.indices) {
            val t = typeToTokenName[i]
            if (t != Grammar.INVALID_TOKEN_NAME) {
                if (t.startsWith(Grammar.AUTO_GENERATED_TOKEN_NAME_PREFIX)) {
                    tokens.add(g.getTokenDisplayName(i))
                } else {
                    tokens.add(t)
                }
            }
        }

        val expectedTokens = allValidTokensStr.split(',', ' ').filter { it.isNotEmpty() }
        for (tokenName in expectedTokens) {
            assertTrue(
                g.getTokenType(tokenName) != Token.INVALID_TYPE,
                "token $tokenName expected, but was undefined",
            )
            tokens.remove(tokenName)
        }

        for (tokenName in tokens) {
            assertTrue(
                g.getTokenType(tokenName) < Token.MIN_USER_TOKEN_TYPE,
                "unexpected token name $tokenName",
            )
        }

        val expectedRules = rulesStr.split(',', ' ').filter { it.isNotEmpty() }
        var n = 0
        for (ruleName in expectedRules) {
            assertNotNull(g.getRule(ruleName), "rule $ruleName expected")
            n++
        }
        assertEquals(n, g.rules.size, "number of rules mismatch; expecting $n; found ${g.rules.size}")
    }
}
