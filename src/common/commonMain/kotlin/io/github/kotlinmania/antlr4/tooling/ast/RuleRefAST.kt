/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.Token

/**
 * AST node representing a parser rule invocation (`expr` or `expr[n]`).
 */
class RuleRefAST :
    GrammarASTWithOptions,
    RuleElementAST {
    constructor(node: RuleRefAST) : super(node)

    constructor(t: Token?) : super(t)

    constructor(type: Int) : super(type)

    constructor(
        type: Int,
        t: Token?,
    ) : super(type, t)

    override fun dupNode(): RuleRefAST {
        val r = RuleRefAST(this)
        r.token = this.token
        val originalToken = r.token
        if (originalToken != null) {
            this.token = CommonToken(originalToken)
        }
        return r
    }

    override fun visit(v: GrammarASTVisitor): Any? = v.visit(this)
}
