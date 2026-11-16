/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.misc.FrequencySet
import io.github.kotlinmania.antlr4.tooling.misc.MutableInt
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.parse.GrammarTreeVisitor
import io.github.kotlinmania.antlr4.tooling.tool.Grammar

/**
 * Pure-Kotlin tree visitor computing minimum and maximum element frequencies within a rule or
 * alternative AST so [RuleFunction] knows whether to generate scalar vs list context getters.
 */
open class ElementFrequenciesVisitor(
    private val grammar: Grammar? = null,
) : GrammarTreeVisitor() {
    internal val frequencies: ArrayDeque<FrequencySet<String>> = ArrayDeque()
    private val minFrequencies: ArrayDeque<FrequencySet<String>> = ArrayDeque()

    init {
        frequencies.addLast(FrequencySet())
        minFrequencies.addLast(FrequencySet())
    }

    internal fun getMinFrequencies(): FrequencySet<String> {
        check(minFrequencies.size == 1) { "Expected a single frequency frame at root, found ${minFrequencies.size}." }
        check(minFrequencies.first() === SENTINEL) { "Expected sentinel frame at bottom of minFrequencies." }
        return SENTINEL
    }

    internal fun computeMinAndMaxForAlt(altAst: AltAST): Pair<FrequencySet<String>, FrequencySet<String>> {
        frequencies.clear()
        minFrequencies.clear()
        frequencies.addLast(FrequencySet())
        minFrequencies.addLast(FrequencySet())
        visit(altAst, "outerAlternative")
        return minFrequencies.last() to frequencies.last()
    }

    override fun tokenRef(ref: TerminalAST) {
        if (ref.type == ANTLRParserTypes.TOKEN_REF && ref.text != "EOF") {
            frequencies.last().add(ref.text)
            minFrequencies.last().add(ref.text)
        }
    }

    override fun stringRef(ref: TerminalAST) {
        val g = grammar ?: return
        if (inContext("... SEMPRED ...")) return
        val sym = g.getTokenName(ref.text)
        if (sym != null && !sym.startsWith("T__")) {
            frequencies.last().add(sym)
            minFrequencies.last().add(sym)
        }
    }

    override fun ruleRef(
        ref: GrammarAST,
        arg: ActionAST?,
    ) {
        frequencies.last().add(ref.text)
        minFrequencies.last().add(ref.text)
    }

    override fun enterAlternative(tree: AltAST) {
        frequencies.addLast(FrequencySet())
        minFrequencies.addLast(FrequencySet())
    }

    override fun exitAlternative(tree: AltAST) {
        val altMax = frequencies.removeLast()
        val prevMax = frequencies.removeLast()
        frequencies.addLast(combineMax(prevMax, altMax))

        val altMin = minFrequencies.removeLast()
        val prevMin = minFrequencies.removeLast()
        minFrequencies.addLast(combineMin(prevMin, altMin))
    }

    override fun enterElement(tree: GrammarAST) {
        frequencies.addLast(FrequencySet())
        minFrequencies.addLast(FrequencySet())
    }

    override fun exitElement(tree: GrammarAST) {
        val elemMax = frequencies.removeLast()
        val prevMax = frequencies.removeLast()
        frequencies.addLast(combineAndClip(prevMax, elemMax, 2))

        val elemMin = minFrequencies.removeLast()
        val prevMin = minFrequencies.removeLast()
        minFrequencies.addLast(combineAndClip(prevMin, elemMin, 2))
    }

    override fun enterBlockSet(tree: GrammarAST) {
        frequencies.addLast(FrequencySet())
        minFrequencies.addLast(FrequencySet())
    }

    override fun exitBlockSet(tree: GrammarAST) {
        val setFreq = frequencies.removeLast()
        for (key in setFreq.keys.toList()) {
            setFreq[key]?.v = 1
        }
        val prevMax = frequencies.removeLast()
        frequencies.addLast(combineAndClip(prevMax, setFreq, 2))

        // Inside `(A | B)` block set, neither token alone is guaranteed unless there is only 1 member.
        val setMin = minFrequencies.removeLast()
        if (setMin.size > 1) {
            setMin.clear()
        } else {
            for (key in setMin.keys.toList()) {
                setMin[key]?.v = 1
            }
        }
        val prevMin = minFrequencies.removeLast()
        minFrequencies.addLast(combineAndClip(prevMin, setMin, 2))
    }

    override fun enterBlock(tree: GrammarAST) {
        // Prepare neutral accumulator for `|` alternatives (`combineMin` needs first alt to seed).
        frequencies.addLast(FrequencySet())
        minFrequencies.addLast(UNSEEDED_MIN_MARKER)
    }

    override fun exitBlock(tree: GrammarAST) {
        val blockMax = frequencies.removeLast()
        val prevMax = frequencies.removeLast()
        frequencies.addLast(combineAndClip(prevMax, blockMax, 2))

        val rawBlockMin = minFrequencies.removeLast()
        val blockMin = if (rawBlockMin === UNSEEDED_MIN_MARKER) FrequencySet() else rawBlockMin
        val prevMin = minFrequencies.removeLast()
        minFrequencies.addLast(combineAndClip(prevMin, blockMin, 2))
    }

    override fun exitSubrule(tree: GrammarAST) {
        if (tree.type == ANTLRParserTypes.CLOSURE || tree.type == ANTLRParserTypes.POSITIVE_CLOSURE) {
            val topMax = frequencies.last()
            for (entry in topMax.asMap().values) {
                entry.v = 2
            }
        }
        if (tree.type == ANTLRParserTypes.CLOSURE || tree.type == ANTLRParserTypes.OPTIONAL) {
            minFrequencies.last().clear()
        }
    }

    override fun enterLexerAlternative(tree: GrammarAST) {
        frequencies.addLast(FrequencySet())
        minFrequencies.addLast(FrequencySet())
    }

    override fun exitLexerAlternative(tree: GrammarAST) {
        val altMax = frequencies.removeLast()
        val prevMax = frequencies.removeLast()
        frequencies.addLast(combineMax(prevMax, altMax))

        val altMin = minFrequencies.removeLast()
        val prevMin = minFrequencies.removeLast()
        minFrequencies.addLast(combineMin(prevMin, altMin))
    }

    override fun enterLexerElement(tree: GrammarAST) {
        frequencies.addLast(FrequencySet())
        minFrequencies.addLast(FrequencySet())
    }

    override fun exitLexerElement(tree: GrammarAST) {
        val elemMax = frequencies.removeLast()
        val prevMax = frequencies.removeLast()
        frequencies.addLast(combineAndClip(prevMax, elemMax, 2))

        val elemMin = minFrequencies.removeLast()
        val prevMin = minFrequencies.removeLast()
        minFrequencies.addLast(combineAndClip(prevMin, elemMin, 2))
    }

    override fun exitLexerSubrule(tree: GrammarAST) {
        if (tree.type == ANTLRParserTypes.CLOSURE || tree.type == ANTLRParserTypes.POSITIVE_CLOSURE) {
            for (entry in frequencies.last().asMap().values) {
                entry.v = 2
            }
        }
        if (tree.type == ANTLRParserTypes.CLOSURE || tree.type == ANTLRParserTypes.OPTIONAL) {
            minFrequencies.last().clear()
        }
    }

    companion object {
        private val SENTINEL: FrequencySet<String> = FrequencySet()
        private val UNSEEDED_MIN_MARKER: FrequencySet<String> = FrequencySet()

        internal fun combineMax(
            a: FrequencySet<String>,
            b: FrequencySet<String>,
        ): FrequencySet<String> {
            val result = copy(a)
            for ((k, v) in b.asMap()) {
                val existing = result[k]
                if (existing == null) {
                    result.put(k, MutableInt(v.v))
                } else if (v.v > existing.v) {
                    existing.v = v.v
                }
            }
            return result
        }

        internal fun combineMin(
            a: FrequencySet<String>,
            b: FrequencySet<String>,
        ): FrequencySet<String> {
            if (a === UNSEEDED_MIN_MARKER) {
                return copy(b)
            }
            val result = FrequencySet<String>()
            for ((k, va) in a.asMap()) {
                val vb = b[k]
                if (vb != null) {
                    result.put(k, MutableInt(minOf(va.v, vb.v)))
                }
            }
            return result
        }

        internal fun combineAndClip(
            a: FrequencySet<String>,
            b: FrequencySet<String>,
            clip: Int,
        ): FrequencySet<String> {
            val result = copy(a)
            for ((k, vb) in b.asMap()) {
                repeat(vb.v) {
                    result.add(k)
                }
            }
            for (entry in result.asMap().values) {
                if (entry.v > clip) {
                    entry.v = clip
                }
            }
            return result
        }

        private fun copy(a: FrequencySet<String>): FrequencySet<String> {
            val copy = FrequencySet<String>()
            for ((k, v) in a.asMap()) {
                copy.put(k, MutableInt(v.v))
            }
            return copy
        }
    }
}
