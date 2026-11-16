/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.DecisionState
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Invokes `errorHandler.sync(this)` prior to an LL(1) decision or loop iteration. */
class Sync(
    factory: OutputModelFactory,
    ast: GrammarAST,
    val expectSet: IntervalSet,
    val position: Int,
    val positionString: String,
) : RuleElement(factory, ast) {
    val decision: Int = (ast.atnState as? DecisionState)?.decision ?: -1
}
