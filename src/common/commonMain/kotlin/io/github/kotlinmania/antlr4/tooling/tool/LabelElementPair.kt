/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes

/**
 * Associates a label AST node (`x=` or `xs+=`) with its labeled element AST node and classified [LabelType].
 */
class LabelElementPair(
    isLexer: Boolean,
    var label: GrammarAST,
    var element: GrammarAST,
    labelOp: Int,
) {
    var type: LabelType? = null

    constructor(
        owner: GrammarASTOwner,
        label: GrammarAST,
        element: GrammarAST,
        labelOp: Int,
    ) : this(owner.isLexer, label, element, labelOp)

    init {
        if (element.getFirstDescendantWithType(tokenTypeForTokens) != null) {
            type =
                if (labelOp == ANTLRParserTypes.ASSIGN) {
                    LabelType.TOKEN_LABEL
                } else {
                    LabelType.TOKEN_LIST_LABEL
                }
        } else if (element.getFirstDescendantWithType(ANTLRParserTypes.RULE_REF) != null) {
            type =
                if (labelOp == ANTLRParserTypes.ASSIGN) {
                    LabelType.RULE_LABEL
                } else {
                    LabelType.RULE_LIST_LABEL
                }
        }

        if (isLexer && element.getFirstDescendantWithType(ANTLRParserTypes.STRING_LITERAL) != null) {
            if (labelOp == ANTLRParserTypes.ASSIGN) {
                type = LabelType.LEXER_STRING_LABEL
            }
        }
    }

    override fun toString(): String = "${label.text} $type $element"

    companion object {
        val tokenTypeForTokens: IntervalSet =
            IntervalSet().apply {
                add(ANTLRParserTypes.TOKEN_REF)
                add(ANTLRParserTypes.STRING_LITERAL)
                add(ANTLRParserTypes.WILDCARD)
            }
    }
}
