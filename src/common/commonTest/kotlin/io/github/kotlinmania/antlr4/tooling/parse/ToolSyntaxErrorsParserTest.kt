/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.ANTLRInputStream
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.tooling.tool.ANTLRMessage
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.ToolErrorContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ToolSyntaxErrorsParserTest {
    private class CollectingErrorContext : ToolErrorContext {
        override var longMessages: Boolean = false
        override val errMgr: ErrorManager = ErrorManager(this)
        private val lines: MutableList<String> = ArrayList()

        override fun info(msg: String?) {}

        override fun error(msg: ANTLRMessage) {
            lines.add(errMgr.formatMessage(msg) + "\n")
        }

        override fun warning(msg: ANTLRMessage) {
            lines.add(errMgr.formatMessage(msg) + "\n")
        }

        fun joinedOutput(): String = lines.joinToString("")
    }

    private fun inferGrammarFileName(grammarText: String): String {
        val headerRegex = Regex("""(?:lexer\s+|parser\s+)?grammar\s+(\S+)\s*;""")
        val match = headerRegex.find(grammarText)
        if (match != null) {
            return "${match.groupValues[1]}.g4"
        }
        val looseRegex = Regex("""(?:lexer\s+|parser\s+)?grammar\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val loose = looseRegex.find(grammarText)
        if (loose != null) {
            return "${loose.groupValues[1]}.g4"
        }
        return "A.g4"
    }

    private fun runSyntaxCheck(
        grammarText: String,
        explicitFileName: String? = null,
    ): String {
        val ctx = CollectingErrorContext()
        val stream = ANTLRInputStream(grammarText)
        stream.name = explicitFileName ?: inferGrammarFileName(grammarText)
        val lexer = ToolANTLRLexer(stream, ctx.errMgr)
        val tokens = CommonTokenStream(lexer)
        val parser = ToolANTLRParser(tokens, ctx.errMgr)
        parser.grammarSpec()
        return ctx.joinedOutput()
    }

    private fun assertSyntaxPairs(pairs: List<String>) {
        var i = 0
        while (i < pairs.size) {
            val grammarText = pairs[i]
            val expected = pairs[i + 1]
            val actual = runSyntaxCheck(grammarText)
            assertEquals(expected, actual, "Grammar input:\n$grammarText")
            i += 2
        }
    }

    @Test
    fun allErrorCodesDistinct() {
        val errorTypes = ErrorType.entries
        for (i in errorTypes.indices) {
            for (j in i + 1 until errorTypes.size) {
                assertNotEquals(
                    errorTypes[i].code,
                    errorTypes[j].code,
                    "Duplicate error code between ${errorTypes[i]} and ${errorTypes[j]}",
                )
            }
        }
    }

    @Test
    fun testSyntaxPairsA() {
        assertSyntaxPairs(SYNTAX_PAIRS_A)
    }

    @Test
    fun testExtraColon() {
        assertSyntaxPairs(
            listOf(
                "grammar A;\na : : A ;\nb : B ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:4: syntax error: ':' came as a complete surprise to me while matching alternative\n",
            ),
        )
    }

    @Test
    fun testMissingRuleSemi() {
        assertSyntaxPairs(
            listOf(
                "grammar A;\na : A \nb : B ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:3:0: syntax error: unterminated rule (missing ';') detected at 'b :' while looking for rule element\n",
            ),
        )
    }

    @Test
    fun testMissingRuleSemi2() {
        assertSyntaxPairs(
            listOf(
                "lexer grammar A;\nA : 'a' \nB : 'b' ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:3:2: syntax error: unterminated rule (missing ';') detected at ': 'b'' while looking for lexer rule element\n",
            ),
        )
    }

    @Test
    fun testMissingRuleSemi3() {
        assertSyntaxPairs(
            listOf(
                "grammar A;\na : A \nb[int i] returns [int y] : B ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:3:9: syntax error: unterminated rule (missing ';') detected at 'returns int y' while looking for rule element\n",
            ),
        )
    }

    @Test
    fun testMissingRuleSemi4() {
        assertSyntaxPairs(
            listOf(
                "grammar A;\na : b \n  catch [Exception e] {...}\nb : B ;\n",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:4: syntax error: unterminated rule (missing ';') detected at 'b catch' while looking for rule element\n",
            ),
        )
    }

    @Test
    fun testMissingRuleSemi5() {
        assertSyntaxPairs(
            listOf(
                "grammar A;\na : A \n  catch [Exception e] {...}\n",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:4: syntax error: unterminated rule (missing ';') detected at 'A catch' while looking for rule element\n",
            ),
        )
    }

    @Test
    fun testBadRulePrequelStart() {
        assertSyntaxPairs(
            listOf(
                "grammar A;\na @ options {k=1;} : A ;\nb : B ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:4: syntax error: 'options {' came as a complete surprise to me while looking for an identifier\n",
            ),
        )
    }

    @Test
    fun testBadRulePrequelStart2() {
        assertSyntaxPairs(
            listOf(
                "grammar A;\na } : A ;\nb : B ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:2: syntax error: '}' came as a complete surprise to me while matching rule preamble\n",
            ),
        )
    }

    @Test
    fun testModeInParser() {
        assertSyntaxPairs(
            listOf(
                "grammar A;\na : A ;\nmode foo;\nb : B ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:4:0: syntax error: 'b' came as a complete surprise to me\n" +
                    "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:4:6: syntax error: mismatched input ';' expecting COLON while matching a lexer rule\n",
            ),
        )
    }

    @Test
    fun testUnterminatedStringLiteral() {
        assertSyntaxPairs(
            listOf(
                "grammar A;\na : 'x\n  ;\n",
                "error(${ErrorType.UNTERMINATED_STRING_LITERAL.code}): A.g4:2:4: unterminated string literal\n",
            ),
        )
    }

    @Test
    fun testParserRuleNameStartingWithUnderscore() {
        assertSyntaxPairs(
            listOf(
                "grammar A;\n_a : 'x' ;\n",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:0: syntax error: '_' came as a complete surprise to me\n",
            ),
        )
    }

    @Test
    fun testEmptyGrammarOptions() {
        assertSyntaxPairs(listOf("grammar A;\noptions {}\na : 'x' ;\n", ""))
    }

    @Test
    fun testEmptyRuleOptions() {
        assertSyntaxPairs(listOf("grammar A;\na options{} : 'x' ;\n", ""))
    }

    @Test
    fun testEmptyBlockOptions() {
        assertSyntaxPairs(listOf("grammar A;\na : (options{} : 'x') ;\n", ""))
    }

    @Test
    fun testEmptyTokensBlock() {
        assertSyntaxPairs(listOf("grammar A;\ntokens {}\na : 'x' ;\n", ""))
    }

    @Test
    fun testActionAtEndOfOneLexerAlternative() {
        val grammar =
            "grammar A;\n" +
                "stat : 'start' CharacterLiteral 'end' EOF;\n" +
                "\n" +
                "// Lexer\n" +
                "\n" +
                "CharacterLiteral\n" +
                "    :   '\\'' SingleCharacter '\\''\n" +
                "    |   '\\'' ~[\\r\\n] {notifyErrorListeners(\"unclosed character literal\");}\n" +
                "    ;\n" +
                "\n" +
                "fragment\n" +
                "SingleCharacter\n" +
                "    :   ~['\\\\\\r\\n]\n" +
                "    ;\n" +
                "\n" +
                "WS   : [ \\r\\t\\n]+ -> skip ;\n"
        assertSyntaxPairs(listOf(grammar, ""))
    }

    @Test
    fun testDoubleQuotedStringLiteral() {
        val grammar = "lexer grammar A;\nWHITESPACE : (\" \" | \"\\t\" | \"\\n\" | \"\\r\" | \"\\f\");\n"
        val expected =
            "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:14: syntax error: '\"' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:16: syntax error: '\"' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:20: syntax error: '\"' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:21: syntax error: '\\' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:23: syntax error: '\"' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:27: syntax error: '\"' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:28: syntax error: '\\' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:30: syntax error: '\"' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:34: syntax error: '\"' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:35: syntax error: '\\' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:37: syntax error: '\"' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:41: syntax error: '\"' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:42: syntax error: '\\' came as a complete surprise to me\n" +
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:44: syntax error: '\"' came as a complete surprise to me\n"
        assertSyntaxPairs(listOf(grammar, expected))
    }

    @Test
    fun testDoubleQuoteInTwoStringLiterals() {
        val grammar = "lexer grammar A;\nSTRING : '\\\"' '\\\"' 'x' ;"
        val expected =
            "error(${ErrorType.INVALID_ESCAPE_SEQUENCE.code}): A.g4:2:10: invalid escape sequence \\\"\n" +
                "error(${ErrorType.INVALID_ESCAPE_SEQUENCE.code}): A.g4:2:15: invalid escape sequence \\\"\n"
        assertSyntaxPairs(listOf(grammar, expected))
    }

    @Test
    fun testValidEscapeSequences() {
        val grammar =
            "lexer grammar A;\n" +
                "NORMAL_ESCAPE : '\\b \\t \\n \\f \\r \\' \\\\';\n" +
                "UNICODE_ESCAPE : '\\u0001 \\u00A1 \\u00a1 \\uaaaa \\uAAAA';\n"
        assertSyntaxPairs(listOf(grammar, ""))
    }

    @Test
    fun testInvalidEscapeSequences() {
        val grammar = "lexer grammar A;\nRULE : 'Foo \\uAABG \\x \\u';\n"
        val expected =
            "error(${ErrorType.INVALID_ESCAPE_SEQUENCE.code}): A.g4:2:12: invalid escape sequence \\uAABG\n" +
                "error(${ErrorType.INVALID_ESCAPE_SEQUENCE.code}): A.g4:2:19: invalid escape sequence \\x\n" +
                "error(${ErrorType.INVALID_ESCAPE_SEQUENCE.code}): A.g4:2:22: invalid escape sequence \\u\n"
        assertSyntaxPairs(listOf(grammar, expected))
    }

    @Test
    fun testRuleNamesAsTree() {
        assertSyntaxPairs(listOf("grammar T;\ntree : 'X';", ""))
    }

    @Test
    fun testLexerRuleLabel() {
        assertSyntaxPairs(
            listOf(
                "grammar T;\na : A;\nA : h=~('b'|'c') ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): T.g4:3:5: syntax error: '=' came as a complete surprise to me while looking for lexer rule element\n",
            ),
        )
    }

    companion object {
        private val SYNTAX_PAIRS_A: List<String> =
            listOf(
                "A;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:1:0: syntax error: 'A' came as a complete surprise to me\n",
                "grammar ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:1:8: syntax error: ';' came as a complete surprise to me while looking for an identifier\n",
                "grammar A\na : ID ;\n",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:0: syntax error: missing SEMI at 'a'\n",
                "grammar A;\na : ID ;;\nb : B ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:8: syntax error: ';' came as a complete surprise to me\n",
                "grammar A;;\na : ID ;\n",
                "error(${ErrorType.SYNTAX_ERROR.code}): A;.g4:1:10: syntax error: ';' came as a complete surprise to me\n",
                "grammar A;\na @init : ID ;\n",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:8: syntax error: mismatched input ':' expecting ACTION while matching rule preamble\n",
                "grammar A;\na  ( A | B ) D ;\nb : B ;",
                "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:3: syntax error: '(' came as a complete surprise to me while matching rule preamble\n" +
                    "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:11: syntax error: mismatched input ')' expecting SEMI while matching a rule\n" +
                    "error(${ErrorType.SYNTAX_ERROR.code}): A.g4:2:15: syntax error: mismatched input ';' expecting COLON while matching a lexer rule\n",
            )
    }
}
