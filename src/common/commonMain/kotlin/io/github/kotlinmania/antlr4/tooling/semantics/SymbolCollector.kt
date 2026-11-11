/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.semantics

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarASTWithOptions
import io.github.kotlinmania.antlr4.tooling.ast.PredAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.parse.GrammarTreeVisitor
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LabelElementPair
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/**
 * Collects rule references, terminals, string literals, token/channel definitions, actions,
 * predicates, and element labels from a [GrammarAST], binding [ActionAST.resolver] pointers.
 */
class SymbolCollector(
    val g: Grammar,
) : GrammarTreeVisitor() {
    internal val mutableRulerefs: MutableList<GrammarAST> = ArrayList()
    val rulerefs: List<GrammarAST>
        get() = mutableRulerefs

    internal val mutableQualifiedRulerefs: MutableList<GrammarAST> = ArrayList()
    val qualifiedRulerefs: List<GrammarAST>
        get() = mutableQualifiedRulerefs

    internal val mutableTerminals: MutableList<GrammarAST> = ArrayList()
    val terminals: List<GrammarAST>
        get() = mutableTerminals

    internal val mutableTokenIDRefs: MutableList<GrammarAST> = ArrayList()
    val tokenIDRefs: List<GrammarAST>
        get() = mutableTokenIDRefs

    internal val mutableStrings: MutableSet<String> = HashSet()
    val strings: Set<String>
        get() = mutableStrings

    internal val mutableTokensDefs: MutableList<GrammarAST> = ArrayList()
    val tokensDefs: List<GrammarAST>
        get() = mutableTokensDefs

    internal val mutableChannelDefs: MutableList<GrammarAST> = ArrayList()
    val channelDefs: List<GrammarAST>
        get() = mutableChannelDefs

    internal val mutableNamedActions: MutableList<GrammarAST> = ArrayList()
    val namedActions: List<GrammarAST>
        get() = mutableNamedActions

    val errMgr: ErrorManager = g.tool.errMgr

    var currentRule: Rule? = null

    override fun getErrorManager(): ErrorManager = errMgr

    fun process(ast: GrammarAST?) {
        visitGrammar(ast)
    }

    override fun globalNamedAction(
        scope: GrammarAST?,
        id: GrammarAST,
        action: ActionAST,
    ) {
        action.scope = scope
        val parent = id.parent
        if (parent != null) {
            mutableNamedActions.add(parent)
        }
        action.resolver = g
    }

    override fun defineToken(id: GrammarAST) {
        mutableTerminals.add(id)
        mutableTokenIDRefs.add(id)
        mutableTokensDefs.add(id)
    }

    override fun defineChannel(id: GrammarAST) {
        mutableChannelDefs.add(id)
    }

    override fun discoverRule(
        rule: RuleAST,
        id: GrammarAST,
        modifiers: List<GrammarAST>,
        arg: ActionAST?,
        returns: ActionAST?,
        thrws: GrammarAST?,
        options: GrammarAST?,
        locals: ActionAST?,
        actions: List<GrammarAST>,
        block: GrammarAST,
    ) {
        currentRule = g.getRule(id.text)
    }

    override fun discoverLexerRule(
        rule: RuleAST,
        id: GrammarAST,
        modifiers: List<GrammarAST>,
        options: GrammarAST?,
        block: GrammarAST,
    ) {
        currentRule = g.getRule(id.text)
    }

    override fun discoverOuterAlt(alt: AltAST) {
        currentRule?.alt?.getOrNull(currentOuterAltNumber)?.ast = alt
    }

    override fun actionInAlt(action: ActionAST) {
        val rule = currentRule ?: return
        rule.defineActionInAlt(currentOuterAltNumber, action)
        action.resolver = rule.alt.getOrNull(currentOuterAltNumber)
    }

    override fun sempredInAlt(pred: PredAST) {
        val rule = currentRule ?: return
        rule.definePredicateInAlt(currentOuterAltNumber, pred)
        pred.resolver = rule.alt.getOrNull(currentOuterAltNumber)
    }

    override fun ruleCatch(
        arg: GrammarAST,
        action: ActionAST,
    ) {
        val rule = currentRule ?: return
        val catchme = action.parent
        if (catchme != null) {
            rule.mutableExceptions.add(catchme)
        }
        action.resolver = rule
    }

    override fun finallyAction(action: ActionAST) {
        val rule = currentRule ?: return
        rule.finallyAction = action
        action.resolver = rule
    }

    override fun label(
        op: GrammarAST,
        id: GrammarAST,
        element: GrammarAST,
    ) {
        val rule = currentRule ?: return
        val lp = LabelElementPair(g, id, element, op.type)
        rule.alt.getOrNull(currentOuterAltNumber)?.mutableLabelDefs?.map(id.text, lp)
    }

    override fun stringRef(ref: TerminalAST) {
        mutableTerminals.add(ref)
        mutableStrings.add(ref.text)
        currentRule?.alt?.getOrNull(currentOuterAltNumber)?.mutableTokenRefs?.map(ref.text, ref)
    }

    override fun tokenRef(ref: TerminalAST) {
        mutableTerminals.add(ref)
        mutableTokenIDRefs.add(ref)
        currentRule?.alt?.getOrNull(currentOuterAltNumber)?.mutableTokenRefs?.map(ref.text, ref)
    }

    override fun ruleRef(
        ref: GrammarAST,
        arg: ActionAST?,
    ) {
        mutableRulerefs.add(ref)
        currentRule?.alt?.getOrNull(currentOuterAltNumber)?.mutableRuleRefs?.map(ref.text, ref)
    }

    override fun grammarOption(
        id: GrammarAST,
        valueAST: GrammarAST,
    ) {
        setActionResolver(valueAST)
    }

    override fun ruleOption(
        id: GrammarAST,
        valueAST: GrammarAST,
    ) {
        setActionResolver(valueAST)
    }

    override fun blockOption(
        id: GrammarAST,
        valueAST: GrammarAST,
    ) {
        setActionResolver(valueAST)
    }

    override fun elementOption(
        t: GrammarASTWithOptions,
        id: GrammarAST,
        valueAST: GrammarAST?,
    ) {
        setActionResolver(valueAST)
    }

    private fun setActionResolver(valueAST: GrammarAST?) {
        if (valueAST is ActionAST) {
            valueAST.resolver = currentRule?.alt?.getOrNull(currentOuterAltNumber)
        }
    }
}
