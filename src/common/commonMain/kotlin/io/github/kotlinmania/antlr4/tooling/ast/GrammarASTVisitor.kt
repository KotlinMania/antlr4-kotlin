/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

/**
 * Double-dispatch visitor for walking [GrammarAST] trees produced by parsing ANTLR `.g4` grammars.
 */
interface GrammarASTVisitor {
    fun visit(node: GrammarAST): Any?

    fun visit(node: GrammarRootAST): Any?

    fun visit(node: RuleAST): Any?

    fun visit(node: BlockAST): Any?

    fun visit(node: OptionalBlockAST): Any?

    fun visit(node: PlusBlockAST): Any?

    fun visit(node: StarBlockAST): Any?

    fun visit(node: AltAST): Any?

    fun visit(node: NotAST): Any?

    fun visit(node: PredAST): Any?

    fun visit(node: RangeAST): Any?

    fun visit(node: SetAST): Any?

    fun visit(node: RuleRefAST): Any?

    fun visit(node: TerminalAST): Any?
}
