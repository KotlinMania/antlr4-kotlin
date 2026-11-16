/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.Token

/**
 * AST node representing a rule body block or parenthesized subrule block.
 */
class BlockAST :
    GrammarASTWithOptions,
    RuleElementAST {
    constructor(node: BlockAST) : super(node)

    constructor(t: Token?) : super(t)

    constructor(type: Int) : super(type)

    constructor(
        type: Int,
        t: Token?,
    ) : super(type, t)

    constructor(
        type: Int,
        t: Token?,
        text: String?,
    ) : super(type, t, text)

    override fun dupNode(): BlockAST = BlockAST(this)

    override fun visit(v: GrammarASTVisitor): Any? = v.visit(this)

    companion object {
        val defaultBlockOptions: Map<String, String> = emptyMap()
        val defaultLexerBlockOptions: Map<String, String> = emptyMap()
    }
}
