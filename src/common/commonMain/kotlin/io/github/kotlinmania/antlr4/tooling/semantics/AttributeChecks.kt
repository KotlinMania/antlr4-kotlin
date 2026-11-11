/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.semantics

import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.parse.ActionSplitter
import io.github.kotlinmania.antlr4.tooling.parse.ActionSplitterListener
import io.github.kotlinmania.antlr4.tooling.tool.Alternative
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LabelElementPair
import io.github.kotlinmania.antlr4.tooling.tool.LabelType
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/**
 * Semantic validator checking `$x`, `$x.y`, `$x = ...`, and `$rule::attr` expressions inside actions.
 */
class AttributeChecks(
    val g: Grammar,
    val r: Rule?,
    val alt: Alternative?,
    val node: ActionAST,
    val actionToken: Token?,
) : ActionSplitterListener {
    val errMgr: ErrorManager = g.tool.errMgr

    fun examineAction() {
        val tok = actionToken ?: return
        val stream = CharStreams.fromString(tok.text ?: "")
        val splitter = ActionSplitter(stream, this, tok.line, tok.charPositionInLine)
        node.chunks = splitter.getActionTokens()
    }

    override fun qualifiedAttr(
        expr: String,
        x: Token,
        y: Token,
    ) {
        val xText = x.text ?: ""
        val yText = y.text ?: ""
        if (g.isLexer) {
            errMgr.grammarError(
                ErrorType.ATTRIBUTE_IN_LEXER_ACTION,
                g.fileName,
                x,
                "$xText.$yText",
                expr,
            )
            return
        }
        val resolver = node.resolver ?: return
        if (resolver.resolveToAttribute(xText, node) != null || resolver.resolvesToListLabel(xText, node)) {
            attr(expr, x)
            return
        }

        if (resolver.resolveToAttribute(xText, yText, node) == null) {
            val rref = isolatedRuleRef(xText)
            if (rref != null) {
                val ruleArgs = rref.args
                if (ruleArgs != null && ruleArgs.get(yText) != null) {
                    g.tool.errMgr.grammarError(
                        ErrorType.INVALID_RULE_PARAMETER_REF,
                        g.fileName,
                        y,
                        yText,
                        rref.name,
                        expr,
                    )
                } else {
                    errMgr.grammarError(
                        ErrorType.UNKNOWN_RULE_ATTRIBUTE,
                        g.fileName,
                        y,
                        yText,
                        rref.name,
                        expr,
                    )
                }
            } else if (!resolver.resolvesToAttributeDict(xText, node)) {
                errMgr.grammarError(
                    ErrorType.UNKNOWN_SIMPLE_ATTRIBUTE,
                    g.fileName,
                    x,
                    xText,
                    expr,
                )
            } else {
                errMgr.grammarError(
                    ErrorType.UNKNOWN_ATTRIBUTE_IN_SCOPE,
                    g.fileName,
                    y,
                    yText,
                    expr,
                )
            }
        }
    }

    override fun setAttr(
        expr: String,
        x: Token,
        rhs: Token,
    ) {
        val xText = x.text ?: ""
        if (g.isLexer) {
            errMgr.grammarError(
                ErrorType.ATTRIBUTE_IN_LEXER_ACTION,
                g.fileName,
                x,
                xText,
                expr,
            )
            return
        }
        val resolver = node.resolver
        if (resolver != null && resolver.resolveToAttribute(xText, node) == null) {
            val errorType =
                if (resolver.resolvesToListLabel(xText, node)) {
                    ErrorType.ASSIGNMENT_TO_LIST_LABEL
                } else {
                    ErrorType.UNKNOWN_SIMPLE_ATTRIBUTE
                }
            errMgr.grammarError(
                errorType,
                g.fileName,
                x,
                xText,
                expr,
            )
        }
        AttributeChecks(g, r, alt, node, rhs).examineAction()
    }

    override fun attr(
        expr: String,
        x: Token,
    ) {
        val xText = x.text ?: ""
        if (g.isLexer) {
            errMgr.grammarError(
                ErrorType.ATTRIBUTE_IN_LEXER_ACTION,
                g.fileName,
                x,
                xText,
                expr,
            )
            return
        }
        val resolver = node.resolver ?: return
        if (resolver.resolveToAttribute(xText, node) == null) {
            if (resolver.resolvesToToken(xText, node)) {
                return
            }
            if (resolver.resolvesToListLabel(xText, node)) {
                return
            }
            if (isolatedRuleRef(xText) != null) {
                errMgr.grammarError(
                    ErrorType.ISOLATED_RULE_REF,
                    g.fileName,
                    x,
                    xText,
                    expr,
                )
                return
            }
            errMgr.grammarError(
                ErrorType.UNKNOWN_SIMPLE_ATTRIBUTE,
                g.fileName,
                x,
                xText,
                expr,
            )
        }
    }

    override fun nonLocalAttr(
        expr: String,
        x: Token,
        y: Token,
    ) {
        val xText = x.text ?: ""
        val yText = y.text ?: ""
        val targetRule = g.getRule(xText)
        if (targetRule == null) {
            errMgr.grammarError(
                ErrorType.UNDEFINED_RULE_IN_NONLOCAL_REF,
                g.fileName,
                x,
                xText,
                yText,
                expr,
            )
        } else if (targetRule.resolveToAttribute(yText, null) == null) {
            errMgr.grammarError(
                ErrorType.UNKNOWN_RULE_ATTRIBUTE,
                g.fileName,
                y,
                yText,
                xText,
                expr,
            )
        }
    }

    override fun setNonLocalAttr(
        expr: String,
        x: Token,
        y: Token,
        rhs: Token,
    ) {
        val xText = x.text ?: ""
        val yText = y.text ?: ""
        val targetRule = g.getRule(xText)
        if (targetRule == null) {
            errMgr.grammarError(
                ErrorType.UNDEFINED_RULE_IN_NONLOCAL_REF,
                g.fileName,
                x,
                xText,
                yText,
                expr,
            )
        } else if (targetRule.resolveToAttribute(yText, null) == null) {
            errMgr.grammarError(
                ErrorType.UNKNOWN_RULE_ATTRIBUTE,
                g.fileName,
                y,
                yText,
                xText,
                expr,
            )
        }
    }

    override fun text(text: String) {
    }

    fun isolatedRuleRef(x: String): Rule? {
        val resolver = node.resolver ?: return null
        if (resolver is Grammar) return null

        val enclosingRule = r
        if (enclosingRule != null && x == enclosingRule.name) {
            return enclosingRule
        }
        val labels: List<LabelElementPair>? =
            when (resolver) {
                is Rule -> enclosingRule?.elementLabelDefs?.get(x)
                is Alternative -> resolver.mutableLabelDefs[x]
                else -> null
            }
        if (!labels.isNullOrEmpty()) {
            val anyLabelDef = labels[0]
            if (anyLabelDef.type == LabelType.RULE_LABEL) {
                return g.getRule(anyLabelDef.element.text)
            }
        }
        if (resolver is Alternative) {
            if (resolver.mutableRuleRefs[x] != null) {
                return g.getRule(x)
            }
        }
        return null
    }

    companion object {
        fun checkAllAttributeExpressions(g: Grammar) {
            for (act in g.namedActions.values) {
                AttributeChecks(g, null, null, act, act.token).examineAction()
            }

            for (r in g.rules.values) {
                for (a in r.namedActions.values) {
                    AttributeChecks(g, r, null, a, a.token).examineAction()
                }
                for (i in 1..r.numberOfAlts) {
                    val alt = r.alt[i] ?: continue
                    for (a in alt.actions) {
                        AttributeChecks(g, r, alt, a, a.token).examineAction()
                    }
                }
                for (e in r.exceptions) {
                    val a = e.getChild(1) as? ActionAST ?: continue
                    AttributeChecks(g, r, null, a, a.token).examineAction()
                }
                val fin = r.finallyAction
                if (fin != null) {
                    AttributeChecks(g, r, null, fin, fin.token).examineAction()
                }
            }
        }
    }
}
