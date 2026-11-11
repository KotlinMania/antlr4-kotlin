/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.automata

import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.atn.AtomTransition
import io.github.kotlinmania.antlr4.atn.BlockEndState
import io.github.kotlinmania.antlr4.atn.CodePointTransitions
import io.github.kotlinmania.antlr4.atn.EpsilonTransition
import io.github.kotlinmania.antlr4.atn.NotSetTransition
import io.github.kotlinmania.antlr4.atn.RangeTransition
import io.github.kotlinmania.antlr4.atn.SetTransition
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.misc.CharSupport
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar

/**
 * Optimizes constructed ATNs by coalescing adjacent single-character/range/set lexer alternatives
 * and compacting removed states (`INVALID_TYPE` sentinels).
 */
object ATNOptimizer {
    fun optimize(
        g: Grammar,
        atn: ATN,
    ) {
        optimizeSets(g, atn)
        optimizeStates(atn)
    }

    private fun optimizeSets(
        g: Grammar,
        atn: ATN,
    ) {
        if (g.isParser) {
            return
        }

        val decisions = atn.decisionToState
        for (decision in decisions) {
            if (decision.ruleIndex >= 0) {
                val rule = g.getRule(decision.ruleIndex)
                if (rule != null && rule.name.isNotEmpty() && rule.name[0].isLowerCase()) {
                    continue
                }
            }

            val setTransitions = IntervalSet()
            for (i in 0 until decision.numberOfTransitions) {
                val epsTransition = decision.transition(i)
                if (epsTransition !is EpsilonTransition) {
                    continue
                }
                if (epsTransition.target.numberOfTransitions != 1) {
                    continue
                }
                val transition = epsTransition.target.transition(0)
                if (transition.target !is BlockEndState) {
                    continue
                }
                if (transition is NotSetTransition) {
                    continue
                }
                if (transition is AtomTransition ||
                    transition is RangeTransition ||
                    transition is SetTransition
                ) {
                    setTransitions.add(i)
                }
            }

            val intervalsList = setTransitions.getIntervals()
            for (i in intervalsList.size - 1 downTo 0) {
                val interval = intervalsList[i]
                if (interval.length() <= 1) {
                    continue
                }

                val blockEndState = decision.transition(interval.a).target.transition(0).target
                val matchSet = IntervalSet()
                for (j in interval.a..interval.b) {
                    val matchTransition = decision.transition(j).target.transition(0)
                    if (matchTransition is NotSetTransition) {
                        throw UnsupportedOperationException("Not yet implemented.")
                    }
                    val labelSet = matchTransition.label()
                    if (labelSet != null) {
                        val subIntervals = labelSet.getIntervals()
                        for (setInterval in subIntervals) {
                            val a = setInterval.a
                            val b = setInterval.b
                            if (a != -1 && b != -1) {
                                for (v in a..b) {
                                    if (matchSet.contains(v)) {
                                        g.tool.errMgr.grammarError(
                                            ErrorType.CHARACTERS_COLLISION_IN_SET,
                                            g.fileName,
                                            null,
                                            CharSupport.getANTLRCharLiteralForChar(v),
                                            CharSupport.getIntervalSetEscapedString(matchSet),
                                        )
                                        break
                                    }
                                }
                            }
                        }
                        matchSet.addAll(labelSet)
                    }
                }

                val newTransition =
                    if (matchSet.getIntervals().size == 1) {
                        if (matchSet.size() == 1) {
                            CodePointTransitions.createWithCodePoint(blockEndState, matchSet.minElement)
                        } else {
                            val matchInterval = matchSet.getIntervals()[0]
                            CodePointTransitions.createWithCodePointRange(
                                blockEndState,
                                matchInterval.a,
                                matchInterval.b,
                            )
                        }
                    } else {
                        SetTransition(blockEndState, matchSet)
                    }

                decision.transition(interval.a).target.setTransition(0, newTransition)
                for (j in interval.a + 1..interval.b) {
                    val removed = decision.removeTransition(interval.a + 1)
                    atn.removeState(removed.target)
                }
            }
        }
    }

    private fun optimizeStates(atn: ATN) {
        val compressed = ArrayList<ATNState>(atn.mutableStates.size)
        var i = 0
        for (s in atn.mutableStates) {
            if (s.stateType != ATNState.INVALID_TYPE) {
                compressed.add(s)
                s.stateNumber = i
                i++
            }
        }
        atn.mutableStates.clear()
        atn.mutableStates.addAll(compressed)
    }
}
