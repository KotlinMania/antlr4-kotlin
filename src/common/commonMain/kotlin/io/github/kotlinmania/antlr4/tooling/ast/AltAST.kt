/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.analysis.LeftRecursiveRuleAltInfo
import io.github.kotlinmania.antlr4.tooling.tool.Alternative
import io.github.kotlinmania.antlr4.tooling.tool.AttributeResolver

/**
 * AST node representing a rule or subrule alternative (`ALT` or `LEXER_ALT_ACTION`).
 */
class AltAST : GrammarASTWithOptions {
    /** Outermost [Alternative] associated with this node during semantic analysis. */
    var alt: Alternative? = null

    /** Outermost alternative symbol scope when resolved during semantic analysis. */
    var altResolver: AttributeResolver?
        get() = alt
        set(value) {
            if (value is Alternative?) {
                alt = value
            }
        }

    /** Left-recursion transformation metadata when transformed from a left-recursive rule. */
    var leftRecursiveAltInfo: LeftRecursiveRuleAltInfo? = null

    /** Explicit outermost alternative label (`#foo`) if specified. */
    var altLabel: GrammarAST? = null

    constructor(node: AltAST) : super(node) {
        this.alt = node.alt
        this.altLabel = node.altLabel
        this.leftRecursiveAltInfo = node.leftRecursiveAltInfo
    }

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

    override fun dupNode(): AltAST = AltAST(this)

    override fun visit(v: GrammarASTVisitor): Any? = v.visit(this)
}
