/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.automata

import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.atn.BlockEndState
import io.github.kotlinmania.antlr4.atn.EpsilonTransition
import io.github.kotlinmania.antlr4.atn.PlusLoopbackState
import io.github.kotlinmania.antlr4.atn.RuleTransition
import io.github.kotlinmania.antlr4.atn.StarLoopbackState

/**
 * Strips redundant tail-epsilon `BasicState` hops (`p -x-> q -eps-> r`) before block/loop end states.
 */
class TailEpsilonRemover(
    private val targetAtn: ATN,
) : ATNVisitor() {
    override fun visitState(s: ATNState) {
        if (s.stateType == ATNState.BASIC && s.numberOfTransitions == 1) {
            val firstTrans = s.transition(0)
            val q = if (firstTrans is RuleTransition) firstTrans.followState else firstTrans.target
            if (q.stateType == ATNState.BASIC && q.numberOfTransitions == 1) {
                val trans = q.transition(0)
                if (trans is EpsilonTransition) {
                    val r = trans.target
                    if (r is BlockEndState || r is PlusLoopbackState || r is StarLoopbackState) {
                        if (firstTrans is RuleTransition) {
                            firstTrans.followState = r
                        } else {
                            firstTrans.target = r
                        }
                        targetAtn.removeState(q)
                    }
                }
            }
        }
    }
}
