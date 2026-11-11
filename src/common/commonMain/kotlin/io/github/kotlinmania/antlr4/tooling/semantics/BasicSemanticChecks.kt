/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.semantics

import io.github.kotlinmania.antlr4.IntStream
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.misc.MultiMap
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarASTWithOptions
import io.github.kotlinmania.antlr4.tooling.ast.GrammarRootAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleRefAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.misc.Utils
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.parse.GrammarTreeVisitor
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar

/**
 * Structural and option-level semantic validation pass over a [Grammar]'s AST prior to
 * left-recursion elimination and symbol resolution.
 */
class BasicSemanticChecks(
    val g: Grammar,
    val ruleCollector: RuleCollector,
) : GrammarTreeVisitor() {
    val errMgr: ErrorManager = g.tool.errMgr

    var checkAssocElementOption: Boolean = true

    private var nonFragmentRuleCount: Int = 0
    private var inFragmentRule: Boolean = false
    private var grammarCaseInsensitive: Boolean = false

    override fun getErrorManager(): ErrorManager = errMgr

    fun process() {
        visitGrammar(g.ast)
    }

    override fun discoverGrammar(
        root: GrammarRootAST,
        id: GrammarAST,
    ) {
        checkGrammarName(id.token)
    }

    override fun finishPrequels(firstPrequel: GrammarAST?) {
        if (firstPrequel == null) return
        val parent = firstPrequel.parent ?: return
        val options = parent.getAllChildrenWithType(ANTLRParserTypes.OPTIONS)
        val imports = parent.getAllChildrenWithType(ANTLRParserTypes.IMPORT)
        val tokens = parent.getAllChildrenWithType(ANTLRParserTypes.TOKENS_SPEC)
        checkNumPrequels(options, imports, tokens)
    }

    override fun importGrammar(
        label: GrammarAST?,
        id: GrammarAST,
    ) {
        checkImport(id.token)
    }

    override fun discoverRules(rules: GrammarAST) {
        checkNumRules(rules)
    }

    override fun enterMode(tree: GrammarAST) {
        nonFragmentRuleCount = 0
    }

    override fun exitMode(tree: GrammarAST) {
        if (nonFragmentRuleCount == 0) {
            var token: Token? = tree.token
            var name = "?"
            if (tree.childCount > 0) {
                val firstChild = tree.getChild(0)
                val childText = firstChild?.text
                if (!childText.isNullOrEmpty()) {
                    name = childText
                }
                if (firstChild != null) {
                    token = firstChild.token
                }
            }
            g.tool.errMgr.grammarError(ErrorType.MODE_WITHOUT_RULES, g.fileName, token, name, g)
        }
    }

    override fun modeDef(
        m: GrammarAST,
        id: GrammarAST,
    ) {
        if (!g.isLexer) {
            g.tool.errMgr.grammarError(
                ErrorType.MODE_NOT_IN_LEXER,
                g.fileName,
                id.token,
                id.token?.text ?: id.text,
                g,
            )
        }
    }

    override fun discoverRule(
        rule: RuleAST,
        id: GrammarAST,
        modifiers: List<GrammarAST>,
        arg: ActionAST?,
        returns: ActionAST?,
        thrws: GrammarAST?,
        options: GrammarAST?,
        locals: ActionAST?,
        actions: List<GrammarAST>,
        block: GrammarAST,
    ) {
        checkInvalidRuleDef(id.token)
    }

    override fun discoverLexerRule(
        rule: RuleAST,
        id: GrammarAST,
        modifiers: List<GrammarAST>,
        options: GrammarAST?,
        block: GrammarAST,
    ) {
        checkInvalidRuleDef(id.token)

        for (tree in modifiers) {
            if (tree.type == ANTLRParserTypes.FRAGMENT) {
                inFragmentRule = true
            }
        }

        if (!inFragmentRule) {
            nonFragmentRuleCount++
        }
    }

    override fun exitLexerRule(tree: GrammarAST) {
        inFragmentRule = false
    }

    override fun ruleRef(
        ref: GrammarAST,
        arg: ActionAST?,
    ) {
        checkInvalidRuleRef(ref.token)
    }

    override fun grammarOption(
        id: GrammarAST,
        valueAST: GrammarAST,
    ) {
        checkOptions(g.ast, id.token, valueAST)
    }

    override fun ruleOption(
        id: GrammarAST,
        valueAST: GrammarAST,
    ) {
        checkOptions(id.getAncestor(ANTLRParserTypes.RULE), id.token, valueAST)
    }

    override fun blockOption(
        id: GrammarAST,
        valueAST: GrammarAST,
    ) {
        checkOptions(id.getAncestor(ANTLRParserTypes.BLOCK), id.token, valueAST)
    }

    override fun defineToken(id: GrammarAST) {
        checkTokenDefinition(id.token)
    }

    override fun enterChannelsSpec(tree: GrammarAST) {
        val errorType =
            when {
                g.isParser -> ErrorType.CHANNELS_BLOCK_IN_PARSER_GRAMMAR
                g.isCombined -> ErrorType.CHANNELS_BLOCK_IN_COMBINED_GRAMMAR
                else -> null
            }
        if (errorType != null) {
            g.tool.errMgr.grammarError(errorType, g.fileName, tree.token)
        }
    }

    override fun defineChannel(id: GrammarAST) {
        checkChannelDefinition(id.token)
    }

    override fun elementOption(
        t: GrammarASTWithOptions,
        id: GrammarAST,
        valueAST: GrammarAST?,
    ) {
        checkElementOptions(t, id, valueAST)
    }

    override fun finishRule(
        rule: RuleAST,
        id: GrammarAST,
        block: GrammarAST,
    ) {
        if (rule.isLexerRule) return
        val blk = rule.getFirstChildWithType(ANTLRParserTypes.BLOCK) as? BlockAST ?: return
        val nalts = blk.childCount
        val idAST = rule.getChild(0) ?: return
        val ruleName = rule.ruleName
        for (i in 0 until nalts) {
            val altAST = blk.getChild(i) as? AltAST ?: continue
            val labelNode = altAST.altLabel
            if (labelNode != null) {
                val altLabel = labelNode.text
                val conflictingRule = ruleCollector.orderedRules[Utils.decapitalize(altLabel)]
                if (conflictingRule != null) {
                    g.tool.errMgr.grammarError(
                        ErrorType.ALT_LABEL_CONFLICTS_WITH_RULE,
                        g.fileName,
                        labelNode.token,
                        altLabel,
                        conflictingRule.name,
                    )
                }
                val prevRuleForLabel = ruleCollector.mutableAltLabelToRuleName[altLabel]
                if (prevRuleForLabel != null && prevRuleForLabel != ruleName) {
                    g.tool.errMgr.grammarError(
                        ErrorType.ALT_LABEL_REDEF,
                        g.fileName,
                        labelNode.token,
                        altLabel,
                        ruleName,
                        prevRuleForLabel,
                    )
                }
            }
        }
        val altLabels = if (ruleName != null) ruleCollector.mutableRuleToAltLabels[ruleName] else null
        val numAltLabels = altLabels?.size ?: 0
        if (numAltLabels > 0 && nalts != numAltLabels) {
            g.tool.errMgr.grammarError(
                ErrorType.RULE_WITH_TOO_FEW_ALT_LABELS,
                g.fileName,
                idAST.token,
                ruleName,
            )
        }
    }

    fun checkGrammarName(nameToken: Token?) {
        if (nameToken == null) return
        val fullyQualifiedName = nameToken.inputStream?.sourceName
        if (
            fullyQualifiedName.isNullOrEmpty() ||
            fullyQualifiedName == IntStream.UNKNOWN_SOURCE_NAME
        ) {
            return
        }

        val lastSep = maxOf(fullyQualifiedName.lastIndexOf('/'), fullyQualifiedName.lastIndexOf('\\'))
        val fileName = if (lastSep >= 0) fullyQualifiedName.substring(lastSep + 1) else fullyQualifiedName
        if (g.originalGrammar != null) return

        val tokenText = nameToken.text
        if (
            Utils.stripFileExtension(fileName) != tokenText &&
            fileName != Grammar.GRAMMAR_FROM_STRING_NAME
        ) {
            g.tool.errMgr.grammarError(
                ErrorType.FILE_AND_GRAMMAR_NAME_DIFFER,
                fileName,
                nameToken,
                tokenText,
                fileName,
            )
        }
    }

    fun checkNumRules(rulesNode: GrammarAST) {
        if (rulesNode.childCount == 0) {
            val root = rulesNode.parent
            val idNode = root?.getChild(0)
            g.tool.errMgr.grammarError(
                ErrorType.NO_RULES,
                g.fileName,
                null,
                idNode?.text,
                g,
            )
        }
    }

    fun checkNumPrequels(
        options: List<GrammarAST>?,
        imports: List<GrammarAST>?,
        tokens: List<GrammarAST>?,
    ) {
        val secondOptionTokens = ArrayList<Token>()
        if (options != null && options.size > 1) {
            options[1].token?.let { secondOptionTokens.add(it) }
        }
        if (imports != null && imports.size > 1) {
            imports[1].token?.let { secondOptionTokens.add(it) }
        }
        if (tokens != null && tokens.size > 1) {
            tokens[1].token?.let { secondOptionTokens.add(it) }
        }
        for (t in secondOptionTokens) {
            val fileName = sourceFileNameOrGrammarFile(t)
            g.tool.errMgr.grammarError(
                ErrorType.REPEATED_PREQUEL,
                fileName,
                t,
            )
        }
    }

    fun checkInvalidRuleDef(ruleID: Token?) {
        if (ruleID == null) return
        val fileName = sourceFileNameOrGrammarFile(ruleID)
        val text = ruleID.text ?: return
        if (g.isLexer && text.isNotEmpty() && text[0].isLowerCase()) {
            g.tool.errMgr.grammarError(
                ErrorType.PARSER_RULES_NOT_ALLOWED,
                fileName,
                ruleID,
                text,
            )
        }
        if (g.isParser && Grammar.isTokenName(text)) {
            g.tool.errMgr.grammarError(
                ErrorType.LEXER_RULES_NOT_ALLOWED,
                fileName,
                ruleID,
                text,
            )
        }
    }

    fun checkInvalidRuleRef(ruleID: Token?) {
        if (ruleID == null) return
        val fileName = sourceFileNameOrGrammarFile(ruleID)
        val text = ruleID.text ?: return
        if (g.isLexer && text.isNotEmpty() && text[0].isLowerCase()) {
            g.tool.errMgr.grammarError(
                ErrorType.PARSER_RULE_REF_IN_LEXER_RULE,
                fileName,
                ruleID,
                text,
                currentRuleName,
            )
        }
    }

    fun checkTokenDefinition(tokenID: Token?) {
        if (tokenID == null) return
        val fileName = sourceFileNameOrGrammarFile(tokenID)
        val text = tokenID.text ?: return
        if (!Grammar.isTokenName(text)) {
            g.tool.errMgr.grammarError(
                ErrorType.TOKEN_NAMES_MUST_START_UPPER,
                fileName,
                tokenID,
                text,
            )
        }
    }

    fun checkChannelDefinition(tokenID: Token?) {
        if (tokenID == null) return
    }

    override fun enterLexerElement(tree: GrammarAST) {
    }

    override fun enterLexerCommand(tree: GrammarAST) {
        checkElementIsOuterMostInSingleAlt(tree)

        if (inFragmentRule) {
            val fileName = sourceFileNameOrGrammarFile(tree.token)
            g.tool.errMgr.grammarError(
                ErrorType.FRAGMENT_ACTION_IGNORED,
                fileName,
                tree.token,
                currentRuleName,
            )
        }
    }

    override fun actionInAlt(action: ActionAST) {
        if (inFragmentRule) {
            val fileName = sourceFileNameOrGrammarFile(action.token)
            g.tool.errMgr.grammarError(
                ErrorType.FRAGMENT_ACTION_IGNORED,
                fileName,
                action.token,
                currentRuleName,
            )
        }
    }

    private fun checkElementIsOuterMostInSingleAlt(tree: GrammarAST) {
        val alt = tree.parent ?: return
        val blk = alt.parent ?: return
        val outerMostAlt = blk.parent?.type == ANTLRParserTypes.RULE
        val rule = tree.getAncestor(ANTLRParserTypes.RULE)
        val fileName = sourceFileNameOrGrammarFile(tree.token)
        if (!outerMostAlt || blk.childCount > 1) {
            g.tool.errMgr.grammarError(
                ErrorType.LEXER_COMMAND_PLACEMENT_ISSUE,
                fileName,
                tree.token,
                rule?.getChild(0)?.text,
            )
        }
    }

    override fun label(
        op: GrammarAST,
        id: GrammarAST,
        element: GrammarAST,
    ) {
        when (element.type) {
            ANTLRParserTypes.TOKEN_REF,
            ANTLRParserTypes.STRING_LITERAL,
            ANTLRParserTypes.RANGE,
            ANTLRParserTypes.SET,
            ANTLRParserTypes.NOT,
            ANTLRParserTypes.RULE_REF,
            ANTLRParserTypes.WILDCARD,
            -> return

            else -> {
                val fileName = sourceFileNameOrGrammarFile(id.token)
                g.tool.errMgr.grammarError(
                    ErrorType.LABEL_BLOCK_NOT_A_SET,
                    fileName,
                    id.token,
                    id.text,
                )
            }
        }
    }

    override fun enterTerminal(tree: GrammarAST) {
        if (tree.text == "''") {
            g.tool.errMgr.grammarError(
                ErrorType.EMPTY_STRINGS_AND_SETS_NOT_ALLOWED,
                g.fileName,
                tree.token,
                "''",
            )
        }
    }

    fun checkOptions(
        parent: GrammarAST?,
        optionID: Token?,
        valueAST: GrammarAST,
    ) {
        if (parent == null || optionID == null) return
        val parentType = parent.type
        val optionsToCheck: Set<String>? =
            when (parentType) {
                ANTLRParserTypes.BLOCK -> if (g.isLexer) Grammar.lexerBlockOptions else Grammar.parserBlockOptions
                ANTLRParserTypes.RULE -> if (g.isLexer) Grammar.lexerRuleOptions else Grammar.parseRuleOptions
                ANTLRParserTypes.GRAMMAR ->
                    if (g.type == ANTLRParserTypes.LEXER) {
                        Grammar.lexerOptions
                    } else {
                        Grammar.parserOptions
                    }
                else -> null
            }
        val optionName = optionID.text ?: return
        if (optionsToCheck != null && !optionsToCheck.contains(optionName)) {
            g.tool.errMgr.grammarError(ErrorType.ILLEGAL_OPTION, g.fileName, optionID, optionName)
        } else {
            checkCaseInsensitiveOption(optionID, valueAST, parentType)
        }
    }

    private fun checkCaseInsensitiveOption(
        optionID: Token,
        valueAST: GrammarAST,
        parentType: Int,
    ) {
        val optionName = optionID.text ?: return
        if (optionName == Grammar.CASE_INSENSITIVE_OPTION_NAME) {
            val valueText = valueAST.text
            if (valueText == "true" || valueText == "false") {
                val currentValue = valueText.toBoolean()
                if (parentType == ANTLRParserTypes.GRAMMAR) {
                    grammarCaseInsensitive = currentValue
                } else if (grammarCaseInsensitive == currentValue) {
                    g.tool.errMgr.grammarError(
                        ErrorType.REDUNDANT_CASE_INSENSITIVE_LEXER_RULE_OPTION,
                        g.fileName,
                        optionID,
                        currentValue,
                    )
                }
            } else {
                g.tool.errMgr.grammarError(
                    ErrorType.ILLEGAL_OPTION_VALUE,
                    g.fileName,
                    valueAST.token,
                    optionName,
                    valueText,
                )
            }
        }
    }

    fun checkElementOptions(
        elem: GrammarASTWithOptions,
        id: GrammarAST?,
        valueAST: GrammarAST?,
    ): Boolean {
        if (checkAssocElementOption && id != null && id.text == "assoc") {
            if (elem.type != ANTLRParserTypes.ALT) {
                val optionID = id.token
                val fileName = sourceFileNameOrGrammarFile(optionID)
                g.tool.errMgr.grammarError(
                    ErrorType.UNRECOGNIZED_ASSOC_OPTION,
                    fileName,
                    optionID,
                    currentRuleName,
                )
            }
        }

        if (elem is RuleRefAST) {
            return checkRuleRefOptions(elem, id, valueAST)
        }
        if (elem is TerminalAST) {
            return checkTokenOptions(elem, id, valueAST)
        }
        if (elem.type == ANTLRParserTypes.ACTION) {
            return false
        }
        if (elem.type == ANTLRParserTypes.SEMPRED && id != null) {
            val optionID = id.token
            val optionText = optionID?.text ?: id.text
            val fileName = sourceFileNameOrGrammarFile(optionID)
            if (valueAST != null && !Grammar.semPredOptions.contains(optionText)) {
                g.tool.errMgr.grammarError(
                    ErrorType.ILLEGAL_OPTION,
                    fileName,
                    optionID,
                    optionText,
                )
                return false
            }
        }
        return false
    }

    fun checkRuleRefOptions(
        elem: RuleRefAST,
        id: GrammarAST?,
        valueAST: GrammarAST?,
    ): Boolean {
        if (id == null) return true
        val optionID = id.token
        val optionText = optionID?.text ?: id.text
        val fileName = sourceFileNameOrGrammarFile(optionID)
        if (valueAST != null && !Grammar.ruleRefOptions.contains(optionText)) {
            g.tool.errMgr.grammarError(
                ErrorType.ILLEGAL_OPTION,
                fileName,
                optionID,
                optionText,
            )
            return false
        }
        return true
    }

    fun checkTokenOptions(
        elem: TerminalAST,
        id: GrammarAST?,
        valueAST: GrammarAST?,
    ): Boolean {
        if (id == null) return true
        val optionID = id.token
        val optionText = optionID?.text ?: id.text
        val fileName = sourceFileNameOrGrammarFile(optionID)
        if (valueAST != null && !Grammar.tokenOptions.contains(optionText)) {
            g.tool.errMgr.grammarError(
                ErrorType.ILLEGAL_OPTION,
                fileName,
                optionID,
                optionText,
            )
            return false
        }
        return true
    }

    fun checkImport(importID: Token?) {
        if (importID == null) return
        val delegate = g.getImportedGrammar(importID.text) ?: return
        val validDelegators = validImportTypes[delegate.type]
        if (validDelegators != null && !validDelegators.contains(g.type)) {
            g.tool.errMgr.grammarError(
                ErrorType.INVALID_IMPORT,
                g.fileName,
                importID,
                g,
                delegate,
            )
        }
        val grammarName = g.name.orEmpty()
        if (
            g.isCombined &&
            (
                delegate.name == grammarName + Grammar.getGrammarTypeToFileNameSuffix(ANTLRParserTypes.LEXER) ||
                    delegate.name == grammarName + Grammar.getGrammarTypeToFileNameSuffix(ANTLRParserTypes.PARSER)
            )
        ) {
            g.tool.errMgr.grammarError(
                ErrorType.IMPORT_NAME_CLASH,
                g.fileName,
                importID,
                g,
                delegate,
            )
        }
    }

    private fun sourceFileNameOrGrammarFile(token: Token?): String? {
        val src = token?.inputStream?.sourceName
        if (src.isNullOrEmpty() || src == IntStream.UNKNOWN_SOURCE_NAME) {
            return g.fileName
        }
        return src
    }

    companion object {
        internal val mutableValidImportTypes: MultiMap<Int, Int> =
            MultiMap<Int, Int>().apply {
                map(ANTLRParserTypes.LEXER, ANTLRParserTypes.LEXER)
                map(ANTLRParserTypes.LEXER, ANTLRParserTypes.COMBINED)
                map(ANTLRParserTypes.PARSER, ANTLRParserTypes.PARSER)
                map(ANTLRParserTypes.PARSER, ANTLRParserTypes.COMBINED)
                map(ANTLRParserTypes.COMBINED, ANTLRParserTypes.COMBINED)
            }

        val validImportTypes: Map<Int, List<Int>>
            get() = mutableValidImportTypes.toReadOnlyMap()
    }
}
