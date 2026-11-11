/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.Token

/**
 * AST node representing a zero-or-more EBNF subrule (`(...)*` or `(...)*?`).
 */
class StarBlockAST :
    GrammarAST,
    RuleElementAST,
    QuantifierAST {
    override val isGreedy: Boolean

    constructor(node: StarBlockAST) : super(node) {
        this.isGreedy = node.isGreedy
    }

    constructor(
        type: Int,
        t: Token?,
        nongreedy: Token?,
    ) : super(type, t) {
        this.isGreedy = nongreedy == null
    }

    override fun dupNode(): StarBlockAST = StarBlockAST(this)

    override fun visit(v: GrammarASTVisitor): Any? = v.visit(this)
}
