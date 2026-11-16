/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.misc.Utils
import io.github.kotlinmania.antlr4.tooling.codegen.CodeGenerator
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.GrammarSemanticsMessage
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.github.kotlinmania.antlr4.tooling.gui.Trees as GuiTrees

class BasicSemanticErrorsTest {
    @Test
    fun testU() {
        ToolTestSupport.testErrors(U_CASES, false)
    }

    @Test
    fun testIllegalNonSetLabel() {
        val grammar =
            "grammar T;\n" +
                "ss : op=('=' | '+=' | expr) EOF;\n" +
                "expr : '=' '=';\n"
        val expected =
            "error(${ErrorType.LABEL_BLOCK_NOT_A_SET.code}): T.g4:2:5: label op assigned to a block which is not a set\n"
        ToolTestSupport.testErrors(arrayOf(grammar, expected), false)
    }

    @Test
    fun testArgumentRetvalLocalConflicts() {
        val args = "int expr"
        val retvals = "int expr"
        val locals = "int expr"
        val body = "expr=expr"
        val renderedGrammar =
            "grammar T;\n" +
                "ss[$args] returns [$retvals]\n" +
                "locals [$locals]\n" +
                "  : $body EOF;\n" +
                "expr : '=';\n"

        val expected =
            "error(${ErrorType.ARG_CONFLICTS_WITH_RULE.code}): T.g4:2:7: parameter expr conflicts with rule with same name\n" +
                "error(${ErrorType.RETVAL_CONFLICTS_WITH_RULE.code}): T.g4:2:26: return value expr conflicts with rule with same name\n" +
                "error(${ErrorType.LOCAL_CONFLICTS_WITH_RULE.code}): T.g4:3:12: local expr conflicts with rule with same name\n" +
                "error(${ErrorType.RETVAL_CONFLICTS_WITH_ARG.code}): T.g4:2:26: return value expr conflicts with parameter with same name\n" +
                "error(${ErrorType.LOCAL_CONFLICTS_WITH_ARG.code}): T.g4:3:12: local expr conflicts with parameter with same name\n" +
                "error(${ErrorType.LOCAL_CONFLICTS_WITH_RETVAL.code}): T.g4:3:12: local expr conflicts with return value with same name\n" +
                "error(${ErrorType.LABEL_CONFLICTS_WITH_RULE.code}): T.g4:4:4: label expr conflicts with rule with same name\n" +
                "error(${ErrorType.LABEL_CONFLICTS_WITH_ARG.code}): T.g4:4:4: label expr conflicts with parameter with same name\n" +
                "error(${ErrorType.LABEL_CONFLICTS_WITH_RETVAL.code}): T.g4:4:4: label expr conflicts with return value with same name\n" +
                "error(${ErrorType.LABEL_CONFLICTS_WITH_LOCAL.code}): T.g4:4:4: label expr conflicts with local with same name\n"

        ToolTestSupport.testErrors(arrayOf(renderedGrammar, expected), false)
    }

    companion object {
        val U_CASES: Array<String> =
            arrayOf(
                "parser grammar U;\n" +
                    "options { foo=bar; k=3;}\n" +
                    "tokens {\n" +
                    "        ID,\n" +
                    "        f,\n" +
                    "        S\n" +
                    "}\n" +
                    "tokens { A }\n" +
                    "options { x=y; }\n" +
                    "\n" +
                    "a\n" +
                    "options { blech=bar; greedy=true; }\n" +
                    "        :       ID\n" +
                    "        ;\n" +
                    "b : ( options { ick=bar; greedy=true; } : ID )+ ;\n" +
                    "c : ID<blue> ID<x=y> ;",
                "warning(${ErrorType.ILLEGAL_OPTION.code}): U.g4:2:10: unsupported option foo\n" +
                    "warning(${ErrorType.ILLEGAL_OPTION.code}): U.g4:2:19: unsupported option k\n" +
                    "error(${ErrorType.TOKEN_NAMES_MUST_START_UPPER.code}): U.g4:5:8: token names must start with an uppercase letter: f\n" +
                    "warning(${ErrorType.ILLEGAL_OPTION.code}): U.g4:9:10: unsupported option x\n" +
                    "error(${ErrorType.REPEATED_PREQUEL.code}): U.g4:9:0: repeated grammar prequel spec (options, tokens, or import); please merge\n" +
                    "error(${ErrorType.REPEATED_PREQUEL.code}): U.g4:8:0: repeated grammar prequel spec (options, tokens, or import); please merge\n" +
                    "warning(${ErrorType.ILLEGAL_OPTION.code}): U.g4:12:10: unsupported option blech\n" +
                    "warning(${ErrorType.ILLEGAL_OPTION.code}): U.g4:12:21: unsupported option greedy\n" +
                    "warning(${ErrorType.ILLEGAL_OPTION.code}): U.g4:15:16: unsupported option ick\n" +
                    "warning(${ErrorType.ILLEGAL_OPTION.code}): U.g4:15:25: unsupported option greedy\n" +
                    "warning(${ErrorType.ILLEGAL_OPTION.code}): U.g4:16:16: unsupported option x\n",
            )
    }
}

class ErrorSetsTest {
    @Test
    fun testNotCharSetWithRuleRef() {
        val pair =
            arrayOf(
                "grammar T;\n" +
                    "a : A {println(\$A.text)} ;\n" +
                    "A : ~('a'|B) ;\n" +
                    "B : 'b' ;\n",
                "error(${ErrorType.UNSUPPORTED_REFERENCE_IN_LEXER_SET.code}): T.g4:3:10: rule reference B is not currently supported in a set\n",
            )
        ToolTestSupport.testErrors(pair, true)
    }

    @Test
    fun testNotCharSetWithString() {
        val pair =
            arrayOf(
                "grammar T;\n" +
                    "a : A {println(\$A.text)} ;\n" +
                    "A : ~('a'|'aa') ;\n" +
                    "B : 'b' ;\n",
                "error(${ErrorType.INVALID_LITERAL_IN_LEXER_SET.code}): T.g4:3:10: multi-character literals are not allowed in lexer sets: 'aa'\n",
            )
        ToolTestSupport.testErrors(pair, true)
    }
}

class TokenPositionOptionsTest {
    @Test
    fun testLeftRecursionRewrite() {
        val g =
            Grammar(
                "grammar T;\n" +
                    "s : e ';' ;\n" +
                    "e : e '*' e\n" +
                    "  | e '+' e\n" +
                    "  | e '.' ID\n" +
                    "  | '-' e\n" +
                    "  | ID\n" +
                    "  ;\n" +
                    "ID : [a-z]+ ;\n",
            )

        val ast = assertNotNull(g.ast)
        val expectedTree =
            "(COMBINED" + "_GRAMMAR T (RULES (RULE s (BLOCK (ALT e ';'))) (RULE e (BLOCK (ALT (BLOCK (ALT {} ('-' (ELEMENT" + "_OPTIONS (= tokenIndex 43))) (e (ELEMENT" + "_OPTIONS (= tokenIndex 45) (= p 2)))) (ALT (ID (ELEMENT" + "_OPTIONS (= tokenIndex 49))))) (* (BLOCK (ALT ({precpred(ctx, 5)}? (ELEMENT" + "_OPTIONS (= p 5))) ('*' (ELEMENT" + "_OPTIONS (= tokenIndex 21))) (e (ELEMENT" + "_OPTIONS (= tokenIndex 23) (= p 6)))) (ALT ({precpred(ctx, 4)}? (ELEMENT" + "_OPTIONS (= p 4))) ('+' (ELEMENT" + "_OPTIONS (= tokenIndex 29))) (e (ELEMENT" + "_OPTIONS (= tokenIndex 31) (= p 5)))) (ALT ({precpred(ctx, 3)}? (ELEMENT" + "_OPTIONS (= p 3))) ('.' (ELEMENT" + "_OPTIONS (= tokenIndex 37))) (ID (ELEMENT" + "_OPTIONS (= tokenIndex 39)))))))))))"
        assertEquals(expectedTree, ast.toStringTree())

        val ruleRefType = ANTLRParserTypes.RULE_REF
        val stringLitType = ANTLRParserTypes.STRING_LITERAL
        val tokenRefType = ANTLRParserTypes.TOKEN_REF
        val expectedElementTokens =
            "[@5,11:11='s',<$ruleRefType>,2:0]\n" +
                "[@9,15:15='e',<$ruleRefType>,2:4]\n" +
                "[@11,17:19='';'',<$stringLitType>,2:6]\n" +
                "[@15,23:23='e',<$ruleRefType>,3:0]\n" +
                "[@43,64:66=''-'',<$stringLitType>,6:4]\n" +
                "[@45,68:68='e',<$ruleRefType>,6:8]\n" +
                "[@49,74:75='ID',<$tokenRefType>,7:4]\n" +
                "[@21,29:31=''*'',<$stringLitType>,3:6]\n" +
                "[@23,33:33='e',<$ruleRefType>,3:10]\n" +
                "[@29,41:43=''+'',<$stringLitType>,4:6]\n" +
                "[@31,45:45='e',<$ruleRefType>,4:10]\n" +
                "[@37,53:55=''.'',<$stringLitType>,5:6]\n" +
                "[@39,57:58='ID',<$tokenRefType>,5:10]"

        val types =
            IntervalSet.of(tokenRefType).apply {
                add(stringLitType)
                add(ruleRefType)
            }
        val nodes = ast.getNodesWithTypePreorderDFS(types)
        val tokens = nodes.map { it.token }
        assertEquals(expectedElementTokens, Utils.join(tokens.iterator(), "\n"))
    }

    @Test
    fun testLeftRecursionWithLabels() {
        val g =
            Grammar(
                "grammar T;\n" +
                    "s : e ';' ;\n" +
                    "e : e '*' x=e\n" +
                    "  | e '+' e\n" +
                    "  | e '.' y=ID\n" +
                    "  | '-' e\n" +
                    "  | ID\n" +
                    "  ;\n" +
                    "ID : [a-z]+ ;\n",
            )

        val ast = assertNotNull(g.ast)
        val expectedTree =
            "(COMBINED" + "_GRAMMAR T (RULES (RULE s (BLOCK (ALT e ';'))) (RULE e (BLOCK (ALT (BLOCK (ALT {} ('-' (ELEMENT" + "_OPTIONS (= tokenIndex 47))) (e (ELEMENT" + "_OPTIONS (= tokenIndex 49) (= p 2)))) (ALT (ID (ELEMENT" + "_OPTIONS (= tokenIndex 53))))) (* (BLOCK (ALT ({precpred(ctx, 5)}? (ELEMENT" + "_OPTIONS (= p 5))) ('*' (ELEMENT" + "_OPTIONS (= tokenIndex 21))) (= x (e (ELEMENT" + "_OPTIONS (= tokenIndex 25) (= p 6))))) (ALT ({precpred(ctx, 4)}? (ELEMENT" + "_OPTIONS (= p 4))) ('+' (ELEMENT" + "_OPTIONS (= tokenIndex 31))) (e (ELEMENT" + "_OPTIONS (= tokenIndex 33) (= p 5)))) (ALT ({precpred(ctx, 3)}? (ELEMENT" + "_OPTIONS (= p 3))) ('.' (ELEMENT" + "_OPTIONS (= tokenIndex 39))) (= y (ID (ELEMENT" + "_OPTIONS (= tokenIndex 43))))))))))))"
        assertEquals(expectedTree, ast.toStringTree())

        val ruleRefType = ANTLRParserTypes.RULE_REF
        val stringLitType = ANTLRParserTypes.STRING_LITERAL
        val tokenRefType = ANTLRParserTypes.TOKEN_REF
        val expectedElementTokens =
            "[@5,11:11='s',<$ruleRefType>,2:0]\n" +
                "[@9,15:15='e',<$ruleRefType>,2:4]\n" +
                "[@11,17:19='';'',<$stringLitType>,2:6]\n" +
                "[@15,23:23='e',<$ruleRefType>,3:0]\n" +
                "[@47,68:70=''-'',<$stringLitType>,6:4]\n" +
                "[@49,72:72='e',<$ruleRefType>,6:8]\n" +
                "[@53,78:79='ID',<$tokenRefType>,7:4]\n" +
                "[@21,29:31=''*'',<$stringLitType>,3:6]\n" +
                "[@25,35:35='e',<$ruleRefType>,3:12]\n" +
                "[@31,43:45=''+'',<$stringLitType>,4:6]\n" +
                "[@33,47:47='e',<$ruleRefType>,4:10]\n" +
                "[@39,55:57=''.'',<$stringLitType>,5:6]\n" +
                "[@43,61:62='ID',<$tokenRefType>,5:12]"

        val types =
            IntervalSet.of(tokenRefType).apply {
                add(stringLitType)
                add(ruleRefType)
            }
        val nodes = ast.getNodesWithTypePreorderDFS(types)
        val tokens = nodes.map { it.token }
        assertEquals(expectedElementTokens, Utils.join(tokens.iterator(), "\n"))
    }

    @Test
    fun testLeftRecursionWithSet() {
        val g =
            Grammar(
                "grammar T;\n" +
                    "s : e ';' ;\n" +
                    "e : e op=('*'|'/') e\n" +
                    "  | e '+' e\n" +
                    "  | e '.' ID\n" +
                    "  | '-' e\n" +
                    "  | ID\n" +
                    "  ;\n" +
                    "ID : [a-z]+ ;\n",
            )

        val ast = assertNotNull(g.ast)
        val expectedTree =
            "(COMBINED" + "_GRAMMAR T (RULES (RULE s (BLOCK (ALT e ';'))) (RULE e (BLOCK (ALT (BLOCK (ALT {} ('-' (ELEMENT" + "_OPTIONS (= tokenIndex 49))) (e (ELEMENT" + "_OPTIONS (= tokenIndex 51) (= p 2)))) (ALT (ID (ELEMENT" + "_OPTIONS (= tokenIndex 55))))) (* (BLOCK (ALT ({precpred(ctx, 5)}? (ELEMENT" + "_OPTIONS (= p 5))) (= op (SET ('*' (ELEMENT" + "_OPTIONS (= tokenIndex 24))) ('/' (ELEMENT" + "_OPTIONS (= tokenIndex 26))))) (e (ELEMENT" + "_OPTIONS (= tokenIndex 29) (= p 6)))) (ALT ({precpred(ctx, 4)}? (ELEMENT" + "_OPTIONS (= p 4))) ('+' (ELEMENT" + "_OPTIONS (= tokenIndex 35))) (e (ELEMENT" + "_OPTIONS (= tokenIndex 37) (= p 5)))) (ALT ({precpred(ctx, 3)}? (ELEMENT" + "_OPTIONS (= p 3))) ('.' (ELEMENT" + "_OPTIONS (= tokenIndex 43))) (ID (ELEMENT" + "_OPTIONS (= tokenIndex 45)))))))))))"
        assertEquals(expectedTree, ast.toStringTree())

        val ruleRefType = ANTLRParserTypes.RULE_REF
        val stringLitType = ANTLRParserTypes.STRING_LITERAL
        val tokenRefType = ANTLRParserTypes.TOKEN_REF
        val expectedElementTokens =
            "[@5,11:11='s',<$ruleRefType>,2:0]\n" +
                "[@9,15:15='e',<$ruleRefType>,2:4]\n" +
                "[@11,17:19='';'',<$stringLitType>,2:6]\n" +
                "[@15,23:23='e',<$ruleRefType>,3:0]\n" +
                "[@49,73:75=''-'',<$stringLitType>,6:4]\n" +
                "[@51,77:77='e',<$ruleRefType>,6:8]\n" +
                "[@55,83:84='ID',<$tokenRefType>,7:4]\n" +
                "[@24,33:35=''*'',<$stringLitType>,3:10]\n" +
                "[@26,37:39=''/'',<$stringLitType>,3:14]\n" +
                "[@29,42:42='e',<$ruleRefType>,3:19]\n" +
                "[@35,50:52=''+'',<$stringLitType>,4:6]\n" +
                "[@37,54:54='e',<$ruleRefType>,4:10]\n" +
                "[@43,62:64=''.'',<$stringLitType>,5:6]\n" +
                "[@45,66:67='ID',<$tokenRefType>,5:10]"

        val types =
            IntervalSet.of(tokenRefType).apply {
                add(stringLitType)
                add(ruleRefType)
            }
        val nodes = ast.getNodesWithTypePreorderDFS(types)
        val tokens = nodes.map { it.token }
        assertEquals(expectedElementTokens, Utils.join(tokens.iterator(), "\n"))
    }
}

class UnicodeGrammarTest {
    @Test
    fun unicodeBMPLiteralInGrammar() {
        val grammarText =
            "grammar Unicode;\n" +
                "r : 'hello' WORLD;\n" +
                "WORLD : ('world' | '\\u4E16\\u754C' | '\\u1000\\u1019\\u1039\\u1018\\u102C' );\n" +
                "WS : [ \\t\\r\\n]+ -> skip;\n"
        val inputText = "hello \u4E16\u754C"
        assertEquals("(r:1 $inputText)", parseTreeForGrammarWithInput(grammarText, "r", inputText))
    }

    // Upstream unicodeSurrogatePairLiteralInGrammar is disabled in ANTLR4 because 4-hex BMP surrogate escapes do not auto-coalesce into SMP code points.

    @Test
    fun unicodeSMPLiteralInGrammar() {
        val grammarText =
            "grammar Unicode;\n" +
                "r : 'hello' WORLD;\n" +
                "WORLD : ('\\u{1F30D}' | '\\u{1F30E}' | '\\u{1F30F}' );\n" +
                "WS : [ \\t\\r\\n]+ -> skip;\n"
        val globeCodePoint = appendSupplementaryCodePoint("hello ", 0x1F30E)
        assertEquals("(r:1 $globeCodePoint)", parseTreeForGrammarWithInput(grammarText, "r", globeCodePoint))
    }

    @Test
    fun unicodeSMPRangeInGrammar() {
        val grammarText =
            "grammar Unicode;\n" +
                "r : 'hello' WORLD;\n" +
                "WORLD : ('\\u{1F30D}'..'\\u{1F30F}' );\n" +
                "WS : [ \\t\\r\\n]+ -> skip;\n"
        val globeCodePoint = appendSupplementaryCodePoint("hello ", 0x1F30E)
        assertEquals("(r:1 $globeCodePoint)", parseTreeForGrammarWithInput(grammarText, "r", globeCodePoint))
    }

    @Test
    fun matchingDanglingSurrogateInInput() {
        val grammarText =
            "grammar Unicode;\n" +
                "r : 'hello' WORLD;\n" +
                "WORLD : ('\\uD83C' | '\\uD83D' | '\\uD83E' );\n" +
                "WS : [ \\t\\r\\n]+ -> skip;\n"
        val danglingSurrogate = charArrayOf(0xD83C.toChar()).concatToString()
        val inputText = "hello $danglingSurrogate"
        assertEquals("(r:1 $inputText)", parseTreeForGrammarWithInput(grammarText, "r", inputText))
    }

    @Test
    fun binaryGrammar() {
        val grammarText =
            "grammar Binary;\n" +
                "r : HEADER PACKET+ FOOTER;\n" +
                "HEADER : '\\u0002\\u0000\\u0001\\u0007';\n" +
                "PACKET : '\\u00D0' ('\\u00D1' | '\\u00D2' | '\\u00D3') +;\n" +
                "FOOTER : '\\u00FF';\n"
        val toParse =
            byteArrayOf(
                0x02.toByte(),
                0x00.toByte(),
                0x01.toByte(),
                0x07.toByte(),
                0xD0.toByte(),
                0xD2.toByte(),
                0xD2.toByte(),
                0xD3.toByte(),
                0xD3.toByte(),
                0xD3.toByte(),
                0xD0.toByte(),
                0xD3.toByte(),
                0xD3.toByte(),
                0xD1.toByte(),
                0xFF.toByte(),
            )
        val charStream = CharStreams.fromLatin1Bytes(toParse)
        val grammar = Grammar(grammarText)
        val lexEngine = grammar.createLexerInterpreter(charStream)
        val tokens = CommonTokenStream(lexEngine)
        val parser = grammar.createGrammarParserInterpreter(tokens)
        val rule = assertNotNull(grammar.getRule("r"))
        val parseTree = parser.parse(rule.index)
        val nodeTextProvider = InterpreterTreeTextProvider(grammar.ruleNames)
        val result = GuiTrees.toStringTree(parseTree, nodeTextProvider)
        assertEquals(
            "(r:1 \u0002\u0000\u0001\u0007 \u00D0\u00D2\u00D2\u00D3\u00D3\u00D3 \u00D0\u00D3\u00D3\u00D1 \u00FF)",
            result,
        )
    }

    companion object {
        private fun appendSupplementaryCodePoint(
            prefix: String,
            codePoint: Int,
        ): String {
            val offset = codePoint - 0x10000
            val high = ((offset ushr 10) + 0xD800).toChar()
            val low = ((offset and 0x3FF) + 0xDC00).toChar()
            return prefix + charArrayOf(high, low).concatToString()
        }

        private fun parseTreeForGrammarWithInput(
            grammarText: String,
            rootRule: String,
            inputText: String,
        ): String {
            val grammar = Grammar(grammarText)
            val lexEngine = grammar.createLexerInterpreter(CharStreams.fromString(inputText))
            val tokens = CommonTokenStream(lexEngine)
            val parser = grammar.createGrammarParserInterpreter(tokens)
            val rule = assertNotNull(grammar.getRule(rootRule))
            val parseTree = parser.parse(rule.index)
            val nodeTextProvider = InterpreterTreeTextProvider(grammar.ruleNames)
            return GuiTrees.toStringTree(parseTree, nodeTextProvider)
        }
    }
}

class CompositeGrammarsTest {
    @Test
    fun testImportFileLocationInSubdir() {
        val slave =
            "parser grammar S;\n" +
                "a : B {println(\"S.a\")} ;\n"
        val master =
            "grammar M;\n" +
                "import S;\n" +
                "s : a ;\n" +
                "B : 'b' ;" +
                "WS : (' '|'\\n') -> skip ;\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("sub/S.g4" to slave),
            )
        assertEquals(0, equeue.size())
    }

    @Test
    fun testImportSelfLoop() {
        val master =
            "grammar M;\n" +
                "import M;\n" +
                "s : 'a' ;\n"
        val equeue = ToolTestSupport.antlrOnString(master, fileNameOverride = "M.g4")
        assertEquals(0, equeue.size())
    }

    @Test
    fun testImportIntoLexerGrammar() {
        val master =
            "lexer grammar M;\n" +
                "import S;\n" +
                "A : 'a';\n" +
                "B : 'b';\n"
        val slave =
            "lexer grammar S;\n" +
                "C : 'c';\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("S.g4" to slave),
            )
        assertEquals(0, equeue.errors.size)
    }

    @Test
    fun testImportModesIntoLexerGrammar() {
        val master =
            "lexer grammar M;\n" +
                "import S;\n" +
                "A : 'a' -> pushMode(X);\n" +
                "B : 'b';\n"
        val slave =
            "lexer grammar S;\n" +
                "D : 'd';\n" +
                "mode X;\n" +
                "C : 'c' -> popMode;\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("S.g4" to slave),
            )
        assertEquals(0, equeue.errors.size)
    }

    @Test
    fun testImportChannelsIntoLexerGrammar() {
        val master =
            "lexer grammar M;\n" +
                "import S;\n" +
                "channels {CH_A, CH_B}\n" +
                "A : 'a' -> channel(CH_A);\n" +
                "B : 'b' -> channel(CH_B);\n"
        val slave =
            "lexer grammar S;\n" +
                "C : 'c';\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("S.g4" to slave),
            )
        assertEquals(0, equeue.errors.size)
    }

    @Test
    fun testImportMixedChannelsIntoLexerGrammar() {
        val master =
            "lexer grammar M;\n" +
                "import S;\n" +
                "channels {CH_A, CH_B}\n" +
                "A : 'a' -> channel(CH_A);\n" +
                "B : 'b' -> channel(CH_B);\n"
        val slave =
            "lexer grammar S;\n" +
                "channels {CH_C}\n" +
                "C : 'c' -> channel(CH_C);\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("S.g4" to slave),
            )
        assertEquals(0, equeue.errors.size)
    }

    @Test
    fun testImportClashingChannelsIntoLexerGrammar() {
        val master =
            "lexer grammar M;\n" +
                "import S;\n" +
                "channels {CH_A, CH_B, CH_C}\n" +
                "A : 'a' -> channel(CH_A);\n" +
                "B : 'b' -> channel(CH_B);\n" +
                "C : 'C' -> channel(CH_C);\n"
        val slave =
            "lexer grammar S;\n" +
                "channels {CH_C}\n" +
                "C : 'c' -> channel(CH_C);\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("S.g4" to slave),
            )
        assertEquals(0, equeue.errors.size)
    }

    @Test
    fun testMergeModesIntoLexerGrammar() {
        val master =
            "lexer grammar M;\n" +
                "import S;\n" +
                "A : 'a' -> pushMode(X);\n" +
                "mode X;\n" +
                "B : 'b';\n"
        val slave =
            "lexer grammar S;\n" +
                "D : 'd';\n" +
                "mode X;\n" +
                "C : 'c' -> popMode;\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("S.g4" to slave),
            )
        assertEquals(0, equeue.errors.size)
    }

    @Test
    fun testEmptyModesInLexerGrammar() {
        val master =
            "lexer grammar M;\n" +
                "import S;\n" +
                "A : 'a';\n" +
                "C : 'e';\n" +
                "B : 'b';\n"
        val slave =
            "lexer grammar S;\n" +
                "D : 'd';\n" +
                "mode X;\n" +
                "C : 'c' -> popMode;\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("S.g4" to slave),
            )
        assertEquals(0, equeue.errors.size)
    }

    @Test
    fun testCombinedGrammarImportsModalLexerGrammar() {
        val master =
            "grammar M;\n" +
                "import S;\n" +
                "A : 'a';\n" +
                "B : 'b';\n" +
                "r : A B;\n"
        val slave =
            "lexer grammar S;\n" +
                "D : 'd';\n" +
                "mode X;\n" +
                "C : 'c' -> popMode;\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("S.g4" to slave),
            )
        assertEquals(1, equeue.errors.size)
        val msg = equeue.errors[0]
        assertEquals(ErrorType.MODE_NOT_IN_LEXER, msg.errorType)
        assertEquals("X", msg.argsList[0])
        assertEquals(3, msg.line)
        assertEquals(5, msg.charPosition)
        assertEquals("M.g4", msg.fileName?.substringAfterLast('/'))
    }

    @Test
    fun testDelegatesSeeSameTokenType() {
        val slaveS =
            "parser grammar S;\n" +
                "tokens { A, B, C }\n" +
                "x : A ;\n"
        val slaveT =
            "parser grammar T;\n" +
                "tokens { C, B, A }\n" +
                "y : A ;\n"
        val master =
            "grammar M;\n" +
                "import S,T;\n" +
                "s : x y ;\n" +
                "B : 'b' ;\n" +
                "A : 'a' ;\n" +
                "C : 'c' ;\n" +
                "WS : (' '|'\\n') -> skip ;\n"

        val (g, equeue) = loadCompositeGrammar("M.g4", master, mapOf("S.g4" to slaveS, "T.g4" to slaveT))
        assertEquals("{EOF=-1, B=1, A=2, C=3, WS=4}", g.tokenNameToTypeMap.toString())
        assertEquals("{'a'=2, 'b'=1, 'c'=3}", sortByKey(g.stringLiteralToTypeMap).toString())
        assertEquals("[B, A, C, WS]", ToolTestSupport.realElements(g.typeToTokenList).toString())
        assertEquals(0, equeue.errors.size, "unexpected errors: $equeue")
    }

    @Test
    fun testErrorInImportedGetsRightFilename() {
        val slave =
            "parser grammar S;\n" +
                "a : 'a' | c;\n"
        val master =
            "grammar M;\n" +
                "import S;\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("S.g4" to slave),
            )
        val msg = equeue.errors[0]
        assertEquals(ErrorType.UNDEFINED_RULE_REF, msg.errorType)
        assertEquals("c", msg.argsList[0])
        assertEquals(2, msg.line)
        assertEquals(10, msg.charPosition)
        assertEquals("S.g4", msg.fileName?.substringAfterLast('/'))
    }

    @Test
    fun testMissingImportedGrammarReportsError() {
        val master =
            "grammar M;\n" +
                "import S;\n" +
                "s : a ;\n" +
                "B : 'b' ;" +
                "WS : (' '|'\\n') -> skip ;\n"
        val equeue = ToolTestSupport.antlrOnString(master, fileNameOverride = "M.g4")
        assertTrue(equeue.errors.isNotEmpty())
        assertEquals(ErrorType.CANNOT_FIND_IMPORTED_GRAMMAR, equeue.errors[0].errorType)
    }

    @Test
    fun testTokensVocabAndImportedSubdirGrammar() {
        val slave =
            "parser grammar S;\n" +
                "a : B {println(\"S.a\")} ;\n"
        val lexer =
            "lexer grammar MLexer;\n" +
                "B : 'b' ;" +
                "WS : (' '|'\\n') -> skip ;\n"
        val parser =
            "parser grammar MParser;\n" +
                "import S;\n" +
                "options {tokenVocab=MLexer;}\n" +
                "s : a ;\n"

        val lexerTool = Tool(listOf("MLexer.g4"))
        lexerTool.writeFilesToDisk = false
        lexerTool.registerVirtualGrammar("MLexer.g4", lexer)
        val lexerQueue = ToolErrorQueue(lexerTool)
        lexerTool.removeListeners()
        lexerTool.addListener(lexerQueue)
        lexerTool.processGrammarsOnCommandLine()
        assertEquals(0, lexerQueue.size())
        val lexerTokens = assertNotNull(lexerTool.virtualOutputs["MLexer.tokens"])

        val parserQueue =
            ToolTestSupport.antlrOnString(
                grammarStr = parser,
                fileNameOverride = "MParser.g4",
                extraVirtualGrammars = mapOf("sub/S.g4" to slave),
                extraVirtualTokenVocabs = mapOf("MLexer" to lexerTokens),
            )
        assertEquals(0, parserQueue.size())
    }

    @Test
    fun testImportedTokenVocabIgnoredWithWarning() {
        val slave =
            "parser grammar S;\n" +
                "options {tokenVocab=whatever;}\n" +
                "tokens { A }\n" +
                "x : A {println(\"S.x\")} ;\n"
        val master =
            "grammar M;\n" +
                "import S;\n" +
                "s : x ;\n" +
                "WS : (' '|'\\n') -> skip ;\n"
        val (_, equeue) = loadCompositeGrammar("M.g4", master, mapOf("S.g4" to slave))
        assertEquals(0, equeue.errors.size, "unexpected errors: $equeue")
        assertEquals(1, equeue.warnings.size, "unexpected warnings: $equeue")
        val warning = equeue.warnings[0]
        assertTrue(warning is GrammarSemanticsMessage)
        assertEquals(ErrorType.OPTIONS_IN_DELEGATE, warning.errorType)
        assertEquals(listOf<Any?>("S"), warning.argsList)
    }

    @Test
    fun testSyntaxErrorsInImportsNotThrownOut() {
        val slave =
            "parser grammar S;\n" +
                "options {toke\n"
        val master =
            "grammar M;\n" +
                "import S;\n" +
                "s : x ;\n" +
                "WS : (' '|'\\n') -> skip ;\n"
        val (_, equeue) = loadCompositeGrammar("M.g4", master, mapOf("S.g4" to slave))
        assertTrue(equeue.errors.isNotEmpty())
        assertEquals(ErrorType.SYNTAX_ERROR, equeue.errors[0].errorType)
    }

    @Test
    fun test3LevelImport() {
        val slaveT =
            "parser grammar T;\n" +
                "a : T ;\n"
        val slaveS =
            "parser grammar S;\n" +
                "import T;\n" +
                "a : S ;\n"
        val master =
            "grammar M;\n" +
                "import S;\n" +
                "a : M ;\n"
        val (g, equeue) = loadCompositeGrammar("M.g4", master, mapOf("T.g4" to slaveT, "S.g4" to slaveS))
        assertEquals("{EOF=-1, M=1}", g.tokenNameToTypeMap.toString())
        assertEquals("{}", g.stringLiteralToTypeMap.toString())
        assertEquals("[M]", ToolTestSupport.realElements(g.typeToTokenList).toString())
        assertEquals(0, equeue.errors.size, "unexpected errors: $equeue")
        val generated = CodeGenerator.create(g).generateParser().render()
        assertTrue(generated.contains("class MParser"))
    }

    @Test
    fun testBigTreeOfImports() {
        val imports =
            mapOf(
                "T.g4" to "parser grammar T;\ntokens{T}\nx : T ;\n",
                "S.g4" to "parser grammar S;\nimport T;\ntokens{S}\ny : S ;\n",
                "C.g4" to "parser grammar C;\ntokens{C}\ni : C ;\n",
                "B.g4" to "parser grammar B;\ntokens{B}\nj : B ;\n",
                "A.g4" to "parser grammar A;\nimport B,C;\ntokens{A}\nk : A ;\n",
            )
        val master =
            "grammar M;\n" +
                "import S,A;\n" +
                "tokens{M}\n" +
                "a : M ;\n"
        val (g, equeue) = loadCompositeGrammar("M.g4", master, imports)
        assertEquals("[]", equeue.errors.toString())
        assertEquals("[]", equeue.warnings.toString())
        assertEquals("{EOF=-1, M=1, S=2, T=3, A=4, B=5, C=6}", g.tokenNameToTypeMap.toString())
        assertEquals("{}", g.stringLiteralToTypeMap.toString())
        assertEquals("[M, S, T, A, B, C]", ToolTestSupport.realElements(g.typeToTokenList).toString())
        val generated = CodeGenerator.create(g).generateParser().render()
        assertTrue(generated.contains("class MParser"))
    }

    @Test
    fun testRulesVisibleThroughMultilevelImport() {
        val slaveT = "parser grammar T;\nx : T ;\n"
        val slaveS = "parser grammar S;\nimport T;\na : S ;\n"
        val master = "grammar M;\nimport S;\na : M x ;\n"
        val (g, equeue) = loadCompositeGrammar("M.g4", master, mapOf("T.g4" to slaveT, "S.g4" to slaveS))
        assertEquals("{EOF=-1, M=1, T=2}", g.tokenNameToTypeMap.toString())
        assertEquals("{}", g.stringLiteralToTypeMap.toString())
        assertEquals("[M, T]", ToolTestSupport.realElements(g.typeToTokenList).toString())
        assertEquals(0, equeue.errors.size, "unexpected errors: $equeue")
    }

    @Test
    fun testNestedComposite() {
        val imports =
            mapOf(
                "L.g4" to "lexer grammar L;\nT1: '1';\nT2: '2';\nT3: '3';\nT4: '4';\n",
                "G1.g4" to "parser grammar G1;\ns: a | b;\na: T1;\nb: T2;\n",
                "G2.g4" to "parser grammar G2;\nimport G1;\na: T3;\n",
            )
        val master = "grammar G3;\nimport G2;\nb: T4;\n"
        val (g, equeue) = loadCompositeGrammar("G3.g4", master, imports)
        assertEquals("{EOF=-1, T4=1, T3=2}", g.tokenNameToTypeMap.toString())
        assertEquals("{}", g.stringLiteralToTypeMap.toString())
        assertEquals("[T4, T3]", ToolTestSupport.realElements(g.typeToTokenList).toString())
        assertEquals(0, equeue.errors.size, "unexpected errors: $equeue")
        val generated = CodeGenerator.create(g).generateParser().render()
        assertTrue(generated.contains("class G3Parser"))
    }

    @Test
    fun testHeadersPropagatedCorrectlyToImportedGrammars() {
        val slave =
            "parser grammar S;\n" +
                "a : B {print(\"S.a\")} ;\n"
        val master =
            "grammar M;\n" +
                "import S;\n" +
                "@header{package mypackage}\n" +
                "s : a ;\n" +
                "B : 'b' ;" +
                "WS : (' '|'\\n') -> skip ;\n"
        val equeue =
            ToolTestSupport.antlrOnString(
                grammarStr = master,
                fileNameOverride = "M.g4",
                extraVirtualGrammars = mapOf("S.g4" to slave),
            )
        assertEquals(0, equeue.errors.size)
    }

    @Test
    fun testImportLeftRecursiveGrammar() {
        val slave =
            "grammar SubExpr;\n" +
                "e : '(' e ')'\n" +
                "  | e '=' e\n" +
                "  | ID\n" +
                "  ;\n" +
                "ID : [a-z]+ ;\n"
        val master =
            "grammar T;\n" +
                "import SubExpr;\n" +
                "s : e ;\n"
        val (g, equeue) = loadCompositeGrammar("T.g4", master, mapOf("SubExpr.g4" to slave))
        assertEquals(0, equeue.errors.size, "unexpected errors: $equeue")

        val lexer = g.createLexerInterpreter(CharStreams.fromString("a=b"))
        val tokens = CommonTokenStream(lexer)
        val parser = g.createGrammarParserInterpreter(tokens)
        val startRule = assertNotNull(g.getRule("s"))
        val tree = parser.parse(startRule.index)
        assertEquals(0, parser.numberOfSyntaxErrors)
        val rendered = GuiTrees.toStringTree(tree, InterpreterTreeTextProvider(g.ruleNames))
        assertEquals("(s:1 (e:2 (e:3 a) = (e:3 b)))", rendered)
    }

    @Test
    fun testCircularGrammarInclusion() {
        val g1 =
            "grammar G1;\n" +
                "import G2;\n" +
                "r : 'R1';"
        val g2 =
            "grammar G2;\n" +
                "import G1;\n" +
                "r : 'R2';"
        val (g, equeue) = loadCompositeGrammar("G2.g4", g2, mapOf("G1.g4" to g1))
        assertEquals(0, equeue.errors.size, "unexpected errors: $equeue")

        val lexer = g.createLexerInterpreter(CharStreams.fromString("R2"))
        val tokens = CommonTokenStream(lexer)
        val parser = g.createGrammarParserInterpreter(tokens)
        val startRule = assertNotNull(g.getRule("r"))
        val tree = parser.parse(startRule.index)
        assertEquals(0, parser.numberOfSyntaxErrors)
        val rendered = GuiTrees.toStringTree(tree, InterpreterTreeTextProvider(g.ruleNames))
        assertEquals("(r:1 R2)", rendered)
    }

    companion object {
        private fun loadCompositeGrammar(
            fileName: String,
            masterText: String,
            imports: Map<String, String>,
        ): Pair<Grammar, ToolErrorQueue> {
            val tool = Tool()
            tool.writeFilesToDisk = false
            val equeue = ToolErrorQueue(tool)
            tool.removeListeners()
            tool.addListener(equeue)
            for ((impName, impContent) in imports) {
                tool.registerVirtualGrammar(impName, impContent)
            }
            tool.registerVirtualGrammar(fileName, masterText)
            val rootAst = assertNotNull(tool.parseGrammar(fileName))
            val g = tool.createGrammar(rootAst)
            g.fileName = fileName
            tool.process(g, false)
            return g to equeue
        }

        private fun <K : Comparable<K>, V> sortByKey(data: Map<K, V>): Map<K, V> {
            val sorted = LinkedHashMap<K, V>()
            for (k in data.keys.sorted()) {
                val value = data[k]
                if (value != null) {
                    sorted[k] = value
                }
            }
            return sorted
        }
    }
}
