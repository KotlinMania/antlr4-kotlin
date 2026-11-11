/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.misc.MultiMap
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST

/**
 * Outermost alternative (1..n) of a [Rule], tracking terminal/rule/label references and actions.
 */
class Alternative(
    var rule: Rule,
    var altNum: Int,
) : AttributeResolver {
    var ast: AltAST? = null

    internal val mutableTokenRefs: MultiMap<String, TerminalAST> = MultiMap()
    val tokenRefs: Map<String, List<TerminalAST>>
        get() = mutableTokenRefs.toReadOnlyMap()

    internal val mutableTokenRefsInActions: MultiMap<String, GrammarAST> = MultiMap()
    val tokenRefsInActions: Map<String, List<GrammarAST>>
        get() = mutableTokenRefsInActions.toReadOnlyMap()

    internal val mutableRuleRefs: MultiMap<String, GrammarAST> = MultiMap()
    val ruleRefs: Map<String, List<GrammarAST>>
        get() = mutableRuleRefs.toReadOnlyMap()

    internal val mutableRuleRefsInActions: MultiMap<String, GrammarAST> = MultiMap()
    val ruleRefsInActions: Map<String, List<GrammarAST>>
        get() = mutableRuleRefsInActions.toReadOnlyMap()

    internal val mutableLabelDefs: MultiMap<String, LabelElementPair> = MultiMap()
    val labelDefs: Map<String, List<LabelElementPair>>
        get() = mutableLabelDefs.toReadOnlyMap()

    internal val mutableActions: MutableList<ActionAST> = ArrayList()
    val actions: List<ActionAST>
        get() = mutableActions

    override fun resolvesToToken(
        x: String?,
        node: ActionAST?,
    ): Boolean {
        if (x == null) return false
        if (mutableTokenRefs[x] != null) return true
        val anyLabelDef = getAnyLabelDef(x)
        return anyLabelDef != null && anyLabelDef.type == LabelType.TOKEN_LABEL
    }

    override fun resolvesToAttributeDict(
        x: String?,
        node: ActionAST?,
    ): Boolean {
        if (x == null) return false
        if (resolvesToToken(x, node)) return true
        if (mutableRuleRefs[x] != null) return true
        val anyLabelDef = getAnyLabelDef(x)
        return anyLabelDef != null && anyLabelDef.type == LabelType.RULE_LABEL
    }

    override fun resolveToAttribute(
        x: String?,
        node: ActionAST?,
    ): Attribute? = rule.resolveToAttribute(x, node)

    override fun resolveToAttribute(
        x: String?,
        y: String?,
        node: ActionAST?,
    ): Attribute? {
        if (x == null || y == null) return null
        if (mutableTokenRefs[x] != null) {
            return rule.getPredefinedScope(LabelType.TOKEN_LABEL)?.get(y)
        }
        if (mutableRuleRefs[x] != null) {
            return rule.g.getRule(x)?.resolveRetvalOrProperty(y)
        }
        val anyLabelDef = getAnyLabelDef(x)
        if (anyLabelDef != null && anyLabelDef.type == LabelType.RULE_LABEL) {
            return rule.g.getRule(anyLabelDef.element.text)?.resolveRetvalOrProperty(y)
        } else if (anyLabelDef != null) {
            val scope = rule.getPredefinedScope(anyLabelDef.type) ?: return null
            return scope.get(y)
        }
        return null
    }

    override fun resolvesToLabel(
        x: String?,
        node: ActionAST?,
    ): Boolean {
        val anyLabelDef = getAnyLabelDef(x) ?: return false
        return anyLabelDef.type == LabelType.TOKEN_LABEL || anyLabelDef.type == LabelType.RULE_LABEL
    }

    override fun resolvesToListLabel(
        x: String?,
        node: ActionAST?,
    ): Boolean {
        val anyLabelDef = getAnyLabelDef(x) ?: return false
        return anyLabelDef.type == LabelType.RULE_LIST_LABEL || anyLabelDef.type == LabelType.TOKEN_LIST_LABEL
    }

    fun getAnyLabelDef(x: String?): LabelElementPair? {
        if (x == null) return null
        val labels = mutableLabelDefs[x]
        return if (!labels.isNullOrEmpty()) labels[0] else null
    }

    fun resolveToRule(x: String?): Rule? {
        if (x == null) return null
        if (mutableRuleRefs[x] != null) return rule.g.getRule(x)
        val anyLabelDef = getAnyLabelDef(x)
        if (anyLabelDef != null && anyLabelDef.type == LabelType.RULE_LABEL) {
            return rule.g.getRule(anyLabelDef.element.text)
        }
        return null
    }
}
