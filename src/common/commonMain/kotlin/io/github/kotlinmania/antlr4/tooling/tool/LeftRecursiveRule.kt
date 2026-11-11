/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.tooling.analysis.LeftRecursiveRuleAltInfo
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.misc.OrderedHashMap

/**
 * Records a label (`e=expr`) stripped from a direct left-recursive rule reference together with
 * its enclosing alternative `#label` (if any).
 */
class LeftRecursiveRuleRefLabel(
    val labelAST: GrammarAST,
    val altLabel: String?,
)

/**
 * Direct left-recursive parser [Rule] rewritten into precedence-climbing `(primary)(op)*` form.
 */
class LeftRecursiveRule(
    g: Grammar,
    name: String,
    ast: RuleAST,
) : Rule(g, name, ast, 1) {
    var recPrimaryAlts: List<LeftRecursiveRuleAltInfo>? = null
    internal var orderedRecOpAlts: OrderedHashMap<Int, LeftRecursiveRuleAltInfo>? = null
    val recOpAlts: Map<Int, LeftRecursiveRuleAltInfo>?
        get() = orderedRecOpAlts?.asMap()

    var originalAST: RuleAST = ast

    internal val mutableLeftRecursiveRuleRefLabels: MutableList<LeftRecursiveRuleRefLabel> = ArrayList()
    val leftRecursiveRuleRefLabels: List<LeftRecursiveRuleRefLabel>
        get() = mutableLeftRecursiveRuleRefLabels

    override fun computeHasAltSpecificContexts(): Boolean =
        super.computeHasAltSpecificContexts() || altLabels != null

    override fun computeOriginalNumberOfAlts(): Int {
        var n = 0
        val primaries = recPrimaryAlts
        if (primaries != null) n += primaries.size
        val ops = orderedRecOpAlts
        if (ops != null) n += ops.size
        return n
    }

    override fun computeUnlabeledAltASTs(): List<AltAST>? {
        val alts = ArrayList<AltAST>()
        val primaries = recPrimaryAlts
        if (primaries != null) {
            for (altInfo in primaries) {
                val orig = altInfo.originalAltAST
                if (altInfo.altLabel == null && orig != null) {
                    alts.add(orig)
                }
            }
        }
        val ops = orderedRecOpAlts
        if (ops != null) {
            for (i in 0 until ops.size) {
                val altInfo = ops.getElement(i) ?: continue
                val orig = altInfo.originalAltAST
                if (altInfo.altLabel == null && orig != null) {
                    alts.add(orig)
                }
            }
        }
        return if (alts.isEmpty()) null else alts
    }

    val primaryAlts: IntArray?
        get() {
            val primaries = recPrimaryAlts ?: return null
            if (primaries.isEmpty()) return null
            val alts = IntArray(primaries.size + 1)
            for (i in primaries.indices) {
                alts[i + 1] = primaries[i].altNum
            }
            return alts
        }

    val recursiveOpAlts: IntArray?
        get() {
            val ops = orderedRecOpAlts ?: return null
            if (ops.isEmpty()) return null
            val alts = IntArray(ops.size + 1)
            var altIdx = 1
            for (altInfo in ops.values) {
                alts[altIdx] = altInfo.altNum
                altIdx++
            }
            return alts
        }

    override fun computeAltLabels(): Map<String, List<AltLabelOccurrence>>? {
        val labels = LinkedHashMap<String, MutableList<AltLabelOccurrence>>()
        val normalAltLabels = baseAltLabels()
        if (normalAltLabels != null) {
            for ((k, v) in normalAltLabels) {
                labels.getOrPut(k) { ArrayList() }.addAll(v)
            }
        }
        val primaries = recPrimaryAlts
        if (primaries != null) {
            for (altInfo in primaries) {
                val labelName = altInfo.altLabel
                val orig = altInfo.originalAltAST
                if (labelName != null && orig != null) {
                    labels.getOrPut(labelName) { ArrayList() }.add(AltLabelOccurrence(altInfo.altNum, orig))
                }
            }
        }
        val ops = orderedRecOpAlts
        if (ops != null) {
            for (i in 0 until ops.size) {
                val altInfo = ops.getElement(i) ?: continue
                val labelName = altInfo.altLabel
                val orig = altInfo.originalAltAST
                if (labelName != null && orig != null) {
                    labels.getOrPut(labelName) { ArrayList() }.add(AltLabelOccurrence(altInfo.altNum, orig))
                }
            }
        }
        return if (labels.isEmpty()) null else labels
    }
}
