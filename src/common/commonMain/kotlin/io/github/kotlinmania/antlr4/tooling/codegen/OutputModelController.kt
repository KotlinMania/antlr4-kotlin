/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.PredAST
import io.github.kotlinmania.antlr4.tooling.codegen.model.Action
import io.github.kotlinmania.antlr4.tooling.codegen.model.BaseListenerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.BaseVisitorFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.Choice
import io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForOuterMostAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.LeftRecursiveRuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.Lexer
import io.github.kotlinmania.antlr4.tooling.codegen.model.LexerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.ListenerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.Loop
import io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject
import io.github.kotlinmania.antlr4.tooling.codegen.model.Parser
import io.github.kotlinmania.antlr4.tooling.codegen.model.ParserFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleActionFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleSempredFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.SemPred
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp
import io.github.kotlinmania.antlr4.tooling.codegen.model.VisitorFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.LeftRecursiveActionKind
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.LeftRecursiveActionSpec
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.Alternative
import io.github.kotlinmania.antlr4.tooling.tool.LeftRecursiveRule
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/**
 * Coordinates [OutputModelFactory], [SourceGenTriggers], and [CodeGeneratorExtension]s to build
 * complete target-independent output models for parsers, lexers, listeners, and visitors.
 */
class OutputModelController(
    val delegate: OutputModelFactory,
) {
    internal val mutableExtensions: MutableList<CodeGeneratorExtension> = ArrayList()
    val extensions: List<CodeGeneratorExtension>
        get() = mutableExtensions

    var grammarLevel: Int = -1
    var treeLevel: Int = 0
    var root: OutputModelObject? = null

    private val blockStack: ArrayDeque<CodeBlock> = ArrayDeque()

    var currentRuleFunction: RuleFunction? = null
    var currentOuterMostAlternativeBlock: CodeBlockForOuterMostAlt? = null
    var currentOuterMostAlt: Int = 0

    val currentBlock: CodeBlock?
        get() = blockStack.lastOrNull()

    fun addExtension(ext: CodeGeneratorExtension) {
        mutableExtensions.add(ext)
    }

    fun pushCurrentBlock(blk: CodeBlock) {
        blockStack.addLast(blk)
    }

    fun popCurrentBlock(): CodeBlock? =
        if (blockStack.isEmpty()) null else blockStack.removeLast()

    fun buildParserOutputModel(header: Boolean): OutputModelObject =
        buildParserOutputModel(if (header) SourceType.HEADER else SourceType.SOURCE)

    fun buildParserOutputModel(sourceType: SourceType): OutputModelObject {
        val gen = delegate.generator
        val file = parserFile(gen.target.getRecognizerFileName(sourceType))
        file.genLean = sourceType == SourceType.SOURCE_LEAN
        file.genContexts = sourceType == SourceType.SOURCE_CONTEXTS
        file.genDFA = sourceType == SourceType.SOURCE_DFA
        root = file
        val p = parser(file)
        file.parser = p
        for (r in delegate.grammar.rules.values) {
            buildRuleFunction(p, r)
        }
        return file
    }

    fun buildLexerOutputModel(header: Boolean): OutputModelObject {
        val gen = delegate.generator
        val file = lexerFile(gen.target.getRecognizerFileName(header))
        root = file
        val l = lexer(file)
        file.lexer = l
        val g = delegate.grammar
        for (r in g.rules.values) {
            val ctxType = gen.target.getRuleFunctionContextStructName(r)
            for (a in r.actions) {
                if (a is PredAST) {
                    val predIdx = g.sempreds[a]
                    if (predIdx != null) {
                        val rf =
                            l.mutableSempredFuncs.getOrPut(r) {
                                RuleSempredFunction(delegate, r, ctxType)
                            }
                        rf.mutableActions[predIdx] = Action(delegate, a)
                    }
                } else {
                    val actIdx = g.lexerActions[a]
                    if (actIdx != null) {
                        val rf =
                            l.mutableActionFuncs.getOrPut(r) {
                                RuleActionFunction(delegate, r, ctxType)
                            }
                        rf.mutableActions[actIdx] = Action(delegate, a)
                    }
                }
            }
        }
        return file
    }

    fun buildListenerOutputModel(header: Boolean): OutputModelObject =
        ListenerFile(delegate, delegate.generator.target.getListenerFileName(header))

    fun buildBaseListenerOutputModel(header: Boolean): OutputModelObject =
        BaseListenerFile(delegate, delegate.generator.target.getBaseListenerFileName(header))

    fun buildVisitorOutputModel(header: Boolean): OutputModelObject =
        VisitorFile(delegate, delegate.generator.target.getVisitorFileName(header))

    fun buildBaseVisitorOutputModel(header: Boolean): OutputModelObject =
        BaseVisitorFile(delegate, delegate.generator.target.getBaseVisitorFileName(header))

    fun parserFile(fileName: String): ParserFile {
        var f = delegate.parserFile(fileName)
        for (ext in mutableExtensions) {
            f = ext.parserFile(f)
        }
        return f
    }

    fun parser(file: ParserFile): Parser {
        var p = delegate.parser(file)
        for (ext in mutableExtensions) {
            p = ext.parser(p)
        }
        return p
    }

    fun lexerFile(fileName: String): LexerFile {
        var f = delegate.lexerFile(fileName)
        for (ext in mutableExtensions) {
            f = ext.lexerFile(f)
        }
        return f
    }

    fun lexer(file: LexerFile): Lexer {
        var l = delegate.lexer(file)
        for (ext in mutableExtensions) {
            l = ext.lexer(l)
        }
        return l
    }

    fun buildRuleFunction(
        parser: Parser,
        r: Rule,
    ) {
        val rf = rule(r) ?: return
        parser.addRuleFunction(rf)
        currentRuleFunction = rf
        treeLevel = 0
        rf.fillNamedActions(delegate, r)

        if (r is LeftRecursiveRule && rf is LeftRecursiveRuleFunction) {
            buildLeftRecursiveRuleFunction(r, rf)
        } else {
            buildNormalRuleFunction(r, rf)
        }

        val codeOps = rf.code
        if (codeOps != null) {
            for (op in codeOps) {
                linkEnclosingScopes(op, null, rf)
            }
        }

        rf.postamble = rulePostamble(rf, r)
        currentRuleFunction = null
        currentOuterMostAlternativeBlock = null
    }

    fun buildNormalRuleFunction(
        r: Rule,
        rf: RuleFunction,
    ) {
        val blk = r.ast?.getFirstChildWithType(ANTLRParserTypes.BLOCK) as? BlockAST ?: return
        val triggers = SourceGenTriggers(this)
        treeLevel = 1
        val code = triggers.block(blk, null, outerMost = true)
        treeLevel = 0
        val altCtxByAltNum = rf.getEffectiveAltLabelContexts(this)
        if (altCtxByAltNum.isNotEmpty()) {
            attachOuterMostAltLabels(code, altCtxByAltNum)
        }
        rf.hasLookaheadBlock = rf.hasLookaheadBlock || triggers.hasLookaheadBlock
        rf.code = code
    }

    fun buildLeftRecursiveRuleFunction(
        r: LeftRecursiveRule,
        rf: LeftRecursiveRuleFunction,
    ) {
        val blk = r.ast?.getFirstChildWithType(ANTLRParserTypes.BLOCK) as? BlockAST ?: return
        val triggers = SourceGenTriggers(this)
        treeLevel = 1
        val code = triggers.block(blk, null, outerMost = true)
        treeLevel = 0
        rf.hasLookaheadBlock = rf.hasLookaheadBlock || triggers.hasLookaheadBlock

        val outerAlt = code.firstOrNull() as? CodeBlockForOuterMostAlt
        if (outerAlt != null) {
            val primaryAlts = r.recPrimaryAlts.orEmpty()
            if (r.hasAltSpecificContexts() && primaryAlts.isNotEmpty()) {
                val primaryChoice = outerAlt.ops?.firstOrNull { it is Choice && it !is Loop } as? Choice
                if (primaryChoice != null) {
                    val limit = minOf(primaryChoice.alts.size, primaryAlts.size)
                    for (i in 0 until limit) {
                        val altInfo = primaryAlts[i]
                        val labelName = altInfo.altLabel
                        if (labelName != null) {
                            val altStruct = rf.altLabelCtxs?.get(labelName)
                            val ctxName =
                                altStruct?.name ?: delegate.generator.target.getAltLabelContextStructName(labelName)
                            primaryChoice.alts[i].addPreambleOp(
                                Action(
                                    delegate,
                                    altStruct ?: rf.ruleCtx,
                                    LeftRecursiveActionSpec(
                                        kind = LeftRecursiveActionKind.REPLACE_CONTEXT,
                                        ctxName = ctxName,
                                    ),
                                ),
                            )
                        }
                    }
                } else {
                    val singlePrimary = primaryAlts.first()
                    val labelName = singlePrimary.altLabel
                    if (labelName != null) {
                        val altStruct = rf.altLabelCtxs?.get(labelName)
                        val ctxName =
                            altStruct?.name ?: delegate.generator.target.getAltLabelContextStructName(labelName)
                        outerAlt.addPreambleOp(
                            Action(
                                delegate,
                                altStruct ?: rf.ruleCtx,
                                LeftRecursiveActionSpec(
                                    kind = LeftRecursiveActionKind.REPLACE_CONTEXT,
                                    ctxName = ctxName,
                                ),
                            ),
                        )
                    }
                }
            }

            val opLoop = outerAlt.ops?.lastOrNull { it is Loop } as? Loop
            val opAltsList =
                r.recOpAlts
                    ?.values
                    ?.toList()
                    .orEmpty()
            if (opLoop != null && opAltsList.isNotEmpty()) {
                // Ensure prevCtx reflects the matched primary context right before entering the operator loop.
                val outerOps = outerAlt.mutableOps
                val loopIdx = outerOps.indexOf(opLoop)
                if (loopIdx >= 0) {
                    outerOps.add(
                        loopIdx,
                        Action(
                            delegate,
                            rf.ruleCtx,
                            LeftRecursiveActionSpec(kind = LeftRecursiveActionKind.SET_PREV_CTX),
                        ),
                    )
                }

                val limit = minOf(opLoop.alts.size, opAltsList.size)
                for (i in 0 until limit) {
                    val opCodeForAlt = opLoop.alts[i]
                    val altInfo = opAltsList[i]
                    val altLabel = altInfo.altLabel
                    val rawLabel = altInfo.leftRecursiveRuleRefLabel
                    val escapedLabel =
                        if (rawLabel != null) delegate.generator.target.escapeIfNeeded(rawLabel) else null
                    val spec =
                        if (altLabel != null) {
                            val altStruct = rf.altLabelCtxs?.get(altLabel)
                            val altCtxName =
                                altStruct?.name ?: delegate.generator.target.getAltLabelContextStructName(altLabel)
                            LeftRecursiveActionSpec(
                                kind = LeftRecursiveActionKind.LABELED_ALT_START,
                                ctxName = rf.ruleCtx.name,
                                ruleName = r.name,
                                currentAltLabel = altCtxName,
                                label = escapedLabel,
                                isListLabel = altInfo.isListLabel,
                            )
                        } else {
                            LeftRecursiveActionSpec(
                                kind = LeftRecursiveActionKind.ALT_START,
                                ctxName = rf.ruleCtx.name,
                                ruleName = r.name,
                                label = escapedLabel,
                                isListLabel = altInfo.isListLabel,
                            )
                        }
                    val targetStruct =
                        if (altLabel != null) rf.altLabelCtxs?.get(altLabel) ?: rf.ruleCtx else rf.ruleCtx
                    val startAction = Action(delegate, targetStruct, spec)
                    val insertIdx = if (opCodeForAlt.ops?.firstOrNull() is SemPred) 1 else 0
                    opCodeForAlt.insertOp(insertIdx, startAction)
                }

                opLoop.addIterationOp(
                    Action(
                        delegate,
                        rf.ruleCtx,
                        LeftRecursiveActionSpec(kind = LeftRecursiveActionKind.SET_PREV_CTX),
                    ),
                )
            }
        }

        rf.code = code
    }

    private fun attachOuterMostAltLabels(
        ops: List<SrcOp>,
        altCtxByAltNum: Map<Int, io.github.kotlinmania.antlr4.tooling.codegen.model.decl.AltLabelStructDecl>,
    ) {
        for (op in ops) {
            if (op is CodeBlockForOuterMostAlt) {
                op.altLabel = altCtxByAltNum[op.alt.altNum]
            } else if (op is Choice) {
                attachOuterMostAltLabels(op.alts, altCtxByAltNum)
            }
        }
    }

    private fun linkEnclosingScopes(
        op: SrcOp,
        enclosingBlock: CodeBlock?,
        rf: RuleFunction,
    ) {
        op.enclosingBlock = enclosingBlock
        op.enclosingRuleRunction = rf
        when (op) {
            is CodeBlock -> {
                op.locals?.forEach { linkEnclosingScopes(it, op, rf) }
                op.preamble?.forEach { linkEnclosingScopes(it, op, rf) }
                op.ops?.forEach { linkEnclosingScopes(it, op, rf) }
            }

            is Choice -> {
                op.preamble.forEach { linkEnclosingScopes(it, enclosingBlock, rf) }
                op.alts.forEach { linkEnclosingScopes(it, enclosingBlock, rf) }
                if (op is Loop) {
                    op.iteration.forEach { linkEnclosingScopes(it, enclosingBlock, rf) }
                }
            }
        }
    }

    fun rule(r: Rule): RuleFunction? {
        var rf = delegate.rule(r) ?: return null
        for (ext in mutableExtensions) {
            rf = ext.rule(rf)
        }
        return rf
    }

    fun rulePostamble(
        rf: RuleFunction,
        r: Rule,
    ): List<SrcOp> {
        var ops = delegate.rulePostamble(rf, r)
        for (ext in mutableExtensions) {
            ops = ext.rulePostamble(ops, r)
        }
        return ops
    }

    fun alternative(
        alt: Alternative,
        outerMost: Boolean,
    ): CodeBlockForAlt {
        var blk = delegate.alternative(alt, outerMost)
        for (ext in mutableExtensions) {
            blk = ext.alternative(blk, outerMost)
        }
        return blk
    }

    fun finishAlternative(
        blk: CodeBlockForAlt,
        ops: List<SrcOp>,
    ): CodeBlockForAlt {
        var finished = delegate.finishAlternative(blk, ops)
        for (ext in mutableExtensions) {
            finished = ext.finishAlternative(finished, ops)
        }
        return finished
    }

    fun epsilon(
        alt: Alternative,
        epsASTRoot: GrammarAST,
    ): List<SrcOp> {
        var ops = delegate.epsilon(alt, epsASTRoot)
        for (ext in mutableExtensions) {
            ops = ext.epsilon(ops)
        }
        return ops
    }

    fun ruleRef(
        ID: GrammarAST,
        label: GrammarAST?,
        args: GrammarAST?,
    ): List<SrcOp> {
        var ops = delegate.ruleRef(ID, label, args)
        for (ext in mutableExtensions) {
            ops = ext.ruleRef(ops)
        }
        return ops
    }

    fun tokenRef(
        ID: GrammarAST,
        label: GrammarAST?,
        args: GrammarAST?,
    ): List<SrcOp> {
        var ops = delegate.tokenRef(ID, label, args)
        for (ext in mutableExtensions) {
            ops = ext.tokenRef(ops)
        }
        return ops
    }

    fun stringRef(
        ID: GrammarAST,
        label: GrammarAST?,
    ): List<SrcOp> {
        var ops = delegate.stringRef(ID, label)
        for (ext in mutableExtensions) {
            ops = ext.stringRef(ops)
        }
        return ops
    }

    fun set(
        setAST: GrammarAST,
        label: GrammarAST?,
        invert: Boolean,
    ): List<SrcOp> {
        var ops = delegate.set(setAST, label, invert)
        for (ext in mutableExtensions) {
            ops = ext.set(ops)
        }
        return ops
    }

    fun wildcard(
        ast: GrammarAST,
        labelAST: GrammarAST?,
    ): List<SrcOp> {
        var ops = delegate.wildcard(ast, labelAST)
        for (ext in mutableExtensions) {
            ops = ext.wildcard(ops)
        }
        return ops
    }

    fun action(ast: ActionAST): List<SrcOp> {
        var ops = delegate.action(ast)
        for (ext in mutableExtensions) {
            ops = ext.action(ops)
        }
        return ops
    }

    fun sempred(ast: ActionAST): List<SrcOp> {
        var ops = delegate.sempred(ast)
        for (ext in mutableExtensions) {
            ops = ext.sempred(ops)
        }
        return ops
    }

    fun getChoiceBlock(
        blkAST: BlockAST,
        alts: List<CodeBlockForAlt>,
        label: GrammarAST?,
    ): Choice {
        var c = delegate.getChoiceBlock(blkAST, alts, label)
        for (ext in mutableExtensions) {
            c = ext.getChoiceBlock(c)
        }
        return c
    }

    fun getEBNFBlock(
        ebnfRoot: GrammarAST?,
        alts: List<CodeBlockForAlt>,
    ): Choice {
        var c = delegate.getEBNFBlock(ebnfRoot, alts)
        for (ext in mutableExtensions) {
            c = ext.getEBNFBlock(c)
        }
        return c
    }
}
