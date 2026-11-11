/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Output model operation matching any non-EOF token (`.`). */
class Wildcard(
    factory: OutputModelFactory,
    ast: GrammarAST,
) : MatchToken(factory) {
    init {
        this.ast = ast
        this.stateNumber = ast.atnState?.stateNumber ?: -1
    }
}
