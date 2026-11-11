/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

/**
 * Grammar context contract exposed to [io.github.kotlinmania.antlr4.tooling.ast.GrammarAST] nodes
 * and [LabelElementPair] for option validation and lexer/parser label classification.
 */
interface GrammarASTOwner {
    val fileName: String?
    val isLexer: Boolean
    val errorManager: ErrorManager?
}
