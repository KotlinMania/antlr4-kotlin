/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.atn.ATNConfig
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.atn.AbstractPredicateTransition
import io.github.kotlinmania.antlr4.atn.ActionTransition
import io.github.kotlinmania.antlr4.atn.AtomTransition
import io.github.kotlinmania.antlr4.atn.BlockEndState
import io.github.kotlinmania.antlr4.atn.BlockStartState
import io.github.kotlinmania.antlr4.atn.DecisionState
import io.github.kotlinmania.antlr4.atn.NotSetTransition
import io.github.kotlinmania.antlr4.atn.PlusBlockStartState
import io.github.kotlinmania.antlr4.atn.PlusLoopbackState
import io.github.kotlinmania.antlr4.atn.RangeTransition
import io.github.kotlinmania.antlr4.atn.RuleStartState
import io.github.kotlinmania.antlr4.atn.RuleStopState
import io.github.kotlinmania.antlr4.atn.RuleTransition
import io.github.kotlinmania.antlr4.atn.SetTransition
import io.github.kotlinmania.antlr4.atn.StarBlockStartState
import io.github.kotlinmania.antlr4.atn.StarLoopEntryState
import io.github.kotlinmania.antlr4.atn.StarLoopbackState
import io.github.kotlinmania.antlr4.dfa.DFA
import io.github.kotlinmania.antlr4.dfa.DFAState
import io.github.kotlinmania.antlr4.misc.IntegerList
import io.github.kotlinmania.antlr4.tooling.misc.CharSupport
import io.github.kotlinmania.antlr4.tooling.misc.Utils

/**
 * Pure-Kotlin Graphviz DOT generator rendering ATN and DFA graphs matching `graphs.stg`.
 */
class DOTGenerator(
    val grammar: Grammar?,
) {
    var arrowhead: String = "normal"
    var rankdir: String = "LR"

    fun getDOT(
        dfa: DFA,
        isLexer: Boolean,
    ): String? {
        if (dfa.s0 == null) return null

        val states = ArrayList<String>()
        val edges = ArrayList<String>()

        for (d in dfa.states.keys) {
            if (!d.isAcceptState) continue
            states.add(renderStopState("s${d.stateNumber}", getStateLabel(d), null, false))
        }

        for (d in dfa.states.keys) {
            if (d.isAcceptState) continue
            if (d.stateNumber == Int.MAX_VALUE) continue
            states.add(renderState("s${d.stateNumber}", getStateLabel(d), 0))
        }

        for (d in dfa.states.keys) {
            val dfaEdges = d.edges ?: continue
            for (i in dfaEdges.indices) {
                val target = dfaEdges[i] ?: continue
                if (target.stateNumber == Int.MAX_VALUE) continue
                val ttype = i - 1
                val label =
                    when {
                        isLexer -> "'${getEdgeLabel(codePointToString(i))}'"
                        grammar != null -> grammar.getTokenDisplayName(ttype)
                        else -> ttype.toString()
                    }
                edges.add(
                    renderEdge(
                        src = "s${d.stateNumber}",
                        target = "s${target.stateNumber}",
                        label = label,
                        arrowhead = arrowhead,
                        transitionIndex = null,
                    ),
                )
            }
        }

        val raw =
            buildString {
                append("digraph DFA").append(dfa.decision).append("  {\n")
                if (rankdir.isNotEmpty()) {
                    append("rankdir=").append(rankdir).append(";\n")
                }
                if (states.isNotEmpty()) {
                    append(states.joinToString("\n")).append("\n")
                }
                if (edges.isNotEmpty()) {
                    append(edges.joinToString("\n")).append("\n")
                }
                append("}\n")
            }
        return Utils.sortLinesInString(raw)
    }

    fun getStateLabel(s: DFAState?): String {
        if (s == null) return "null"
        val buf = StringBuilder(250)
        buf.append('s')
        buf.append(s.stateNumber)
        if (s.isAcceptState) {
            buf.append("=>").append(s.prediction)
        }
        if (s.requiresFullContext) {
            buf.append('^')
        }
        if (grammar != null) {
            val alts = s.altSet
            if (alts != null) {
                buf.append("\\n")
                val altList = IntegerList()
                for (a in alts) altList.add(a)
                altList.sort()
                val configurations = s.configs
                for (altIndex in 0 until altList.size()) {
                    val alt = altList.get(altIndex)
                    if (altIndex > 0) {
                        buf.append("\\n")
                    }
                    buf.append("alt").append(alt).append(':')
                    val configsInAlt = ArrayList<ATNConfig>()
                    for (c in configurations) {
                        if (c.alt != alt) continue
                        configsInAlt.add(c)
                    }
                    var n = 0
                    for (cIndex in configsInAlt.indices) {
                        val c = configsInAlt[cIndex]
                        n++
                        buf.append(c.toString(null, false))
                        if (cIndex + 1 < configsInAlt.size) {
                            buf.append(", ")
                        }
                        if (n % 5 == 0 && configsInAlt.size - cIndex > 3) {
                            buf.append("\\n")
                        }
                    }
                }
            }
        }
        return buf.toString()
    }

    fun getDOT(startState: ATNState?): String? = getDOT(startState, false)

    fun getDOT(
        startState: ATNState?,
        isLexer: Boolean,
    ): String? {
        val g = grammar
        val names = ArrayList<String>()
        if (g != null) {
            names.addAll(g.rules.keys)
        }
        return getDOT(startState, names, isLexer)
    }

    fun getDOT(
        startState: ATNState?,
        ruleNames: List<String>,
        isLexer: Boolean,
    ): String? {
        if (startState == null) return null

        val markedStates = LinkedHashSet<ATNState>()
        val states = ArrayList<String>()
        val edges = ArrayList<String>()
        val work = ArrayDeque<ATNState>()
        work.addLast(startState)

        while (work.isNotEmpty()) {
            val s = work.first()
            if (!markedStates.add(s)) {
                work.removeFirst()
                continue
            }
            work.removeFirst()

            if (s is RuleStopState) continue

            for (i in 0 until s.numberOfTransitions) {
                val edge = s.transition(i)
                if (edge is RuleTransition) {
                    val ruleName = ruleNames.getOrNull(edge.ruleIndex) ?: edge.ruleIndex.toString()
                    var label = "<$ruleName"
                    val targetStart = edge.target as? RuleStartState
                    if (targetStart != null && targetStart.isLeftRecursiveRule) {
                        label += "[${edge.precedence}]"
                    }
                    label += ">"
                    edges.add(
                        renderEdge(
                            src = "s${s.stateNumber}",
                            target = "s${edge.followState.stateNumber}",
                            label = label,
                            arrowhead = arrowhead,
                            transitionIndex = null,
                        ),
                    )
                    work.addLast(edge.followState)
                    continue
                }

                val transitionPort: Int? = if (s.numberOfTransitions > 1) i else null
                when {
                    edge is ActionTransition -> {
                        edges.add(
                            renderEdge(
                                src = "s${s.stateNumber}",
                                target = "s${edge.target.stateNumber}",
                                label = getEdgeLabel(edge.toString()),
                                arrowhead = arrowhead,
                                transitionIndex = transitionPort,
                            ),
                        )
                    }

                    edge is AbstractPredicateTransition -> {
                        edges.add(
                            renderEdge(
                                src = "s${s.stateNumber}",
                                target = "s${edge.target.stateNumber}",
                                label = getEdgeLabel(edge.toString()),
                                arrowhead = arrowhead,
                                transitionIndex = transitionPort,
                            ),
                        )
                    }

                    edge.isEpsilon -> {
                        val target = edge.target
                        val loopback =
                            when (target) {
                                is PlusBlockStartState -> s == target.loopBackState
                                is StarLoopEntryState -> s == target.loopBackState
                                else -> false
                            }
                        edges.add(
                            renderEpsilonEdge(
                                src = "s${s.stateNumber}",
                                target = "s${target.stateNumber}",
                                transitionIndex = transitionPort,
                                loopback = loopback,
                            ),
                        )
                    }

                    edge is AtomTransition -> {
                        val rawLabel =
                            when {
                                isLexer -> "'${getEdgeLabel(codePointToString(edge.atomLabel))}'"
                                grammar != null -> grammar.getTokenDisplayName(edge.atomLabel)
                                else -> edge.atomLabel.toString()
                            }
                        edges.add(
                            renderEdge(
                                src = "s${s.stateNumber}",
                                target = "s${edge.target.stateNumber}",
                                label = getEdgeLabel(rawLabel),
                                arrowhead = arrowhead,
                                transitionIndex = transitionPort,
                            ),
                        )
                    }

                    edge is SetTransition -> {
                        var rawLabel =
                            when {
                                isLexer -> edge.label().toString(true)
                                grammar != null -> edge.label().toString(grammar.getVocabulary()).orEmpty()
                                else -> edge.label().toString()
                            }
                        if (edge is NotSetTransition) {
                            rawLabel = "~$rawLabel"
                        }
                        edges.add(
                            renderEdge(
                                src = "s${s.stateNumber}",
                                target = "s${edge.target.stateNumber}",
                                label = getEdgeLabel(rawLabel),
                                arrowhead = arrowhead,
                                transitionIndex = transitionPort,
                            ),
                        )
                    }

                    edge is RangeTransition -> {
                        val rawLabel =
                            when {
                                isLexer -> edge.toString()
                                grammar != null -> edge.label().toString(grammar.getVocabulary()).orEmpty()
                                else -> edge.label().toString()
                            }
                        edges.add(
                            renderEdge(
                                src = "s${s.stateNumber}",
                                target = "s${edge.target.stateNumber}",
                                label = getEdgeLabel(rawLabel),
                                arrowhead = arrowhead,
                                transitionIndex = transitionPort,
                            ),
                        )
                    }

                    else -> {
                        edges.add(
                            renderEdge(
                                src = "s${s.stateNumber}",
                                target = "s${edge.target.stateNumber}",
                                label = getEdgeLabel(edge.toString()),
                                arrowhead = arrowhead,
                                transitionIndex = transitionPort,
                            ),
                        )
                    }
                }
                work.addLast(edge.target)
            }
        }

        for (s in markedStates) {
            if (s !is RuleStopState) continue
            states.add(renderStopState("s${s.stateNumber}", getStateLabel(s), null, false))
        }

        for (s in markedStates) {
            if (s is RuleStopState) continue
            states.add(renderState("s${s.stateNumber}", getStateLabel(s), s.numberOfTransitions))
        }

        return buildString {
            append("digraph ATN {\n")
            append("rankdir=LR;\n")
            if (states.isNotEmpty()) {
                append(states.joinToString("\n")).append("\n")
            }
            if (edges.isNotEmpty()) {
                append(edges.joinToString("\n")).append("\n")
            }
            append("}\n")
        }
    }

    fun getEdgeLabel(label: String): String =
        label
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\\\n")
            .replace("\r", "")

    fun getStateLabel(s: ATNState?): String {
        if (s == null) return "null"
        val sb = StringBuilder()
        if (s is BlockStartState) {
            sb.append("&rarr;\\n")
        } else if (s is BlockEndState) {
            sb.append("&larr;\\n")
        }
        sb.append(s.stateNumber)
        if (s is PlusBlockStartState || s is PlusLoopbackState) {
            sb.append('+')
        } else if (s is StarBlockStartState || s is StarLoopEntryState || s is StarLoopbackState) {
            sb.append('*')
        }
        if (s is DecisionState && s.decision >= 0) {
            sb.append("\\nd=").append(s.decision)
        }
        return sb.toString()
    }

    private fun renderEdge(
        src: String,
        target: String,
        label: String,
        arrowhead: String?,
        transitionIndex: Int?,
    ): String =
        buildString {
            append(src)
            if (transitionIndex != null) {
                append(":p").append(transitionIndex)
            }
            append(" -> ").append(target)
            append(" [fontsize=11, fontname=\"Courier\", arrowsize=.7, label = \"").append(label).append("\"")
            if (!arrowhead.isNullOrEmpty()) {
                append(", arrowhead = ").append(arrowhead)
            }
            append("];")
        }

    private fun renderEpsilonEdge(
        src: String,
        target: String,
        transitionIndex: Int?,
        loopback: Boolean,
    ): String =
        buildString {
            append(src)
            if (transitionIndex != null) {
                append(":p").append(transitionIndex)
            }
            append(" -> ").append(target)
            append(" [fontname=\"Times-Italic\", label=\"&epsilon;\"")
            if (loopback) {
                append(", style=\"dashed\"")
            }
            append("];")
        }

    private fun renderState(
        name: String,
        label: String,
        numTransitions: Int,
    ): String =
        if (numTransitions > 1) {
            val ports = (0 until numTransitions).joinToString("|") { "<p$it>" }
            "$name[fontsize=11, label=\"{$label|{$ports}}\", shape=record, fixedsize=false, peripheries=1];"
        } else {
            "$name[fontsize=11, label=\"$label\", shape=circle, fixedsize=true, width=.55, peripheries=1];"
        }

    private fun renderStopState(
        name: String,
        label: String,
        actionIndex: Int?,
        useBox: Boolean,
    ): String =
        buildString {
            append(name).append("[fontsize=11, label=\"").append(label)
            if (actionIndex != null) {
                append(",\\naction:").append(actionIndex)
            }
            append("\", ")
            if (useBox) {
                append("shape=polygon,sides=4,peripheries=2,fixedsize=false")
            } else {
                append("shape=doublecircle, fixedsize=true, width=.6")
            }
            append("];")
        }

    private fun codePointToString(cp: Int): String =
        if (cp < 0) cp.toString() else CharSupport.codePointToString(cp)

    companion object {
        const val STRIP_NONREDUCED_STATES: Boolean = false
    }
}
