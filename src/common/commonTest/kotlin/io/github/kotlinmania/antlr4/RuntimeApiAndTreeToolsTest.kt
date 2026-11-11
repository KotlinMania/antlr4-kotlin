package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.atn.ATNDeserializer
import io.github.kotlinmania.antlr4.atn.ATNSerializer
import io.github.kotlinmania.antlr4.misc.InterpreterDataReader
import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.GrammarInterpreterRuleContext
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import io.github.kotlinmania.antlr4.tree.AbstractParseTreeVisitor
import io.github.kotlinmania.antlr4.tree.ErrorNode
import io.github.kotlinmania.antlr4.tree.ParseTree
import io.github.kotlinmania.antlr4.tree.RuleNode
import io.github.kotlinmania.antlr4.tree.TerminalNode
import io.github.kotlinmania.antlr4.tree.pattern.ParseTreeMatch
import io.github.kotlinmania.antlr4.tree.pattern.ParseTreePatternMatcher
import io.github.kotlinmania.antlr4.tree.xpath.XPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XPathTest {
    @Test
    fun testValidPaths() {
        val xpaths =
            listOf(
                "/prog/func",
                "/prog/*",
                "/*/func",
                "prog",
                "/prog",
                "/*",
                "*",
                "//ID",
                "//expr/primary/ID",
                "//body//ID",
                "//'return'",
                "//RETURN",
                "//primary/*",
                "//func/*/stat",
                "/prog/func/'def'",
                "//stat/';'",
                "//expr/primary/!ID",
                "//expr/!primary",
                "//!*",
                "/!*",
                "//expr//ID",
            )
        val expected =
            listOf(
                "[func, func]",
                "[func, func]",
                "[func, func]",
                "[prog]",
                "[prog]",
                "[prog]",
                "[prog]",
                "[f, x, y, x, y, g, x, x]",
                "[y, x]",
                "[x, y, x]",
                "[return]",
                "[return]",
                "[3, 4, y, 1, 2, x]",
                "[stat, stat, stat, stat]",
                "[def, def]",
                "[;, ;, ;, ;]",
                "[3, 4, 1, 2]",
                "[expr, expr, expr, expr, expr, expr]",
                "[]",
                "[]",
                "[y, x]",
            )

        val grammar = Grammar(EXPR_GRAMMAR)
        for (i in xpaths.indices) {
            val nodes = getNodeStrings(grammar, SAMPLE_PROGRAM, xpaths[i], "prog")
            assertEquals(expected[i], nodes.toString(), "path ${xpaths[i]} failed")
        }
    }

    @Test
    fun testWeirdChar() {
        assertPathError("&", "Invalid tokens or characters at index 0 in path '&'")
    }

    @Test
    fun testWeirdChar2() {
        assertPathError("//w&e/", "Invalid tokens or characters at index 3 in path '//w&e/'")
    }

    @Test
    fun testBadSyntax() {
        assertPathError("///", "/ at index 2 isn't a valid rule name")
    }

    @Test
    fun testMissingWordAtEnd() {
        assertPathError("//", "Missing path element at end of path")
    }

    @Test
    fun testBadTokenName() {
        assertPathError("//Ick", "Ick at index 2 isn't a valid token name")
    }

    @Test
    fun testBadRuleName() {
        assertPathError("/prog/ick", "ick at index 6 isn't a valid rule name")
    }

    private fun assertPathError(
        xpath: String,
        expected: String,
    ) {
        val grammar = Grammar(EXPR_GRAMMAR)
        val err =
            assertFailsWith<IllegalArgumentException> {
                compileAndExtract(grammar, SAMPLE_PROGRAM, xpath, "prog")
            }
        assertEquals(expected, err.message)
    }

    private fun getNodeStrings(
        grammar: Grammar,
        input: String,
        xpath: String,
        startRuleName: String,
    ): List<String> {
        val (ruleNames, found) = compileAndExtract(grammar, input, xpath, startRuleName)
        val nodes = ArrayList<String>()
        for (tree in found) {
            if (tree is RuleContext) {
                nodes.add(ruleNames[tree.ruleIndex])
            } else if (tree is TerminalNode) {
                nodes.add(tree.text.orEmpty())
            }
        }
        return nodes
    }

    private fun compileAndExtract(
        grammar: Grammar,
        input: String,
        xpath: String,
        startRuleName: String,
    ): Pair<List<String>, Collection<ParseTree>> {
        val lexer = grammar.createLexerInterpreter(ANTLRInputStream(input))
        val tokens = CommonTokenStream(lexer)
        val parser = grammar.createParserInterpreter(tokens)
        val tree = parser.parse(parser.getRuleIndex(startRuleName))
        val found = XPath.findAll(tree, xpath, parser)
        return Pair(parser.ruleNames.orEmpty(), found)
    }

    companion object {
        private val EXPR_GRAMMAR =
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
                "RETURN : 'return' ;\n" +
                "ID  :   [a-zA-Z]+ ;\n" +
                "INT :   [0-9]+ ;\n" +
                "NEWLINE:'\\r'? '\\n' -> skip;\n" +
                "WS  :   [ \\t]+ -> skip ;\n"

        private const val SAMPLE_PROGRAM =
            "def f(x,y) { x = 3+4; y; ; }\n" +
                "def g(x) { return 1+2*x; }\n"
    }
}

class ParseTreeMatcherTest {
    @Test
    fun testChunking() {
        val matcher = ParseTreePatternMatcher(null, null)
        assertEquals("[ID, ' = ', expr, ' ;']", matcher.split("<ID> = <expr> ;").toString())
        assertEquals("[' ', ID, ' = ', expr]", matcher.split(" <ID> = <expr>").toString())
        assertEquals("[ID, ' = ', expr]", matcher.split("<ID> = <expr>").toString())
        assertEquals("[expr]", matcher.split("<expr>").toString())
        assertEquals("['<x> foo']", matcher.split("\\<x\\> foo").toString())
        assertEquals("['foo <x> bar ', tag]", matcher.split("foo \\<x\\> bar <tag>").toString())
    }

    @Test
    fun testDelimiters() {
        val matcher = ParseTreePatternMatcher(null, null)
        matcher.setDelimiters("<<", ">>", "$")
        val result = matcher.split("<<ID>> = <<expr>> ;$<< ick $>>").toString()
        assertEquals("[ID, ' = ', expr, ' ;<< ick >>']", result)
    }

    @Test
    fun testInvertedTags() {
        val matcher = ParseTreePatternMatcher(null, null)
        val err = assertFailsWith<IllegalArgumentException> { matcher.split(">expr<") }
        assertEquals("tag delimiters out of order in pattern: >expr<", err.message)
    }

    @Test
    fun testUnclosedTag() {
        val matcher = ParseTreePatternMatcher(null, null)
        val err = assertFailsWith<IllegalArgumentException> { matcher.split("<expr hi mom") }
        assertEquals("unterminated tag in pattern: <expr hi mom", err.message)
    }

    @Test
    fun testExtraClose() {
        val matcher = ParseTreePatternMatcher(null, null)
        val err = assertFailsWith<IllegalArgumentException> { matcher.split("<expr> >") }
        assertEquals("missing start tag in pattern: <expr> >", err.message)
    }

    @Test
    fun testTokenizingPattern() {
        val grammar =
            "grammar X1;\n" +
                "s : ID '=' expr ';' ;\n" +
                "expr : ID | INT ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        val matcher = getPatternMatcher("X1.g4", grammar)
        val tokens = matcher.tokenize("<ID> = <expr> ;")
        assertEquals("[ID:3, [@-1,1:1='=',<1>,1:1], expr:7, [@-1,1:1=';',<2>,1:1]]", tokens.toString())
    }

    @Test
    fun testCompilingPattern() {
        val grammar =
            "grammar X2;\n" +
                "s : ID '=' expr ';' ;\n" +
                "expr : ID | INT ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        val matcher = getPatternMatcher("X2.g4", grammar)
        val parser = requireNotNull(matcher.getParser())
        val pattern = matcher.compile("<ID> = <expr> ;", parser.getRuleIndex("s"))
        assertEquals("(s <ID> = (expr <expr>) ;)", pattern.getPatternTree()?.toStringTree(parser))
    }

    @Test
    fun testCompilingPatternConsumesAllTokens() {
        val grammar =
            "grammar X2;\n" +
                "s : ID '=' expr ';' ;\n" +
                "expr : ID | INT ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        val matcher = getPatternMatcher("X2.g4", grammar)
        val parser = requireNotNull(matcher.getParser())
        assertFailsWith<ParseTreePatternMatcher.StartRuleDoesNotConsumeFullPattern> {
            matcher.compile("<ID> = <expr> ; extra", parser.getRuleIndex("s"))
        }
    }

    @Test
    fun testPatternMatchesStartRule() {
        val grammar =
            "grammar X2;\n" +
                "s : ID '=' expr ';' ;\n" +
                "expr : ID | INT ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        val matcher = getPatternMatcher("X2.g4", grammar)
        val parser = requireNotNull(matcher.getParser())
        val control =
            assertFailsWith<RecognitionControlException> {
                matcher.compile("<ID> ;", parser.getRuleIndex("s"))
            }
        assertIs<InputMismatchException>(control.recognitionException)
    }

    @Test
    fun testPatternMatchesStartRule2() {
        val grammar =
            "grammar X2;\n" +
                "s : ID '=' expr ';' | expr ';' ;\n" +
                "expr : ID | INT ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        val matcher = getPatternMatcher("X2.g4", grammar)
        val parser = requireNotNull(matcher.getParser())
        val control =
            assertFailsWith<RecognitionControlException> {
                matcher.compile("<ID> <ID> ;", parser.getRuleIndex("s"))
            }
        assertIs<NoViableAltException>(control.recognitionException)
    }

    @Test
    fun testHiddenTokensNotSeenByTreePatternParser() {
        val grammar =
            "grammar X2;\n" +
                "s : ID '=' expr ';' ;\n" +
                "expr : ID | INT ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> channel(HIDDEN) ;\n"
        val matcher = getPatternMatcher("X2.g4", grammar)
        val parser = requireNotNull(matcher.getParser())
        val pattern = matcher.compile("<ID> = <expr> ;", parser.getRuleIndex("s"))
        assertEquals("(s <ID> = (expr <expr>) ;)", pattern.getPatternTree()?.toStringTree(parser))
    }

    @Test
    fun testCompilingMultipleTokens() {
        val grammar =
            "grammar X2;\n" +
                "s : ID '=' ID ';' ;\n" +
                "ID : [a-z]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        val matcher = getPatternMatcher("X2.g4", grammar)
        val parser = requireNotNull(matcher.getParser())
        val pattern = matcher.compile("<ID> = <ID> ;", parser.getRuleIndex("s"))
        assertEquals("(s <ID> = <ID> ;)", pattern.getPatternTree()?.toStringTree(parser))
    }

    @Test
    fun testIDNodeMatches() {
        val grammar =
            "grammar X3;\n" +
                "s : ID ';' ;\n" +
                "ID : [a-z]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        checkPatternMatch(grammar, "s", "x ;", "<ID>;", "X3")
    }

    @Test
    fun testIDNodeWithLabelMatches() {
        val grammar =
            "grammar X8;\n" +
                "s : ID ';' ;\n" +
                "ID : [a-z]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        val match = checkPatternMatch(grammar, "s", "x ;", "<id:ID>;", "X8")
        assertEquals("{ID=[x], id=[x]}", match.getLabels().toString())
        assertNotNull(match.get("id"))
        assertNotNull(match.get("ID"))
        assertEquals("x", match.get("id")?.text)
        assertEquals("x", match.get("ID")?.text)
        assertEquals("[x]", match.getAll("id").toString())
        assertEquals("[x]", match.getAll("ID").toString())
        assertNull(match.get("undefined"))
        assertEquals("[]", match.getAll("undefined").toString())
    }

    @Test
    fun testLabelGetsLastIDNode() {
        val grammar =
            "grammar X9;\n" +
                "s : ID ID ';' ;\n" +
                "ID : [a-z]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        val match = checkPatternMatch(grammar, "s", "x y;", "<id:ID> <id:ID>;", "X9")
        assertEquals("{ID=[x, y], id=[x, y]}", match.getLabels().toString())
        assertNotNull(match.get("id"))
        assertNotNull(match.get("ID"))
        assertEquals("y", match.get("id")?.text)
        assertEquals("y", match.get("ID")?.text)
        assertEquals("[x, y]", match.getAll("id").toString())
        assertEquals("[x, y]", match.getAll("ID").toString())
        assertNull(match.get("undefined"))
        assertEquals("[]", match.getAll("undefined").toString())
    }

    @Test
    fun testIDNodeWithMultipleLabelMatches() {
        val grammar =
            "grammar X7;\n" +
                "s : ID ID ID ';' ;\n" +
                "ID : [a-z]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        val match = checkPatternMatch(grammar, "s", "x y z;", "<a:ID> <b:ID> <a:ID>;", "X7")
        assertEquals("{ID=[x, y, z], a=[x, z], b=[y]}", match.getLabels().toString())
        assertNotNull(match.get("a"))
        assertNotNull(match.get("b"))
        assertNotNull(match.get("ID"))
        assertEquals("z", match.get("a")?.text)
        assertEquals("y", match.get("b")?.text)
        assertEquals("z", match.get("ID")?.text)
        assertEquals("[x, z]", match.getAll("a").toString())
        assertEquals("[y]", match.getAll("b").toString())
        assertEquals("[x, y, z]", match.getAll("ID").toString())
        assertEquals("xyz;", match.getTree().text)
        assertNull(match.get("undefined"))
        assertEquals("[]", match.getAll("undefined").toString())
    }

    @Test
    fun testTokenAndRuleMatch() {
        val grammar =
            "grammar X4;\n" +
                "s : ID '=' expr ';' ;\n" +
                "expr : ID | INT ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        checkPatternMatch(grammar, "s", "x = 99;", "<ID> = <expr> ;", "X4")
    }

    @Test
    fun testTokenTextMatch() {
        val grammar =
            "grammar X4;\n" +
                "s : ID '=' expr ';' ;\n" +
                "expr : ID | INT ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        checkPatternMatch(grammar, "s", "x = 0;", "<ID> = 1;", "X4", invertMatch = true)
        checkPatternMatch(grammar, "s", "x = 0;", "<ID> = 0;", "X4", invertMatch = false)
        checkPatternMatch(grammar, "s", "x = 0;", "x = 0;", "X4", invertMatch = false)
        checkPatternMatch(grammar, "s", "x = 0;", "y = 0;", "X4", invertMatch = true)
    }

    @Test
    fun testAssign() {
        val grammar =
            "grammar X5;\n" +
                "s   : expr ';'\n" +
                "    ;\n" +
                "expr: expr '.' ID\n" +
                "    | expr '*' expr\n" +
                "    | expr '=' expr\n" +
                "    | ID\n" +
                "    | INT\n" +
                "    ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        checkPatternMatch(grammar, "s", "x = 99;", "<ID> = <expr>;", "X5")
    }

    @Test
    fun testLRecursiveExpr() {
        val grammar =
            "grammar X6;\n" +
                "s   : expr ';'\n" +
                "    ;\n" +
                "expr: expr '.' ID\n" +
                "    | expr '*' expr\n" +
                "    | expr '=' expr\n" +
                "    | ID\n" +
                "    | INT\n" +
                "    ;\n" +
                "ID : [a-z]+ ;\n" +
                "INT : [0-9]+ ;\n" +
                "WS : [ \\r\\n\\t]+ -> skip ;\n"
        checkPatternMatch(grammar, "expr", "3*4*5", "<expr> * <expr> * <expr>", "X6")
    }

    private fun getPatternMatcher(
        grammarFileName: String,
        grammarText: String,
    ): ParseTreePatternMatcher {
        val g = Grammar(grammarFileName, grammarText)
        val lexer = g.createLexerInterpreter(ANTLRInputStream(""))
        val parser = g.createParserInterpreter(CommonTokenStream(lexer))
        return ParseTreePatternMatcher(lexer, parser)
    }

    private fun checkPatternMatch(
        grammarText: String,
        startRule: String,
        input: String,
        pattern: String,
        grammarName: String,
        invertMatch: Boolean = false,
    ): ParseTreeMatch {
        val g = Grammar("$grammarName.g4", grammarText)
        val lexer = g.createLexerInterpreter(ANTLRInputStream(input))
        val tokens = CommonTokenStream(lexer)
        val parser = g.createParserInterpreter(tokens)
        val tree = requireNotNull(parser.parse(parser.getRuleIndex(startRule)))

        val patternLexer = g.createLexerInterpreter(ANTLRInputStream(""))
        val patternParser = g.createParserInterpreter(CommonTokenStream(patternLexer))
        val compiled = patternParser.compileParseTreePattern(pattern, patternParser.getRuleIndex(startRule))
        val match = compiled.match(tree)
        if (invertMatch) {
            assertFalse(match.succeeded())
        } else {
            assertTrue(match.succeeded())
        }
        return match
    }
}

class IntervalSetTest {
    @Test
    fun testSingleElement() {
        val s = IntervalSet.of(99)
        assertEquals("99", s.toString())
    }

    @Test
    fun testMin() {
        assertEquals(0, IntervalSet.COMPLETE_CHAR_SET.minElement)
        assertEquals(Token.EPSILON, IntervalSet.COMPLETE_CHAR_SET.or(IntervalSet.of(Token.EPSILON)).minElement)
        assertEquals(Token.EOF, IntervalSet.COMPLETE_CHAR_SET.or(IntervalSet.of(Token.EOF)).minElement)
    }

    @Test
    fun testIsolatedElements() {
        val s = IntervalSet()
        s.add(1)
        s.add('z'.code)
        s.add('\uFFF0'.code)
        assertEquals("{1, 122, 65520}", s.toString())
    }

    @Test
    fun testMixedRangesAndElements() {
        val s = IntervalSet()
        s.add(1)
        s.add('a'.code, 'z'.code)
        s.add('0'.code, '9'.code)
        assertEquals("{1, 48..57, 97..122}", s.toString())
    }

    @Test
    fun testSimpleAnd() {
        val s = IntervalSet.of(10, 20)
        val s2 = IntervalSet.of(13, 15)
        assertEquals("{13..15}", s.and(s2).toString())
    }

    @Test
    fun testRangeAndIsolatedElement() {
        val s = IntervalSet.of('a'.code, 'z'.code)
        val s2 = IntervalSet.of('d'.code)
        assertEquals("100", s.and(s2).toString())
    }

    @Test
    fun testEmptyIntersection() {
        val s = IntervalSet.of('a'.code, 'z'.code)
        val s2 = IntervalSet.of('0'.code, '9'.code)
        assertEquals("{}", s.and(s2).toString())
    }

    @Test
    fun testEmptyIntersectionSingleElements() {
        val s = IntervalSet.of('a'.code)
        val s2 = IntervalSet.of('d'.code)
        assertEquals("{}", s.and(s2).toString())
    }

    @Test
    fun testNotSingleElement() {
        val vocabulary = IntervalSet.of(1, 1000)
        vocabulary.add(2000, 3000)
        val s = IntervalSet.of(50, 50)
        assertEquals("{1..49, 51..1000, 2000..3000}", s.complement(vocabulary).toString())
    }

    @Test
    fun testNotSet() {
        val vocabulary = IntervalSet.of(1, 1000)
        val s = IntervalSet.of(50, 60)
        s.add(5)
        s.add(250, 300)
        assertEquals("{1..4, 6..49, 61..249, 301..1000}", s.complement(vocabulary).toString())
    }

    @Test
    fun testNotEqualSet() {
        val vocabulary = IntervalSet.of(1, 1000)
        val s = IntervalSet.of(1, 1000)
        assertEquals("{}", s.complement(vocabulary).toString())
    }

    @Test
    fun testNotSetEdgeElement() {
        val vocabulary = IntervalSet.of(1, 2)
        val s = IntervalSet.of(1)
        assertEquals("2", s.complement(vocabulary).toString())
    }

    @Test
    fun testNotSetFragmentedVocabulary() {
        val vocabulary = IntervalSet.of(1, 255)
        vocabulary.add(1000, 2000)
        vocabulary.add(9999)
        val s = IntervalSet.of(50, 60)
        s.add(3)
        s.add(250, 300)
        s.add(10000)
        assertEquals("{1..2, 4..49, 61..249, 1000..2000, 9999}", s.complement(vocabulary).toString())
    }

    @Test
    fun testSubtractOfCompletelyContainedRange() {
        val s = IntervalSet.of(10, 20)
        val s2 = IntervalSet.of(12, 15)
        assertEquals("{10..11, 16..20}", s.subtract(s2).toString())
    }

    @Test
    fun testSubtractFromSetWithEOF() {
        val s = IntervalSet.of(10, 20)
        s.add(Token.EOF)
        val s2 = IntervalSet.of(12, 15)
        assertEquals("{<EOF>, 10..11, 16..20}", s.subtract(s2).toString())
    }

    @Test
    fun testSubtractOfOverlappingRangeFromLeft() {
        val s = IntervalSet.of(10, 20)
        val s2 = IntervalSet.of(5, 11)
        assertEquals("{12..20}", s.subtract(s2).toString())

        val s3 = IntervalSet.of(5, 10)
        assertEquals("{11..20}", s.subtract(s3).toString())
    }

    @Test
    fun testSubtractOfOverlappingRangeFromRight() {
        val s = IntervalSet.of(10, 20)
        val s2 = IntervalSet.of(15, 25)
        assertEquals("{10..14}", s.subtract(s2).toString())

        val s3 = IntervalSet.of(20, 25)
        assertEquals("{10..19}", s.subtract(s3).toString())
    }

    @Test
    fun testSubtractOfCompletelyCoveredRange() {
        val s = IntervalSet.of(10, 20)
        val s2 = IntervalSet.of(1, 25)
        assertEquals("{}", s.subtract(s2).toString())
    }

    @Test
    fun testSubtractOfRangeSpanningMultipleRanges() {
        val s = IntervalSet.of(10, 20)
        s.add(30, 40)
        s.add(50, 60)
        val s2 = IntervalSet.of(5, 55)
        assertEquals("{56..60}", s.subtract(s2).toString())

        val s3 = IntervalSet.of(15, 55)
        assertEquals("{10..14, 56..60}", s.subtract(s3).toString())
    }

    @Test
    fun testSubtractOfWackyRange() {
        val s = IntervalSet.of(0, 113)
        s.add(115, 200)
        val s2 = IntervalSet.of(0, 115)
        s2.add(117, 200)
        assertEquals("116", s.subtract(s2).toString())
    }

    @Test
    fun testSimpleEquals() {
        val s = IntervalSet.of(10, 20)
        val s2 = IntervalSet.of(10, 20)
        assertEquals(s, s2)

        val s3 = IntervalSet.of(15, 55)
        assertFalse(s == s3)
    }

    @Test
    fun testEquals() {
        val s = IntervalSet.of(10, 20)
        s.add(2)
        s.add(499, 501)
        val s2 = IntervalSet.of(10, 20)
        s2.add(2)
        s2.add(499, 501)
        assertEquals(s, s2)

        val s3 = IntervalSet.of(10, 20)
        s3.add(2)
        assertFalse(s == s3)
    }

    @Test
    fun testSingleElementMinusDisjointSet() {
        val s = IntervalSet.of(15, 15)
        val s2 = IntervalSet.of(1, 5)
        s2.add(10, 20)
        assertEquals("{}", s.subtract(s2).toString())
    }

    @Test
    fun testMembership() {
        val s = IntervalSet.of(15, 15)
        s.add(50, 60)
        assertTrue(!s.contains(0))
        assertTrue(!s.contains(20))
        assertTrue(!s.contains(100))
        assertTrue(s.contains(15))
        assertTrue(s.contains(55))
        assertTrue(s.contains(50))
        assertTrue(s.contains(60))
    }

    @Test
    fun testIntersectionWithTwoContainedElements() {
        val s = IntervalSet.of(10, 20)
        val s2 = IntervalSet.of(2, 2)
        s2.add(15)
        s2.add(18)
        assertEquals("{15, 18}", s.and(s2).toString())
    }

    @Test
    fun testIntersectionWithTwoContainedElementsReversed() {
        val s = IntervalSet.of(10, 20)
        val s2 = IntervalSet.of(2, 2)
        s2.add(15)
        s2.add(18)
        assertEquals("{15, 18}", s2.and(s).toString())
    }

    @Test
    fun testComplement() {
        val s = IntervalSet.of(100, 100)
        s.add(101, 101)
        val s2 = IntervalSet.of(100, 102)
        assertEquals("102", s.complement(s2).toString())
    }

    @Test
    fun testComplement2() {
        val s = IntervalSet.of(100, 101)
        val s2 = IntervalSet.of(100, 102)
        assertEquals("102", s.complement(s2).toString())
    }

    @Test
    fun testComplement3() {
        val s = IntervalSet.of(1, 96)
        s.add(99, Lexer.MAX_CHAR_VALUE)
        assertEquals("{97..98}", s.complement(1, Lexer.MAX_CHAR_VALUE).toString())
    }

    @Test
    fun testMergeOfRangesAndSingleValues() {
        val s = IntervalSet.of(0, 41)
        s.add(42)
        s.add(43, 65534)
        assertEquals("{0..65534}", s.toString())
    }

    @Test
    fun testMergeOfRangesAndSingleValuesReverse() {
        val s = IntervalSet.of(43, 65534)
        s.add(42)
        s.add(0, 41)
        assertEquals("{0..65534}", s.toString())
    }

    @Test
    fun testMergeWhereAdditionMergesTwoExistingIntervals() {
        val s = IntervalSet.of(42)
        s.add(10)
        s.add(0, 9)
        s.add(43, 65534)
        s.add(11, 41)
        assertEquals("{0..65534}", s.toString())
    }

    @Test
    fun testMergeWhereAdditionMergesThreeExistingIntervals() {
        val s = IntervalSet()
        s.add(0)
        s.add(3)
        s.add(5)
        s.add(0, 7)
        assertEquals("{0..7}", s.toString())
    }

    @Test
    fun testMergeWithDoubleOverlap() {
        val s = IntervalSet.of(1, 10)
        s.add(20, 30)
        s.add(5, 25)
        assertEquals("{1..30}", s.toString())
    }

    @Test
    fun testSize() {
        val s = IntervalSet.of(20, 30)
        s.add(50, 55)
        s.add(5, 19)
        assertEquals("32", s.size().toString())
    }

    @Test
    fun testToList() {
        val s = IntervalSet.of(20, 25)
        s.add(50, 55)
        s.add(5, 5)
        assertEquals("[5, 20, 21, 22, 23, 24, 25, 50, 51, 52, 53, 54, 55]", s.toList().toString())
    }

    @Test
    fun testNotRIntersectionNotT() {
        val s = IntervalSet.of(0, 's'.code)
        s.add('u'.code, 200)
        val s2 = IntervalSet.of(0, 'q'.code)
        s2.add('s'.code, 200)
        assertEquals("{0..113, 115, 117..200}", s.and(s2).toString())
    }

    @Test
    fun testRmSingleElement() {
        val s = IntervalSet.of(1, 10)
        s.add(-3, -3)
        s.remove(-3)
        assertEquals("{1..10}", s.toString())
    }

    @Test
    fun testRmLeftSide() {
        val s = IntervalSet.of(1, 10)
        s.add(-3, -3)
        s.remove(1)
        assertEquals("{-3, 2..10}", s.toString())
    }

    @Test
    fun testRmRightSide() {
        val s = IntervalSet.of(1, 10)
        s.add(-3, -3)
        s.remove(10)
        assertEquals("{-3, 1..9}", s.toString())
    }

    @Test
    fun testRmMiddleRange() {
        val s = IntervalSet.of(1, 10)
        s.add(-3, -3)
        s.remove(5)
        assertEquals("{-3, 1..4, 6..10}", s.toString())
    }
}

class VocabularyTest {
    @Test
    fun testEmptyVocabulary() {
        assertNotNull(VocabularyImpl.EMPTY_VOCABULARY)
        assertEquals("EOF", VocabularyImpl.EMPTY_VOCABULARY.getSymbolicName(Token.EOF))
        assertEquals("0", VocabularyImpl.EMPTY_VOCABULARY.getDisplayName(Token.INVALID_TYPE))
    }

    @Test
    fun testVocabularyFromTokenNames() {
        val tokenNames =
            listOf(
                "<INVALID>",
                "TOKEN_REF",
                "RULE_REF",
                "'//'",
                "'/'",
                "'*'",
                "'!'",
                "ID",
                "STRING",
            )
        val vocabulary = VocabularyImpl.fromTokenNames(tokenNames)
        assertNotNull(vocabulary)
        assertEquals("EOF", vocabulary.getSymbolicName(Token.EOF))
        for (i in tokenNames.indices) {
            assertEquals(tokenNames[i], vocabulary.getDisplayName(i))
            if (tokenNames[i].startsWith("'")) {
                assertEquals(tokenNames[i], vocabulary.getLiteralName(i))
                assertNull(vocabulary.getSymbolicName(i))
            } else if (tokenNames[i][0].isUpperCase()) {
                assertNull(vocabulary.getLiteralName(i))
                assertEquals(tokenNames[i], vocabulary.getSymbolicName(i))
            } else {
                assertNull(vocabulary.getLiteralName(i))
                assertNull(vocabulary.getSymbolicName(i))
            }
        }
    }
}

open class BufferedTokenStreamTest {
    protected open fun createTokenStream(src: TokenSource): TokenStream = BufferedTokenStream(src)

    @Test
    fun testFirstToken() {
        val g = LexerGrammar(SAMPLE_LEXER_GRAMMAR)
        val lexEngine = g.createLexerInterpreter(ANTLRInputStream("x = 3 * 0 + 2 * 0;"))
        val tokens = createTokenStream(lexEngine)
        assertEquals("x", tokens.LT(1)?.text)
    }

    @Test
    open fun test2ndToken() {
        val g = LexerGrammar(SAMPLE_LEXER_GRAMMAR)
        val lexEngine = g.createLexerInterpreter(ANTLRInputStream("x = 3 * 0 + 2 * 0;"))
        val tokens = createTokenStream(lexEngine)
        assertEquals(" ", tokens.LT(2)?.text)
    }

    @Test
    fun testCompleteBuffer() {
        val g = LexerGrammar(SAMPLE_LEXER_GRAMMAR)
        val lexEngine = g.createLexerInterpreter(ANTLRInputStream("x = 3 * 0 + 2 * 0;"))
        val tokens = createTokenStream(lexEngine)

        var i = 1
        var tok = requireNotNull(tokens.LT(i))
        while (tok.type != Token.EOF) {
            i++
            tok = requireNotNull(tokens.LT(i))
        }
        tokens.LT(i++)
        tokens.LT(i)

        assertEquals("x = 3 * 0 + 2 * 0;", tokens.text)
    }

    @Test
    fun testCompleteBufferAfterConsuming() {
        val g = LexerGrammar(SAMPLE_LEXER_GRAMMAR)
        val lexEngine = g.createLexerInterpreter(ANTLRInputStream("x = 3 * 0 + 2 * 0;"))
        val tokens = createTokenStream(lexEngine)

        var tok = requireNotNull(tokens.LT(1))
        while (tok.type != Token.EOF) {
            tokens.consume()
            tok = requireNotNull(tokens.LT(1))
        }

        assertEquals("x = 3 * 0 + 2 * 0;", tokens.text)
    }

    @Test
    open fun testLookback() {
        val g = LexerGrammar(SAMPLE_LEXER_GRAMMAR)
        val lexEngine = g.createLexerInterpreter(ANTLRInputStream("x = 3 * 0 + 2 * 0;"))
        val tokens = createTokenStream(lexEngine)

        tokens.consume()
        assertEquals("x", tokens.LT(-1)?.text)

        tokens.consume()
        tokens.consume()
        assertEquals("x", tokens.LT(-3)?.text)
        assertEquals(" ", tokens.LT(-2)?.text)
        assertEquals("=", tokens.LT(-1)?.text)
    }

    companion object {
        internal const val SAMPLE_LEXER_GRAMMAR =
            "lexer grammar t;\n" +
                "ID : 'a'..'z'+;\n" +
                "INT : '0'..'9'+;\n" +
                "SEMI : ';';\n" +
                "ASSIGN : '=';\n" +
                "PLUS : '+';\n" +
                "MULT : '*';\n" +
                "WS : ' '+;\n"
    }
}

class CommonTokenStreamTest : BufferedTokenStreamTest() {
    override fun createTokenStream(src: TokenSource): TokenStream = CommonTokenStream(src)

    @Test
    fun testOffChannel() {
        val lexer =
            arrayTokenSource(
                listOf(
                    hiddenToken(" "),
                    CommonToken(1, "x"),
                    hiddenToken(" "),
                    CommonToken(1, "="),
                    CommonToken(1, "34"),
                    hiddenToken(" "),
                    hiddenToken(" "),
                    CommonToken(1, ";"),
                    hiddenToken("\n"),
                    CommonToken(Token.EOF, ""),
                ),
            )
        val tokens = CommonTokenStream(lexer)

        assertEquals("x", tokens.LT(1)?.text)
        tokens.consume()
        assertEquals("=", tokens.LT(1)?.text)
        assertEquals("x", tokens.LT(-1)?.text)

        tokens.consume()
        assertEquals("34", tokens.LT(1)?.text)
        assertEquals("=", tokens.LT(-1)?.text)

        tokens.consume()
        assertEquals(";", tokens.LT(1)?.text)
        assertEquals("34", tokens.LT(-1)?.text)

        tokens.consume()
        assertEquals(Token.EOF, tokens.LA(1))
        assertEquals(";", tokens.LT(-1)?.text)

        assertEquals("34", tokens.LT(-2)?.text)
        assertEquals("=", tokens.LT(-3)?.text)
        assertEquals("x", tokens.LT(-4)?.text)
    }

    @Test
    fun testFetchOffChannel() {
        val lexer =
            arrayTokenSource(
                listOf(
                    hiddenToken(" "),
                    CommonToken(1, "x"),
                    hiddenToken(" "),
                    CommonToken(1, "="),
                    CommonToken(1, "34"),
                    hiddenToken(" "),
                    hiddenToken(" "),
                    CommonToken(1, ";"),
                    hiddenToken(" "),
                    hiddenToken("\n"),
                    CommonToken(Token.EOF, ""),
                ),
            )
        val tokens = CommonTokenStream(lexer)
        tokens.fill()

        assertEquals(emptyList(), tokens.getHiddenTokensToLeft(0))
        assertEquals(emptyList(), tokens.getHiddenTokensToRight(0))

        assertEquals("[[@0,0:0=' ',<1>,channel=1,0:-1]]", tokens.getHiddenTokensToLeft(1).toString())
        assertEquals("[[@2,0:0=' ',<1>,channel=1,0:-1]]", tokens.getHiddenTokensToRight(1).toString())

        assertEquals(emptyList(), tokens.getHiddenTokensToLeft(2))
        assertEquals(emptyList(), tokens.getHiddenTokensToRight(2))

        assertEquals("[[@2,0:0=' ',<1>,channel=1,0:-1]]", tokens.getHiddenTokensToLeft(3).toString())
        assertEquals(emptyList(), tokens.getHiddenTokensToRight(3))

        assertEquals(emptyList(), tokens.getHiddenTokensToLeft(4))
        assertEquals(
            "[[@5,0:0=' ',<1>,channel=1,0:-1], [@6,0:0=' ',<1>,channel=1,0:-1]]",
            tokens.getHiddenTokensToRight(4).toString(),
        )

        assertEquals(emptyList(), tokens.getHiddenTokensToLeft(5))
        assertEquals("[[@6,0:0=' ',<1>,channel=1,0:-1]]", tokens.getHiddenTokensToRight(5).toString())

        assertEquals("[[@5,0:0=' ',<1>,channel=1,0:-1]]", tokens.getHiddenTokensToLeft(6).toString())
        assertEquals(emptyList(), tokens.getHiddenTokensToRight(6))

        assertEquals(
            "[[@5,0:0=' ',<1>,channel=1,0:-1], [@6,0:0=' ',<1>,channel=1,0:-1]]",
            tokens.getHiddenTokensToLeft(7).toString(),
        )
        assertEquals(
            "[[@8,0:0=' ',<1>,channel=1,0:-1], [@9,0:0='\\n',<1>,channel=1,0:-1]]",
            tokens.getHiddenTokensToRight(7).toString(),
        )

        assertEquals(emptyList(), tokens.getHiddenTokensToLeft(8))
        assertEquals("[[@9,0:0='\\n',<1>,channel=1,0:-1]]", tokens.getHiddenTokensToRight(8).toString())

        assertEquals("[[@8,0:0=' ',<1>,channel=1,0:-1]]", tokens.getHiddenTokensToLeft(9).toString())
        assertEquals(emptyList(), tokens.getHiddenTokensToRight(9))
    }

    @Test
    fun testSingleEOF() {
        val lexer = eofOnlyTokenSource()
        val tokens = CommonTokenStream(lexer)
        tokens.fill()

        assertEquals(Token.EOF, tokens.LA(1))
        assertEquals(0, tokens.index())
        assertEquals(1, tokens.size())
    }

    @Test
    fun testCannotConsumeEOF() {
        val lexer = eofOnlyTokenSource()
        val tokens = CommonTokenStream(lexer)
        tokens.fill()

        assertEquals(Token.EOF, tokens.LA(1))
        assertEquals(0, tokens.index())
        assertEquals(1, tokens.size())
        assertFailsWith<IllegalStateException> { tokens.consume() }
    }

    @Test
    fun testBufferedTokenStreamReuseAfterFill() {
        val g = LexerGrammar("lexer grammar VisitorBasic;\nA : 'A';\n")
        val tokenStream = BufferedTokenStream(g.createLexerInterpreter(ANTLRInputStream("A")))
        tokenStream.fill()
        assertEquals(2, tokenStream.size())
        assertEquals(1, tokenStream.get(0).type)
        assertEquals(Token.EOF, tokenStream.get(1).type)

        tokenStream.resetTokenSource(g.createLexerInterpreter(ANTLRInputStream("AA")))
        tokenStream.fill()
        assertEquals(3, tokenStream.size())
        assertEquals(1, tokenStream.get(0).type)
        assertEquals(1, tokenStream.get(1).type)
        assertEquals(Token.EOF, tokenStream.get(2).type)
    }

    private fun hiddenToken(text: String): CommonToken =
        CommonToken(1, text).also { it.channel = Lexer.HIDDEN }

    private fun arrayTokenSource(items: List<Token>): TokenSource =
        object : TokenSource {
            private var idx = 0

            override fun nextToken(): Token = items[idx++]

            override val line: Int = 0
            override val charPositionInLine: Int = 0
            override val inputStream: CharStream? = null
            override val sourceName: String = "test"

            override fun setTokenFactory(factory: TokenFactory<*>?) {}

            override val tokenFactory: TokenFactory<*>? = null
        }

    private fun eofOnlyTokenSource(): TokenSource =
        object : TokenSource {
            override fun nextToken(): Token = CommonToken(Token.EOF)

            override val line: Int = 0
            override val charPositionInLine: Int = 0
            override val inputStream: CharStream? = null
            override val sourceName: String = IntStream.UNKNOWN_SOURCE_NAME

            override fun setTokenFactory(factory: TokenFactory<*>?): Unit =
                throw UnsupportedOperationException("Not supported yet.")

            override val tokenFactory: TokenFactory<*>?
                get() = throw UnsupportedOperationException("Not supported yet.")
        }
}

class UnbufferedCharStreamTest {
    @Test
    fun testNoChar() {
        val input = createStream("")
        assertEquals(IntStream.EOF, input.LA(1))
        assertEquals(IntStream.EOF, input.LA(2))
    }

    @Test
    fun testConsumeEOF() {
        val input = createStream("")
        assertEquals(IntStream.EOF, input.LA(1))
        assertFailsWith<IllegalStateException> { input.consume() }
    }

    @Test
    fun testNegativeSeek() {
        val input = createStream("")
        assertFailsWith<IllegalArgumentException> { input.seek(-1) }
    }

    @Test
    fun testSeekPastEOF() {
        val input = createStream("")
        assertEquals(0, input.index())
        input.seek(1)
        assertEquals(0, input.index())
    }

    @Test
    fun testMarkReleaseOutOfOrder() {
        val input = createStream("")
        val m1 = input.mark()
        input.mark()
        assertFailsWith<IllegalStateException> { input.release(m1) }
    }

    @Test
    fun testMarkReleasedTwice() {
        val input = createStream("")
        val m1 = input.mark()
        input.release(m1)
        assertFailsWith<IllegalStateException> { input.release(m1) }
    }

    @Test
    fun testNestedMarkReleasedTwice() {
        val input = createStream("")
        input.mark()
        val m2 = input.mark()
        input.release(m2)
        assertFailsWith<IllegalStateException> { input.release(m2) }
    }

    @Test
    fun testMarkPassedToSeek() {
        val input = createStream("")
        val m1 = input.mark()
        assertFailsWith<IllegalArgumentException> { input.seek(m1) }
    }

    @Test
    fun testSeekBeforeBufferStart() {
        val input = createStream("xyz")
        input.consume()
        input.mark()
        assertEquals(1, input.index())
        input.consume()
        assertFailsWith<IllegalArgumentException> { input.seek(0) }
    }

    @Test
    fun testGetTextBeforeBufferStart() {
        val input = createStream("xyz")
        input.consume()
        input.mark()
        assertEquals(1, input.index())
        assertFailsWith<UnsupportedOperationException> { input.getText(Interval(0, 1)) }
    }

    @Test
    fun testGetTextInMarkedRange() {
        val input = createStream("xyz")
        input.consume()
        input.mark()
        assertEquals(1, input.index())
        input.consume()
        input.consume()
        assertEquals("yz", input.getText(Interval(1, 2)))
    }

    @Test
    fun testLastChar() {
        val input = createStream("abcdef")

        input.consume()
        assertEquals('a'.code, input.LA(-1))

        val m1 = input.mark()
        input.consume()
        input.consume()
        input.consume()
        assertEquals('d'.code, input.LA(-1))

        input.seek(2)
        assertEquals('b'.code, input.LA(-1))

        input.release(m1)
        input.seek(3)
        assertEquals('c'.code, input.LA(-1))
        input.seek(2)
        assertEquals('b'.code, input.LA(-1))
    }

    @Test
    fun test1Char() {
        val input = createStream("x")
        assertEquals('x'.code, input.LA(1))
        input.consume()
        assertEquals(IntStream.EOF, input.LA(1))
        assertEquals("\uFFFF", input.remainingBuffer)
        assertEquals("\uFFFF", input.buffer)
    }

    @Test
    fun test2Char() {
        val input = createStream("xy")
        assertEquals('x'.code, input.LA(1))
        input.consume()
        assertEquals('y'.code, input.LA(1))
        assertEquals("y", input.remainingBuffer)
        assertEquals("y", input.buffer)
        input.consume()
        assertEquals(IntStream.EOF, input.LA(1))
        assertEquals("\uFFFF", input.buffer)
    }

    @Test
    fun test2CharAhead() {
        val input = createStream("xy")
        assertEquals('x'.code, input.LA(1))
        assertEquals('y'.code, input.LA(2))
        assertEquals(IntStream.EOF, input.LA(3))
    }

    @Test
    fun testBufferExpand() {
        val input = createStream("01234", 2)
        assertEquals('0'.code, input.LA(1))
        assertEquals('1'.code, input.LA(2))
        assertEquals('2'.code, input.LA(3))
        assertEquals('3'.code, input.LA(4))
        assertEquals('4'.code, input.LA(5))
        assertEquals("01234", input.buffer)
        assertEquals(IntStream.EOF, input.LA(6))
    }

    @Test
    fun testBufferWrapSize1() {
        val input = createStream("01234", 1)
        assertEquals('0'.code, input.LA(1))
        input.consume()
        assertEquals('1'.code, input.LA(1))
        input.consume()
        assertEquals('2'.code, input.LA(1))
        input.consume()
        assertEquals('3'.code, input.LA(1))
        input.consume()
        assertEquals('4'.code, input.LA(1))
        input.consume()
        assertEquals(IntStream.EOF, input.LA(1))
    }

    @Test
    fun testBufferWrapSize2() {
        val input = createStream("01234", 2)
        assertEquals('0'.code, input.LA(1))
        input.consume()
        assertEquals('1'.code, input.LA(1))
        input.consume()
        assertEquals('2'.code, input.LA(1))
        input.consume()
        assertEquals('3'.code, input.LA(1))
        input.consume()
        assertEquals('4'.code, input.LA(1))
        input.consume()
        assertEquals(IntStream.EOF, input.LA(1))
    }

    @Test
    fun test1Mark() {
        val input = createStream("xyz")
        val m = input.mark()
        assertEquals('x'.code, input.LA(1))
        assertEquals('y'.code, input.LA(2))
        assertEquals('z'.code, input.LA(3))
        input.release(m)
        assertEquals(IntStream.EOF, input.LA(4))
        assertEquals("xyz\uFFFF", input.buffer)
    }

    @Test
    fun test1MarkWithConsumesInSequence() {
        val input = createStream("xyz")
        val m = input.mark()
        input.consume()
        input.consume()
        input.consume()
        assertEquals(IntStream.EOF, input.LA(1))
        assertEquals("xyz\uFFFF", input.buffer)
        input.release(m)
        assertEquals("\uFFFF", input.buffer)
    }

    @Test
    fun test2Mark() {
        val input = createStream("xyz", 100)
        assertEquals('x'.code, input.LA(1))
        input.consume()
        val m1 = input.mark()
        assertEquals('y'.code, input.LA(1))
        input.consume()
        val m2 = input.mark()
        assertEquals("yz", input.buffer)
        input.release(m2)
        input.consume()
        input.release(m1)
        assertEquals(IntStream.EOF, input.LA(1))
        assertEquals("\uFFFF", input.buffer)
    }

    @Test
    fun testAFewTokens() {
        val g = LexerGrammar(BufferedTokenStreamTest.SAMPLE_LEXER_GRAMMAR)
        val input = createStream("x = 302 * 91 + 20234234 * 0;")
        val lexEngine = g.createLexerInterpreter(input)
        lexEngine.setTokenFactory(CommonTokenFactory(true))
        val tokens = CommonTokenStream(lexEngine)
        assertEquals("x", tokens.LT(1)?.text)
        tokens.fill()
        val expecting =
            "[[@0,0:0='x',<1>,1:0], [@1,1:1=' ',<7>,1:1], [@2,2:2='=',<4>,1:2]," +
                " [@3,3:3=' ',<7>,1:3], [@4,4:6='302',<2>,1:4], [@5,7:7=' ',<7>,1:7]," +
                " [@6,8:8='*',<6>,1:8], [@7,9:9=' ',<7>,1:9], [@8,10:11='91',<2>,1:10]," +
                " [@9,12:12=' ',<7>,1:12], [@10,13:13='+',<5>,1:13], [@11,14:14=' ',<7>,1:14]," +
                " [@12,15:22='20234234',<2>,1:15], [@13,23:23=' ',<7>,1:23]," +
                " [@14,24:24='*',<6>,1:24], [@15,25:25=' ',<7>,1:25], [@16,26:26='0',<2>,1:26]," +
                " [@17,27:27=';',<3>,1:27], [@18,28:27='',<-1>,1:28]]"
        assertEquals(expecting, tokens.allTokens().toString())
    }

    @Test
    fun testUnicodeSMP() {
        val input = createStream("\uD83C\uDF0E")
        assertEquals(0x1F30E, input.LA(1))
        assertEquals("\uD83C\uDF0E", input.buffer)
        input.consume()
        assertEquals(IntStream.EOF, input.LA(1))
        assertEquals("\uFFFF", input.buffer)
    }

    @Test
    fun testDanglingHighSurrogateAtEOFThrows() {
        val danglingHigh = charArrayOf(0xD83C.toChar()).concatToString()
        assertFailsWith<RuntimeException> { createStream(danglingHigh) }
    }

    @Test
    fun testDanglingHighSurrogateThrows() {
        val danglingHigh = charArrayOf(0xD83C.toChar(), 0x0123.toChar()).concatToString()
        assertFailsWith<RuntimeException> { createStream(danglingHigh) }
    }

    @Test
    fun testDanglingLowSurrogateThrows() {
        val danglingLow = charArrayOf(0xDF0E.toChar()).concatToString()
        assertFailsWith<RuntimeException> { createStream(danglingLow) }
    }

    private fun createStream(
        text: String,
        bufferSize: Int = 256,
    ): TestingUnbufferedCharStream = TestingUnbufferedCharStream(text, bufferSize)

    private class TestingUnbufferedCharStream(
        private val sourceText: String,
        bufferSize: Int = 256,
    ) : UnbufferedCharStream(bufferSize) {
        private var cursor: Int = 0

        init {
            fill(1)
        }

        override fun nextChar(): Int =
            if (cursor < sourceText.length) sourceText[cursor++].code else IntStream.EOF

        val remainingBuffer: String
            get() {
                if (n == 0) return ""
                val len = n
                return if (data[len - 1] == IntStream.EOF) {
                    codePointsToString(data, p, len - p - 1) + "\uFFFF"
                } else {
                    codePointsToString(data, p, len - p)
                }
            }

        val buffer: String
            get() {
                if (n == 0) return ""
                val len = n
                return if (data[len - 1] == IntStream.EOF) {
                    codePointsToString(data, 0, len - 1) + "\uFFFF"
                } else {
                    codePointsToString(data, 0, len)
                }
            }

        private fun codePointsToString(
            codePoints: IntArray,
            offset: Int,
            count: Int,
        ): String {
            val sb = StringBuilder(count)
            for (i in 0..<count) {
                val cp = codePoints[offset + i]
                if (cp < 0x10000) {
                    sb.append(cp.toChar())
                } else {
                    val u = cp - 0x10000
                    sb.append((0xD800 + (u ushr 10)).toChar())
                    sb.append((0xDC00 + (u and 0x3FF)).toChar())
                }
            }
            return sb.toString()
        }
    }
}

class UnbufferedTokenStreamTest {
    @Test
    fun testLookahead() {
        val g = LexerGrammar(BufferedTokenStreamTest.SAMPLE_LEXER_GRAMMAR)
        val lexEngine = g.createLexerInterpreter(ANTLRInputStream("x = 302;"))
        val tokens = TestingUnbufferedTokenStream(lexEngine)

        assertEquals("x", tokens.LT(1)?.text)
        assertEquals(" ", tokens.LT(2)?.text)
        assertEquals("=", tokens.LT(3)?.text)
        assertEquals(" ", tokens.LT(4)?.text)
        assertEquals("302", tokens.LT(5)?.text)
        assertEquals(";", tokens.LT(6)?.text)
    }

    @Test
    fun testNoBuffering() {
        val g = LexerGrammar(BufferedTokenStreamTest.SAMPLE_LEXER_GRAMMAR)
        val lexEngine = g.createLexerInterpreter(ANTLRInputStream("x = 302;"))
        val tokens = TestingUnbufferedTokenStream(lexEngine)

        assertEquals("[[@0,0:0='x',<1>,1:0]]", tokens.buffer.toString())
        assertEquals("x", tokens.LT(1)?.text)
        tokens.consume()
        assertEquals(" ", tokens.LT(1)?.text)
        assertEquals("[[@1,1:1=' ',<7>,1:1]]", tokens.remainingBuffer.toString())
        tokens.consume()
        assertEquals("=", tokens.LT(1)?.text)
        assertEquals("[[@2,2:2='=',<4>,1:2]]", tokens.remainingBuffer.toString())
        tokens.consume()
        assertEquals(" ", tokens.LT(1)?.text)
        assertEquals("[[@3,3:3=' ',<7>,1:3]]", tokens.remainingBuffer.toString())
        tokens.consume()
        assertEquals("302", tokens.LT(1)?.text)
        assertEquals("[[@4,4:6='302',<2>,1:4]]", tokens.remainingBuffer.toString())
        tokens.consume()
        assertEquals(";", tokens.LT(1)?.text)
        assertEquals("[[@5,7:7=';',<3>,1:7]]", tokens.remainingBuffer.toString())
    }

    @Test
    fun testMarkStart() {
        val g = LexerGrammar(BufferedTokenStreamTest.SAMPLE_LEXER_GRAMMAR)
        val lexEngine = g.createLexerInterpreter(ANTLRInputStream("x = 302;"))
        val tokens = TestingUnbufferedTokenStream(lexEngine)

        tokens.mark()
        assertEquals("[[@0,0:0='x',<1>,1:0]]", tokens.buffer.toString())
        assertEquals("x", tokens.LT(1)?.text)
        tokens.consume()
        assertEquals("[[@0,0:0='x',<1>,1:0], [@1,1:1=' ',<7>,1:1]]", tokens.buffer.toString())
        tokens.consume()
        tokens.consume()
        tokens.consume()
        tokens.consume()
        tokens.consume()
        assertEquals(
            "[[@0,0:0='x',<1>,1:0], [@1,1:1=' ',<7>,1:1]," +
                " [@2,2:2='=',<4>,1:2], [@3,3:3=' ',<7>,1:3]," +
                " [@4,4:6='302',<2>,1:4], [@5,7:7=';',<3>,1:7]," +
                " [@6,8:7='<EOF>',<-1>,1:8]]",
            tokens.buffer.toString(),
        )
    }

    @Test
    fun testMarkThenRelease() {
        val g = LexerGrammar(BufferedTokenStreamTest.SAMPLE_LEXER_GRAMMAR)
        val lexEngine = g.createLexerInterpreter(ANTLRInputStream("x = 302 + 1;"))
        val tokens = TestingUnbufferedTokenStream(lexEngine)

        var m = tokens.mark()
        assertEquals("[[@0,0:0='x',<1>,1:0]]", tokens.buffer.toString())
        assertEquals("x", tokens.LT(1)?.text)
        tokens.consume()
        assertEquals("[[@0,0:0='x',<1>,1:0], [@1,1:1=' ',<7>,1:1]]", tokens.buffer.toString())
        tokens.consume()
        tokens.consume()
        tokens.consume()
        assertEquals("302", tokens.LT(1)?.text)
        tokens.release(m)
        tokens.consume()
        tokens.consume()
        m = tokens.mark()
        assertEquals("+", tokens.LT(1)?.text)
        tokens.consume()
        tokens.consume()
        tokens.consume()
        tokens.consume()
        assertEquals("<EOF>", tokens.LT(1)?.text)
        assertEquals(
            "[[@6,8:8='+',<5>,1:8], [@7,9:9=' ',<7>,1:9]," +
                " [@8,10:10='1',<2>,1:10], [@9,11:11=';',<3>,1:11]," +
                " [@10,12:11='<EOF>',<-1>,1:12]]",
            tokens.buffer.toString(),
        )
        tokens.release(m)
    }

    private class TestingUnbufferedTokenStream(
        tokenSource: TokenSource,
    ) : UnbufferedTokenStream<Token>(tokenSource) {
        val remainingBuffer: List<Token?>
            get() = if (n == 0) emptyList() else tokens.asList().subList(p, n)

        val buffer: List<Token?>
            get() = if (n == 0) emptyList() else tokens.asList().subList(0, n)
    }
}

class TokenStreamRewriterTest {
    @Test
    fun testInsertBeforeIndex0() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(0, "0")
        assertEquals("0abc", tokens.text)
    }

    @Test
    fun testInsertAfterLastIndex() {
        val tokens = createABCRewriter("abc")
        tokens.insertAfter(2, "x")
        assertEquals("abcx", tokens.text)
    }

    @Test
    fun test2InsertBeforeAfterMiddleIndex() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(1, "x")
        tokens.insertAfter(1, "x")
        assertEquals("axbxc", tokens.text)
    }

    @Test
    fun testReplaceIndex0() {
        val tokens = createABCRewriter("abc")
        tokens.replace(0, "x")
        assertEquals("xbc", tokens.text)
    }

    @Test
    fun testReplaceLastIndex() {
        val tokens = createABCRewriter("abc")
        tokens.replace(2, "x")
        assertEquals("abx", tokens.text)
    }

    @Test
    fun testReplaceMiddleIndex() {
        val tokens = createABCRewriter("abc")
        tokens.replace(1, "x")
        assertEquals("axc", tokens.text)
    }

    @Test
    fun testToStringStartStop() {
        val g =
            LexerGrammar(
                "lexer grammar T;\n" +
                    "ID : 'a'..'z'+;\n" +
                    "INT : '0'..'9'+;\n" +
                    "SEMI : ';';\n" +
                    "MUL : '*';\n" +
                    "ASSIGN : '=';\n" +
                    "WS : ' '+;\n",
            )
        val stream = CommonTokenStream(g.createLexerInterpreter(ANTLRInputStream("x = 3 * 0;")))
        stream.fill()
        val tokens = TokenStreamRewriter(stream)
        tokens.replace(4, 8, "0")
        stream.fill()

        assertEquals("x = 3 * 0;", tokens.tokenStream.text)
        assertEquals("x = 0;", tokens.text)
        assertEquals("x = 0;", tokens.getText(Interval.of(0, 9)))
        assertEquals("0", tokens.getText(Interval.of(4, 8)))
    }

    @Test
    fun testToStringStartStop2() {
        val g = LexerGrammar(BufferedTokenStreamTest.SAMPLE_LEXER_GRAMMAR)
        val stream = CommonTokenStream(g.createLexerInterpreter(ANTLRInputStream("x = 3 * 0 + 2 * 0;")))
        stream.fill()
        val tokens = TokenStreamRewriter(stream)

        assertEquals("x = 3 * 0 + 2 * 0;", tokens.tokenStream.text)

        tokens.replace(4, 8, "0")
        stream.fill()
        assertEquals("x = 0 + 2 * 0;", tokens.text)
        assertEquals("x = 0 + 2 * 0;", tokens.getText(Interval.of(0, 17)))
        assertEquals("0", tokens.getText(Interval.of(4, 8)))
        assertEquals("x = 0", tokens.getText(Interval.of(0, 8)))
        assertEquals("2 * 0", tokens.getText(Interval.of(12, 16)))

        tokens.insertAfter(17, "// comment")
        assertEquals("2 * 0;// comment", tokens.getText(Interval.of(12, 18)))
        assertEquals("x = 0", tokens.getText(Interval.of(0, 8)))
    }

    @Test
    fun test2ReplaceMiddleIndex() {
        val tokens = createABCRewriter("abc")
        tokens.replace(1, "x")
        tokens.replace(1, "y")
        assertEquals("ayc", tokens.text)
    }

    @Test
    fun test2ReplaceMiddleIndex1InsertBefore() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(0, "_")
        tokens.replace(1, "x")
        tokens.replace(1, "y")
        assertEquals("_ayc", tokens.text)
    }

    @Test
    fun testReplaceThenDeleteMiddleIndex() {
        val tokens = createABCRewriter("abc")
        tokens.replace(1, "x")
        tokens.delete(1)
        assertEquals("ac", tokens.text)
    }

    @Test
    fun testInsertInPriorReplace() {
        val tokens = createABCRewriter("abc")
        tokens.replace(0, 2, "x")
        tokens.insertBefore(1, "0")
        val exc = assertFailsWith<IllegalArgumentException> { tokens.text }
        assertEquals(
            "insert op <InsertBeforeOp@[@1,1:1='b',<2>,1:1]:\"0\"> within boundaries of previous <ReplaceOp@[@0,0:0='a',<1>,1:0]..[@2,2:2='c',<3>,1:2]:\"x\">",
            exc.message,
        )
    }

    @Test
    fun testInsertThenReplaceSameIndex() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(0, "0")
        tokens.replace(0, "x")
        assertEquals("0xbc", tokens.text)
    }

    @Test
    fun test2InsertMiddleIndex() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(1, "x")
        tokens.insertBefore(1, "y")
        assertEquals("ayxbc", tokens.text)
    }

    @Test
    fun test2InsertThenReplaceIndex0() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(0, "x")
        tokens.insertBefore(0, "y")
        tokens.replace(0, "z")
        assertEquals("yxzbc", tokens.text)
    }

    @Test
    fun testReplaceThenInsertBeforeLastIndex() {
        val tokens = createABCRewriter("abc")
        tokens.replace(2, "x")
        tokens.insertBefore(2, "y")
        assertEquals("abyx", tokens.text)
    }

    @Test
    fun testInsertThenReplaceLastIndex() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(2, "y")
        tokens.replace(2, "x")
        assertEquals("abyx", tokens.text)
    }

    @Test
    fun testReplaceThenInsertAfterLastIndex() {
        val tokens = createABCRewriter("abc")
        tokens.replace(2, "x")
        tokens.insertAfter(2, "y")
        assertEquals("abxy", tokens.text)
    }

    @Test
    fun testReplaceRangeThenInsertAtLeftEdge() {
        val tokens = createABCRewriter("abcccba")
        tokens.replace(2, 4, "x")
        tokens.insertBefore(2, "y")
        assertEquals("abyxba", tokens.text)
    }

    @Test
    fun testReplaceRangeThenInsertAtRightEdge() {
        val tokens = createABCRewriter("abcccba")
        tokens.replace(2, 4, "x")
        tokens.insertBefore(4, "y")
        val exc = assertFailsWith<IllegalArgumentException> { tokens.text }
        assertEquals(
            "insert op <InsertBeforeOp@[@4,4:4='c',<3>,1:4]:\"y\"> within boundaries of previous <ReplaceOp@[@2,2:2='c',<3>,1:2]..[@4,4:4='c',<3>,1:4]:\"x\">",
            exc.message,
        )
    }

    @Test
    fun testReplaceRangeThenInsertAfterRightEdge() {
        val tokens = createABCRewriter("abcccba")
        tokens.replace(2, 4, "x")
        tokens.insertAfter(4, "y")
        assertEquals("abxyba", tokens.text)
    }

    @Test
    fun testReplaceAll() {
        val tokens = createABCRewriter("abcccba")
        tokens.replace(0, 6, "x")
        assertEquals("x", tokens.text)
    }

    @Test
    fun testReplaceSubsetThenFetch() {
        val tokens = createABCRewriter("abcccba")
        tokens.replace(2, 4, "xyz")
        assertEquals("abxyzba", tokens.getText(Interval.of(0, 6)))
    }

    @Test
    fun testReplaceThenReplaceSuperset() {
        val tokens = createABCRewriter("abcccba")
        tokens.replace(2, 4, "xyz")
        tokens.replace(3, 5, "foo")
        val exc = assertFailsWith<IllegalArgumentException> { tokens.text }
        assertEquals(
            "replace op boundaries of <ReplaceOp@[@3,3:3='c',<3>,1:3]..[@5,5:5='b',<2>,1:5]:\"foo\"> overlap with previous <ReplaceOp@[@2,2:2='c',<3>,1:2]..[@4,4:4='c',<3>,1:4]:\"xyz\">",
            exc.message,
        )
    }

    @Test
    fun testReplaceThenReplaceLowerIndexedSuperset() {
        val tokens = createABCRewriter("abcccba")
        tokens.replace(2, 4, "xyz")
        tokens.replace(1, 3, "foo")
        val exc = assertFailsWith<IllegalArgumentException> { tokens.text }
        assertEquals(
            "replace op boundaries of <ReplaceOp@[@1,1:1='b',<2>,1:1]..[@3,3:3='c',<3>,1:3]:\"foo\"> overlap with previous <ReplaceOp@[@2,2:2='c',<3>,1:2]..[@4,4:4='c',<3>,1:4]:\"xyz\">",
            exc.message,
        )
    }

    @Test
    fun testReplaceSingleMiddleThenOverlappingSuperset() {
        val tokens = createABCRewriter("abcba")
        tokens.replace(2, 2, "xyz")
        tokens.replace(0, 3, "foo")
        assertEquals("fooa", tokens.text)
    }

    @Test
    fun testCombineInserts() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(0, "x")
        tokens.insertBefore(0, "y")
        assertEquals("yxabc", tokens.text)
    }

    @Test
    fun testCombine3Inserts() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(1, "x")
        tokens.insertBefore(0, "y")
        tokens.insertBefore(1, "z")
        assertEquals("yazxbc", tokens.text)
    }

    @Test
    fun testCombineInsertOnLeftWithReplace() {
        val tokens = createABCRewriter("abc")
        tokens.replace(0, 2, "foo")
        tokens.insertBefore(0, "z")
        assertEquals("zfoo", tokens.text)
    }

    @Test
    fun testCombineInsertOnLeftWithDelete() {
        val tokens = createABCRewriter("abc")
        tokens.delete(0, 2)
        tokens.insertBefore(0, "z")
        assertEquals("z", tokens.text)
    }

    @Test
    fun testDisjointInserts() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(1, "x")
        tokens.insertBefore(2, "y")
        tokens.insertBefore(0, "z")
        assertEquals("zaxbyc", tokens.text)
    }

    @Test
    fun testOverlappingReplace() {
        val tokens = createABCRewriter("abcc")
        tokens.replace(1, 2, "foo")
        tokens.replace(0, 3, "bar")
        assertEquals("bar", tokens.text)
    }

    @Test
    fun testOverlappingReplace2() {
        val tokens = createABCRewriter("abcc")
        tokens.replace(0, 3, "bar")
        tokens.replace(1, 2, "foo")
        val exc = assertFailsWith<IllegalArgumentException> { tokens.text }
        assertEquals(
            "replace op boundaries of <ReplaceOp@[@1,1:1='b',<2>,1:1]..[@2,2:2='c',<3>,1:2]:\"foo\"> overlap with previous <ReplaceOp@[@0,0:0='a',<1>,1:0]..[@3,3:3='c',<3>,1:3]:\"bar\">",
            exc.message,
        )
    }

    @Test
    fun testOverlappingReplace3() {
        val tokens = createABCRewriter("abcc")
        tokens.replace(1, 2, "foo")
        tokens.replace(0, 2, "bar")
        assertEquals("barc", tokens.text)
    }

    @Test
    fun testOverlappingReplace4() {
        val tokens = createABCRewriter("abcc")
        tokens.replace(1, 2, "foo")
        tokens.replace(1, 3, "bar")
        assertEquals("abar", tokens.text)
    }

    @Test
    fun testDropIdenticalReplace() {
        val tokens = createABCRewriter("abcc")
        tokens.replace(1, 2, "foo")
        tokens.replace(1, 2, "foo")
        assertEquals("afooc", tokens.text)
    }

    @Test
    fun testDropPrevCoveredInsert() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(1, "foo")
        tokens.replace(1, 2, "foo")
        assertEquals("afoofoo", tokens.text)
    }

    @Test
    fun testLeaveAloneDisjointInsert() {
        val tokens = createABCRewriter("abcc")
        tokens.insertBefore(1, "x")
        tokens.replace(2, 3, "foo")
        assertEquals("axbfoo", tokens.text)
    }

    @Test
    fun testLeaveAloneDisjointInsert2() {
        val tokens = createABCRewriter("abcc")
        tokens.replace(2, 3, "foo")
        tokens.insertBefore(1, "x")
        assertEquals("axbfoo", tokens.text)
    }

    @Test
    fun testInsertBeforeTokenThenDeleteThatToken() {
        val tokens = createABCRewriter("abc")
        tokens.insertBefore(2, "y")
        tokens.delete(2)
        assertEquals("aby", tokens.text)
    }

    @Test
    fun testDistinguishBetweenInsertAfterAndInsertBeforeToPreserverOrder() {
        val tokens = createABCRewriter("aa")
        tokens.insertBefore(0, "<b>")
        tokens.insertAfter(0, "</b>")
        tokens.insertBefore(1, "<b>")
        tokens.insertAfter(1, "</b>")
        assertEquals("<b>a</b><b>a</b>", tokens.text)
    }

    @Test
    fun testDistinguishBetweenInsertAfterAndInsertBeforeToPreserverOrder2() {
        val tokens = createABCRewriter("aa")
        tokens.insertBefore(0, "<p>")
        tokens.insertBefore(0, "<b>")
        tokens.insertAfter(0, "</p>")
        tokens.insertAfter(0, "</b>")
        tokens.insertBefore(1, "<b>")
        tokens.insertAfter(1, "</b>")
        assertEquals("<b><p>a</p></b><b>a</b>", tokens.text)
    }

    @Test
    fun testPreservesOrderOfContiguousInserts() {
        val tokens = createABCRewriter("ab")
        tokens.insertBefore(0, "<p>")
        tokens.insertBefore(0, "<b>")
        tokens.insertBefore(0, "<div>")
        tokens.insertAfter(0, "</p>")
        tokens.insertAfter(0, "</b>")
        tokens.insertAfter(0, "</div>")
        tokens.insertBefore(1, "!")
        assertEquals("<div><b><p>a</p></b></div>!b", tokens.text)
    }

    private fun createABCRewriter(input: String): TokenStreamRewriter {
        val g = LexerGrammar("lexer grammar T;\nA : 'a';\nB : 'b';\nC : 'c';\n")
        val stream = CommonTokenStream(g.createLexerInterpreter(ANTLRInputStream(input)))
        stream.fill()
        return TokenStreamRewriter(stream)
    }
}

class InterpreterDataReaderTest {
    @Test
    fun testParseParserAndLexerInterpreterData() {
        val g =
            Grammar(
                "grammar Calc;\n" +
                    "s :  expr EOF\n" +
                    "  ;\n" +
                    "expr\n" +
                    "  :  INT            # number\n" +
                    "  |  expr (MUL | DIV) expr  # multiply\n" +
                    "  |  expr (ADD | SUB) expr  # add\n" +
                    "  ;\n" +
                    "\n" +
                    "INT : [0-9]+;\n" +
                    "MUL : '*';\n" +
                    "DIV : '/';\n" +
                    "ADD : '+';\n" +
                    "SUB : '-';\n" +
                    "WS : [ \\t]+ -> channel(HIDDEN);",
            )
        val interpString = Tool.generateInterpreterData(g)
        val interpreterData = InterpreterDataReader.parse(interpString)
        val vocabulary = assertIs<VocabularyImpl>(interpreterData.vocabulary)
        val parsedAtn = requireNotNull(interpreterData.atn)

        assertEquals(6, vocabulary.maxTokenType)
        assertEquals(listOf("s", "expr"), interpreterData.ruleNames)
        assertEquals(listOf("", "", "'*'", "'/'", "'+'", "'-'", ""), vocabulary.literalNames)
        assertEquals(listOf("", "INT", "MUL", "DIV", "ADD", "SUB", "WS"), vocabulary.symbolicNames)
        assertNull(interpreterData.channels)
        assertNull(interpreterData.modes)

        val serialized = ATNSerializer.getSerialized(parsedAtn)
        assertEquals(ATNDeserializer.SERIALIZED_VERSION, serialized.get(0))

        val implicitLexer = requireNotNull(g.implicitLexer)
        val lexerInterpString = Tool.generateInterpreterData(implicitLexer)
        val lexerData = InterpreterDataReader.parse(lexerInterpString)
        assertEquals(listOf("DEFAULT_TOKEN_CHANNEL", "HIDDEN"), lexerData.channels)
        assertEquals(listOf("DEFAULT_MODE"), lexerData.modes)
        assertNotNull(lexerData.atn)
    }
}

class VisitorsTest {
    @Test
    fun testVisitTerminalNode() {
        val (parser, context) = parseVisitorBasic("A")
        assertEquals("(s A <EOF>)", context.toStringTree(parser))

        val visitor =
            object : AbstractParseTreeVisitor<String>() {
                override fun visitTerminal(node: TerminalNode?): String = "${node?.symbol}\n"

                override fun defaultResult(): String = ""

                override fun aggregateResult(
                    aggregate: String?,
                    nextResult: String?,
                ): String = aggregate.orEmpty() + nextResult.orEmpty()
            }

        val expected = "[@0,0:0='A',<1>,1:0]\n[@1,1:0='<EOF>',<-1>,1:1]\n"
        assertEquals(expected, visitor.visit(context))
    }

    @Test
    fun testVisitErrorNode() {
        val g = Grammar(VISITOR_BASIC_GRAMMAR)
        val lexer = g.createLexerInterpreter(ANTLRInputStream(""))
        val parser = g.createParserInterpreter(CommonTokenStream(lexer))
        val errors = ArrayList<String>()
        parser.removeErrorListeners()
        parser.addErrorListener(
            object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String?,
                    e: RecognitionException?,
                ) {
                    errors.add("line $line:$charPositionInLine $msg")
                }
            },
        )

        val context = requireNotNull(parser.parse(parser.getRuleIndex("s")))
        assertEquals("(s <missing 'A'> <EOF>)", context.toStringTree(parser))
        assertEquals(1, errors.size)
        assertEquals("line 1:0 missing 'A' at '<EOF>'", errors[0])

        val visitor =
            object : AbstractParseTreeVisitor<String>() {
                override fun visitErrorNode(node: ErrorNode?): String = "Error encountered: ${node?.symbol}"

                override fun defaultResult(): String = ""

                override fun aggregateResult(
                    aggregate: String?,
                    nextResult: String?,
                ): String = aggregate.orEmpty() + nextResult.orEmpty()
            }

        assertEquals("Error encountered: [@-1,-1:-1='<missing 'A'>',<1>,1:0]", visitor.visit(context))
    }

    @Test
    fun testShouldNotVisitEOF() {
        val (parser, context) = parseVisitorBasic("A")
        assertEquals("(s A <EOF>)", context.toStringTree(parser))

        val visitor =
            object : AbstractParseTreeVisitor<String>() {
                override fun visitTerminal(node: TerminalNode?): String = "${node?.symbol}\n"

                override fun shouldVisitNextChild(
                    node: RuleNode?,
                    currentResult: String?,
                ): Boolean = currentResult.isNullOrEmpty()
            }

        assertEquals("[@0,0:0='A',<1>,1:0]\n", visitor.visit(context))
    }

    @Test
    fun testShouldNotVisitTerminal() {
        val (parser, context) = parseVisitorBasic("A")
        assertEquals("(s A <EOF>)", context.toStringTree(parser))

        val visitor =
            object : AbstractParseTreeVisitor<String>() {
                override fun visitTerminal(node: TerminalNode?): String =
                    throw RuntimeException("Should not be reachable")

                override fun defaultResult(): String = "default result"

                override fun shouldVisitNextChild(
                    node: RuleNode?,
                    currentResult: String?,
                ): Boolean = false
            }

        assertEquals("default result", visitor.visit(context))
    }

    @Test
    fun testCalculatorVisitor() {
        val g = Grammar(VISITOR_CALC_GRAMMAR)
        val lexer = g.createLexerInterpreter(ANTLRInputStream("2 + 8 / 2"))
        val plainParser = g.createParserInterpreter(CommonTokenStream(lexer))
        val plainContext = requireNotNull(plainParser.parse(plainParser.getRuleIndex("s")))
        assertEquals("(s (expr (expr 2) + (expr (expr 8) / (expr 2))) <EOF>)", plainContext.toStringTree(plainParser))

        val grammarLexer = g.createLexerInterpreter(ANTLRInputStream("2 + 8 / 2"))
        val grammarParser = g.createGrammarParserInterpreter(CommonTokenStream(grammarLexer))
        val labeledContext = requireNotNull(grammarParser.parse(grammarParser.getRuleIndex("s")))

        val calcVisitor =
            object : AbstractParseTreeVisitor<Int>() {
                override fun visitChildren(node: RuleNode?): Int {
                    val ctx = assertIs<GrammarInterpreterRuleContext>(node)
                    return when (ctx.ruleIndex) {
                        0 -> requireNotNull(visit(ctx.getChild(0)))
                        1 ->
                            when (ctx.outerAltNum) {
                                1 -> requireNotNull(ctx.getChild(0)?.text).toInt()
                                2 -> {
                                    val left = requireNotNull(visit(ctx.getChild(0)))
                                    val right = requireNotNull(visit(ctx.getChild(2)))
                                    if (ctx.getChild(1)?.text == "*") left * right else left / right
                                }
                                3 -> {
                                    val left = requireNotNull(visit(ctx.getChild(0)))
                                    val right = requireNotNull(visit(ctx.getChild(2)))
                                    if (ctx.getChild(1)?.text == "+") left + right else left - right
                                }
                                else -> throw IllegalStateException("Unexpected outerAltNum ${ctx.outerAltNum}")
                            }
                        else -> throw IllegalStateException("Unexpected ruleIndex ${ctx.ruleIndex}")
                    }
                }

                override fun defaultResult(): Int = throw RuntimeException("Should not be reachable")

                override fun aggregateResult(
                    aggregate: Int?,
                    nextResult: Int?,
                ): Int = throw RuntimeException("Should not be reachable")
            }

        assertEquals(6, calcVisitor.visit(labeledContext))

        val tool = Tool(listOf("-visitor", "VisitorCalc.g4"))
        tool.writeFilesToDisk = false
        tool.removeListeners()
        tool.registerVirtualGrammar("VisitorCalc.g4", VISITOR_CALC_GRAMMAR)
        tool.processGrammarsOnCommandLine()
        assertEquals(0, tool.numErrors)
        val visitorSource = requireNotNull(tool.virtualOutputs["VisitorCalcVisitor.kt"])
        assertTrue(visitorSource.contains("fun visitNumber(ctx: VisitorCalcParser.NumberContext): T?"))
        assertTrue(visitorSource.contains("fun visitMultiply(ctx: VisitorCalcParser.MultiplyContext): T?"))
        assertTrue(visitorSource.contains("fun visitAdd(ctx: VisitorCalcParser.AddContext): T?"))
    }

    private fun parseVisitorBasic(input: String): Pair<ParserInterpreter, ParserRuleContext> {
        val g = Grammar(VISITOR_BASIC_GRAMMAR)
        val lexer = g.createLexerInterpreter(ANTLRInputStream(input))
        val parser = g.createParserInterpreter(CommonTokenStream(lexer))
        val context = requireNotNull(parser.parse(parser.getRuleIndex("s")))
        return Pair(parser, context)
    }

    companion object {
        private const val VISITOR_BASIC_GRAMMAR =
            "grammar VisitorBasic;\n" +
                "s : 'A' EOF ;\n"

        private const val VISITOR_CALC_GRAMMAR =
            "grammar VisitorCalc;\n" +
                "s : expr EOF ;\n" +
                "expr\n" +
                "    : INT                     # number\n" +
                "    | expr (MUL | DIV) expr   # multiply\n" +
                "    | expr (ADD | SUB) expr   # add\n" +
                "    ;\n" +
                "INT : [0-9]+ ;\n" +
                "MUL : '*' ;\n" +
                "DIV : '/' ;\n" +
                "ADD : '+' ;\n" +
                "SUB : '-' ;\n" +
                "WS : [ \\t]+ -> skip ;\n"
    }
}

// TestFastQueue in upstream tool-testsuite tests ANTLR3 legacy FastQueue, which does not exist in pure ANTLR4 KMP.
// TestPerformance in upstream tool-testsuite is an @Disabled manual JDK_SOURCE_ROOT Java-compiler throughput benchmark, not an automated unit test.
