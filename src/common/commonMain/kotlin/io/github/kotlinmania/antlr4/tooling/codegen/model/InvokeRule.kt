/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.RuleTransition
import io.github.kotlinmania.antlr4.misc.OrderedHashSet
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.ActionTranslator
import io.github.kotlinmania.antlr4.tooling.codegen.ParserFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionChunk
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionText
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.Decl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.RuleContextDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.RuleContextListDecl
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes

/** Output model node invoking a parser subrule (`expr()` or `expr(prec)`). */
class InvokeRule(
    factory: ParserFactory,
    ast: GrammarAST,
    labelAST: GrammarAST?,
) : RuleElement(factory, ast), LabeledOp {
    val name: String = ast.text
    val escapedName: String = factory.generator.target.escapeIfNeeded(name)
    val ctxName: String

    internal val mutableLabels: OrderedHashSet<Decl> = OrderedHashSet()

    override val labels: List<Decl>
        get() = mutableLabels.elements()

    @ModelElement
    val argExprsChunks: List<ActionChunk>?

    init {
        val rf = factory.currentRuleFunction
        val targetRule = requireNotNull(factory.grammar.getRule(name)) { "Unknown invoked rule: $name" }
        ctxName = factory.generator.target.getRuleFunctionContextStructName(targetRule)

        val atnState = ast.atnState
        val ruleTrans = atnState?.transition(0) as? RuleTransition

        val argAST = if (ast.childCount > 0) ast.getChild(0) as? ActionAST else null
        val chunks = ArrayList<ActionChunk>()
        if (argAST != null) {
            chunks.addAll(ActionTranslator.translateAction(factory, rf, argAST.token, argAST))
        }
        if (ruleTrans != null && ruleTrans.precedence != 0 && argAST == null) {
            chunks.add(ActionText(null, ruleTrans.precedence.toString()))
        }
        argExprsChunks = if (chunks.isEmpty()) null else chunks

        if (rf != null) {
            val currentAlt = factory.currentOuterMostAlternativeBlock?.alt
            if (labelAST != null) {
                val labelName = labelAST.text
                val parentType = labelAST.parent?.type
                if (parentType == ANTLRParserTypes.PLUS_ASSIGN) {
                    val tempVar = factory.generator.target.getImplicitRuleLabel(name)
                    val tempDecl = RuleContextDecl(factory, tempVar, ctxName).apply { isImplicit = true }
                    rf.addContextDecl(ast.altLabel, tempDecl)
                    mutableLabels.add(tempDecl)
                    val listDecl = RuleContextListDecl(factory, labelName, ctxName)
                    rf.addContextDecl(ast.altLabel, listDecl)
                } else {
                    val decl = RuleContextDecl(factory, labelName, ctxName)
                    rf.addContextDecl(ast.altLabel, decl)
                    mutableLabels.add(decl)
                }
            } else if (currentAlt != null && currentAlt.ruleRefsInActions.containsKey(name)) {
                val tempVar = factory.generator.target.getImplicitRuleLabel(name)
                val tempDecl = RuleContextDecl(factory, tempVar, ctxName).apply { isImplicit = true }
                rf.addContextDecl(ast.altLabel, tempDecl)
                mutableLabels.add(tempDecl)
            }
        }
    }

    override fun addLabel(decl: Decl) {
        mutableLabels.add(decl)
    }
}

private val GrammarAST.altLabel: String?
    get() {
        val altNode = getAncestor(ANTLRParserTypes.ALT) as? io.github.kotlinmania.antlr4.tooling.ast.AltAST
        return altNode?.altLabel?.text ?: altNode?.leftRecursiveAltInfo?.altLabel
    }
