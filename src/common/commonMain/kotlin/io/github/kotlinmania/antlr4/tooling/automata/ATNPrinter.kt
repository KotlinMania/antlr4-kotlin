/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.automata

import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.atn.ActionTransition
import io.github.kotlinmania.antlr4.atn.AtomTransition
import io.github.kotlinmania.antlr4.atn.BlockEndState
import io.github.kotlinmania.antlr4.atn.BlockStartState
import io.github.kotlinmania.antlr4.atn.EpsilonTransition
import io.github.kotlinmania.antlr4.atn.NotSetTransition
import io.github.kotlinmania.antlr4.atn.PlusBlockStartState
import io.github.kotlinmania.antlr4.atn.PlusLoopbackState
import io.github.kotlinmania.antlr4.atn.RuleStartState
import io.github.kotlinmania.antlr4.atn.RuleStopState
import io.github.kotlinmania.antlr4.atn.RuleTransition
import io.github.kotlinmania.antlr4.atn.SetTransition
import io.github.kotlinmania.antlr4.atn.StarBlockStartState
import io.github.kotlinmania.antlr4.atn.StarLoopEntryState
import io.github.kotlinmania.antlr4.atn.StarLoopbackState
import io.github.kotlinmania.antlr4.tooling.tool.Grammar

/**
 * Breadth-first ATN walker that formats state-transition edges into canonical textual test output.
 */
class ATNPrinter(
    val g: Grammar,
    val start: ATNState?,
) {
    fun asString(): String? {
        val root = start ?: return null
        val marked = HashSet<ATNState>()
        val work = ArrayDeque<ATNState>()
        work.addLast(root)

        val buf = StringBuilder()
        while (work.isNotEmpty()) {
            val s = work.removeFirst()
            if (!marked.add(s)) continue
            val n = s.numberOfTransitions
            for (i in 0 until n) {
                val t = s.transition(i)
                if (s !is RuleStopState) {
                    if (t is RuleTransition) {
                        work.addLast(t.followState)
                    } else {
                        work.addLast(t.target)
                    }
                }
                buf.append(getStateString(s))
                when (t) {
                    is EpsilonTransition -> {
                        buf.append("->").append(getStateString(t.target)).append('\n')
                    }

                    is RuleTransition -> {
                        val ruleName = g.getRule(t.ruleIndex)?.name ?: t.ruleIndex.toString()
                        buf.append("-").append(ruleName).append("->").append(getStateString(t.target)).append('\n')
                    }

                    is ActionTransition -> {
                        buf.append("-").append(t.toString()).append("->").append(getStateString(t.target)).append('\n')
                    }

                    is SetTransition -> {
                        val not = t is NotSetTransition
                        if (g.isLexer) {
                            buf.append("-")
                                .append(if (not) "~" else "")
                                .append(t.toString())
                                .append("->")
                                .append(getStateString(t.target))
                                .append('\n')
                        } else {
                            buf.append("-")
                                .append(if (not) "~" else "")
                                .append(t.label().toString(g.getVocabulary()))
                                .append("->")
                                .append(getStateString(t.target))
                                .append('\n')
                        }
                    }

                    is AtomTransition -> {
                        val label = g.getTokenDisplayName(t.atomLabel)
                        buf.append("-").append(label).append("->").append(getStateString(t.target)).append('\n')
                    }

                    else -> {
                        buf.append("-").append(t.toString()).append("->").append(getStateString(t.target)).append('\n')
                    }
                }
            }
        }
        return buf.toString()
    }

    fun getStateString(s: ATNState): String {
        val n = s.stateNumber
        return when (s) {
            is StarBlockStartState -> "StarBlockStart_$n"
            is PlusBlockStartState -> "PlusBlockStart_$n"
            is BlockStartState -> "BlockStart_$n"
            is BlockEndState -> "BlockEnd_$n"
            is RuleStartState -> "RuleStart_${g.getRule(s.ruleIndex)?.name ?: s.ruleIndex}_$n"
            is RuleStopState -> "RuleStop_${g.getRule(s.ruleIndex)?.name ?: s.ruleIndex}_$n"
            is PlusLoopbackState -> "PlusLoopBack_$n"
            is StarLoopbackState -> "StarLoopBack_$n"
            is StarLoopEntryState -> "StarLoopEntry_$n"
            else -> "s$n"
        }
    }
}
