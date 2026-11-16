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

/** LL(1) multi-alternative block (`(a | b | c)`). */
open class LL1AltBlock(
    factory: OutputModelFactory,
    blkAST: GrammarAST,
    alts: List<CodeBlockForAlt>,
) : LL1Choice(
        factory = factory,
        blkAST = blkAST,
        alts = alts,
        altLook = computeAltLook(factory, blkAST),
        error = computeNoViableAlt(factory, blkAST),
    ) {
    init {
        decision = (blkAST.atnState as? DecisionState)?.decision ?: -1
        val expected =
            if (factory.grammar.atn != null) {
                val decState = blkAST.atnState as? DecisionState
                if (decState != null) {
                    val sets = factory.grammar.decisionLOOK?.getOrNull(decState.decision)
                    IntervalSet.or(sets?.filterNotNull().orEmpty())
                } else {
                    null
                }
            } else {
                null
            }
        if (expected != null && !expected.isNil) {
            error = ThrowNoViableAlt(factory, blkAST, expected)
        }
    }

    companion object {
        internal fun computeLookSets(
            factory: OutputModelFactory,
            blkAST: GrammarAST,
        ): List<IntervalSet?> {
            val dec = (blkAST.atnState as? DecisionState)?.decision ?: return emptyList()
            val list = factory.grammar.decisionLOOK?.getOrNull(dec) ?: return emptyList()
            return list.toList()
        }

        internal fun computeAltLook(
            factory: OutputModelFactory,
            blkAST: GrammarAST,
        ): List<List<TokenInfo>> {
            val lookSets = computeLookSets(factory, blkAST)
            val target = factory.generator.target
            val g = factory.grammar
            val result = ArrayList<List<TokenInfo>>(lookSets.size)
            for (set in lookSets) {
                if (set == null) {
                    result.add(emptyList())
                } else {
                    result.add(set.toList().map { ttype -> TokenInfo(ttype, target.getTokenTypeAsTargetLabel(g, ttype)) })
                }
            }
            return result
        }

        internal fun computeNoViableAlt(
            factory: OutputModelFactory,
            blkAST: GrammarAST,
        ): ThrowNoViableAlt {
            val lookSets = computeLookSets(factory, blkAST)
            val combined = IntervalSet.or(lookSets.filterNotNull())
            return ThrowNoViableAlt(factory, blkAST, combined)
        }
    }
}
