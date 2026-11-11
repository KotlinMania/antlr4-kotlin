/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.tool.AttributeResolver

/**
 * AST node representing an embedded target action (`{...}`) or argument/return action (`[...]`).
 */
open class ActionAST :
    GrammarASTWithOptions,
    RuleElementAST {
    var scope: GrammarAST? = null
    var resolver: AttributeResolver? = null
    var chunks: List<Token>? = null

    constructor(node: ActionAST) : super(node) {
        this.scope = node.scope
        this.resolver = node.resolver
        this.chunks = node.chunks
    }

    constructor(t: Token?) : super(t)

    constructor(type: Int) : super(type)

    constructor(
        type: Int,
        t: Token?,
    ) : super(type, t)

    override fun dupNode(): ActionAST = ActionAST(this)

    override fun visit(v: GrammarASTVisitor): Any? = v.visit(this)
}
