/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.tooling.analysis.AnalysisPipeline
import io.github.kotlinmania.antlr4.tooling.automata.LexerATNFactory
import io.github.kotlinmania.antlr4.tooling.automata.ParserATNFactory
import io.github.kotlinmania.antlr4.tooling.codegen.CodeGenerator
import io.github.kotlinmania.antlr4.tooling.codegen.SourceType
import io.github.kotlinmania.antlr4.tooling.semantics.SemanticPipeline
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActionTranslationTest {
    @Test
    fun testEscapedLessThanInAction() {
        val action = "i<3; '<xmltag>'"
        val expected = "i<3; '<xmltag>'"
        for (slot in ALL_SLOTS) {
            assertAttributeAction(slot, action, expected)
        }
    }

    @Test
    fun testEscapedDollarInAction() {
        val action = "int \\\$n; \"\\\$in string\$\""
        val expected = "int \$n; \"\$in string\$\""
        for (slot in ALL_SLOTS) {
            assertAttributeAction(slot, action, expected)
        }
    }

    @Test
    fun testUnescapedDollarInAction() {
        val action = "\\\$string\$"
        val expected = "\$string\$"
        for (slot in ALL_SLOTS) {
            assertAttributeAction(slot, action, expected)
        }
    }

    @Test
    fun testEscapedSlash() {
        val action = "x = '\\n';"
        val expected = "x = '\\n';"
        for (slot in ALL_SLOTS) {
            assertAttributeAction(slot, action, expected)
        }
    }

    @Test
    fun testComplicatedArgParsing() {
        val action = "x, (*a).foo(21,33), 3.2+1, '\\n', \"a,oo\\nick\", {bl, \"fdkj\"eck}"
        val expected = "x, (*a).foo(21,33), 3.2+1, '\\n', \"a,oo\\nick\", {bl, \"fdkj\"eck}"
        for (slot in ALL_SLOTS) {
            assertAttributeAction(slot, action, expected)
        }
    }

    @Test
    fun testComplicatedArgParsingWithTranslation() {
        val action = "x, \$ID.text+\"3242\", (*\$ID).foo(21,33), 3.2+1, '\\n', \"a,oo\\nick\", {bl, \"fdkj\"eck}"
        val expected =
            "x, (((AContext)_localctx).ID!=null?((AContext)_localctx).ID.getText():null)+\"3242\", " +
                "(*((AContext)_localctx).ID).foo(21,33), 3.2+1, '\\n', \"a,oo\\nick\", {bl, \"fdkj\"eck}"
        assertAttributeAction("inline", action, expected)
    }

    @Test
    fun testArguments() {
        assertAttributeAction("inline", "\$x; \$ctx.x", "_localctx.x; _localctx.x")
    }

    @Test
    fun testReturnValue() {
        assertAttributeAction("inline", "\$y; \$ctx.y", "_localctx.y; _localctx.y")
    }

    @Test
    fun testReturnValueWithNumber() {
        assertAttributeAction("inline", "\$ctx.x1", "_localctx.x1")
    }

    @Test
    fun testReturnValuesCurrentRule() {
        assertAttributeAction("inline", "\$y; \$ctx.y;", "_localctx.y; _localctx.y;")
    }

    @Test
    fun testReturnValues() {
        assertAttributeAction(
            "inline",
            "\$lab.e; \$b.e; \$y.e = \"\";",
            "((AContext)_localctx).lab.e; ((AContext)_localctx).b.e; _localctx.y.e = \"\";",
        )
    }

    @Test
    fun testReturnWithMultipleRuleRefs() {
        assertAttributeAction(
            "inline",
            "\$c.x; \$c.y;",
            "((AContext)_localctx).c.x; ((AContext)_localctx).c.y;",
        )
    }

    @Test
    fun testTokenRefs() {
        val action = "\$id; \$ID; \$id.text; \$id.getText(); \$id.line;"
        val expected =
            "((AContext)_localctx).id; ((AContext)_localctx).ID; " +
                "(((AContext)_localctx).id!=null?((AContext)_localctx).id.getText():null); " +
                "((AContext)_localctx).id.getText(); " +
                "(((AContext)_localctx).id!=null?((AContext)_localctx).id.getLine():0);"
        assertAttributeAction("inline", action, expected)
    }

    @Test
    fun testRuleRefs() {
        val action = "\$lab.start; \$c.text;"
        val expected =
            "(((AContext)_localctx).lab!=null?(((AContext)_localctx).lab.start):null); " +
                "(((AContext)_localctx).c!=null?_input.getText(((AContext)_localctx).c.start,((AContext)_localctx).c.stop):null);"
        assertAttributeAction("inline", action, expected)
    }

    @Test
    fun testUnknownAttr() {
        assertAttributeAction("inline", "\$qqq.text", "")
    }

    @Test
    fun testRuleRefsRecursive() {
        val recursiveBuilder = { inlineAction: String ->
            "parser grammar A;\n" +
                "options { language=Java; }\n" +
                "e returns [int v]\n" +
                "    :   INT {\$v = \$INT.int;}\n" +
                "    |   '(' e ')' {\n" +
                "\t\t #inline#$inlineAction#end-inline#\n" +
                "\t\t }\n" +
                "    ;\n"
        }
        val leftRecursiveBuilder = { inlineAction: String ->
            "parser grammar A;\n" +
                "options { language=Java; }\n" +
                "e returns [int v]\n" +
                "    :   a=e op=('*'|'/') b=e  {\$v = eval(\$a.v, \$op.type, \$b.v);}\n" +
                "    |   INT {\$v = \$INT.int;}\n" +
                "    |   '(' e ')' {\n" +
                "\t\t #inline#$inlineAction#end-inline#\n" +
                "\t\t }\n" +
                "    ;\n"
        }

        val action1 = "\$v = \$e.v;"
        val expected1 = "((EContext)_localctx).v =  ((EContext)_localctx).e.v;"
        assertRenderedSnippet(recursiveBuilder(action1), "inline", expected1)
        assertRenderedSnippet(leftRecursiveBuilder(action1), "inline", expected1)

        val action2 = "\$v = \$e.text.length();"
        val expected2 =
            "((EContext)_localctx).v =  (((EContext)_localctx).e!=null?_input.getText(((EContext)_localctx).e.start,((EContext)_localctx).e.stop):null).length();"
        assertRenderedSnippet(recursiveBuilder(action2), "inline", expected2)
        assertRenderedSnippet(leftRecursiveBuilder(action2), "inline", expected2)
    }

    @Test
    fun testRefToTextAttributeForCurrentRule() {
        val action = "\$ctx.text; \$text"
        val expected = "_localctx.text; _input.getText(_localctx.start, _input.LT(-1))"
        assertAttributeAction("init", action, expected)
        assertAttributeAction("inline", action, expected)
        assertAttributeAction("finally", action, expected)
    }

    @Test
    fun testKotlinTargetActionTranslation() {
        val grammarText =
            "parser grammar A;\n" +
                "a[x: Int] returns [var y: Int = 0]\n" +
                "    :   id=ID { #inline#\$y = \$x + (\$id.line);#end-inline# }\n" +
                "    ;\n"
        val rendered = renderParserOutput(grammarText)
        val snippet = extractSnippet(rendered, "inline")
        assertTrue(snippet.contains("localCtx.y"))
        assertTrue(snippet.contains("localCtx.x"))
    }

    @Test
    fun testEmptyActions() {
        val gS =
            "grammar A;\n" +
                "a[] : 'a' ;\n" +
                "c : a[] c[] ;\n"
        val g = Grammar(gS)
        assertNotNull(g.ast)
    }

    companion object {
        private val ALL_SLOTS = listOf("members", "init", "inline", "finally", "inline2")

        private fun buildAttributeGrammar(
            slotName: String,
            action: String,
        ): String {
            val members = if (slotName == "members") action else ""
            val init = if (slotName == "init") action else ""
            val inline = if (slotName == "inline") action else ""
            val finallyBody = if (slotName == "finally") action else ""
            val inline2 = if (slotName == "inline2") action else ""
            return "parser grammar A;\n" +
                "options { language=Java; }\n" +
                "@members {#members#$members#end-members#}\n" +
                "a[int x, int x1] returns [int y]\n" +
                "@init {#init#$init#end-init#}\n" +
                "    :   id=ID ids+=ID lab=b[34] c d {\n" +
                "\t\t #inline#$inline#end-inline#\n" +
                "\t\t }\n" +
                "\t\t c\n" +
                "    ;\n" +
                "    finally {#finally#$finallyBody#end-finally#}\n" +
                "b[int d] returns [int e]\n" +
                "    :   {#inline2#$inline2#end-inline2#}\n" +
                "    ;\n" +
                "c returns [int x, int y] : ;\n" +
                "d\t :   ;\n"
        }

        private fun assertAttributeAction(
            slotName: String,
            action: String,
            expected: String,
        ) {
            val grammarText = buildAttributeGrammar(slotName, action)
            assertRenderedSnippet(grammarText, slotName, expected)
        }

        private fun assertRenderedSnippet(
            grammarText: String,
            slotName: String,
            expected: String,
        ) {
            val output = renderParserOutput(grammarText)
            val snippet = extractSnippet(output, slotName)
            assertEquals(expected, snippet)
        }

        private fun renderParserOutput(grammarText: String): String {
            val equeue = ToolErrorQueue()
            val g = Grammar(grammarText, equeue)
            val ast = assertNotNull(g.ast)
            assertFalse(ast.hasErrors)
            if (g.atn == null) {
                SemanticPipeline(g).process()
                val factory = if (g.isLexer) LexerATNFactory(g as LexerGrammar) else ParserATNFactory(g)
                g.atn = factory.createATN()
                AnalysisPipeline(g).process()
            }
            val gen = CodeGenerator.create(g)
            return gen.generateParser(SourceType.SOURCE).render()
        }

        private fun extractSnippet(
            output: String,
            slotName: String,
        ): String {
            val beginTag = "#$slotName#"
            val endTag = "#end-$slotName#"
            val start = output.indexOf(beginTag)
            val end = output.indexOf(endTag)
            assertTrue(start >= 0 && end >= start + beginTag.length, "Missing markers $beginTag..$endTag in:\n$output")
            return output.substring(start + beginTag.length, end)
        }
    }
}

class CodeGenerationTest {
    @Test
    fun testArgDecl() {
        val javaGrammar =
            "grammar T;\n" +
                "options { language=Java; }\n" +
                "a[int xyz] : 'a' ;\n"
        val javaCode = renderParser(javaGrammar)
        assertTrue(javaCode.contains("int xyz"))
        assertFalse(javaCode.contains("AttributeDecl@"))

        val kotlinGrammar =
            "grammar T;\n" +
                "a[xyz: Int] : 'a' ;\n"
        val kotlinCode = renderParser(kotlinGrammar)
        assertTrue(kotlinCode.contains("xyz: Int"))
        assertFalse(kotlinCode.contains("AttributeDecl@"))
    }

    @Test
    fun assignTokenNamesToStringLiteralsInGeneratedParserRuleContexts() {
        val javaGrammar =
            "grammar T;\n" +
                "options { language=Java; }\n" +
                "root: 't1';\n" +
                "Token: 't1';"
        val javaCode = renderParser(javaGrammar)
        assertTrue(javaCode.contains("Token() { return getToken("))

        val kotlinGrammar =
            "grammar T;\n" +
                "root: 't1';\n" +
                "Token: 't1';"
        val kotlinCode = renderParser(kotlinGrammar)
        assertTrue(kotlinCode.contains("fun Token(): TerminalNode? = getToken("))
    }

    @Test
    fun assignTokenNamesToStringLiteralArraysInGeneratedParserRuleContexts() {
        val javaGrammar =
            "grammar T;\n" +
                "options { language=Java; }\n" +
                "root: 't1' 't1';\n" +
                "Token: 't1';"
        val javaCode = renderParser(javaGrammar)
        assertTrue(javaCode.contains("Token() { return getTokens("))

        val kotlinGrammar =
            "grammar T;\n" +
                "root: 't1' 't1';\n" +
                "Token: 't1';"
        val kotlinCode = renderParser(kotlinGrammar)
        assertTrue(kotlinCode.contains("fun Token(): List<TerminalNode> = getTokens("))
    }

    companion object {
        private fun renderParser(grammarText: String): String {
            val g = Grammar(grammarText)
            return CodeGenerator.create(g).generateParser(SourceType.SOURCE).render()
        }
    }
}

class DollarParserTest {
    @Test
    fun testSimpleCall() {
        val javaGrammar =
            "grammar T;\n" +
                "options { language=Java; }\n" +
                "a : ID { outStream.println(\$parser.getSourceName()); }\n" +
                "  ;\n" +
                "ID : 'a'..'z'+ ;\n"
        val javaOutput = CodeGenerator.create(Grammar(javaGrammar)).generateParser(SourceType.SOURCE).render()
        assertTrue(javaOutput.contains("outStream.println(this.getSourceName());"))

        val kotlinGrammar =
            "grammar T;\n" +
                "a : ID { println(\$parser.sourceName) }\n" +
                "  ;\n" +
                "ID : 'a'..'z'+ ;\n"
        val g = Grammar(kotlinGrammar)
        val kotlinOutput = CodeGenerator.create(g).generateParser(SourceType.SOURCE).render()
        assertTrue(kotlinOutput.contains("println(this.sourceName)"))

        val lexer = g.createLexerInterpreter(CharStreams.fromString("x", "input"))
        val tokens = CommonTokenStream(lexer)
        val parser = g.createGrammarParserInterpreter(tokens)
        assertEquals("input", parser.sourceName)
        val startRule = assertNotNull(g.getRule("a"))
        val tree = requireNotNull(parser.parse(startRule.index))
        assertEquals(0, parser.numberOfSyntaxErrors)
        assertEquals("x", tree.text)
    }
}

class SplitParserTest {
    @Test
    fun testGeneratesDefaultFiles() {
        val javaCreated = generateFiles("Java")
        assertEquals(
            setOf(
                "JavaLRLexer.java",
                "JavaLRParser.java",
                "JavaLRBaseListener.java",
                "JavaLRListener.java",
            ),
            javaCreated.keys,
        )

        val ktCreated = generateFiles("Kotlin")
        assertEquals(
            setOf(
                "JavaLRLexer.kt",
                "JavaLRParser.kt",
                "JavaLRBaseListener.kt",
                "JavaLRListener.kt",
            ),
            ktCreated.keys,
        )
    }

    @Test
    fun testGeneratesNoListenerFiles() {
        val javaCreated = generateFiles("Java", "-no-listener")
        assertEquals(setOf("JavaLRLexer.java", "JavaLRParser.java"), javaCreated.keys)

        val ktCreated = generateFiles("Kotlin", "-no-listener")
        assertEquals(setOf("JavaLRLexer.kt", "JavaLRParser.kt"), ktCreated.keys)
    }

    @Test
    fun testGeneratesVisitorFiles() {
        val javaCreated = generateFiles("Java", "-visitor")
        assertEquals(
            setOf(
                "JavaLRLexer.java",
                "JavaLRParser.java",
                "JavaLRBaseListener.java",
                "JavaLRListener.java",
                "JavaLRBaseVisitor.java",
                "JavaLRVisitor.java",
            ),
            javaCreated.keys,
        )

        val ktCreated = generateFiles("Kotlin", "-visitor")
        assertEquals(
            setOf(
                "JavaLRLexer.kt",
                "JavaLRParser.kt",
                "JavaLRBaseListener.kt",
                "JavaLRListener.kt",
                "JavaLRBaseVisitor.kt",
                "JavaLRVisitor.kt",
            ),
            ktCreated.keys,
        )
    }

    @Test
    fun testGeneratesSplitFiles() {
        val javaCreated = generateFiles("Java", "-split-parser", "-visitor")
        assertEquals(
            setOf(
                "JavaLRLexer.java",
                "JavaLRParser.java",
                "JavaLRParserContexts.java",
                "JavaLRParserDFA.java",
                "JavaLRBaseListener.java",
                "JavaLRListener.java",
                "JavaLRBaseVisitor.java",
                "JavaLRVisitor.java",
            ),
            javaCreated.keys,
        )
        val javaParser = assertNotNull(javaCreated["JavaLRParser.java"])
        assertFalse(javaParser.contains("ATNDeserializer()"))
        assertFalse(javaParser.contains("extends ParserRuleContext"))

        val javaContexts = assertNotNull(javaCreated["JavaLRParserContexts.java"])
        assertFalse(javaContexts.contains("ATNDeserializer()"))
        assertTrue(javaContexts.contains("extends ParserRuleContext"))

        val javaDfa = assertNotNull(javaCreated["JavaLRParserDFA.java"])
        assertTrue(javaDfa.contains("ATNDeserializer()"))
        assertFalse(javaDfa.contains("extends ParserRuleContext"))

        val ktCreated = generateFiles("Kotlin", "-split-parser", "-visitor")
        assertEquals(
            setOf(
                "JavaLRLexer.kt",
                "JavaLRParser.kt",
                "JavaLRParserContexts.kt",
                "JavaLRParserDFA.kt",
                "JavaLRBaseListener.kt",
                "JavaLRListener.kt",
                "JavaLRBaseVisitor.kt",
                "JavaLRVisitor.kt",
            ),
            ktCreated.keys,
        )
        val ktParser = assertNotNull(ktCreated["JavaLRParser.kt"])
        assertFalse(ktParser.contains("ATNDeserializer()"))
        assertFalse(ktParser.contains(": ParserRuleContext {"))

        val ktContexts = assertNotNull(ktCreated["JavaLRParserContexts.kt"])
        assertFalse(ktContexts.contains("ATNDeserializer()"))
        assertTrue(ktContexts.contains(": ParserRuleContext {"))

        val ktDfa = assertNotNull(ktCreated["JavaLRParserDFA.kt"])
        assertTrue(ktDfa.contains("ATNDeserializer()"))
        assertFalse(ktDfa.contains(": ParserRuleContext {"))
    }

    companion object {
        private val SAMPLE_LR_GRAMMAR =
            "grammar JavaLR;\n" +
                "stat : expr ';' # exprStat\n" +
                "     | ID '=' expr ';' # assignStat\n" +
                "     ;\n" +
                "expr : expr '*' expr # mulExpr\n" +
                "     | expr '+' expr # addExpr\n" +
                "     | '(' expr ')' # parenExpr\n" +
                "     | ID # idExpr\n" +
                "     | INT # intExpr\n" +
                "     ;\n" +
                "ID : [a-zA-Z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\t\\r\\n]+ -> skip ;\n"

        private fun generateFiles(
            language: String,
            vararg extraFlags: String,
        ): Map<String, String> {
            val ext = if (language == "Java") ".java" else ".kt"
            val cliArgs = extraFlags.toList() + listOf("-Dlanguage=$language", "-package", "antlr4.split", "JavaLR.g4")
            val tool = Tool(cliArgs)
            tool.writeFilesToDisk = false
            tool.registerVirtualGrammar("JavaLR.g4", SAMPLE_LR_GRAMMAR)
            val equeue = ToolErrorQueue(tool)
            tool.removeListeners()
            tool.addListener(equeue)
            tool.processGrammarsOnCommandLine()
            assertEquals(0, equeue.errors.size, "Unexpected errors: $equeue")

            val matched = LinkedHashMap<String, String>()
            for ((name, content) in tool.virtualOutputs) {
                if (!name.contains('/') && name.endsWith(ext)) {
                    matched[name] = content
                }
            }
            return matched
        }
    }
}
