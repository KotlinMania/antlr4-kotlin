/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.analysis

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.atn.LL1Analyzer
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar

/**
 * Post-ATN grammar analysis pipeline performing indirect left-recursion cycle detection,
 * epsilon-matching non-fragment lexer rule checks, and LL(1) decision lookahead computation.
 */
class AnalysisPipeline(
    val g: Grammar,
) {
    fun process() {
        val atn = g.atn ?: return
        val lr = LeftRecursionDetector(g, atn)
        lr.check()
        if (lr.listOfRecursiveCycles.isNotEmpty()) return

        if (g.isLexer) {
            processLexer()
        } else {
            processParser()
        }
    }

    protected fun processLexer() {
        val atn = g.atn ?: return
        for (rule in g.orderedRules.values) {
            if (rule.isFragment) {
                continue
            }
            val startState = atn.ruleToStartState.getOrNull(rule.index) ?: continue
            val analyzer = LL1Analyzer(atn)
            val look = analyzer.LOOK(startState, null)
            if (look.contains(Token.EPSILON)) {
                g.tool.errMgr.grammarError(
                    ErrorType.EPSILON_TOKEN,
                    g.fileName,
                    rule.ast?.getChild(0)?.token,
                    rule.name,
                )
            }
        }
    }

    protected fun processParser() {
        val atn = g.atn ?: return
        val lookTable = ArrayList<List<IntervalSet?>>(atn.numberOfDecisions + 1)
        g.decisionLOOK = lookTable
        for (s in atn.decisionToState) {
            val ruleName = g.getRule(s.ruleIndex)?.name.orEmpty()
            g.tool.log("LL1", "\nDECISION ${s.decision} in rule $ruleName")
            val look: List<IntervalSet?> =
                if (s.nonGreedy) {
                    List(s.numberOfTransitions + 1) { null }
                } else {
                    val anal = LL1Analyzer(atn)
                    val computed = anal.getDecisionLookahead(s)
                    g.tool.log("LL1", "look=$computed")
                    computed
                }

            while (lookTable.size <= s.decision) {
                lookTable.add(emptyList())
            }
            lookTable[s.decision] = look
            g.tool.log("LL1", "LL(1)? ${disjointNullable(look)}")
        }
    }

    companion object {
        /** Returns `true` iff the lookahead sets in [altLook] are pairwise disjoint. */
        fun disjoint(altLook: List<IntervalSet>): Boolean {
            var collision = false
            val combined = IntervalSet()
            for (look in altLook) {
                val intersection = look.and(combined)
                if (intersection != null && !intersection.isNil) {
                    collision = true
                    break
                }
                combined.addAll(look)
            }
            return !collision
        }

        internal fun disjointNullable(altLook: List<IntervalSet?>?): Boolean {
            if (altLook == null) return false
            val nonNullSets = ArrayList<IntervalSet>(altLook.size)
            for (look in altLook) {
                if (look == null) return false
                nonNullSets.add(look)
            }
            return disjoint(nonNullSets)
        }
    }
}
