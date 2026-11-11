/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.analysis

import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.atn.RuleStartState
import io.github.kotlinmania.antlr4.atn.RuleStopState
import io.github.kotlinmania.antlr4.atn.RuleTransition
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/**
 * Detects mutually or indirectly left-recursive rule cycles remaining in [atn] after direct
 * left-recursion elimination and reports them via [Grammar.tool]'s error manager.
 */
class LeftRecursionDetector(
    val g: Grammar,
    val atn: ATN,
) {
    internal val mutableListOfRecursiveCycles: MutableList<MutableSet<Rule>> = ArrayList()

    /** Discovered cycles of mutually left-recursive rules, in encounter order. */
    val listOfRecursiveCycles: List<Set<Rule>>
        get() = mutableListOfRecursiveCycles

    private val rulesVisitedPerRuleCheck: MutableSet<RuleStartState> = HashSet()

    fun check() {
        for (start in atn.ruleToStartState) {
            if (start == null) continue
            rulesVisitedPerRuleCheck.clear()
            rulesVisitedPerRuleCheck.add(start)
            check(g.getRule(start.ruleIndex), start, HashSet())
        }
        if (mutableListOfRecursiveCycles.isNotEmpty()) {
            g.tool.errMgr.leftRecursionCycles(g.fileName, mutableListOfRecursiveCycles)
        }
    }

    /**
     * Traces epsilon and rule transitions starting from [s] within [enclosingRule], returning
     * `true` if [s] can reach a [RuleStopState] without consuming input tokens.
     */
    internal fun check(
        enclosingRule: Rule?,
        s: ATNState,
        visitedStates: MutableSet<ATNState>,
    ): Boolean {
        if (s is RuleStopState) return true
        if (!visitedStates.add(s)) return false

        val n = s.numberOfTransitions
        var stateReachesStopState = false
        for (i in 0 until n) {
            val t = s.transition(i)
            if (t is RuleTransition) {
                val r = g.getRule(t.ruleIndex)
                val targetStart = t.target as? RuleStartState
                if (targetStart != null && rulesVisitedPerRuleCheck.contains(targetStart)) {
                    addRulesToCycle(enclosingRule, r)
                } else {
                    if (targetStart != null) {
                        rulesVisitedPerRuleCheck.add(targetStart)
                    }
                    val nullable = check(r, t.target, HashSet())
                    if (targetStart != null) {
                        rulesVisitedPerRuleCheck.remove(targetStart)
                    }
                    if (nullable) {
                        stateReachesStopState =
                            check(enclosingRule, t.followState, visitedStates) || stateReachesStopState
                    }
                }
            } else if (t.isEpsilon) {
                stateReachesStopState =
                    check(enclosingRule, t.target, visitedStates) || stateReachesStopState
            }
        }
        return stateReachesStopState
    }

    protected fun addRulesToCycle(
        enclosingRule: Rule?,
        targetRule: Rule?,
    ) {
        var foundCycle = false
        for (rulesInCycle in mutableListOfRecursiveCycles) {
            if (targetRule != null && rulesInCycle.contains(targetRule)) {
                if (enclosingRule != null) {
                    rulesInCycle.add(enclosingRule)
                }
                foundCycle = true
            }
            if (enclosingRule != null && rulesInCycle.contains(enclosingRule)) {
                if (targetRule != null) {
                    rulesInCycle.add(targetRule)
                }
                foundCycle = true
            }
        }
        if (!foundCycle) {
            val cycle = LinkedHashSet<Rule>()
            if (targetRule != null) {
                cycle.add(targetRule)
            }
            if (enclosingRule != null) {
                cycle.add(enclosingRule)
            }
            if (cycle.isNotEmpty()) {
                mutableListOfRecursiveCycles.add(cycle)
            }
        }
    }
}
