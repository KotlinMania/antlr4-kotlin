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
import io.github.kotlinmania.antlr4.tooling.tool.Alternative
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/**
 * Finds token and rule references inside embedded grammar actions and records them on [Alternative].
 */
class ActionSniffer(
    val g: Grammar,
    val r: Rule?,
    val alt: Alternative,
    val node: ActionAST,
    val actionToken: Token,
) : BlankActionSplitterListener() {
    val errMgr: ErrorManager = g.tool.errMgr

    fun examineAction() {
        val stream = CharStreams.fromString(actionToken.text.orEmpty())
        val splitter = ActionSplitter(stream, this, actionToken.line, actionToken.charPositionInLine)
        node.chunks = splitter.getActionTokens()
    }

    fun processNested(nestedToken: Token) {
        val stream = CharStreams.fromString(nestedToken.text.orEmpty())
        val splitter = ActionSplitter(stream, this, nestedToken.line, nestedToken.charPositionInLine)
        splitter.getActionTokens()
    }

    override fun attr(
        expr: String,
        x: Token,
    ) {
        trackRef(x)
    }

    override fun qualifiedAttr(
        expr: String,
        x: Token,
        y: Token,
    ) {
        trackRef(x)
    }

    override fun setAttr(
        expr: String,
        x: Token,
        rhs: Token,
    ) {
        trackRef(x)
        processNested(rhs)
    }

    override fun setNonLocalAttr(
        expr: String,
        x: Token,
        y: Token,
        rhs: Token,
    ) {
        processNested(rhs)
    }

    fun trackRef(x: Token) {
        val refName = x.text ?: return
        if (alt.mutableTokenRefs[refName] != null) {
            alt.mutableTokenRefsInActions.map(refName, node)
        }
        if (alt.mutableRuleRefs[refName] != null) {
            alt.mutableRuleRefsInActions.map(refName, node)
        }
    }
}
