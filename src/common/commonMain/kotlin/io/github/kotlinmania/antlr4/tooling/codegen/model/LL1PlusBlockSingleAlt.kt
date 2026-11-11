/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.PlusBlockStartState
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** LL(1) single-alternative positive-closure subrule (`(a)+`) lowered to `do { ... } while (...)`. */
class LL1PlusBlockSingleAlt(
    factory: OutputModelFactory,
    blkOrEbnfRootAST: GrammarAST,
    alts: List<CodeBlockForAlt>,
) : LL1Loop(factory, blkOrEbnfRootAST, alts) {
    init {
        val blkAST = blkOrEbnfRootAST.getChild(0) as? BlockAST
        val blkStart = blkAST?.atnState as? PlusBlockStartState
        val loop = blkStart?.loopBackState
        stateNumber = loop?.stateNumber ?: -1
        blockStartStateNumber = blkStart?.stateNumber ?: -1
        loopBackStateNumber = loop?.stateNumber ?: -1
        if (loop != null) {
            decision = loop.decision
            val altLookSets = factory.grammar.decisionLOOK?.getOrNull(decision)
            val loopBackLook = altLookSets?.getOrNull(0) ?: IntervalSet()
            loopExpr = addCodeForLoopLookaheadTempVar(loopBackLook)
        }
    }
}
