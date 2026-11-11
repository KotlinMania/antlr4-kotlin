/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Base model operation reporting and recovering from a syntax error at a decision point. */
open class ThrowRecognitionException(
    factory: OutputModelFactory,
    ast: GrammarAST,
    val expectSet: IntervalSet,
) : SrcOp(factory, ast) {
    val grammarLine: Int = ast.line
    val grammarCharPosInLine: Int = ast.charPositionInLine
    val grammarFile: String =
        factory.grammar.fileName.orEmpty().substringAfterLast('/').substringAfterLast('\\')
}
