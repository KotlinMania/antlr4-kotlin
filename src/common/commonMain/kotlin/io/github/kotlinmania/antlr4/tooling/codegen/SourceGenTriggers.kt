/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1Choice
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1Loop
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.Alternative

/**
 * Pure-Kotlin recursive AST walker implementing `SourceGenTriggers.g` over ANTLR4
 * [BlockAST], [AltAST], and [GrammarAST] nodes to drive [OutputModelController].
 */
class SourceGenTriggers(
    val controller: OutputModelController,
) {
    var hasLookaheadBlock: Boolean = false

    fun block(
        t: BlockAST,
        label: GrammarAST?,
        outerMost: Boolean = false,
    ): List<SrcOp> {
        val alts = ArrayList<CodeBlockForAlt>()
        var altIndex = 0
        for (child in t.children) {
            if (child.type == ANTLRParserTypes.OPTIONS) {
                continue
            }
            val altNode = child as? AltAST ?: continue
            altIndex++
            if (outerMost) {
                controller.currentOuterMostAlt = altIndex
            }
            alts.add(alternative(altNode, outerMost))
        }
        if (alts.size == 1) {
            return alts
        }
        val choice = controller.getChoiceBlock(t, alts, label)
        hasLookaheadBlock = hasLookaheadBlock || choice !is LL1Choice
        return listOf(choice)
    }

    fun alternative(
        t: AltAST,
        outerMost: Boolean,
    ): CodeBlockForAlt {
        val rf = controller.currentRuleFunction
        val altModel =
            t.alt
                ?: rf?.rule?.getAlternative(controller.currentOuterMostAlt)
                ?: Alternative(requireNotNull(rf).rule, maxOf(1, controller.currentOuterMostAlt))

        val blk = controller.alternative(altModel, outerMost)
        if (outerMost && blk is io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForOuterMostAlt) {
            controller.currentOuterMostAlternativeBlock = blk
        }
        controller.pushCurrentBlock(blk)
        val ops = ArrayList<SrcOp>()
        try {
            if (t.childCount == 1 && t.getChild(0)?.type == ANTLRParserTypes.EPSILON) {
                val eps = t.getChild(0)
                if (eps != null) {
                    ops.addAll(controller.epsilon(altModel, eps))
                }
            } else {
                for (child in t.children) {
                    ops.addAll(element(child))
                }
            }
            controller.finishAlternative(blk, ops)
        } finally {
            controller.popCurrentBlock()
        }
        return blk
    }

    fun element(t: GrammarAST): List<SrcOp> =
        when (t.type) {
            ANTLRParserTypes.ASSIGN,
            ANTLRParserTypes.PLUS_ASSIGN,
            -> {
                val label = t.getChild(0)
                val rhs = t.getChild(1)
                if (rhs == null) {
                    emptyList()
                } else {
                    labeledElement(label, rhs)
                }
            }

            ANTLRParserTypes.RULE_REF -> {
                val arg = if (t.childCount > 0) t.getChild(0) else null
                controller.ruleRef(t, null, arg)
            }

            ANTLRParserTypes.TOKEN_REF -> {
                val arg = if (t.childCount > 0) t.getChild(0) else null
                controller.tokenRef(t, null, arg)
            }

            ANTLRParserTypes.STRING_LITERAL -> controller.stringRef(t, null)

            ANTLRParserTypes.WILDCARD -> controller.wildcard(t, null)

            ANTLRParserTypes.ACTION -> {
                val act = t as? ActionAST
                if (act != null) controller.action(act) else emptyList()
            }

            ANTLRParserTypes.SEMPRED -> {
                val pred = t as? ActionAST
                if (pred != null) controller.sempred(pred) else emptyList()
            }

            ANTLRParserTypes.SET -> controller.set(t, null, invert = false)

            ANTLRParserTypes.NOT -> {
                val inner = t.getChild(0)
                if (inner != null) controller.set(inner, null, invert = true) else emptyList()
            }

            ANTLRParserTypes.BLOCK -> {
                val blk = t as? BlockAST
                if (blk != null) block(blk, null, outerMost = false) else emptyList()
            }

            ANTLRParserTypes.OPTIONAL,
            ANTLRParserTypes.CLOSURE,
            ANTLRParserTypes.POSITIVE_CLOSURE,
            -> subrule(t, null)

            else -> emptyList()
        }

    private fun labeledElement(
        label: GrammarAST?,
        rhs: GrammarAST,
    ): List<SrcOp> =
        when (rhs.type) {
            ANTLRParserTypes.RULE_REF -> {
                val arg = if (rhs.childCount > 0) rhs.getChild(0) else null
                controller.ruleRef(rhs, label, arg)
            }

            ANTLRParserTypes.TOKEN_REF -> {
                val arg = if (rhs.childCount > 0) rhs.getChild(0) else null
                controller.tokenRef(rhs, label, arg)
            }

            ANTLRParserTypes.STRING_LITERAL -> controller.stringRef(rhs, label)

            ANTLRParserTypes.WILDCARD -> controller.wildcard(rhs, label)

            ANTLRParserTypes.BLOCK -> {
                val blk = rhs as? BlockAST
                if (blk != null) block(blk, label, outerMost = false) else emptyList()
            }

            ANTLRParserTypes.SET -> controller.set(rhs, label, invert = false)

            ANTLRParserTypes.NOT -> {
                val inner = rhs.getChild(0)
                if (inner != null) controller.set(inner, label, invert = true) else emptyList()
            }

            ANTLRParserTypes.OPTIONAL,
            ANTLRParserTypes.CLOSURE,
            ANTLRParserTypes.POSITIVE_CLOSURE,
            -> subrule(rhs, label)

            else -> element(rhs)
        }

    fun subrule(
        t: GrammarAST,
        label: GrammarAST?,
    ): List<SrcOp> {
        controller.treeLevel++
        return try {
            val innerBlock = t.getChild(0) as? BlockAST ?: return emptyList()
            val ebnf =
                if (t.type == ANTLRParserTypes.CLOSURE || t.type == ANTLRParserTypes.POSITIVE_CLOSURE) {
                    val innerOps = block(innerBlock, label, outerMost = false)
                    val innerOp = innerOps.firstOrNull() ?: return emptyList()
                    val singleAlt =
                        innerOp as? CodeBlockForAlt
                            ?: CodeBlockForAlt(controller.delegate).apply { addOp(innerOp) }
                    controller.getEBNFBlock(t, listOf(singleAlt))
                } else {
                    val alts = ArrayList<CodeBlockForAlt>()
                    for (child in innerBlock.children) {
                        if (child.type == ANTLRParserTypes.OPTIONS) continue
                        val altNode = child as? AltAST ?: continue
                        alts.add(alternative(altNode, outerMost = false))
                    }
                    val opt = controller.getEBNFBlock(t, alts)
                    if (label != null) {
                        opt.label = (controller.delegate as? ParserFactory)?.getTokenLabelDecl(label.text)
                    }
                    opt
                }
            hasLookaheadBlock = hasLookaheadBlock || (ebnf !is LL1Choice && ebnf !is LL1Loop)
            listOf(ebnf)
        } finally {
            controller.treeLevel--
        }
    }
}
