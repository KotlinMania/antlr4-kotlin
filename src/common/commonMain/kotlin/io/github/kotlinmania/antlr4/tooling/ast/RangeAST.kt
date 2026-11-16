/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.Token

/**
 * AST node representing a character range (`'a'..'z'`).
 */
class RangeAST :
    GrammarAST,
    RuleElementAST {
    constructor(node: RangeAST) : super(node)

    constructor(t: Token?) : super(t)

    override fun dupNode(): RangeAST = RangeAST(this)

    override fun visit(v: GrammarASTVisitor): Any? = v.visit(this)
}
