/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.ListTokenSource
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.analysis.LeftRecursiveRuleAltInfo
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.ANTLRMessage
import io.github.kotlinmania.antlr4.tooling.tool.Attribute
import io.github.kotlinmania.antlr4.tooling.tool.AttributeDict
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.ErrorTemplateObject
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.GrammarASTOwner
import io.github.kotlinmania.antlr4.tooling.tool.LabelElementPair
import io.github.kotlinmania.antlr4.tooling.tool.LabelType
import io.github.kotlinmania.antlr4.tooling.tool.ToolErrorContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GrammarASTAndErrorManagerTest {
    private class CapturingToolContext : ToolErrorContext {
        override var longMessages: Boolean = false
        override val errMgr: ErrorManager = ErrorManager(this)
        val infos: MutableList<String> = ArrayList()
        val errors: MutableList<String> = ArrayList()
        val warnings: MutableList<String> = ArrayList()

        override fun info(msg: String?) {
            if (msg != null) infos.add(msg)
        }

        override fun error(msg: ANTLRMessage) {
            errors.add(errMgr.formatMessage(msg))
        }

        override fun warning(msg: ANTLRMessage) {
            warnings.add(errMgr.formatMessage(msg))
        }
    }

    private class TestRuleObj(
        override val name: String,
        override val startToken: Token? = null,
    ) : ErrorTemplateObject

    @Test
    fun testGrammarASTTreeOperationsAndFormatting() {
        val stream =
            CommonTokenStream(
                ListTokenSource(
                    listOf(
                        CommonToken(ANTLRParserTypes.GRAMMAR, "grammar").apply { tokenIndex = 0; line = 1; charPositionInLine = 0 },
                        CommonToken(ANTLRParserTypes.TOKEN_REF, "Expr").apply { tokenIndex = 1; line = 1; charPositionInLine = 8 },
                    ),
                ),
            )
        val root = GrammarRootAST(ANTLRParserTypes.GRAMMAR, CommonToken(ANTLRParserTypes.GRAMMAR, "grammar"), "COMBINED_GRAMMAR", stream)
        root.addChild(GrammarAST(ANTLRParserTypes.ID, CommonToken(ANTLRParserTypes.TOKEN_REF, "Expr")))

        val rulesNode = GrammarAST(ANTLRParserTypes.RULES)
        val parserRule = RuleAST(ANTLRParserTypes.RULE)
        parserRule.addChild(GrammarAST(CommonToken(ANTLRParserTypes.RULE_REF, "expr")))
        val block = BlockAST(ANTLRParserTypes.BLOCK)
        val alt1 = AltAST(ANTLRParserTypes.ALT)
        alt1.altLabel = GrammarAST(CommonToken(ANTLRParserTypes.ID, "AddAlt"))
        val ref1 = RuleRefAST(CommonToken(ANTLRParserTypes.RULE_REF, "expr"))
        val plusLit = TerminalAST(CommonToken(ANTLRParserTypes.STRING_LITERAL, "'+'"))
        val ref2 = RuleRefAST(CommonToken(ANTLRParserTypes.RULE_REF, "expr"))
        alt1.addChild(ref1)
        alt1.addChild(plusLit)
        alt1.addChild(ref2)
        block.addChild(alt1)
        parserRule.addChild(block)
        rulesNode.addChild(parserRule)
        root.addChild(rulesNode)

        root.sanityCheckParentAndChildIndexes()
        assertEquals("Expr", root.grammarName)
        assertFalse(parserRule.isLexerRule)
        assertEquals("expr", parserRule.ruleName)
        assertSame(alt1, plusLit.outermostAltNode)
        assertEquals("AddAlt", plusLit.getAltLabel())
        assertEquals(
            "(COMBINED_GRAMMAR Expr (RULES (RULE expr (BLOCK (ALT expr '+' expr)))))",
            root.toStringTree(),
        )

        // Verify LeftRecursiveRuleAltInfo fallback on getAltLabel()
        alt1.altLabel = null
        alt1.leftRecursiveAltInfo = LeftRecursiveRuleAltInfo(1, "expr '+' expr", "e", "LeftRecAdd", false, alt1)
        assertEquals("LeftRecAdd", ref2.getAltLabel())

        // Verify nil child list flattening & replaceChildren
        val nilList = GrammarAST()
        assertTrue(nilList.isNil)
        nilList.addChild(TerminalAST(CommonToken(ANTLRParserTypes.TOKEN_REF, "INT")))
        nilList.addChild(ActionAST(CommonToken(ANTLRParserTypes.ACTION, "{println()}")))
        val alt2 = AltAST(ANTLRParserTypes.ALT)
        alt2.addChild(nilList)
        assertEquals(2, alt2.childCount)
        assertEquals("(ALT INT {println()})", alt2.toStringTree())

        alt2.replaceChildren(0, 0, TerminalAST(CommonToken(ANTLRParserTypes.TOKEN_REF, "ID")))
        alt2.sanityCheckParentAndChildIndexes()
        assertEquals("(ALT ID {println()})", alt2.toStringTree())

        // Verify dupTree & RuleRefAST.dupNode token isolation
        val cloned = root.dupTree()
        cloned.sanityCheckParentAndChildIndexes()
        assertEquals(root.toStringTree(), cloned.toStringTree())
        assertNotSame(root, cloned)
        val clonedRefs = cloned.getNodesWithType(ANTLRParserTypes.RULE_REF)
        assertEquals(3, clonedRefs.size)

        val dfsSet = IntervalSet.of(ANTLRParserTypes.RULE_REF)
        dfsSet.add(ANTLRParserTypes.STRING_LITERAL)
        val dfsTexts = root.getNodesWithTypePreorderDFS(dfsSet).map { it.text }
        assertEquals(listOf("expr", "expr", "'+'", "expr"), dfsTexts)
    }

    @Test
    fun testVisitorDoubleDispatchAndOptionsValidation() {
        val ctx = CapturingToolContext()
        val owner =
            object : GrammarASTOwner {
                override val fileName: String = "Demo.g4"
                override val isLexer: Boolean = true
                override val errorManager: ErrorManager = ctx.errMgr
            }

        val lexerRule = RuleAST(ANTLRParserTypes.RULE)
        lexerRule.g = owner
        lexerRule.addChild(GrammarAST(CommonToken(ANTLRParserTypes.TOKEN_REF, "WS")))
        val blk = BlockAST(ANTLRParserTypes.BLOCK)
        val alt = AltAST(ANTLRParserTypes.ALT)
        alt.addChild(TerminalAST(CommonToken(ANTLRParserTypes.STRING_LITERAL, "' '")))
        val act = ActionAST(CommonToken(ANTLRParserTypes.ACTION, "{skip();}"))
        alt.addChild(act)
        blk.addChild(alt)
        lexerRule.addChild(blk)

        assertTrue(lexerRule.isLexerRule)
        assertSame(act, lexerRule.lexerAction)

        val validOpt = GrammarAST(CommonToken(ANTLRParserTypes.STRING_LITERAL, "'BaseLexer'"))
        lexerRule.setOption("superClass", validOpt)
        assertEquals("BaseLexer", lexerRule.getOptionString("superClass"))

        val badOptTok = CommonToken(ANTLRParserTypes.STRING_LITERAL, "'\\uZZZZ'").apply { line = 4; charPositionInLine = 12 }
        lexerRule.setOption("bad", GrammarAST(badOptTok))
        assertEquals("", lexerRule.getOptionString("bad"))
        assertEquals(1, ctx.errMgr.numErrors)
        assertEquals(
            listOf("error(156): Demo.g4:4:12: invalid escape sequence '\\uZZZZ'"),
            ctx.errors,
        )

        val visited = ArrayList<String>()
        val visitor =
            object : GrammarASTVisitor {
                override fun visit(node: GrammarAST): Any = visited.add("GrammarAST")
                override fun visit(node: GrammarRootAST): Any = visited.add("GrammarRootAST")
                override fun visit(node: RuleAST): Any = visited.add("RuleAST")
                override fun visit(node: BlockAST): Any = visited.add("BlockAST")
                override fun visit(node: OptionalBlockAST): Any = visited.add("OptionalBlockAST")
                override fun visit(node: PlusBlockAST): Any = visited.add("PlusBlockAST")
                override fun visit(node: StarBlockAST): Any = visited.add("StarBlockAST")
                override fun visit(node: AltAST): Any = visited.add("AltAST")
                override fun visit(node: NotAST): Any = visited.add("NotAST")
                override fun visit(node: PredAST): Any = visited.add("PredAST")
                override fun visit(node: RangeAST): Any = visited.add("RangeAST")
                override fun visit(node: SetAST): Any = visited.add("SetAST")
                override fun visit(node: RuleRefAST): Any = visited.add("RuleRefAST")
                override fun visit(node: TerminalAST): Any = visited.add("TerminalAST")
            }

        val optNode = OptionalBlockAST(ANTLRParserTypes.OPTIONAL, CommonToken(ANTLRParserTypes.QUESTION, "?"), null)
        val nonGreedyStar =
            StarBlockAST(
                ANTLRParserTypes.CLOSURE,
                CommonToken(ANTLRParserTypes.STAR, "*"),
                CommonToken(ANTLRParserTypes.QUESTION, "?"),
            )
        val plusNode = PlusBlockAST(ANTLRParserTypes.POSITIVE_CLOSURE, CommonToken(ANTLRParserTypes.PLUS, "+"), null)
        assertTrue(optNode.isGreedy)
        assertFalse(nonGreedyStar.isGreedy)
        assertTrue(plusNode.isGreedy)

        lexerRule.visit(visitor)
        blk.visit(visitor)
        optNode.visit(visitor)
        plusNode.visit(visitor)
        nonGreedyStar.visit(visitor)
        alt.visit(visitor)
        NotAST(ANTLRParserTypes.NOT, CommonToken(ANTLRParserTypes.NOT, "~")).visit(visitor)
        PredAST(CommonToken(ANTLRParserTypes.SEMPRED, "{ok}?")).visit(visitor)
        RangeAST(CommonToken(ANTLRParserTypes.RANGE, "..")).visit(visitor)
        SetAST(ANTLRParserTypes.SET, CommonToken(ANTLRParserTypes.LPAREN, "("), "SET").visit(visitor)
        RuleRefAST(CommonToken(ANTLRParserTypes.RULE_REF, "id")).visit(visitor)
        TerminalAST(CommonToken(ANTLRParserTypes.TOKEN_REF, "ID")).visit(visitor)

        assertEquals(
            listOf(
                "RuleAST",
                "BlockAST",
                "OptionalBlockAST",
                "PlusBlockAST",
                "StarBlockAST",
                "AltAST",
                "NotAST",
                "PredAST",
                "RangeAST",
                "SetAST",
                "RuleRefAST",
                "TerminalAST",
            ),
            visited,
        )
    }

    @Test
    fun testErrorManagerLayoutsDeduplicationAndCycles() {
        val ctx = CapturingToolContext()
        val errMgr = ctx.errMgr

        // No-location tool error (exact 2 spaces after ':' in ANTLR layout)
        errMgr.toolError(ErrorType.CANNOT_CREATE_TARGET_GENERATOR, "Foo")
        assertEquals(
            "error(31):  ANTLR cannot generate Foo code as of version 4.13.2",
            ctx.errors.last(),
        )

        // File-only location (line = -1, charPosition = -1)
        errMgr.grammarError(ErrorType.NO_RULES, "Empty.g4", null, "Empty")
        assertEquals(
            "error(99): Empty.g4::: grammar Empty has no rules",
            ctx.errors.last(),
        )

        // GNU & VS2005 formats
        val tok = CommonToken(ANTLRParserTypes.ID, "x").apply { line = 10; charPositionInLine = 3 }
        errMgr.setFormat("gnu")
        errMgr.syntaxError(ErrorType.SYNTAX_ERROR, "Sample.g4", tok, "unexpected token")
        assertEquals(
            "Sample.g4:10:3: error: syntax error: unexpected token [error 50]",
            ctx.errors.last(),
        )

        errMgr.setFormat("vs2005")
        errMgr.syntaxError(ErrorType.SYNTAX_ERROR, "Sample.g4", tok, "unexpected token")
        assertEquals(
            "Sample.g4(10,3) : error 50 : syntax error: unexpected token",
            ctx.errors.last(),
        )

        // Standard WARNING accumulation + ERROR_ONE_OFF deduplication + multi-segment non-existent path
        errMgr.setFormat("antlr")
        val warningsBefore = errMgr.warnings
        errMgr.grammarError(ErrorType.OPTIONS_IN_DELEGATE, "/virtual/dir/Sub.g4", tok, "Sub")
        errMgr.grammarError(ErrorType.OPTIONS_IN_DELEGATE, "/virtual/dir/Sub.g4", tok, "Sub")
        assertEquals(warningsBefore + 2, errMgr.warnings)
        assertEquals(2, ctx.warnings.size)
        assertEquals(
            "warning(109): /virtual/dir/Sub.g4:10:3: options ignored in imported grammar Sub",
            ctx.warnings.last(),
        )

        val errorsBefore = errMgr.numErrors
        errMgr.toolError(ErrorType.WARNING_TREATED_AS_ERROR)
        errMgr.toolError(ErrorType.WARNING_TREATED_AS_ERROR)
        assertEquals(errorsBefore + 1, errMgr.numErrors)

        // Left-recursion cycles template rendering (single and multiple cycles)
        val startTok = CommonToken(ANTLRParserTypes.RULE_REF, "a").apply { line = 2; charPositionInLine = 0 }
        val cycle = listOf(TestRuleObj("a", startTok), TestRuleObj("b"))
        errMgr.leftRecursionCycles("Cycle.g4", listOf(cycle))
        assertEquals(
            "error(119): Cycle.g4:2:0: The following sets of rules are mutually left-recursive [a, b]",
            ctx.errors.last(),
        )
        val cycle2 = listOf(TestRuleObj("c"), TestRuleObj("d"))
        errMgr.leftRecursionCycles("Cycle.g4", listOf(cycle, cycle2))
        assertEquals(
            "error(119): Cycle.g4:2:0: The following sets of rules are mutually left-recursive [a, b] and [c, d]",
            ctx.errors.last(),
        )
    }

    @Test
    fun testAttributeDictAndLabelElementPair() {
        assertEquals(
            setOf("text", "type", "line", "index", "pos", "channel", "int"),
            AttributeDict.predefinedTokenDict.attributes.keys,
        )

        val args = AttributeDict(AttributeDict.DictType.ARG)
        args.add(Attribute("x").apply { type = "Int" })
        args.add(Attribute("shared").apply { type = "String"; initValue = "\"hi\"" })
        assertEquals("shared:String=\"hi\"", args.get("shared").toString())

        val locals = AttributeDict(AttributeDict.DictType.LOCAL)
        locals.add(Attribute("shared"))
        locals.add(Attribute("y"))
        assertEquals(setOf("shared"), args.intersection(locals))

        val label = GrammarAST(CommonToken(ANTLRParserTypes.ID, "tok"))
        val tokElem = TerminalAST(CommonToken(ANTLRParserTypes.TOKEN_REF, "ID"))
        val strElem = TerminalAST(CommonToken(ANTLRParserTypes.STRING_LITERAL, "'abc'"))
        val ruleElem = RuleRefAST(CommonToken(ANTLRParserTypes.RULE_REF, "expr"))

        assertEquals(
            LabelType.TOKEN_LABEL,
            LabelElementPair(false, label, tokElem, ANTLRParserTypes.ASSIGN).type,
        )
        assertEquals(
            LabelType.TOKEN_LIST_LABEL,
            LabelElementPair(false, label, tokElem, ANTLRParserTypes.PLUS_ASSIGN).type,
        )
        assertEquals(
            LabelType.RULE_LABEL,
            LabelElementPair(false, label, ruleElem, ANTLRParserTypes.ASSIGN).type,
        )
        assertEquals(
            LabelType.RULE_LIST_LABEL,
            LabelElementPair(false, label, ruleElem, ANTLRParserTypes.PLUS_ASSIGN).type,
        )
        assertEquals(
            LabelType.LEXER_STRING_LABEL,
            LabelElementPair(true, label, strElem, ANTLRParserTypes.ASSIGN).type,
        )
    }
}
