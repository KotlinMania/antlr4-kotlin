/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Output model operation matching any token NOT in a set (`~(A | B)`). */
class MatchNotSet(
    factory: OutputModelFactory,
    ast: GrammarAST,
) : MatchSet(factory, ast) {
    var varName: String = factory.generator.target.lookaheadTempVarName
}
