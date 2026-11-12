/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.CharStream
import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.Parser
import io.github.kotlinmania.antlr4.ParserRuleContext
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.Vocabulary
import io.github.kotlinmania.antlr4.VocabularyImpl
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNDeserializer
import io.github.kotlinmania.antlr4.atn.ATNSerializer
import io.github.kotlinmania.antlr4.atn.LexerATNSimulator
import io.github.kotlinmania.antlr4.atn.ParserATNSimulator
import io.github.kotlinmania.antlr4.atn.PredictionContextCache
import io.github.kotlinmania.antlr4.dfa.DFA
import io.github.kotlinmania.antlr4.misc.IntegerList
import io.github.kotlinmania.antlr4.tooling.ToolTestSupport
import io.github.kotlinmania.antlr4.tooling.codegen.model.AltBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1OptionalBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1StarBlockSingleAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.OptionalBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject
import io.github.kotlinmania.antlr4.tooling.codegen.model.ParserFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.PlusBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.SerializedJavaATN
import io.github.kotlinmania.antlr4.tooling.codegen.model.StarBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.ThrowNoViableAlt
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import io.github.kotlinmania.antlr4.tree.AbstractParseTreeVisitor
import io.github.kotlinmania.antlr4.tree.ErrorNode
import io.github.kotlinmania.antlr4.tree.ParseTreeListener
import io.github.kotlinmania.antlr4.tree.ParseTreeVisitor
import io.github.kotlinmania.antlr4.tree.ParseTreeWalker
import io.github.kotlinmania.antlr4.tree.TerminalNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeGenPipelineTest {
    @Test
    fun testToolEndToEndCombinedGrammarKotlinTarget() {
        val grammarText =
            """
            grammar Calc;
            prog : stat+ EOF ;
            stat : ID '=' expr ';'   #Assign
                 | expr ';'          #PrintExpr
                 ;
            expr : left=expr op=('*'|'/') right=expr  #MulDiv
                 | left=expr op=('+'|'-') right=expr  #AddSub
                 | INT                                #IntLit
                 | ID                                 #IdRef
                 | '(' expr ')'                       #Parens
                 ;
            MUL : '*' ;
            DIV : '/' ;
            ADD : '+' ;
            SUB : '-' ;
            ID  : [a-zA-Z]+ ;
            INT : [0-9]+ ;
            WS  : [ \t\r\n]+ -> skip ;
            """.trimIndent()

        val tool = Tool(listOf("-visitor", "-listener", "-package", "sample.calc", "Calc.g4"))
        tool.writeFilesToDisk = false
        tool.removeListeners()
        tool.registerVirtualGrammar("Calc.g4", grammarText)
        tool.processGrammarsOnCommandLine()

        assertEquals(0, tool.numErrors)
        val outputs = tool.virtualOutputs
        val expectedFiles =
            listOf(
                "CalcLexer.kt",
                "CalcLexer.tokens",
                "CalcLexer.interp",
                "CalcParser.kt",
                "Calc.tokens",
                "Calc.interp",
                "CalcListener.kt",
                "CalcBaseListener.kt",
                "CalcVisitor.kt",
                "CalcBaseVisitor.kt",
            )
        for (fileName in expectedFiles) {
            assertTrue(outputs.containsKey(fileName), "Expected virtual output $fileName")
        }

        val parserSource = assertNotNull(outputs["CalcParser.kt"])
        val lexerSource = assertNotNull(outputs["CalcLexer.kt"])
        val listenerSource = assertNotNull(outputs["CalcListener.kt"])
        val baseListenerSource = assertNotNull(outputs["CalcBaseListener.kt"])
        val visitorSource = assertNotNull(outputs["CalcVisitor.kt"])
        val baseVisitorSource = assertNotNull(outputs["CalcBaseVisitor.kt"])

        for (src in listOf(parserSource, lexerSource, listenerSource, baseListenerSource, visitorSource, baseVisitorSource)) {
            assertTrue(src.contains("package sample.calc"))
            assertFalse(src.contains("import java."))
            assertFalse(src.contains("import javax."))
            assertFalse(src.contains("org.antlr."))
            assertFalse(hasUnpairedSurrogate(src))
        }

        assertTrue(parserSource.contains("open class CalcParser(input: TokenStream?) : Parser(input)"))
        assertTrue(parserSource.contains("class MulDivContext(ctx: ExprContext) : ExprContext()"))
        assertTrue(parserSource.contains("class AddSubContext(ctx: ExprContext) : ExprContext()"))
        assertTrue(parserSource.contains("class AssignContext(ctx: StatContext) : StatContext()"))
        assertTrue(parserSource.contains("var left: ExprContext? = null"))
        assertTrue(parserSource.contains("var right: ExprContext? = null"))
        assertTrue(parserSource.contains("var op: Token? = null"))
        assertTrue(parserSource.contains("precpred(ctx, 5)"))
        assertTrue(parserSource.contains("precpred(ctx, 4)"))
        assertTrue(parserSource.contains("val nextAltCtx = MulDivContext(ExprContext(parentCtx, parentState))"))
        assertTrue(parserSource.contains("val nextAltCtx = AddSubContext(ExprContext(parentCtx, parentState))"))
        assertTrue(parserSource.contains("val SERIALIZED_ATN: String by lazy {"))
        assertTrue(parserSource.contains("ATNDeserializer().deserialize(SERIALIZED_ATN_INTS)"))
        assertFalse(parserSource.contains("EarlyExitException"))

        assertTrue(lexerSource.contains("open class CalcLexer(input: CharStream?) : Lexer(input)"))
        assertTrue(listenerSource.contains("fun enterMulDiv(ctx: CalcParser.MulDivContext)"))
        assertTrue(baseListenerSource.contains("override fun enterEveryRule(ctx: ParserRuleContext?) {}"))
        assertTrue(visitorSource.contains("fun visitMulDiv(ctx: CalcParser.MulDivContext): T?"))
        assertTrue(baseVisitorSource.contains("override fun visitMulDiv(ctx: CalcParser.MulDivContext): T? = visitChildren(ctx)"))
    }

    @Test
    fun testLexerModesChannelsActionsAndSempredsGeneration() {
        val lexerGrammarText =
            """
            lexer grammar RichLexer;
            channels { COMMENTS }
            COMMENT : '//' ~[\r\n]* -> channel(COMMENTS) ;
            QUOTE   : '"' -> pushMode(STR_MODE), more ;
            NUM     : [0-9]+ { val n = text?.toInt() ?: 0; val s = text.orEmpty(); val ln = line; if (n < 0 || s.isEmpty() || ln < 0) skip() } ;
            BOL_ID  : {charPositionInLine == 0}? [a-z]+ ;
            WS      : [ \t\r\n]+ -> skip ;
            mode STR_MODE;
            STR_END : '"' -> popMode ;
            STR_CH  : ~["\r\n]+ -> more ;
            """.trimIndent()

        val tool = Tool(listOf("RichLexer.g4"))
        tool.writeFilesToDisk = false
        tool.removeListeners()
        tool.registerVirtualGrammar("RichLexer.g4", lexerGrammarText)
        tool.processGrammarsOnCommandLine()

        assertEquals(0, tool.numErrors)
        val lexerSource = assertNotNull(tool.virtualOutputs["RichLexer.kt"])
        assertTrue(lexerSource.contains("object Channels"))
        assertTrue(lexerSource.contains("const val COMMENTS: Int = 2"))
        assertTrue(lexerSource.contains("object Modes"))
        assertTrue(lexerSource.contains("const val STR_MODE: Int = 1"))
        assertTrue(lexerSource.contains("override fun action(localctx: RuleContext?, ruleIndex: Int, actionIndex: Int)"))
        assertTrue(lexerSource.contains("override fun sempred(localctx: RuleContext?, ruleIndex: Int, actionIndex: Int): Boolean"))
        assertTrue(lexerSource.contains("text?.toInt() ?: 0"))
        assertTrue(lexerSource.contains("text.orEmpty()"))
    }

    @Test
    fun testActionTranslationAttributesAndJavaTargetParity() {
        val grammarText =
            """
            grammar AttrDemo;
            outer locals [int depth = 0]
                : items+=inner[2]+ { ${'$'}outer::depth = ${'$'}items.size } EOF
                ;
            inner[int factor] returns [int computed]
                : tok=INT { ${'$'}computed = ${'$'}tok.int * ${'$'}factor + ${'$'}outer::depth; val txt = ${'$'}text; if (txt.isEmpty()) ${'$'}computed = 0 }
                ;
            INT : [0-9]+ ;
            WS  : [ \t\r\n]+ -> skip ;
            """.trimIndent()

        val ktTool = Tool(listOf("AttrDemo.g4"))
        ktTool.writeFilesToDisk = false
        ktTool.removeListeners()
        ktTool.registerVirtualGrammar("AttrDemo.g4", grammarText)
        ktTool.processGrammarsOnCommandLine()
        assertEquals(0, ktTool.numErrors)

        val ktParser = assertNotNull(ktTool.virtualOutputs["AttrDemoParser.kt"])
        assertTrue(ktParser.contains("var depth: Int = 0"))
        assertTrue(ktParser.contains("val items: MutableList<InnerContext> = ArrayList()"))
        assertTrue(ktParser.contains("var factor: Int = 0"))
        assertTrue(ktParser.contains("var computed: Int = 0"))
        assertTrue(ktParser.contains("(getInvokingContext(0) as OuterContext).depth"))
        assertTrue(ktParser.contains("(localCtx.tok?.text?.toInt() ?: 0)"))

        val javaTool = Tool(listOf("-Dlanguage=Java", "AttrDemo.g4"))
        javaTool.writeFilesToDisk = false
        javaTool.removeListeners()
        javaTool.registerVirtualGrammar("AttrDemo.g4", grammarText)
        javaTool.processGrammarsOnCommandLine()
        assertEquals(0, javaTool.numErrors)

        val javaParser = assertNotNull(javaTool.virtualOutputs["AttrDemoParser.java"])
        assertTrue(javaParser.contains("public class AttrDemoParser extends Parser"))
        assertTrue(javaParser.contains("((OuterContext)getInvokingContext(0)).depth"))
        assertTrue(javaParser.contains("public static final String _serializedATN ="))

        val g = Grammar(grammarText)
        val javaGen = CodeGenerator.create(g.tool, g, "Java")
        val factory = ParserFactory(javaGen)
        val controller = OutputModelController(factory)
        factory.controller = controller
        val model: OutputModelObject = controller.buildParserOutputModel(header = false)
        assertTrue(model is ParserFile)
        val serializedModel = assertNotNull(model.parser?.atn as? SerializedJavaATN)
        assertTrue(serializedModel.segments.isNotEmpty())
        assertTrue(serializedModel.serializedAsString.isNotEmpty())
        val rawSerialized = ATNSerializer.getSerialized(assertNotNull(g.atn))
        val words16: IntegerList = ATNDeserializer.encodeIntsWith16BitWords(rawSerialized)
        val encodedChars = CharArray(words16.size()) { i -> words16.get(i).toChar() }
        val roundTripped = ATNDeserializer().deserialize(encodedChars)
        assertEquals(g.atn?.states?.size, roundTripped.states.size)
        assertEquals(rawSerialized.get(0), ATNSerializer.getSerialized(roundTripped).get(0))

        val badWordQueue =
            ToolTestSupport.antlrOnString(
                "grammar BadJava;\noptions { language = Java; }\nreturn : 'x' ;\n",
                "BadJava.g4",
            )
        assertEquals(1, badWordQueue.errors.size)
        assertEquals(ErrorType.USE_OF_BAD_WORD, badWordQueue.errors[0].errorType)
    }

    @Test
    fun testLiveCompiledGeneratedKotlinRecognizerExecution() {
        val miniCalcGrammar =
            """
            grammar MiniCalc;
            prog : stat+ EOF ;
            stat : ID '=' expr ';'   #AssignStat
                 | expr ';'          #ExprStat
                 ;
            expr : left=expr op=('*'|'/') right=expr  #MulDivExpr
                 | left=expr op=('+'|'-') right=expr  #AddSubExpr
                 | INT                                #IntExpr
                 | ID                                 #IdExpr
                 | '(' expr ')'                       #ParenExpr
                 ;
            MUL : '*' ;
            DIV : '/' ;
            ADD : '+' ;
            SUB : '-' ;
            ID  : [a-zA-Z]+ ;
            INT : [0-9]+ ;
            WS  : [ \t\r\n]+ -> skip ;
            """.trimIndent()

        val g = Grammar(miniCalcGrammar)
        val gen = CodeGenerator.create(g.tool, g, "Kotlin")
        val generatedParserCode = gen.generateParser().render()
        assertTrue(generatedParserCode.contains("class MulDivExprContext(ctx: ExprContext) : ExprContext()"))
        assertTrue(generatedParserCode.contains("class AddSubExprContext(ctx: ExprContext) : ExprContext()"))
        assertTrue(generatedParserCode.contains("executeRuleGuarded(localCtx)"))

        val program = "x = 2 + 3 * (4 + 5); y = x * 10 - 5; y / 5;"
        val lexer = CompiledMiniCalcLexer(CharStreams.fromString(program), assertNotNull(g.implicitLexer?.atn))
        val tokens = CommonTokenStream(lexer)
        val parser = CompiledMiniCalcParser(tokens, assertNotNull(g.atn))
        val tree = parser.prog()

        assertNull(tree.exception)
        assertEquals(3, tree.stat().size)

        val env = LinkedHashMap<String, Int>()
        val evalVisitor =
            object : CompiledMiniCalcBaseVisitor<Int>() {
                override fun visitProg(ctx: CompiledMiniCalcParser.ProgContext): Int {
                    var last = 0
                    for (st in ctx.stat()) {
                        last = visit(st) ?: 0
                    }
                    return last
                }

                override fun visitAssignStat(ctx: CompiledMiniCalcParser.AssignStatContext): Int {
                    val name = ctx.ID()?.text.orEmpty()
                    val value = ctx.expr()?.let { visit(it) } ?: 0
                    env[name] = value
                    return value
                }

                override fun visitExprStat(ctx: CompiledMiniCalcParser.ExprStatContext): Int =
                    ctx.expr()?.let { visit(it) } ?: 0

                override fun visitMulDivExpr(ctx: CompiledMiniCalcParser.MulDivExprContext): Int {
                    val lhs = ctx.left?.let { visit(it) } ?: 0
                    val rhs = ctx.right?.let { visit(it) } ?: 0
                    return if (ctx.op?.type == CompiledMiniCalcParser.Tokens.MUL) lhs * rhs else lhs / rhs
                }

                override fun visitAddSubExpr(ctx: CompiledMiniCalcParser.AddSubExprContext): Int {
                    val lhs = ctx.left?.let { visit(it) } ?: 0
                    val rhs = ctx.right?.let { visit(it) } ?: 0
                    return if (ctx.op?.type == CompiledMiniCalcParser.Tokens.ADD) lhs + rhs else lhs - rhs
                }

                override fun visitIntExpr(ctx: CompiledMiniCalcParser.IntExprContext): Int =
                    ctx.INT()?.text?.toInt() ?: 0

                override fun visitIdExpr(ctx: CompiledMiniCalcParser.IdExprContext): Int =
                    env[ctx.ID()?.text.orEmpty()] ?: 0

                override fun visitParenExpr(ctx: CompiledMiniCalcParser.ParenExprContext): Int =
                    ctx.expr()?.let { visit(it) } ?: 0
            }

        val finalResult = evalVisitor.visit(tree)
        assertEquals(29, env["x"])
        assertEquals(285, env["y"])
        assertEquals(57, finalResult)

        val entered = ArrayList<String>()
        val listener =
            object : CompiledMiniCalcBaseListener() {
                override fun enterAssignStat(ctx: CompiledMiniCalcParser.AssignStatContext) {
                    entered.add("assign:${ctx.ID()?.text}")
                }

                override fun exitExprStat(ctx: CompiledMiniCalcParser.ExprStatContext) {
                    entered.add("expr:${ctx.text}")
                }
            }
        ParseTreeWalker.DEFAULT.walk(listener, tree)
        assertEquals(listOf("assign:x", "assign:y", "expr:y/5;"), entered)

        val badLexer = CompiledMiniCalcLexer(CharStreams.fromString("x = ;"), assertNotNull(g.implicitLexer?.atn))
        val badParser = CompiledMiniCalcParser(CommonTokenStream(badLexer), assertNotNull(g.atn))
        badParser.removeErrorListeners()
        val recoveredTree = badParser.prog()
        assertEquals(1, recoveredTree.stat().size)
        val firstStat = recoveredTree.stat(0) as CompiledMiniCalcParser.AssignStatContext
        assertNotNull(firstStat.expr()?.exception)
    }

    @Test
    fun testEbnfMultiAltClosuresOptionalBlocksAndAtnChunking() {
        val ebnfGrammarText =
            """
            grammar EbnfSuite;
            optLl1       : ('a' ID | 'b' INT)? EOF ;
            optNonGreedy : ('a' ID | 'b' INT)?? ('a' ID | 'b' INT)? EOF ;
            starLl1      : ('a' ID | 'b' INT)* EOF ;
            plusLl1      : ('a' ID | 'b' INT)+ EOF ;
            starHybrid   : (ID '=' INT ';' | ID ':' INT ';')* EOF ;
            starAtn      : (ID '=' INT ';' | ID ':' INT ';')* ID EOF ;
            plusAtn      : (ID '=' INT ';' | ID ':' INT ';')+ ID EOF ;
            ID           : [a-zA-Z]+ ;
            INT          : [0-9]+ ;
            WS           : [ \t\r\n]+ -> skip ;
            """.trimIndent()

        val g = Grammar(ebnfGrammarText)
        assertEquals(0, g.tool.numErrors)
        val gen = CodeGenerator.create(g.tool, g, "Kotlin")
        val factory = ParserFactory(gen)
        val controller = OutputModelController(factory)
        factory.controller = controller
        val model = controller.buildParserOutputModel(header = false) as ParserFile
        val parserModel = assertNotNull(model.parser)

        val optLl1Func = assertNotNull(parserModel.funcs.firstOrNull { it.name == "optLl1" })
        val optLl1Outer = assertNotNull(optLl1Func.code?.firstOrNull() as? io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock)
        assertNotNull(optLl1Outer.ops?.filterIsInstance<LL1OptionalBlock>()?.firstOrNull())

        val optNgFunc = assertNotNull(parserModel.funcs.firstOrNull { it.name == "optNonGreedy" })
        val optNgOuter = assertNotNull(optNgFunc.code?.firstOrNull() as? io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock)
        assertNotNull(optNgOuter.ops?.filterIsInstance<OptionalBlock>()?.firstOrNull())

        val starHybridFunc = assertNotNull(parserModel.funcs.firstOrNull { it.name == "starHybrid" })
        assertTrue(starHybridFunc.hasLookaheadBlock)
        val starHybridOuter = assertNotNull(starHybridFunc.code?.firstOrNull() as? io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock)
        val starHybridOp = assertNotNull(starHybridOuter.ops?.filterIsInstance<LL1StarBlockSingleAlt>()?.firstOrNull())
        assertEquals(1, starHybridOp.alts.size)
        val innerHybridChoice =
            assertNotNull(
                starHybridOp.alts[0]
                    .ops
                    ?.filterIsInstance<AltBlock>()
                    ?.firstOrNull(),
            )
        assertEquals(2, innerHybridChoice.alts.size)

        val starAtnFunc = assertNotNull(parserModel.funcs.firstOrNull { it.name == "starAtn" })
        val starAtnOuter = assertNotNull(starAtnFunc.code?.firstOrNull() as? io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock)
        val starOp = assertNotNull(starAtnOuter.ops?.filterIsInstance<StarBlock>()?.firstOrNull())
        assertEquals(1, starOp.alts.size, "Multi-alt closure must wrap inner Choice in single outer loop alt")
        val innerStarChoice =
            assertNotNull(
                starOp.alts[0]
                    .ops
                    ?.filterIsInstance<AltBlock>()
                    ?.firstOrNull(),
            )
        assertEquals(2, innerStarChoice.alts.size)

        val plusAtnFunc = assertNotNull(parserModel.funcs.firstOrNull { it.name == "plusAtn" })
        val plusAtnOuter = assertNotNull(plusAtnFunc.code?.firstOrNull() as? io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock)
        val plusOp = assertNotNull(plusAtnOuter.ops?.filterIsInstance<PlusBlock>()?.firstOrNull())
        assertEquals(1, plusOp.alts.size, "Multi-alt positive closure must wrap inner Choice in single outer loop alt")
        val errOp: ThrowNoViableAlt = plusOp.error
        assertNotNull(errOp)
        val innerPlusChoice =
            assertNotNull(
                plusOp.alts[0]
                    .ops
                    ?.filterIsInstance<AltBlock>()
                    ?.firstOrNull(),
            )
        assertEquals(2, innerPlusChoice.alts.size)

        val rendered = KotlinCodegenEmitter.render(model)
        assertFalse(rendered.contains("EarlyExitException"))
        assertFalse(rendered.contains("-> {}"))

        val optLl1Slice = rendered.substringAfter("fun optLl1(): OptLl1Context {").substringBefore("fun optNonGreedy(")
        assertTrue(optLl1Slice.contains("else -> Unit"))
        assertFalse(optLl1Slice.contains("else -> throwNoViableAlt()"))

        val optNgSlice = rendered.substringAfter("fun optNonGreedy(): OptNonGreedyContext {").substringBefore("fun starLl1(")
        assertTrue(optNgSlice.contains("2 -> {"))
        assertTrue(optNgSlice.contains("3 -> {"))

        val plusAtnSlice = rendered.substringAfter("fun plusAtn(): PlusAtnContext {").substringBefore("private fun adaptivePredict(")
        assertTrue(plusAtnSlice.contains("altChoice = 1"))
        assertTrue(plusAtnSlice.contains("do {"))
        assertTrue(plusAtnSlice.contains("else -> {"))
        assertTrue(plusAtnSlice.contains("throwNoViableAlt()"))
        assertTrue(plusAtnSlice.contains("} while (altChoice != 2 && altChoice != ATN.INVALID_ALT_NUMBER)"))

        // Verify ATN chunking above 2000 serialized ints
        val largeGrammarSb = StringBuilder("grammar LargeAtn;\nroot : ")
        val branchCount = 260
        for (i in 0 until branchCount) {
            if (i > 0) largeGrammarSb.append(" | ")
            largeGrammarSb.append("r$i")
        }
        largeGrammarSb.append(" ;\n")
        for (i in 0 until branchCount) {
            largeGrammarSb.append("r$i : 't$i' ID INT ;\n")
        }
        largeGrammarSb.append("ID : [a-z]+ ;\nINT : [0-9]+ ;\n")

        val largeTool = Tool(listOf("LargeAtn.g4"))
        largeTool.writeFilesToDisk = false
        largeTool.removeListeners()
        largeTool.registerVirtualGrammar("LargeAtn.g4", largeGrammarSb.toString())
        largeTool.processGrammarsOnCommandLine()
        assertEquals(0, largeTool.numErrors)
        val largeParserKt = assertNotNull(largeTool.virtualOutputs["LargeAtnParser.kt"])
        assertTrue(largeParserKt.contains("private fun concatIntArrays(chunks: List<IntArray>): IntArray {"))
        assertTrue(largeParserKt.contains("private fun serializedAtnChunk0(): IntArray ="))
        assertTrue(largeParserKt.contains("private fun serializedAtnChunk1(): IntArray ="))
        assertTrue(largeParserKt.contains("val SERIALIZED_ATN: String by lazy {"))
    }

    private fun hasUnpairedSurrogate(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch.isHighSurrogate()) {
                if (i + 1 >= s.length || !s[i + 1].isLowSurrogate()) return true
                i += 2
            } else if (ch.isLowSurrogate()) {
                return true
            } else {
                i++
            }
        }
        return false
    }
}

private interface CompiledMiniCalcListener : ParseTreeListener {
    fun enterProg(ctx: CompiledMiniCalcParser.ProgContext)

    fun exitProg(ctx: CompiledMiniCalcParser.ProgContext)

    fun enterAssignStat(ctx: CompiledMiniCalcParser.AssignStatContext)

    fun exitAssignStat(ctx: CompiledMiniCalcParser.AssignStatContext)

    fun enterExprStat(ctx: CompiledMiniCalcParser.ExprStatContext)

    fun exitExprStat(ctx: CompiledMiniCalcParser.ExprStatContext)

    fun enterMulDivExpr(ctx: CompiledMiniCalcParser.MulDivExprContext)

    fun exitMulDivExpr(ctx: CompiledMiniCalcParser.MulDivExprContext)

    fun enterAddSubExpr(ctx: CompiledMiniCalcParser.AddSubExprContext)

    fun exitAddSubExpr(ctx: CompiledMiniCalcParser.AddSubExprContext)

    fun enterIntExpr(ctx: CompiledMiniCalcParser.IntExprContext)

    fun exitIntExpr(ctx: CompiledMiniCalcParser.IntExprContext)

    fun enterIdExpr(ctx: CompiledMiniCalcParser.IdExprContext)

    fun exitIdExpr(ctx: CompiledMiniCalcParser.IdExprContext)

    fun enterParenExpr(ctx: CompiledMiniCalcParser.ParenExprContext)

    fun exitParenExpr(ctx: CompiledMiniCalcParser.ParenExprContext)
}

private open class CompiledMiniCalcBaseListener : CompiledMiniCalcListener {
    override fun enterProg(ctx: CompiledMiniCalcParser.ProgContext) {}

    override fun exitProg(ctx: CompiledMiniCalcParser.ProgContext) {}

    override fun enterAssignStat(ctx: CompiledMiniCalcParser.AssignStatContext) {}

    override fun exitAssignStat(ctx: CompiledMiniCalcParser.AssignStatContext) {}

    override fun enterExprStat(ctx: CompiledMiniCalcParser.ExprStatContext) {}

    override fun exitExprStat(ctx: CompiledMiniCalcParser.ExprStatContext) {}

    override fun enterMulDivExpr(ctx: CompiledMiniCalcParser.MulDivExprContext) {}

    override fun exitMulDivExpr(ctx: CompiledMiniCalcParser.MulDivExprContext) {}

    override fun enterAddSubExpr(ctx: CompiledMiniCalcParser.AddSubExprContext) {}

    override fun exitAddSubExpr(ctx: CompiledMiniCalcParser.AddSubExprContext) {}

    override fun enterIntExpr(ctx: CompiledMiniCalcParser.IntExprContext) {}

    override fun exitIntExpr(ctx: CompiledMiniCalcParser.IntExprContext) {}

    override fun enterIdExpr(ctx: CompiledMiniCalcParser.IdExprContext) {}

    override fun exitIdExpr(ctx: CompiledMiniCalcParser.IdExprContext) {}

    override fun enterParenExpr(ctx: CompiledMiniCalcParser.ParenExprContext) {}

    override fun exitParenExpr(ctx: CompiledMiniCalcParser.ParenExprContext) {}

    override fun enterEveryRule(ctx: ParserRuleContext?) {}

    override fun exitEveryRule(ctx: ParserRuleContext?) {}

    override fun visitTerminal(node: TerminalNode?) {}

    override fun visitErrorNode(node: ErrorNode?) {}
}

private interface CompiledMiniCalcVisitor<out T> : ParseTreeVisitor<T> {
    fun visitProg(ctx: CompiledMiniCalcParser.ProgContext): T?

    fun visitAssignStat(ctx: CompiledMiniCalcParser.AssignStatContext): T?

    fun visitExprStat(ctx: CompiledMiniCalcParser.ExprStatContext): T?

    fun visitMulDivExpr(ctx: CompiledMiniCalcParser.MulDivExprContext): T?

    fun visitAddSubExpr(ctx: CompiledMiniCalcParser.AddSubExprContext): T?

    fun visitIntExpr(ctx: CompiledMiniCalcParser.IntExprContext): T?

    fun visitIdExpr(ctx: CompiledMiniCalcParser.IdExprContext): T?

    fun visitParenExpr(ctx: CompiledMiniCalcParser.ParenExprContext): T?
}

private open class CompiledMiniCalcBaseVisitor<T> :
    AbstractParseTreeVisitor<T>(),
    CompiledMiniCalcVisitor<T> {
    override fun visitProg(ctx: CompiledMiniCalcParser.ProgContext): T? = visitChildren(ctx)

    override fun visitAssignStat(ctx: CompiledMiniCalcParser.AssignStatContext): T? = visitChildren(ctx)

    override fun visitExprStat(ctx: CompiledMiniCalcParser.ExprStatContext): T? = visitChildren(ctx)

    override fun visitMulDivExpr(ctx: CompiledMiniCalcParser.MulDivExprContext): T? = visitChildren(ctx)

    override fun visitAddSubExpr(ctx: CompiledMiniCalcParser.AddSubExprContext): T? = visitChildren(ctx)

    override fun visitIntExpr(ctx: CompiledMiniCalcParser.IntExprContext): T? = visitChildren(ctx)

    override fun visitIdExpr(ctx: CompiledMiniCalcParser.IdExprContext): T? = visitChildren(ctx)

    override fun visitParenExpr(ctx: CompiledMiniCalcParser.ParenExprContext): T? = visitChildren(ctx)
}

private class CompiledMiniCalcLexer(
    input: CharStream?,
    private val runtimeAtn: ATN,
) : Lexer(input) {
    private val dfa: List<DFA> =
        List(runtimeAtn.numberOfDecisions) { d -> DFA(runtimeAtn.getDecisionState(d), d) }
    private val cache: PredictionContextCache = PredictionContextCache()

    init {
        interpreter = LexerATNSimulator(this, runtimeAtn, dfa, cache)
    }

    override val grammarFileName: String
        get() = "MiniCalc.g4"

    override val ruleNames: List<String>
        get() = listOf("T__0", "T__1", "T__2", "T__3", "MUL", "DIV", "ADD", "SUB", "ID", "INT", "WS")

    override val vocabulary: Vocabulary
        get() = VOCAB

    override val tokenNames: List<String>
        get() = TOK_NAMES

    override val serializedATN: String
        get() = ""

    override val atn: ATN
        get() = runtimeAtn

    companion object {
        private val LIT_NAMES: List<String> = listOf("", "'='", "';'", "'('", "')'", "'*'", "'/'", "'+'", "'-'")
        private val SYM_NAMES: List<String> = listOf("", "", "", "", "", "MUL", "DIV", "ADD", "SUB", "ID", "INT", "WS")
        val VOCAB: Vocabulary = VocabularyImpl(LIT_NAMES, SYM_NAMES)
        val TOK_NAMES: List<String> =
            List(VOCAB.maxTokenType + 1) { idx ->
                VOCAB.getLiteralName(idx) ?: VOCAB.getSymbolicName(idx) ?: "<INVALID>"
            }
    }
}

private class CompiledMiniCalcParser(
    input: TokenStream?,
    private val runtimeAtn: ATN,
) : Parser(input) {
    private val dfa: List<DFA> =
        List(runtimeAtn.numberOfDecisions) { d -> DFA(runtimeAtn.getDecisionState(d), d) }
    private val cache: PredictionContextCache = PredictionContextCache()

    init {
        interpreter = ParserATNSimulator(this, runtimeAtn, dfa, cache)
    }

    override val grammarFileName: String
        get() = "MiniCalc.g4"

    override val tokenNames: List<String>
        get() = CompiledMiniCalcLexer.TOK_NAMES

    override val ruleNames: List<String>
        get() = listOf("prog", "stat", "expr")

    override val vocabulary: Vocabulary
        get() = CompiledMiniCalcLexer.VOCAB

    override val serializedATN: String
        get() = ""

    override val atn: ATN
        get() = runtimeAtn

    object Tokens {
        const val EQ: Int = 1
        const val SEMI: Int = 2
        const val LPAREN: Int = 3
        const val RPAREN: Int = 4
        const val MUL: Int = 5
        const val DIV: Int = 6
        const val ADD: Int = 7
        const val SUB: Int = 8
        const val ID: Int = 9
        const val INT: Int = 10
        const val WS: Int = 11
    }

    open class ProgContext(
        parent: ParserRuleContext?,
        invokingState: Int,
    ) : ParserRuleContext(parent, invokingState) {
        override var ruleIndex: Int
            get() = RULE_PROG
            set(_) {}

        fun stat(): List<StatContext> = getRuleContextsOfType<StatContext>()

        fun stat(i: Int): StatContext? = getRuleContextOfType<StatContext>(i)

        fun EOF(): TerminalNode? = getToken(Token.EOF, 0)

        override fun enterRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.enterProg(this)
        }

        override fun exitRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.exitProg(this)
        }

        override fun <T> accept(visitor: ParseTreeVisitor<T>?): T? =
            if (visitor is CompiledMiniCalcVisitor) {
                visitor.visitProg(this)
            } else {
                visitor?.visitChildren(this)
            }
    }

    open class StatContext : ParserRuleContext {
        constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState)
        constructor() : super()

        fun copyFrom(ctx: StatContext) {
            super.copyFrom(ctx)
        }

        override var ruleIndex: Int
            get() = RULE_STAT
            set(_) {}
    }

    class AssignStatContext(
        ctx: StatContext,
    ) : StatContext() {
        init {
            copyFrom(ctx)
        }

        fun ID(): TerminalNode? = getToken(Tokens.ID, 0)

        fun expr(): ExprContext? = getRuleContextOfType<ExprContext>(0)

        override fun enterRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.enterAssignStat(this)
        }

        override fun exitRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.exitAssignStat(this)
        }

        override fun <T> accept(visitor: ParseTreeVisitor<T>?): T? =
            if (visitor is CompiledMiniCalcVisitor) {
                visitor.visitAssignStat(this)
            } else {
                visitor?.visitChildren(this)
            }
    }

    class ExprStatContext(
        ctx: StatContext,
    ) : StatContext() {
        init {
            copyFrom(ctx)
        }

        fun expr(): ExprContext? = getRuleContextOfType<ExprContext>(0)

        override fun enterRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.enterExprStat(this)
        }

        override fun exitRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.exitExprStat(this)
        }

        override fun <T> accept(visitor: ParseTreeVisitor<T>?): T? =
            if (visitor is CompiledMiniCalcVisitor) {
                visitor.visitExprStat(this)
            } else {
                visitor?.visitChildren(this)
            }
    }

    open class ExprContext : ParserRuleContext {
        constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState)
        constructor() : super()

        fun copyFrom(ctx: ExprContext) {
            super.copyFrom(ctx)
        }

        override var ruleIndex: Int
            get() = RULE_EXPR
            set(_) {}
    }

    class MulDivExprContext(
        ctx: ExprContext,
    ) : ExprContext() {
        var left: ExprContext? = null
        var op: Token? = null
        var right: ExprContext? = null

        init {
            copyFrom(ctx)
        }

        fun expr(): List<ExprContext> = getRuleContextsOfType<ExprContext>()

        fun expr(i: Int): ExprContext? = getRuleContextOfType<ExprContext>(i)

        override fun enterRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.enterMulDivExpr(this)
        }

        override fun exitRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.exitMulDivExpr(this)
        }

        override fun <T> accept(visitor: ParseTreeVisitor<T>?): T? =
            if (visitor is CompiledMiniCalcVisitor) {
                visitor.visitMulDivExpr(this)
            } else {
                visitor?.visitChildren(this)
            }
    }

    class AddSubExprContext(
        ctx: ExprContext,
    ) : ExprContext() {
        var left: ExprContext? = null
        var op: Token? = null
        var right: ExprContext? = null

        init {
            copyFrom(ctx)
        }

        fun expr(): List<ExprContext> = getRuleContextsOfType<ExprContext>()

        fun expr(i: Int): ExprContext? = getRuleContextOfType<ExprContext>(i)

        override fun enterRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.enterAddSubExpr(this)
        }

        override fun exitRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.exitAddSubExpr(this)
        }

        override fun <T> accept(visitor: ParseTreeVisitor<T>?): T? =
            if (visitor is CompiledMiniCalcVisitor) {
                visitor.visitAddSubExpr(this)
            } else {
                visitor?.visitChildren(this)
            }
    }

    class IntExprContext(
        ctx: ExprContext,
    ) : ExprContext() {
        init {
            copyFrom(ctx)
        }

        fun INT(): TerminalNode? = getToken(Tokens.INT, 0)

        override fun enterRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.enterIntExpr(this)
        }

        override fun exitRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.exitIntExpr(this)
        }

        override fun <T> accept(visitor: ParseTreeVisitor<T>?): T? =
            if (visitor is CompiledMiniCalcVisitor) {
                visitor.visitIntExpr(this)
            } else {
                visitor?.visitChildren(this)
            }
    }

    class IdExprContext(
        ctx: ExprContext,
    ) : ExprContext() {
        init {
            copyFrom(ctx)
        }

        fun ID(): TerminalNode? = getToken(Tokens.ID, 0)

        override fun enterRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.enterIdExpr(this)
        }

        override fun exitRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.exitIdExpr(this)
        }

        override fun <T> accept(visitor: ParseTreeVisitor<T>?): T? =
            if (visitor is CompiledMiniCalcVisitor) {
                visitor.visitIdExpr(this)
            } else {
                visitor?.visitChildren(this)
            }
    }

    class ParenExprContext(
        ctx: ExprContext,
    ) : ExprContext() {
        init {
            copyFrom(ctx)
        }

        fun expr(): ExprContext? = getRuleContextOfType<ExprContext>(0)

        override fun enterRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.enterParenExpr(this)
        }

        override fun exitRule(listener: ParseTreeListener?) {
            if (listener is CompiledMiniCalcListener) listener.exitParenExpr(this)
        }

        override fun <T> accept(visitor: ParseTreeVisitor<T>?): T? =
            if (visitor is CompiledMiniCalcVisitor) {
                visitor.visitParenExpr(this)
            } else {
                visitor?.visitChildren(this)
            }
    }

    private fun ruleCallStates(
        enclosingRuleIndex: Int,
        invokedRuleIndex: Int,
    ): List<Int> =
        runtimeAtn.states
            .filterNotNull()
            .filter { st ->
                st.ruleIndex == enclosingRuleIndex &&
                    st.numberOfTransitions > 0 &&
                    (st.transition(0) as? io.github.kotlinmania.antlr4.atn.RuleTransition)?.ruleIndex == invokedRuleIndex
            }.map { it.stateNumber }

    private fun tokenMatchStates(
        enclosingRuleIndex: Int,
        tokenType: Int,
    ): List<Int> =
        runtimeAtn.states
            .filterNotNull()
            .filter { st ->
                st.ruleIndex == enclosingRuleIndex &&
                    st.numberOfTransitions > 0 &&
                    (st.transition(0) as? io.github.kotlinmania.antlr4.atn.AtomTransition)?.atomLabel == tokenType
            }.map { it.stateNumber }

    fun prog(): ProgContext {
        val startState = requireNotNull(runtimeAtn.ruleToStartState[RULE_PROG]).stateNumber
        val statCallState = ruleCallStates(RULE_PROG, RULE_STAT).first()
        val eofMatchState = tokenMatchStates(RULE_PROG, Token.EOF).first()
        val localCtx = ProgContext(ctx, state)
        enterRule(localCtx, startState, RULE_PROG)
        try {
            executeRuleGuarded(localCtx) {
                do {
                    state = statCallState
                    stat()
                } while (
                    tokenStream.LA(1) == Tokens.ID ||
                    tokenStream.LA(1) == Tokens.INT ||
                    tokenStream.LA(1) == Tokens.LPAREN
                )
                state = eofMatchState
                match(Token.EOF)
            }
        } finally {
            exitRule()
        }
        return localCtx
    }

    fun stat(): StatContext {
        val statStart = requireNotNull(runtimeAtn.ruleToStartState[RULE_STAT])
        val startState = statStart.stateNumber
        val exprCallStates = ruleCallStates(RULE_STAT, RULE_EXPR)
        val semiMatchStates = tokenMatchStates(RULE_STAT, Tokens.SEMI)
        var localCtx = StatContext(ctx, state)
        enterRule(localCtx, startState, RULE_STAT)
        try {
            executeRuleGuarded(localCtx) {
                val decisionState = statStart.transition(0).target as? io.github.kotlinmania.antlr4.atn.DecisionState
                if (decisionState != null) {
                    state = decisionState.stateNumber
                }
                val decision = decisionState?.decision ?: 1
                when (adaptivePredict(decision)) {
                    1 -> {
                        localCtx = AssignStatContext(localCtx)
                        enterOuterAlt(localCtx, 1)
                        state = tokenMatchStates(RULE_STAT, Tokens.ID).first()
                        match(Tokens.ID)
                        state = tokenMatchStates(RULE_STAT, Tokens.EQ).first()
                        match(Tokens.EQ)
                        state = exprCallStates[0]
                        expr()
                        state = semiMatchStates.first()
                        match(Tokens.SEMI)
                    }

                    2 -> {
                        localCtx = ExprStatContext(localCtx)
                        enterOuterAlt(localCtx, 2)
                        state = exprCallStates.last()
                        expr()
                        state = semiMatchStates.last()
                        match(Tokens.SEMI)
                    }

                    else -> throwNoViableAlt()
                }
            }
        } finally {
            exitRule()
        }
        return localCtx
    }

    fun expr(): ExprContext = expr(0)

    private fun expr(precValue: Int): ExprContext {
        val parentCtx = ctx
        val parentState = state
        val startState = requireNotNull(runtimeAtn.ruleToStartState[RULE_EXPR]).stateNumber
        val exprSelfCallStates = ruleCallStates(RULE_EXPR, RULE_EXPR)
        var localCtx = ExprContext(ctx, parentState)
        enterRecursionRule(localCtx, startState, RULE_EXPR, precValue)
        try {
            executeRuleGuarded(localCtx) {
                when (tokenStream.LA(1)) {
                    Tokens.INT -> {
                        localCtx = IntExprContext(localCtx)
                        ctx = localCtx
                        state = tokenMatchStates(RULE_EXPR, Tokens.INT).first()
                        match(Tokens.INT)
                    }

                    Tokens.ID -> {
                        localCtx = IdExprContext(localCtx)
                        ctx = localCtx
                        state = tokenMatchStates(RULE_EXPR, Tokens.ID).first()
                        match(Tokens.ID)
                    }

                    Tokens.LPAREN -> {
                        localCtx = ParenExprContext(localCtx)
                        ctx = localCtx
                        state = tokenMatchStates(RULE_EXPR, Tokens.LPAREN).first()
                        match(Tokens.LPAREN)
                        state = exprSelfCallStates.first()
                        expr(0)
                        state = tokenMatchStates(RULE_EXPR, Tokens.RPAREN).first()
                        match(Tokens.RPAREN)
                    }

                    else -> throwNoViableAlt()
                }
                ctx?.stop = tokenStream.LT(-1)
                while (tokenStream.LA(1) in intArrayOf(Tokens.MUL, Tokens.DIV, Tokens.ADD, Tokens.SUB)) {
                    val la = tokenStream.LA(1)
                    if ((la == Tokens.MUL || la == Tokens.DIV) && precpred(ctx, 5)) {
                        val prevCtx = localCtx
                        val nextAltCtx = MulDivExprContext(ExprContext(parentCtx, parentState))
                        nextAltCtx.left = prevCtx
                        localCtx = nextAltCtx
                        pushNewRecursionContext(localCtx, startState, RULE_EXPR)
                        nextAltCtx.op = tokenStream.LT(1)
                        consume()
                        state = exprSelfCallStates.getOrElse(1) { exprSelfCallStates.last() }
                        nextAltCtx.right = expr(6)
                        ctx?.stop = tokenStream.LT(-1)
                    } else if ((la == Tokens.ADD || la == Tokens.SUB) && precpred(ctx, 4)) {
                        val prevCtx = localCtx
                        val nextAltCtx = AddSubExprContext(ExprContext(parentCtx, parentState))
                        nextAltCtx.left = prevCtx
                        localCtx = nextAltCtx
                        pushNewRecursionContext(localCtx, startState, RULE_EXPR)
                        nextAltCtx.op = tokenStream.LT(1)
                        consume()
                        state = exprSelfCallStates.last()
                        nextAltCtx.right = expr(5)
                        ctx?.stop = tokenStream.LT(-1)
                    } else {
                        break
                    }
                }
            }
        } finally {
            unrollRecursionContexts(parentCtx)
        }
        return localCtx
    }

    private fun adaptivePredict(decision: Int): Int {
        val sim = requireNotNull(interpreter)
        return sim.adaptivePredict(tokenStream, decision, ctx)
    }

    companion object {
        const val RULE_PROG: Int = 0
        const val RULE_STAT: Int = 1
        const val RULE_EXPR: Int = 2
    }
}
