/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForOuterMostAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionTemplate
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionText
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.LeftRecursiveActionKind
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.LeftRecursiveActionSpec
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LeftRecursiveRule
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Shared [OutputModelFactory] delegating context state to [OutputModelController]. */
abstract class DefaultOutputModelFactory protected constructor(
    override val generator: CodeGenerator,
) : BlankOutputModelFactory() {
    override val grammar: Grammar
        get() = generator.grammar

    override var controller: OutputModelController = OutputModelController(this)

    override val root: OutputModelObject?
        get() = controller.root

    override val currentRuleFunction: RuleFunction?
        get() = controller.currentRuleFunction

    override val currentOuterMostAlt: Int
        get() = controller.currentOuterMostAlt

    override val currentBlock: CodeBlock?
        get() = controller.currentBlock

    override val currentOuterMostAlternativeBlock: CodeBlockForOuterMostAlt?
        get() = controller.currentOuterMostAlternativeBlock

    override val treeLevel: Int
        get() = controller.treeLevel

    override fun rulePostamble(
        rf: RuleFunction,
        r: Rule,
    ): List<SrcOp> {
        if (r.namedActions.containsKey("after") || r.finallyAction != null) {
            val stopAction =
                io.github.kotlinmania.antlr4.tooling.codegen.model.Action(
                    this,
                    rf.ruleCtx,
                    LeftRecursiveActionSpec(kind = LeftRecursiveActionKind.SET_STOP_TOKEN),
                )
            return listOf(stopAction)
        }
        if (r is LeftRecursiveRule) {
            val stopAction =
                io.github.kotlinmania.antlr4.tooling.codegen.model.Action(
                    this,
                    rf.ruleCtx,
                    LeftRecursiveActionSpec(kind = LeftRecursiveActionKind.SET_STOP_TOKEN),
                )
            return listOf(stopAction)
        }
        return emptyList()
    }

    companion object {
        fun list(vararg elems: SrcOp): List<SrcOp> = elems.toList()

        internal fun listNonNull(elems: Collection<SrcOp?>): List<SrcOp> {
            val result = ArrayList<SrcOp>(elems.size)
            for (elem in elems) {
                if (elem != null) {
                    result.add(elem)
                }
            }
            return result
        }

        fun textChunk(text: String): ActionText = ActionText(null, text)

        fun templateChunk(spec: LeftRecursiveActionSpec): ActionTemplate = ActionTemplate(null, spec)
    }
}
