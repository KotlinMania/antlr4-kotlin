/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.misc.MultiMap
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.PredAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST

/**
 * Strongly-typed occurrence of a `#label` alternative label on a 1-indexed outer alternative.
 */
class AltLabelOccurrence(
    val altNum: Int,
    val altAST: AltAST,
)

/**
 * Parser or lexer rule symbol in a [Grammar], owning its arguments, return values, locals,
 * named actions, exception handlers, and 1-indexed [Alternative] table.
 */
open class Rule(
    val g: Grammar,
    override val name: String,
    var ast: RuleAST?,
    val numberOfAlts: Int,
    val mode: String? = null,
    val caseInsensitive: Boolean = false,
) : AttributeResolver, ErrorTemplateObject {
    var modifiers: List<GrammarAST>? = null

    var args: AttributeDict? = null
    var retvals: AttributeDict? = null
    var locals: AttributeDict? = null

    internal val mutableNamedActions: MutableMap<String, ActionAST> = LinkedHashMap()
    val namedActions: Map<String, ActionAST>
        get() = mutableNamedActions

    internal val mutableExceptions: MutableList<GrammarAST> = ArrayList()
    val exceptions: List<GrammarAST>
        get() = mutableExceptions

    internal val mutableActions: MutableList<ActionAST> = ArrayList()
    val actions: List<ActionAST>
        get() = mutableActions

    var finallyAction: ActionAST? = null

    var isStartRule: Boolean = true

    internal var alt: Array<Alternative?> = Array(numberOfAlts + 1) { idx ->
        if (idx == 0) null else Alternative(this, idx)
    }

    val alternatives: List<Alternative>
        get() {
            val result = ArrayList<Alternative>(numberOfAlts)
            for (i in 1..numberOfAlts) {
                val item = alt[i]
                if (item != null) result.add(item)
            }
            return result
        }

    var index: Int = 0
    var actionIndex: Int = -1

    override val startToken: Token?
        get() = ast?.token

    fun getAlternative(altNum: Int): Alternative? =
        if (altNum in 1 until alt.size) alt[altNum] else null

    val originalNumberOfAlts: Int
        get() = computeOriginalNumberOfAlts()

    internal open fun computeOriginalNumberOfAlts(): Int = numberOfAlts

    fun defineActionInAlt(
        currentAlt: Int,
        actionAST: ActionAST,
    ) {
        mutableActions.add(actionAST)
        alt[currentAlt]?.mutableActions?.add(actionAST)
        if (g.isLexer) {
            defineLexerAction(actionAST)
        }
    }

    fun defineLexerAction(actionAST: ActionAST) {
        actionIndex = g.mutableLexerActions.size
        if (g.mutableLexerActions[actionAST] == null) {
            g.mutableLexerActions[actionAST] = actionIndex
        }
    }

    fun definePredicateInAlt(
        currentAlt: Int,
        predAST: PredAST,
    ) {
        mutableActions.add(predAST)
        alt[currentAlt]?.mutableActions?.add(predAST)
        if (g.mutableSempreds[predAST] == null) {
            g.mutableSempreds[predAST] = g.mutableSempreds.size
        }
    }

    fun resolveRetvalOrProperty(y: String): Attribute? {
        val rv = retvals?.get(y)
        if (rv != null) return rv
        return getPredefinedScope(LabelType.RULE_LABEL)?.get(y)
    }

    val tokenRefs: Set<String>
        get() {
            val refs = LinkedHashSet<String>()
            for (i in 1..numberOfAlts) {
                val a = alt[i] ?: continue
                refs.addAll(a.mutableTokenRefs.keys)
            }
            return refs
        }

    val elementLabelNames: Set<String>?
        get() {
            val refs = LinkedHashSet<String>()
            for (i in 1..numberOfAlts) {
                val a = alt[i] ?: continue
                refs.addAll(a.mutableLabelDefs.keys)
            }
            return if (refs.isEmpty()) null else refs
        }

    internal val internalElementLabelDefs: MultiMap<String, LabelElementPair>
        get() {
            val defs = MultiMap<String, LabelElementPair>()
            for (i in 1..numberOfAlts) {
                val a = alt[i] ?: continue
                for (pairs in a.mutableLabelDefs.values) {
                    for (p in pairs) {
                        defs.map(p.label.text, p)
                    }
                }
            }
            return defs
        }

    val elementLabelDefs: Map<String, List<LabelElementPair>>
        get() = internalElementLabelDefs.toReadOnlyMap()

    fun hasAltSpecificContexts(): Boolean = computeHasAltSpecificContexts()

    internal open fun computeHasAltSpecificContexts(): Boolean = altLabels != null

    val altLabels: Map<String, List<AltLabelOccurrence>>?
        get() = computeAltLabels()

    internal open fun computeAltLabels(): Map<String, List<AltLabelOccurrence>>? = baseAltLabels()

    internal fun baseAltLabels(): Map<String, List<AltLabelOccurrence>>? {
        val labels = LinkedHashMap<String, MutableList<AltLabelOccurrence>>()
        for (i in 1..numberOfAlts) {
            val altNode = alt[i]?.ast ?: continue
            val altLabel = altNode.altLabel
            if (altLabel != null) {
                labels.getOrPut(altLabel.text) { ArrayList() }.add(AltLabelOccurrence(i, altNode))
            }
        }
        return if (labels.isEmpty()) null else labels
    }

    val unlabeledAltASTs: List<AltAST>?
        get() = computeUnlabeledAltASTs()

    internal open fun computeUnlabeledAltASTs(): List<AltAST>? {
        val alts = ArrayList<AltAST>()
        for (i in 1..numberOfAlts) {
            val altNode = alt[i]?.ast ?: continue
            if (altNode.altLabel == null) {
                alts.add(altNode)
            }
        }
        return if (alts.isEmpty()) null else alts
    }

    override fun resolveToAttribute(
        x: String?,
        node: ActionAST?,
    ): Attribute? {
        if (x == null) return null
        val argAttr = args?.get(x)
        if (argAttr != null) return argAttr
        val retAttr = retvals?.get(x)
        if (retAttr != null) return retAttr
        val localAttr = locals?.get(x)
        if (localAttr != null) return localAttr
        return getPredefinedScope(LabelType.RULE_LABEL)?.get(x)
    }

    override fun resolveToAttribute(
        x: String?,
        y: String?,
        node: ActionAST?,
    ): Attribute? {
        if (x == null || y == null) return null
        val anyLabelDef = getAnyLabelDef(x) ?: return null
        return if (anyLabelDef.type == LabelType.RULE_LABEL) {
            g.getRule(anyLabelDef.element.text)?.resolveRetvalOrProperty(y)
        } else {
            getPredefinedScope(anyLabelDef.type)?.get(y)
        }
    }

    override fun resolvesToLabel(
        x: String?,
        node: ActionAST?,
    ): Boolean {
        val anyLabelDef = getAnyLabelDef(x) ?: return false
        return anyLabelDef.type == LabelType.RULE_LABEL || anyLabelDef.type == LabelType.TOKEN_LABEL
    }

    override fun resolvesToListLabel(
        x: String?,
        node: ActionAST?,
    ): Boolean {
        val anyLabelDef = getAnyLabelDef(x) ?: return false
        return anyLabelDef.type == LabelType.RULE_LIST_LABEL || anyLabelDef.type == LabelType.TOKEN_LIST_LABEL
    }

    override fun resolvesToToken(
        x: String?,
        node: ActionAST?,
    ): Boolean {
        val anyLabelDef = getAnyLabelDef(x) ?: return false
        return anyLabelDef.type == LabelType.TOKEN_LABEL
    }

    override fun resolvesToAttributeDict(
        x: String?,
        node: ActionAST?,
    ): Boolean = resolvesToToken(x, node)

    fun resolveToRule(x: String): Rule? {
        if (x == this.name) return this
        val anyLabelDef = getAnyLabelDef(x)
        if (anyLabelDef != null && anyLabelDef.type == LabelType.RULE_LABEL) {
            return g.getRule(anyLabelDef.element.text)
        }
        return g.getRule(x)
    }

    fun getAnyLabelDef(x: String?): LabelElementPair? {
        if (x == null) return null
        val labels = internalElementLabelDefs[x]
        return if (!labels.isNullOrEmpty()) labels[0] else null
    }

    fun getPredefinedScope(ltype: LabelType?): AttributeDict? {
        if (ltype == null) return null
        val grammarLabelKey = "${g.typeString}:$ltype"
        return Grammar.grammarAndLabelRefTypeToScope[grammarLabelKey]
    }

    val isFragment: Boolean
        get() {
            val mods = modifiers ?: return false
            return mods.any { it.text == "fragment" }
        }

    override fun hashCode(): Int = name.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rule) return false
        return name == other.name
    }

    override fun toString(): String =
        buildString {
            append("Rule{name=").append(name)
            val currentArgs = args
            if (currentArgs != null) append(", args=").append(currentArgs)
            val currentRetvals = retvals
            if (currentRetvals != null) append(", retvals=").append(currentRetvals)
            append("}")
        }

    companion object {
        val predefinedRulePropertiesDict: AttributeDict =
            AttributeDict(AttributeDict.DictType.PREDEFINED_RULE).apply {
                add(Attribute("parser"))
                add(Attribute("text"))
                add(Attribute("start"))
                add(Attribute("stop"))
                add(Attribute("ctx"))
            }

        val validLexerCommands: Set<String> =
            setOf(
                "mode",
                "pushMode",
                "type",
                "channel",
                "popMode",
                "skip",
                "more",
            )
    }
}
