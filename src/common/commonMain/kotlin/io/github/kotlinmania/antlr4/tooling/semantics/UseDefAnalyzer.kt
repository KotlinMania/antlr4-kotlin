/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.semantics

import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.parse.ActionSplitter
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/**
 * Use-def analysis utilities for action attribute/token/rule references and lexer rule dependencies.
 */
object UseDefAnalyzer {
    fun trackTokenRuleRefsInActions(g: Grammar) {
        for (r in g.rules.values) {
            for (i in 1..r.numberOfAlts) {
                val alt = r.alt[i] ?: continue
                for (a in alt.actions) {
                    val tok = a.token ?: continue
                    val sniffer = ActionSniffer(g, r, alt, a, tok)
                    sniffer.examineAction()
                }
            }
        }
    }

    fun actionIsContextDependent(actionAST: ActionAST): Boolean {
        val tok = actionAST.token ?: return false
        val stream = CharStreams.fromString(tok.text ?: "")
        var dependent = false
        val listener =
            object : BlankActionSplitterListener() {
                override fun nonLocalAttr(
                    expr: String,
                    x: Token,
                    y: Token,
                ) {
                    dependent = true
                }

                override fun qualifiedAttr(
                    expr: String,
                    x: Token,
                    y: Token,
                ) {
                    dependent = true
                }

                override fun setAttr(
                    expr: String,
                    x: Token,
                    rhs: Token,
                ) {
                    dependent = true
                }

                override fun setExprAttribute(expr: String?) {
                    dependent = true
                }

                override fun setNonLocalAttr(
                    expr: String,
                    x: Token,
                    y: Token,
                    rhs: Token,
                ) {
                    dependent = true
                }

                override fun attr(
                    expr: String,
                    x: Token,
                ) {
                    dependent = true
                }
            }
        val splitter = ActionSplitter(stream, listener, tok.line, tok.charPositionInLine)
        splitter.getActionTokens()
        return dependent
    }

    fun getRuleDependencies(g: Grammar): Map<Rule, Set<Rule>> =
        getRuleDependenciesForRules(g, g.rules.values)

    fun getRuleDependenciesInMode(
        g: LexerGrammar,
        modeName: String,
    ): Map<Rule, Set<Rule>> =
        getRuleDependenciesForRules(g, g.modes[modeName] ?: emptyList())

    fun getRuleDependenciesForRules(
        g: Grammar,
        rules: Collection<Rule>,
    ): Map<Rule, Set<Rule>> {
        val dependencies = LinkedHashMap<Rule, MutableSet<Rule>>()
        for (r in rules) {
            val ruleAst = r.ast ?: continue
            val tokenRefs = ruleAst.getNodesWithType(ANTLRParserTypes.TOKEN_REF)
            for (tref in tokenRefs) {
                val target = g.getRule(tref.text) ?: continue
                val calls = dependencies.getOrPut(r) { LinkedHashSet() }
                calls.add(target)
            }
        }
        return dependencies
    }
}
