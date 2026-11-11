/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.Token

/**
 * AST node representing a token/character set (`(A | B)` or `~(A | B)`).
 */
class SetAST :
    GrammarAST,
    RuleElementAST {
    constructor(node: SetAST) : super(node)

    constructor(
        type: Int,
        t: Token?,
        text: String?,
    ) : super(type, t, text)

    override fun dupNode(): SetAST = SetAST(this)

    override fun visit(v: GrammarASTVisitor): Any? = v.visit(this)
}
