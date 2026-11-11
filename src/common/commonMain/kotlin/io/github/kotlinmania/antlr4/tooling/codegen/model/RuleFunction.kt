/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.misc.OrderedHashSet
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.AltLabelStructDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.AttributeDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextRuleGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextRuleListGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextRuleListIndexedGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextTokenGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextTokenListGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextTokenListIndexedGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.Decl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.RuleContextDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.RuleContextListDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.StructDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenListDecl
import io.github.kotlinmania.antlr4.tooling.misc.FrequencySet
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.LabelElementPair
import io.github.kotlinmania.antlr4.tooling.tool.LabelType
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Output model node representing a generated parser rule method and its context hierarchy. */
open class RuleFunction(
    factory: OutputModelFactory,
    val rule: Rule,
) : OutputModelObject(factory, rule.ast) {
    val name: String = rule.name
    val modifiers: List<String>
    var ctxType: String
    val ruleLabels: Collection<String>?
    val tokenLabels: Collection<String>?
    val startState: Int
    val index: Int = rule.index
    val escapedName: String = factory.generator.target.escapeIfNeeded(rule.name)

    @ModelElement
    var code: List<SrcOp>? = null

    internal val mutableLocals: OrderedHashSet<Decl> = OrderedHashSet()

    @ModelElement
    val locals: List<Decl>
        get() = mutableLocals.elements()

    internal var mutableArgs: MutableList<AttributeDecl>? = null

    @ModelElement
    val args: List<AttributeDecl>?
        get() = mutableArgs

    @ModelElement
    var ruleCtx: StructDecl

    internal var mutableAltLabelCtxs: LinkedHashMap<String, AltLabelStructDecl>? = null

    @ModelElement
    val altLabelCtxs: Map<String, AltLabelStructDecl>?
        get() = mutableAltLabelCtxs

    @ModelElement
    var namedActions: Map<String, Action>

    @ModelElement
    var postamble: List<SrcOp>? = null

    @ModelElement
    var exceptions: List<ExceptionClause>? = null

    @ModelElement
    var finallyAction: Action? = null

    var hasLookaheadBlock: Boolean = false
    var variantOf: String? = null

    init {
        val rawMods = rule.modifiers
        modifiers =
            if (rawMods != null) {
                rawMods.map { it.text }
            } else {
                emptyList()
            }

        ruleLabels = rule.elementLabelNames
        tokenLabels = rule.tokenRefs

        val builtAtn = factory.grammar.atn
        val ruleStart: ATNState? = builtAtn?.ruleToStartState?.getOrNull(rule.index)
        startState = ruleStart?.stateNumber ?: -1

        ruleCtx = StructDecl(factory, rule)
        ctxType = ruleCtx.name
        addContextGetters(factory, rule)

        val ruleArgs = rule.args
        if (ruleArgs != null) {
            val argList = ArrayList<AttributeDecl>()
            for (attr in ruleArgs.attributes.values) {
                argList.add(AttributeDecl(factory, attr))
            }
            mutableArgs = argList
            ruleCtx.addDecls(ruleArgs.attributes.values.toList())
            ruleCtx.mutableCtorAttrs.addAll(ruleCtx.attrs)
        }
        val ruleRetvals = rule.retvals
        if (ruleRetvals != null) {
            ruleCtx.addDecls(ruleRetvals.attributes.values.toList())
        }
        val ruleLocals = rule.locals
        if (ruleLocals != null) {
            ruleCtx.addDecls(ruleLocals.attributes.values.toList())
        }

        val actionsMap = LinkedHashMap<String, Action>()
        for ((actionName, astNode) in rule.namedActions) {
            actionsMap[actionName] = Action(factory, astNode)
        }
        namedActions = actionsMap
    }

    fun addContextGetters(
        factory: OutputModelFactory,
        r: Rule,
    ) {
        val unlabeledAlts = r.unlabeledAltASTs
        if (unlabeledAlts != null) {
            val decls = getDeclsForAllElements(unlabeledAlts)
            for (d in decls) {
                ruleCtx.addDecl(d)
            }
        }

        val altLabels = r.altLabels
        if (altLabels != null) {
            val map = LinkedHashMap<String, AltLabelStructDecl>()
            for ((labelName, occurrences) in altLabels) {
                val firstAltNum = occurrences.firstOrNull()?.altNum ?: 1
                val labelDecl = AltLabelStructDecl(factory, r, firstAltNum, labelName)
                map[labelName] = labelDecl
                val altASTs = occurrences.map { it.altAST }
                for (d in getDeclsForAllElements(altASTs)) {
                    labelDecl.addDecl(d)
                }
            }
            mutableAltLabelCtxs = map
        }
    }

    fun getDeclsForAllElements(altASTs: List<AltAST>): Set<Decl> {
        val ownerFactory = requireNotNull(factory)
        val needsList = LinkedHashSet<String>()
        val nonOptional = LinkedHashSet<String>()
        val allElements = ArrayList<GrammarAST>()
        var firstAlt = true

        for (ast in altASTs) {
            val visitor = ElementFrequenciesVisitor()
            val (minFreq, maxFreq) = visitor.computeMinAndMaxForAlt(ast)

            for ((key, count) in maxFreq.asMap()) {
                if (count.v > 1) {
                    needsList.add(key)
                }
            }

            if (firstAlt) {
                for ((key, count) in minFreq.asMap()) {
                    if (count.v >= 1) {
                        nonOptional.add(key)
                    }
                }
                firstAlt = false
            } else {
                val keep = LinkedHashSet<String>()
                for (key in nonOptional) {
                    if (minFreq.count(key) >= 1) {
                        keep.add(key)
                    }
                }
                nonOptional.clear()
                nonOptional.addAll(keep)
            }

            val refNodes =
                ast.getNodesWithType(
                    io.github.kotlinmania.antlr4.misc.IntervalSet.of(
                        ANTLRParserTypes.RULE_REF,
                        ANTLRParserTypes.RULE_REF,
                    ).apply { add(ANTLRParserTypes.TOKEN_REF) },
                )
            allElements.addAll(refNodes)
        }

        val decls = LinkedHashSet<Decl>()
        for (t in allElements) {
            val name = t.text
            if (name == "EOF" && t.type != ANTLRParserTypes.TOKEN_REF) {
                continue
            }
            if (t.type == ANTLRParserTypes.RULE_REF) {
                val targetRule = ownerFactory.grammar.getRule(name) ?: continue
                val ctxName = ownerFactory.generator.target.getRuleFunctionContextStructName(targetRule)
                if (needsList.contains(name)) {
                    decls.add(ContextRuleListGetterDecl(ownerFactory, name, ctxName))
                    decls.add(ContextRuleListIndexedGetterDecl(ownerFactory, name, ctxName))
                } else {
                    val optional = !nonOptional.contains(name)
                    decls.add(ContextRuleGetterDecl(ownerFactory, name, ctxName, optional))
                }
            } else if (t.type == ANTLRParserTypes.TOKEN_REF) {
                if (needsList.contains(name)) {
                    decls.add(ContextTokenListGetterDecl(ownerFactory, name))
                    decls.add(ContextTokenListIndexedGetterDecl(ownerFactory, name))
                } else {
                    val optional = !nonOptional.contains(name)
                    decls.add(ContextTokenGetterDecl(ownerFactory, name, optional))
                }
            }
        }
        return decls
    }

    fun getEffectiveAltLabelContexts(controller: io.github.kotlinmania.antlr4.tooling.codegen.OutputModelController): Map<Int, AltLabelStructDecl> {
        val result = LinkedHashMap<Int, AltLabelStructDecl>()
        val labelMap = mutableAltLabelCtxs ?: return result
        val altLabels = rule.altLabels ?: return result
        for ((labelName, occurrences) in altLabels) {
            val struct = labelMap[labelName] ?: continue
            for (occ in occurrences) {
                result[occ.altNum] = struct
            }
        }
        return result
    }

    fun fillNamedActions(
        factory: OutputModelFactory,
        r: Rule,
    ) {
        val rFinally = r.finallyAction
        if (rFinally != null) {
            finallyAction = Action(factory, rFinally)
        }
        if (r.exceptions.isNotEmpty()) {
            val clauses = ArrayList<ExceptionClause>()
            for (e in r.exceptions) {
                val catchArg = e.getChild(0) as? io.github.kotlinmania.antlr4.tooling.ast.ActionAST
                val catchBody = e.getChild(1) as? io.github.kotlinmania.antlr4.tooling.ast.ActionAST
                if (catchArg != null && catchBody != null) {
                    clauses.add(ExceptionClause(factory, catchArg, catchBody))
                }
            }
            exceptions = clauses
        }
    }

    fun addLocalDecl(d: Decl) {
        mutableLocals.add(d)
        d.isImplicit = true
    }

    fun addContextDecl(
        altLabel: String?,
        d: Decl,
    ) {
        val altBlock = d.ast?.getAncestor(ANTLRParserTypes.ALT) as? AltAST
        if (altLabel != null) {
            val altCtx = mutableAltLabelCtxs?.get(altLabel)
            if (altCtx != null) {
                altCtx.addDecl(d)
                return
            }
        }
        if (altBlock != null) {
            val explicitLabel = altBlock.altLabel?.text ?: altBlock.leftRecursiveAltInfo?.altLabel
            if (explicitLabel != null) {
                val altCtx = mutableAltLabelCtxs?.get(explicitLabel)
                if (altCtx != null) {
                    altCtx.addDecl(d)
                    return
                }
            }
        }
        ruleCtx.addDecl(d)
    }

    fun getDeclForAltElement(
        t: GrammarAST,
        refLabelName: String?,
        needList: Boolean,
    ): Decl? {
        val ownerFactory = requireNotNull(factory)
        return if (t.type == ANTLRParserTypes.RULE_REF) {
            val rref = ownerFactory.grammar.getRule(t.text) ?: return null
            val ctxName = ownerFactory.generator.target.getRuleFunctionContextStructName(rref)
            val varName = refLabelName ?: ownerFactory.generator.target.getImplicitRuleLabel(t.text)
            if (needList) {
                RuleContextListDecl(ownerFactory, varName, ctxName)
            } else {
                RuleContextDecl(ownerFactory, varName, ctxName).apply {
                    isImplicit = refLabelName == null
                }
            }
        } else {
            val varName = refLabelName ?: ownerFactory.generator.target.getImplicitTokenLabel(t.text)
            if (needList) {
                TokenListDecl(ownerFactory, varName)
            } else {
                TokenDecl(ownerFactory, varName).apply {
                    isImplicit = refLabelName == null
                }
            }
        }
    }

    internal fun defineLabelDecls(pairs: List<LabelElementPair>): Decl? {
        val ownerFactory = requireNotNull(factory)
        var firstDecl: Decl? = null
        for (pair in pairs) {
            val decl: Decl? =
                when (pair.type) {
                    LabelType.RULE_LABEL -> {
                        val rref = ownerFactory.grammar.getRule(pair.element.text)
                        if (rref != null) {
                            val ctxName = ownerFactory.generator.target.getRuleFunctionContextStructName(rref)
                            RuleContextDecl(ownerFactory, pair.label.text, ctxName)
                        } else {
                            null
                        }
                    }

                    LabelType.RULE_LIST_LABEL -> {
                        val rref = ownerFactory.grammar.getRule(pair.element.text)
                        if (rref != null) {
                            val ctxName = ownerFactory.generator.target.getRuleFunctionContextStructName(rref)
                            RuleContextListDecl(ownerFactory, pair.label.text, ctxName)
                        } else {
                            null
                        }
                    }

                    LabelType.TOKEN_LABEL, LabelType.LEXER_STRING_LABEL -> {
                        TokenDecl(ownerFactory, pair.label.text)
                    }

                    LabelType.TOKEN_LIST_LABEL -> {
                        TokenListDecl(ownerFactory, pair.label.text)
                    }

                    null -> null
                }
            if (decl != null) {
                decl.ast = pair.label
                val enclosingAlt = pair.label.getAncestor(ANTLRParserTypes.ALT) as? AltAST
                val altLabel = enclosingAlt?.altLabel?.text ?: enclosingAlt?.leftRecursiveAltInfo?.altLabel
                addContextDecl(altLabel, decl)
                if (firstDecl == null) {
                    firstDecl = decl
                }
            }
        }
        return firstDecl
    }
}
