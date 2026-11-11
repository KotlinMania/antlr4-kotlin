/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.Token

/**
 * AST node representing a token reference, string literal, or wildcard (`.`).
 */
class TerminalAST :
    GrammarASTWithOptions,
    RuleElementAST {
    constructor(node: TerminalAST) : super(node)

    constructor(t: Token?) : super(t)

    constructor(type: Int) : super(type)

    constructor(
        type: Int,
        t: Token?,
    ) : super(type, t)

    override fun dupNode(): TerminalAST = TerminalAST(this)

    override fun visit(v: GrammarASTVisitor): Any? = v.visit(this)
}
