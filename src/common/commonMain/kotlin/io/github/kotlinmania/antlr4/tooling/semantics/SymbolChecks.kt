/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.semantics

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.automata.LexerATNFactory
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.AttributeDict
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LabelElementPair
import io.github.kotlinmania.antlr4.tooling.tool.LabelType
import io.github.kotlinmania.antlr4.tooling.tool.LeftRecursiveRule
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/**
 * Validates grammar symbol tables for reserved rule names, action redefinitions, element-label
 * conflicts and type mismatches, rule argument arity, mode conflicts, and unreachable string tokens.
 */
class SymbolChecks(
    val g: Grammar,
    val collector: SymbolCollector,
) {
    internal val mutableNameToRuleMap: MutableMap<String, Rule> = HashMap()
    val nameToRuleMap: Map<String, Rule>
        get() = mutableNameToRuleMap

    internal val mutableTokenIDs: MutableSet<String> = HashSet()
    val tokenIDs: Set<String>
        get() = mutableTokenIDs

    internal val mutableActionScopeToActionNames: MutableMap<String, MutableSet<String>> = HashMap()
    internal val actionScopeToActionNames: Map<String, Set<String>>
        get() = mutableActionScopeToActionNames

    val actionScopes: Set<String>
        get() = LinkedHashSet(mutableActionScopeToActionNames.keys)

    fun getActionNames(scope: String): Set<String> =
        mutableActionScopeToActionNames[scope]?.let { LinkedHashSet(it) }.orEmpty()

    val errMgr: ErrorManager = g.tool.errMgr

    val reservedNames: Set<String> = HashSet(LexerATNFactory.commonConstants)

    init {
        for (tokenId in collector.tokenIDRefs) {
            mutableTokenIDs.add(tokenId.text)
        }
    }

    fun process() {
        for (r in g.rules.values) {
            mutableNameToRuleMap[r.name] = r
        }
        val ruleList = g.rules.values.toList()
        checkReservedNames(ruleList)
        checkActionRedefinitions(collector.namedActions)
        checkForLabelConflicts(ruleList)
    }

    fun checkActionRedefinitions(actions: List<GrammarAST>?) {
        if (actions == null) return
        var scope: String = g.defaultActionScope ?: ""
        for (ampersandAST in actions) {
            val nameNode = ampersandAST.getChild(0) ?: continue
            val name: String
            if (ampersandAST.childCount == 2) {
                name = nameNode.text
            } else {
                scope = nameNode.text
                name = ampersandAST.getChild(1)?.text ?: continue
            }
            val scopeActions = mutableActionScopeToActionNames.getOrPut(scope) { HashSet() }
            if (!scopeActions.add(name)) {
                errMgr.grammarError(
                    ErrorType.ACTION_REDEFINITION,
                    g.fileName,
                    nameNode.token,
                    name,
                )
            }
        }
    }

    fun checkForLabelConflicts(rules: List<Rule>) {
        for (r in rules) {
            checkForAttributeConflicts(r)

            val labelNameSpace = HashMap<String, LabelElementPair>()
            for (i in 1..r.numberOfAlts) {
                val a = r.alt[i] ?: continue
                for (pairs in a.mutableLabelDefs.values) {
                    if (r.hasAltSpecificContexts()) {
                        val labelPairs = HashMap<String, MutableList<LabelElementPair>>()
                        for (p in pairs) {
                            val labelName = findAltLabelName(p.label)
                            if (labelName != null) {
                                labelPairs.getOrPut(labelName) { ArrayList() }.add(p)
                            }
                        }

                        for (internalPairs in labelPairs.values) {
                            labelNameSpace.clear()
                            checkLabelPairs(r, labelNameSpace, internalPairs)
                        }
                    } else {
                        checkLabelPairs(r, labelNameSpace, pairs)
                    }
                }
            }
        }
    }

    private fun checkLabelPairs(
        r: Rule,
        labelNameSpace: MutableMap<String, LabelElementPair>,
        pairs: List<LabelElementPair>,
    ) {
        for (p in pairs) {
            checkForLabelConflict(r, p.label)
            val name = p.label.text
            val prev = labelNameSpace[name]
            if (prev == null) {
                labelNameSpace[name] = p
            } else {
                checkForTypeMismatch(r, prev, p)
            }
        }
    }

    private fun findAltLabelName(label: GrammarAST?): String? {
        if (label == null) return null
        if (label is AltAST) {
            val directAltLabel = label.altLabel
            if (directAltLabel != null) {
                return directAltLabel.toString()
            }
            val lrAltInfo = label.leftRecursiveAltInfo
            if (lrAltInfo != null) {
                return lrAltInfo.altLabel
            }
            return findAltLabelName(label.parent)
        }
        return findAltLabelName(label.parent)
    }

    private fun checkForTypeMismatch(
        r: Rule,
        prevLabelPair: LabelElementPair,
        labelPair: LabelElementPair,
    ) {
        if (prevLabelPair.type != labelPair.type) {
            val token: Token? =
                if (r is LeftRecursiveRule) {
                    r.ast?.getChild(0)?.token
                } else {
                    labelPair.label.token
                }
            errMgr.grammarError(
                ErrorType.LABEL_TYPE_CONFLICT,
                g.fileName,
                token,
                labelPair.label.text,
                "${labelPair.type}!=${prevLabelPair.type}",
            )
        }
        if (
            prevLabelPair.element.text != labelPair.element.text &&
            (prevLabelPair.type == LabelType.RULE_LABEL || prevLabelPair.type == LabelType.RULE_LIST_LABEL) &&
            (labelPair.type == LabelType.RULE_LABEL || labelPair.type == LabelType.RULE_LIST_LABEL)
        ) {
            val token: Token? =
                if (r is LeftRecursiveRule) {
                    r.ast?.getChild(0)?.token
                } else {
                    labelPair.label.token
                }
            val prevLabelOp = if (prevLabelPair.type == LabelType.RULE_LIST_LABEL) "+=" else "="
            val labelOp = if (labelPair.type == LabelType.RULE_LIST_LABEL) "+=" else "="
            errMgr.grammarError(
                ErrorType.LABEL_TYPE_CONFLICT,
                g.fileName,
                token,
                "${labelPair.label.text}$labelOp${labelPair.element.text}",
                "${prevLabelPair.label.text}$prevLabelOp${prevLabelPair.element.text}",
            )
        }
    }

    fun checkForLabelConflict(
        r: Rule,
        labelID: GrammarAST,
    ) {
        val name = labelID.text
        if (mutableNameToRuleMap.containsKey(name)) {
            errMgr.grammarError(ErrorType.LABEL_CONFLICTS_WITH_RULE, g.fileName, labelID.token, name, r.name)
        }

        if (mutableTokenIDs.contains(name)) {
            errMgr.grammarError(ErrorType.LABEL_CONFLICTS_WITH_TOKEN, g.fileName, labelID.token, name, r.name)
        }

        if (r.args?.get(name) != null) {
            errMgr.grammarError(ErrorType.LABEL_CONFLICTS_WITH_ARG, g.fileName, labelID.token, name, r.name)
        }

        if (r.retvals?.get(name) != null) {
            errMgr.grammarError(ErrorType.LABEL_CONFLICTS_WITH_RETVAL, g.fileName, labelID.token, name, r.name)
        }

        if (r.locals?.get(name) != null) {
            errMgr.grammarError(ErrorType.LABEL_CONFLICTS_WITH_LOCAL, g.fileName, labelID.token, name, r.name)
        }
    }

    fun checkForAttributeConflicts(r: Rule) {
        checkDeclarationRuleConflicts(r, r.args, mutableNameToRuleMap.keys, ErrorType.ARG_CONFLICTS_WITH_RULE)
        checkDeclarationRuleConflicts(r, r.args, mutableTokenIDs, ErrorType.ARG_CONFLICTS_WITH_TOKEN)

        checkDeclarationRuleConflicts(r, r.retvals, mutableNameToRuleMap.keys, ErrorType.RETVAL_CONFLICTS_WITH_RULE)
        checkDeclarationRuleConflicts(r, r.retvals, mutableTokenIDs, ErrorType.RETVAL_CONFLICTS_WITH_TOKEN)

        checkDeclarationRuleConflicts(r, r.locals, mutableNameToRuleMap.keys, ErrorType.LOCAL_CONFLICTS_WITH_RULE)
        checkDeclarationRuleConflicts(r, r.locals, mutableTokenIDs, ErrorType.LOCAL_CONFLICTS_WITH_TOKEN)

        checkLocalConflictingDeclarations(r, r.retvals, r.args, ErrorType.RETVAL_CONFLICTS_WITH_ARG)
        checkLocalConflictingDeclarations(r, r.locals, r.args, ErrorType.LOCAL_CONFLICTS_WITH_ARG)
        checkLocalConflictingDeclarations(r, r.locals, r.retvals, ErrorType.LOCAL_CONFLICTS_WITH_RETVAL)
    }

    private fun checkDeclarationRuleConflicts(
        r: Rule,
        attributes: AttributeDict?,
        ruleNames: Set<String>,
        errorType: ErrorType,
    ) {
        if (attributes == null) return
        for (attribute in attributes.attributes.values) {
            if (ruleNames.contains(attribute.name)) {
                errMgr.grammarError(
                    errorType,
                    g.fileName,
                    attribute.token ?: r.ast?.getChild(0)?.token,
                    attribute.name,
                    r.name,
                )
            }
        }
    }

    private fun checkLocalConflictingDeclarations(
        r: Rule,
        attributes: AttributeDict?,
        referenceAttributes: AttributeDict?,
        errorType: ErrorType,
    ) {
        if (attributes == null || referenceAttributes == null) return
        val conflictingKeys = attributes.intersection(referenceAttributes)
        for (key in conflictingKeys) {
            errMgr.grammarError(
                errorType,
                g.fileName,
                attributes.get(key)?.token ?: r.ast?.getChild(0)?.token,
                key,
                r.name,
            )
        }
    }

    private fun checkReservedNames(rules: Collection<Rule>) {
        for (rule in rules) {
            if (reservedNames.contains(rule.name)) {
                errMgr.grammarError(
                    ErrorType.RESERVED_RULE_NAME,
                    g.fileName,
                    rule.ast?.getChild(0)?.token,
                    rule.name,
                )
            }
        }
    }

    fun checkForModeConflicts(g: Grammar) {
        if (g.isLexer && g is LexerGrammar) {
            for (modeName in g.modes.keys) {
                val modeRules = g.modes[modeName]
                if (modeName != "DEFAULT_MODE" && reservedNames.contains(modeName)) {
                    val rule = modeRules?.firstOrNull()
                    g.tool.errMgr.grammarError(
                        ErrorType.MODE_CONFLICTS_WITH_COMMON_CONSTANTS,
                        g.fileName,
                        rule?.ast?.parent?.token,
                        modeName,
                    )
                }

                if (g.getTokenType(modeName) != Token.INVALID_TYPE) {
                    val rule = modeRules?.firstOrNull()
                    g.tool.errMgr.grammarError(
                        ErrorType.MODE_CONFLICTS_WITH_TOKEN,
                        g.fileName,
                        rule?.ast?.parent?.token,
                        modeName,
                    )
                }
            }
        }
    }

    fun checkForUnreachableTokens(g: Grammar) {
        if (g.isLexer && g is LexerGrammar) {
            for (rules in g.modes.values) {
                val stringLiteralRules = ArrayList<Rule>()
                val stringLiteralValues = ArrayList<List<String>>()
                for (rule in rules) {
                    val ruleStringAlts = getSingleTokenValues(rule)
                    if (ruleStringAlts.isNotEmpty()) {
                        stringLiteralRules.add(rule)
                        stringLiteralValues.add(ruleStringAlts)
                    }
                }

                for (i in stringLiteralRules.indices) {
                    val firstTokenStringValues = stringLiteralValues[i]
                    val rule1 = stringLiteralRules[i]
                    checkForOverlap(g, rule1, rule1, firstTokenStringValues, firstTokenStringValues)

                    if (!rule1.isFragment) {
                        for (j in i + 1 until stringLiteralRules.size) {
                            val rule2 = stringLiteralRules[j]
                            if (!rule2.isFragment) {
                                checkForOverlap(
                                    g,
                                    rule1,
                                    rule2,
                                    firstTokenStringValues,
                                    stringLiteralValues[j],
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getSingleTokenValues(rule: Rule): List<String> {
        val values = ArrayList<String>()
        for (alt in rule.alt) {
            val altNode = alt?.ast ?: continue
            val firstChild = altNode.getChild(0)
            val rootNode: GrammarAST =
                if (altNode.childCount == 2 && firstChild is AltAST && altNode.getChild(1) != null) {
                    firstChild
                } else {
                    altNode
                }

            if (rootNode.tokenStartIndex == -1) {
                continue
            }

            var ignore = false
            val currentValue = StringBuilder()
            for (i in 0 until rootNode.childCount) {
                val child = rootNode.getChild(i)
                if (child !is TerminalAST) {
                    ignore = true
                    break
                }
                val tok = child.token
                if (tok == null || tok.type != ANTLRParserTypes.STRING_LITERAL) {
                    ignore = true
                    break
                }
                val text = tok.text ?: ""
                if (text.length >= 2) {
                    currentValue.append(text.substring(1, text.length - 1))
                }
            }

            if (!ignore) {
                values.add(currentValue.toString())
            }
        }
        return values
    }

    private fun checkForOverlap(
        g: Grammar,
        rule1: Rule,
        rule2: Rule,
        firstTokenStringValues: List<String>,
        secondTokenStringValues: List<String>,
    ) {
        for (i in firstTokenStringValues.indices) {
            val secondTokenInd = if (rule1 === rule2) i + 1 else 0
            val str1 = firstTokenStringValues[i]
            for (j in secondTokenInd until secondTokenStringValues.size) {
                val str2 = secondTokenStringValues[j]
                if (str1 == str2) {
                    errMgr.grammarError(
                        ErrorType.TOKEN_UNREACHABLE,
                        g.fileName,
                        rule2.ast?.getChild(0)?.token,
                        rule2.name,
                        str2,
                        rule1.name,
                    )
                }
            }
        }
    }

    fun checkRuleArgs(
        g: Grammar,
        rulerefs: List<GrammarAST>?,
    ) {
        if (rulerefs == null) return
        for (ref in rulerefs) {
            val ruleName = ref.text
            val r = g.getRule(ruleName)
            val arg = ref.getFirstChildWithType(ANTLRParserTypes.ARG_ACTION)
            if (arg != null && (r == null || r.args == null)) {
                errMgr.grammarError(
                    ErrorType.RULE_HAS_NO_ARGS,
                    g.fileName,
                    ref.token,
                    ruleName,
                )
            } else if (arg == null && r != null && r.args != null) {
                errMgr.grammarError(
                    ErrorType.MISSING_RULE_ARGS,
                    g.fileName,
                    ref.token,
                    ruleName,
                )
            }
        }
    }

    fun checkForQualifiedRuleIssues(
        g: Grammar,
        qualifiedRuleRefs: List<GrammarAST>,
    ) {
        for (dot in qualifiedRuleRefs) {
            val grammar = dot.getChild(0) ?: continue
            val rule = dot.getChild(1) ?: continue
            g.tool.log("semantics", "${grammar.text}.${rule.text}")
            val delegate = g.getImportedGrammar(grammar.text)
            if (delegate == null) {
                errMgr.grammarError(
                    ErrorType.NO_SUCH_GRAMMAR_SCOPE,
                    g.fileName,
                    grammar.token,
                    grammar.text,
                    rule.text,
                )
            } else if (g.getRule(grammar.text, rule.text) == null) {
                errMgr.grammarError(
                    ErrorType.NO_SUCH_RULE_IN_SCOPE,
                    g.fileName,
                    rule.token,
                    grammar.text,
                    rule.text,
                )
            }
        }
    }
}
