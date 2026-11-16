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

/** LL(1) single-alternative optional subrule (`(a)?`) lowered to `if (expr) { ... }`. */
class LL1OptionalBlockSingleAlt(
    factory: OutputModelFactory,
    blkAST: GrammarAST,
    alts: List<CodeBlockForAlt>,
) : LL1Choice(
        factory = factory,
        blkAST = blkAST,
        alts = alts,
        altLook = LL1AltBlock.computeAltLook(factory, blkAST),
        error = LL1AltBlock.computeNoViableAlt(factory, blkAST),
    ) {
    @ModelElement
    var expr: SrcOp

    @ModelElement
    val followExpr: List<SrcOp>

    init {
        decision = (blkAST.atnState as? DecisionState)?.decision ?: -1
        val lookSets = LL1AltBlock.computeLookSets(factory, blkAST)
        val look = lookSets.getOrNull(0) ?: IntervalSet()
        val followLook = lookSets.getOrNull(1) ?: IntervalSet()
        followExpr = factory.getLL1Test(followLook, blkAST)
        expr = addCodeForLookaheadTempVar(look)
    }
}
