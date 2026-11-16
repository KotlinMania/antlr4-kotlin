/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.StarLoopEntryState
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** LL(1) single-alternative Kleene-star subrule (`(a)*`) lowered to `while (...) { ... }`. */
class LL1StarBlockSingleAlt(
    factory: OutputModelFactory,
    blkOrEbnfRootAST: GrammarAST,
    alts: List<CodeBlockForAlt>,
) : LL1Loop(factory, blkOrEbnfRootAST, alts) {
    init {
        val star = blkOrEbnfRootAST.atnState as? StarLoopEntryState
        if (star != null) {
            loopBackStateNumber = star.loopBackState?.stateNumber ?: -1
            decision = star.decision
            val altLookSets = factory.grammar.decisionLOOK?.getOrNull(decision)
            val enterLook = altLookSets?.getOrNull(0) ?: IntervalSet()
            loopExpr = addCodeForLookaheadTempVar(enterLook)
            addIterationOp(CaptureNextTokenType(factory, factory.generator.target.lookaheadTempVarName))
        }
    }
}
