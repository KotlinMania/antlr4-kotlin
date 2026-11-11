/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Output model node representing a grammar element bound to an ATN state number. */
abstract class RuleElement(
    factory: OutputModelFactory?,
    ast: GrammarAST? = null,
) : SrcOp(factory, ast) {
    var stateNumber: Int = ast?.atnState?.stateNumber ?: -1
}
