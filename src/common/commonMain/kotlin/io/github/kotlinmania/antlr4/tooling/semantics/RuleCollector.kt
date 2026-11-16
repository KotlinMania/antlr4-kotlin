/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.semantics

import io.github.kotlinmania.antlr4.misc.MultiMap
import io.github.kotlinmania.antlr4.tooling.analysis.LeftRecursiveRuleAnalyzer
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.misc.OrderedHashMap
import io.github.kotlinmania.antlr4.tooling.misc.Utils
import io.github.kotlinmania.antlr4.tooling.parse.GrammarTreeVisitor
import io.github.kotlinmania.antlr4.tooling.parse.ScopeParser
import io.github.kotlinmania.antlr4.tooling.tool.AttributeDict
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LeftRecursiveRule
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/**
 * Collects parser and lexer [Rule] definitions, argument/return/local scopes, rule-level named
 * actions, and outer-alternative `#label` mappings from a [GrammarAST].
 */
class RuleCollector(
    val g: Grammar,
) : GrammarTreeVisitor() {
    private var grammarCaseInsensitive: Boolean = false

    val errMgr: ErrorManager = g.tool.errMgr

    internal val orderedRules: OrderedHashMap<String, Rule> = OrderedHashMap()
    val rules: Map<String, Rule>
        get() = orderedRules.asMap()

    internal val mutableRuleToAltLabels: MultiMap<String, GrammarAST> = MultiMap()
    val ruleToAltLabels: Map<String, List<GrammarAST>>
        get() = mutableRuleToAltLabels.toReadOnlyMap()

    internal val mutableAltLabelToRuleName: MutableMap<String, String> = HashMap()
    val altLabelToRuleName: Map<String, String>
        get() = mutableAltLabelToRuleName

    override fun getErrorManager(): ErrorManager = errMgr

    fun process(ast: GrammarAST?) {
        visitGrammar(ast)
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
        val numAlts = block.childCount
        val r: Rule =
            if (LeftRecursiveRuleAnalyzer.hasImmediateRecursiveRuleRefs(rule, id.text)) {
                LeftRecursiveRule(g, id.text, rule)
            } else {
                Rule(g, id.text, rule, numAlts)
            }
        orderedRules.put(r.name, r)

        if (arg != null) {
            val parsedArgs = ScopeParser.parseTypedArgList(arg, arg.text, g)
            parsedArgs.type = AttributeDict.DictType.ARG
            parsedArgs.ast = arg
            r.args = parsedArgs
            arg.resolver = r.alt[currentOuterAltNumber]
        }

        if (returns != null) {
            val parsedRetvals = ScopeParser.parseTypedArgList(returns, returns.text, g)
            parsedRetvals.type = AttributeDict.DictType.RET
            parsedRetvals.ast = returns
            r.retvals = parsedRetvals
        }

        if (locals != null) {
            val parsedLocals = ScopeParser.parseTypedArgList(locals, locals.text, g)
            parsedLocals.type = AttributeDict.DictType.LOCAL
            parsedLocals.ast = locals
            r.locals = parsedLocals
        }

        for (a in actions) {
            val actionName = a.getChild(0)?.text ?: continue
            val action = a.getChild(1) as? ActionAST ?: continue
            r.mutableNamedActions[actionName] = action
            action.resolver = r
        }
    }

    override fun discoverOuterAlt(alt: AltAST) {
        val labelNode = alt.altLabel
        val ruleName = currentRuleName
        if (labelNode != null && ruleName != null) {
            mutableRuleToAltLabels.map(ruleName, labelNode)
            val altLabel = labelNode.text
            mutableAltLabelToRuleName[Utils.capitalize(altLabel)] = ruleName
            mutableAltLabelToRuleName[Utils.decapitalize(altLabel)] = ruleName
        }
    }

    override fun grammarOption(
        id: GrammarAST,
        valueAST: GrammarAST,
    ) {
        val caseInsensitive = getCaseInsensitiveValue(id, valueAST)
        if (caseInsensitive != null) {
            grammarCaseInsensitive = caseInsensitive
        }
    }

    override fun discoverLexerRule(
        rule: RuleAST,
        id: GrammarAST,
        modifiers: List<GrammarAST>,
        options: GrammarAST?,
        block: GrammarAST,
    ) {
        var currentCaseInsensitive = grammarCaseInsensitive
        if (options != null) {
            for (childAST in options.children) {
                val caseInsensitive = getCaseInsensitiveValue(childAST.getChild(0), childAST.getChild(1))
                if (caseInsensitive != null) {
                    currentCaseInsensitive = caseInsensitive
                }
            }
        }

        val numAlts = block.childCount
        val r = Rule(g, id.text, rule, numAlts, currentModeName, currentCaseInsensitive)
        if (modifiers.isNotEmpty()) {
            r.modifiers = modifiers
        }
        orderedRules.put(r.name, r)
    }

    private fun getCaseInsensitiveValue(
        optionID: GrammarAST?,
        valueAST: GrammarAST?,
    ): Boolean? {
        if (optionID == null || valueAST == null) return null
        if (optionID.text == Grammar.CASE_INSENSITIVE_OPTION_NAME) {
            val valueText = valueAST.text
            if (valueText == "true" || valueText == "false") {
                return valueText.toBoolean()
            }
        }
        return null
    }
}
