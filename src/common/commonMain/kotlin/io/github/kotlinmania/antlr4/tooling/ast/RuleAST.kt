/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes

/**
 * AST node representing a parser or lexer rule definition.
 */
class RuleAST : GrammarASTWithOptions {
    constructor(node: RuleAST) : super(node)

    constructor(t: Token?) : super(t)

    constructor(type: Int) : super(type)

    constructor(
        type: Int,
        t: Token?,
        text: String?,
    ) : super(type, t, text)

    val isLexerRule: Boolean
        get() {
            val name = ruleName ?: return false
            return name.isNotEmpty() && name[0].isUpperCase()
        }

    val ruleName: String?
        get() = getChild(0)?.text

    override fun dupNode(): RuleAST = RuleAST(this)

    val lexerAction: ActionAST?
        get() {
            val blk = getFirstChildWithType(ANTLRParserTypes.BLOCK) ?: return null
            if (blk.childCount == 1) {
                val onlyAlt = blk.getChild(0) ?: return null
                if (onlyAlt.childCount > 0) {
                    val lastChild = onlyAlt.getChild(onlyAlt.childCount - 1)
                    if (lastChild is ActionAST && lastChild.type == ANTLRParserTypes.ACTION) {
                        return lastChild
                    }
                }
            }
            return null
        }

    override fun visit(v: GrammarASTVisitor): Any? = v.visit(this)
}
