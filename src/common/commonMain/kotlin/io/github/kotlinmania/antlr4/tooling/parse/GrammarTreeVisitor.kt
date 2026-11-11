/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarASTWithOptions
import io.github.kotlinmania.antlr4.tooling.ast.GrammarRootAST
import io.github.kotlinmania.antlr4.tooling.ast.PredAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager

/**
 * Pure-Kotlin, reflection-free tree visitor implementing `GrammarTreeVisitor.g` across
 * ANTLR4 [GrammarRootAST] and [GrammarAST] trees.
 */
open class GrammarTreeVisitor {
    var grammarName: String? = null
    var currentRuleAST: GrammarAST? = null
    var currentModeName: String = DEFAULT_MODE_NAME
    var currentRuleName: String? = null
    var currentOuterAltRoot: GrammarAST? = null
    var currentOuterAltNumber: Int = 1
    var rewriteEBNFLevel: Int = 0

    private var currentActiveNode: GrammarAST? = null

    open fun getErrorManager(): ErrorManager? = null

    fun visitGrammar(t: GrammarAST?) {
        visit(t, "grammarSpec")
    }

    fun visit(
        t: GrammarAST?,
        ruleName: String,
    ) {
        if (t == null) return
        when (ruleName) {
            "grammarSpec" -> grammarSpec(t)
            "prequelConstruct" -> prequelConstruct(t)
            "optionsSpec" -> optionsSpec(t)
            "option" -> option(t)
            "optionValue" -> optionValue(t)
            "delegateGrammars" -> delegateGrammars(t)
            "delegateGrammar" -> delegateGrammar(t)
            "tokensSpec" -> tokensSpec(t)
            "tokenSpec" -> tokenSpec(t)
            "channelsSpec" -> channelsSpec(t)
            "channelSpec" -> channelSpec(t)
            "action" -> action(t)
            "rules" -> rules(t)
            "mode" -> mode(t)
            "lexerRule" -> lexerRule(t)
            "rule" -> rule(t)
            "exceptionHandler" -> exceptionHandler(t)
            "finallyClause" -> finallyClause(t)
            "locals" -> locals(t)
            "ruleReturns" -> ruleReturns(t)
            "throwsSpec" -> throwsSpec(t)
            "ruleAction" -> ruleAction(t)
            "ruleModifier" -> ruleModifier(t)
            "lexerRuleBlock" -> lexerRuleBlock(t)
            "ruleBlock" -> ruleBlock(t)
            "lexerOuterAlternative" -> lexerOuterAlternative(t)
            "outerAlternative" -> outerAlternative(t)
            "lexerAlternative" -> lexerAlternative(t)
            "lexerElements" -> lexerElements(t)
            "lexerElement" -> lexerElement(t)
            "lexerBlock" -> lexerBlock(t)
            "lexerAtom" -> lexerAtom(t)
            "actionElement" -> actionElement(t)
            "alternative" -> alternative(t)
            "lexerCommand" -> lexerCommandRule(t)
            "lexerCommandExpr" -> lexerCommandExpr(t)
            "element" -> element(t)
            "astOperand" -> astOperand(t)
            "labeledElement" -> labeledElement(t)
            "subrule" -> subrule(t)
            "lexerSubrule" -> lexerSubrule(t)
            "blockSuffix" -> blockSuffix(t)
            "ebnfSuffix" -> ebnfSuffix(t)
            "atom" -> atom(t)
            "blockSet" -> blockSet(t)
            "setElement" -> setElement(t)
            "block" -> block(t)
            "ruleref" -> ruleref(t)
            "range" -> range(t)
            "terminal" -> terminal(t)
            "elementOptions" -> elementOptions(t)
            else -> error("Unknown GrammarTreeVisitor rule: $ruleName")
        }
    }

    /**
     * Checks whether the ancestor chain of the active AST node matches [context]
     * using ANTLR3 `TreeParser.inContext` space-separated pattern semantics (`...` matches
     * zero or more ancestor nodes; token names match `ANTLRParserTypes.tokenName(node.type)`).
     */
    fun inContext(context: String): Boolean = inContextFrom(currentActiveNode, context)

    protected fun inContextFrom(
        node: GrammarAST?,
        context: String,
    ): Boolean {
        val tokens = context.trim().split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val ancestors = ArrayList<String>()
        var p = node?.parent
        while (p != null) {
            ancestors.add(ANTLRParserTypes.tokenName(p.type))
            p = p.parent
        }
        ancestors.reverse()
        return matchContextPattern(ancestors, 0, tokens, 0, rootedAtEnd = !tokens.last().equals("..."))
    }

    private fun matchContextPattern(
        ancestors: List<String>,
        aIdx: Int,
        pattern: List<String>,
        pIdx: Int,
        rootedAtEnd: Boolean,
    ): Boolean {
        if (pIdx == pattern.size) {
            return !rootedAtEnd || aIdx == ancestors.size
        }
        val pat = pattern[pIdx]
        if (pat == "...") {
            for (skipTo in aIdx..ancestors.size) {
                if (matchContextPattern(ancestors, skipTo, pattern, pIdx + 1, rootedAtEnd)) {
                    return true
                }
            }
            return false
        }
        for (startAt in aIdx until ancestors.size) {
            if (ancestors[startAt] == pat) {
                if (matchContextPattern(ancestors, startAt + 1, pattern, pIdx + 1, rootedAtEnd)) {
                    return true
                }
            }
            if (pIdx > 0 && pattern[pIdx - 1] != "...") {
                break
            }
        }
        return false
    }

    private inline fun <T> withNode(
        node: GrammarAST,
        block: () -> T,
    ): T {
        val prev = currentActiveNode
        currentActiveNode = node
        return try {
            block()
        } finally {
            currentActiveNode = prev
        }
    }

    // =========================================================================
    // Semantic / Discovery Callbacks
    // =========================================================================

    open fun discoverGrammar(
        root: GrammarRootAST,
        id: GrammarAST,
    ) {
    }

    open fun finishPrequels(firstPrequel: GrammarAST?) {
    }

    open fun finishGrammar(
        root: GrammarRootAST,
        id: GrammarAST,
    ) {
    }

    open fun grammarOption(
        id: GrammarAST,
        valueAST: GrammarAST,
    ) {
    }

    open fun ruleOption(
        id: GrammarAST,
        valueAST: GrammarAST,
    ) {
    }

    open fun blockOption(
        id: GrammarAST,
        valueAST: GrammarAST,
    ) {
    }

    open fun defineToken(id: GrammarAST) {
    }

    open fun defineChannel(id: GrammarAST) {
    }

    open fun globalNamedAction(
        scope: GrammarAST?,
        id: GrammarAST,
        action: ActionAST,
    ) {
    }

    open fun importGrammar(
        label: GrammarAST?,
        id: GrammarAST,
    ) {
    }

    open fun modeDef(
        m: GrammarAST,
        id: GrammarAST,
    ) {
    }

    open fun discoverRules(rules: GrammarAST) {
    }

    open fun finishRules(rule: GrammarAST) {
    }

    open fun discoverRule(
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
    }

    open fun finishRule(
        rule: RuleAST,
        id: GrammarAST,
        block: GrammarAST,
    ) {
    }

    open fun discoverLexerRule(
        rule: RuleAST,
        id: GrammarAST,
        modifiers: List<GrammarAST>,
        options: GrammarAST?,
        block: GrammarAST,
    ) {
    }

    open fun finishLexerRule(
        rule: RuleAST,
        id: GrammarAST,
        block: GrammarAST,
    ) {
    }

    open fun ruleCatch(
        arg: GrammarAST,
        action: ActionAST,
    ) {
    }

    open fun finallyAction(action: ActionAST) {
    }

    open fun discoverOuterAlt(alt: AltAST) {
    }

    open fun finishOuterAlt(alt: AltAST) {
    }

    open fun discoverAlt(alt: AltAST) {
    }

    open fun finishAlt(alt: AltAST) {
    }

    open fun ruleRef(
        ref: GrammarAST,
        arg: ActionAST?,
    ) {
    }

    open fun tokenRef(ref: TerminalAST) {
    }

    open fun elementOption(
        t: GrammarASTWithOptions,
        id: GrammarAST,
        valueAST: GrammarAST?,
    ) {
    }

    open fun stringRef(ref: TerminalAST) {
    }

    open fun wildcardRef(ref: GrammarAST) {
    }

    open fun actionInAlt(action: ActionAST) {
    }

    open fun sempredInAlt(pred: PredAST) {
    }

    open fun label(
        op: GrammarAST,
        id: GrammarAST,
        element: GrammarAST,
    ) {
    }

    open fun lexerCallCommand(
        outerAltNumber: Int,
        id: GrammarAST,
        arg: GrammarAST,
    ) {
    }

    open fun lexerCommand(
        outerAltNumber: Int,
        id: GrammarAST,
    ) {
    }

    // =========================================================================
    // Protected Enter / Exit Hooks
    // =========================================================================

    protected open fun enterGrammarSpec(tree: GrammarAST) {}

    protected open fun exitGrammarSpec(tree: GrammarAST) {}

    protected open fun enterPrequelConstructs(tree: GrammarAST?) {}

    protected open fun exitPrequelConstructs(tree: GrammarAST?) {}

    protected open fun enterPrequelConstruct(tree: GrammarAST) {}

    protected open fun exitPrequelConstruct(tree: GrammarAST) {}

    protected open fun enterOptionsSpec(tree: GrammarAST) {}

    protected open fun exitOptionsSpec(tree: GrammarAST) {}

    protected open fun enterOption(tree: GrammarAST) {}

    protected open fun exitOption(tree: GrammarAST) {}

    protected open fun enterOptionValue(tree: GrammarAST) {}

    protected open fun exitOptionValue(tree: GrammarAST) {}

    protected open fun enterDelegateGrammars(tree: GrammarAST) {}

    protected open fun exitDelegateGrammars(tree: GrammarAST) {}

    protected open fun enterDelegateGrammar(tree: GrammarAST) {}

    protected open fun exitDelegateGrammar(tree: GrammarAST) {}

    protected open fun enterTokensSpec(tree: GrammarAST) {}

    protected open fun exitTokensSpec(tree: GrammarAST) {}

    protected open fun enterTokenSpec(tree: GrammarAST) {}

    protected open fun exitTokenSpec(tree: GrammarAST) {}

    protected open fun enterChannelsSpec(tree: GrammarAST) {}

    protected open fun exitChannelsSpec(tree: GrammarAST) {}

    protected open fun enterChannelSpec(tree: GrammarAST) {}

    protected open fun exitChannelSpec(tree: GrammarAST) {}

    protected open fun enterAction(tree: GrammarAST) {}

    protected open fun exitAction(tree: GrammarAST) {}

    protected open fun enterRules(tree: GrammarAST) {}

    protected open fun exitRules(tree: GrammarAST) {}

    protected open fun enterMode(tree: GrammarAST) {}

    protected open fun exitMode(tree: GrammarAST) {}

    protected open fun enterLexerRule(tree: GrammarAST) {}

    protected open fun exitLexerRule(tree: GrammarAST) {}

    protected open fun enterRule(tree: GrammarAST) {}

    protected open fun exitRule(tree: GrammarAST) {}

    protected open fun enterExceptionGroup(tree: GrammarAST?) {}

    protected open fun exitExceptionGroup(tree: GrammarAST?) {}

    protected open fun enterExceptionHandler(tree: GrammarAST) {}

    protected open fun exitExceptionHandler(tree: GrammarAST) {}

    protected open fun enterFinallyClause(tree: GrammarAST) {}

    protected open fun exitFinallyClause(tree: GrammarAST) {}

    protected open fun enterLocals(tree: GrammarAST) {}

    protected open fun exitLocals(tree: GrammarAST) {}

    protected open fun enterRuleReturns(tree: GrammarAST) {}

    protected open fun exitRuleReturns(tree: GrammarAST) {}

    protected open fun enterThrowsSpec(tree: GrammarAST) {}

    protected open fun exitThrowsSpec(tree: GrammarAST) {}

    protected open fun enterRuleAction(tree: GrammarAST) {}

    protected open fun exitRuleAction(tree: GrammarAST) {}

    protected open fun enterRuleModifier(tree: GrammarAST) {}

    protected open fun exitRuleModifier(tree: GrammarAST) {}

    protected open fun enterLexerRuleBlock(tree: GrammarAST) {}

    protected open fun exitLexerRuleBlock(tree: GrammarAST) {}

    protected open fun enterRuleBlock(tree: GrammarAST) {}

    protected open fun exitRuleBlock(tree: GrammarAST) {}

    protected open fun enterLexerOuterAlternative(tree: AltAST) {}

    protected open fun exitLexerOuterAlternative(tree: AltAST) {}

    protected open fun enterOuterAlternative(tree: AltAST) {}

    protected open fun exitOuterAlternative(tree: AltAST) {}

    protected open fun enterLexerAlternative(tree: GrammarAST) {}

    protected open fun exitLexerAlternative(tree: GrammarAST) {}

    protected open fun enterLexerElements(tree: GrammarAST) {}

    protected open fun exitLexerElements(tree: GrammarAST) {}

    protected open fun enterLexerElement(tree: GrammarAST) {}

    protected open fun exitLexerElement(tree: GrammarAST) {}

    protected open fun enterLexerBlock(tree: GrammarAST) {}

    protected open fun exitLexerBlock(tree: GrammarAST) {}

    protected open fun enterLexerAtom(tree: GrammarAST) {}

    protected open fun exitLexerAtom(tree: GrammarAST) {}

    protected open fun enterActionElement(tree: GrammarAST) {}

    protected open fun exitActionElement(tree: GrammarAST) {}

    protected open fun enterAlternative(tree: AltAST) {}

    protected open fun exitAlternative(tree: AltAST) {}

    protected open fun enterLexerCommand(tree: GrammarAST) {}

    protected open fun exitLexerCommand(tree: GrammarAST) {}

    protected open fun enterLexerCommandExpr(tree: GrammarAST) {}

    protected open fun exitLexerCommandExpr(tree: GrammarAST) {}

    protected open fun enterElement(tree: GrammarAST) {}

    protected open fun exitElement(tree: GrammarAST) {}

    protected open fun enterAstOperand(tree: GrammarAST) {}

    protected open fun exitAstOperand(tree: GrammarAST) {}

    protected open fun enterLabeledElement(tree: GrammarAST) {}

    protected open fun exitLabeledElement(tree: GrammarAST) {}

    protected open fun enterSubrule(tree: GrammarAST) {}

    protected open fun exitSubrule(tree: GrammarAST) {}

    protected open fun enterLexerSubrule(tree: GrammarAST) {}

    protected open fun exitLexerSubrule(tree: GrammarAST) {}

    protected open fun enterBlockSuffix(tree: GrammarAST) {}

    protected open fun exitBlockSuffix(tree: GrammarAST) {}

    protected open fun enterEbnfSuffix(tree: GrammarAST) {}

    protected open fun exitEbnfSuffix(tree: GrammarAST) {}

    protected open fun enterAtom(tree: GrammarAST) {}

    protected open fun exitAtom(tree: GrammarAST) {}

    protected open fun enterBlockSet(tree: GrammarAST) {}

    protected open fun exitBlockSet(tree: GrammarAST) {}

    protected open fun enterSetElement(tree: GrammarAST) {}

    protected open fun exitSetElement(tree: GrammarAST) {}

    protected open fun enterBlock(tree: GrammarAST) {}

    protected open fun exitBlock(tree: GrammarAST) {}

    protected open fun enterRuleref(tree: GrammarAST) {}

    protected open fun exitRuleref(tree: GrammarAST) {}

    protected open fun enterRange(tree: GrammarAST) {}

    protected open fun exitRange(tree: GrammarAST) {}

    protected open fun enterTerminal(tree: GrammarAST) {}

    protected open fun exitTerminal(tree: GrammarAST) {}

    protected open fun enterElementOptions(tree: GrammarAST) {}

    protected open fun exitElementOptions(tree: GrammarAST) {}

    protected open fun enterElementOption(tree: GrammarAST) {}

    protected open fun exitElementOption(tree: GrammarAST) {}

    // =========================================================================
    // Tree Grammar Traversal Methods
    // =========================================================================

    open fun grammarSpec(tree: GrammarAST) {
        val root = tree as? GrammarRootAST ?: return
        withNode(root) {
            enterGrammarSpec(root)
            try {
                val idNode = root.getChild(0) ?: return@withNode
                grammarName = idNode.text
                discoverGrammar(root, idNode)

                var idx = 1
                val firstChildAtPrequels = root.getChild(idx)
                enterPrequelConstructs(firstChildAtPrequels)
                var firstPrequel: GrammarAST? = null
                while (idx < root.childCount) {
                    val child = root.getChild(idx) ?: break
                    if (isPrequelType(child.type)) {
                        if (firstPrequel == null) {
                            firstPrequel = child
                        }
                        prequelConstruct(child)
                        idx++
                    } else {
                        break
                    }
                }
                exitPrequelConstructs(firstChildAtPrequels)
                finishPrequels(firstPrequel)

                if (idx < root.childCount && root.getChild(idx)?.type == ANTLRParserTypes.RULES) {
                    val rulesNode = root.getChild(idx)
                    if (rulesNode != null) {
                        rules(rulesNode)
                    }
                    idx++
                }

                while (idx < root.childCount) {
                    val modeNode = root.getChild(idx) ?: break
                    if (modeNode.type == ANTLRParserTypes.MODE) {
                        mode(modeNode)
                    }
                    idx++
                }

                finishGrammar(root, idNode)
            } finally {
                exitGrammarSpec(root)
            }
        }
    }

    private fun isPrequelType(type: Int): Boolean =
        type == ANTLRParserTypes.OPTIONS ||
            type == ANTLRParserTypes.IMPORT ||
            type == ANTLRParserTypes.TOKENS_SPEC ||
            type == ANTLRParserTypes.CHANNELS ||
            type == ANTLRParserTypes.AT

    open fun prequelConstruct(tree: GrammarAST) {
        withNode(tree) {
            enterPrequelConstructs(tree)
            try {
                when (tree.type) {
                    ANTLRParserTypes.OPTIONS -> optionsSpec(tree)
                    ANTLRParserTypes.IMPORT -> delegateGrammars(tree)
                    ANTLRParserTypes.TOKENS_SPEC -> tokensSpec(tree)
                    ANTLRParserTypes.CHANNELS -> channelsSpec(tree)
                    ANTLRParserTypes.AT -> action(tree)
                }
            } finally {
                exitPrequelConstructs(tree)
            }
        }
    }

    open fun optionsSpec(tree: GrammarAST) {
        withNode(tree) {
            enterOptionsSpec(tree)
            try {
                for (i in 0 until tree.childCount) {
                    val opt = tree.getChild(i)
                    if (opt != null && opt.type == ANTLRParserTypes.ASSIGN) {
                        option(opt)
                    }
                }
            } finally {
                exitOptionsSpec(tree)
            }
        }
    }

    open fun option(tree: GrammarAST) {
        withNode(tree) {
            enterOption(tree)
            val inRule = inContext("RULE ...")
            val inBlock = inContext("BLOCK ...")
            try {
                val idNode = tree.getChild(0) ?: return@withNode
                val valNode = tree.getChild(1) ?: return@withNode
                optionValue(valNode)
                if (inBlock) {
                    blockOption(idNode, valNode)
                } else if (inRule) {
                    ruleOption(idNode, valNode)
                } else {
                    grammarOption(idNode, valNode)
                }
            } finally {
                exitOption(tree)
            }
        }
    }

    open fun optionValue(tree: GrammarAST): String {
        return withNode(tree) {
            enterOptionValue(tree)
            try {
                tree.token?.text ?: tree.text
            } finally {
                exitOptionValue(tree)
            }
        }
    }

    open fun delegateGrammars(tree: GrammarAST) {
        withNode(tree) {
            enterDelegateGrammars(tree)
            try {
                for (i in 0 until tree.childCount) {
                    val dg = tree.getChild(i)
                    if (dg != null) {
                        delegateGrammar(dg)
                    }
                }
            } finally {
                exitDelegateGrammars(tree)
            }
        }
    }

    open fun delegateGrammar(tree: GrammarAST) {
        withNode(tree) {
            enterDelegateGrammar(tree)
            try {
                if (tree.type == ANTLRParserTypes.ASSIGN) {
                    val labelNode = tree.getChild(0)
                    val idNode = tree.getChild(1)
                    if (idNode != null) {
                        importGrammar(labelNode, idNode)
                    }
                } else if (tree.type == ANTLRParserTypes.ID) {
                    importGrammar(null, tree)
                }
            } finally {
                exitDelegateGrammar(tree)
            }
        }
    }

    open fun tokensSpec(tree: GrammarAST) {
        withNode(tree) {
            enterTokensSpec(tree)
            try {
                for (i in 0 until tree.childCount) {
                    val ts = tree.getChild(i)
                    if (ts != null) {
                        tokenSpec(ts)
                    }
                }
            } finally {
                exitTokensSpec(tree)
            }
        }
    }

    open fun tokenSpec(tree: GrammarAST) {
        withNode(tree) {
            enterTokenSpec(tree)
            try {
                if (tree.type == ANTLRParserTypes.ID || tree.type == ANTLRParserTypes.TOKEN_REF) {
                    defineToken(tree)
                }
            } finally {
                exitTokenSpec(tree)
            }
        }
    }

    open fun channelsSpec(tree: GrammarAST) {
        withNode(tree) {
            enterChannelsSpec(tree)
            try {
                for (i in 0 until tree.childCount) {
                    val cs = tree.getChild(i)
                    if (cs != null) {
                        channelSpec(cs)
                    }
                }
            } finally {
                exitChannelsSpec(tree)
            }
        }
    }

    open fun channelSpec(tree: GrammarAST) {
        withNode(tree) {
            enterChannelSpec(tree)
            try {
                if (tree.type == ANTLRParserTypes.ID || tree.type == ANTLRParserTypes.TOKEN_REF) {
                    defineChannel(tree)
                }
            } finally {
                exitChannelSpec(tree)
            }
        }
    }

    open fun action(tree: GrammarAST) {
        withNode(tree) {
            enterAction(tree)
            try {
                if (tree.childCount == 3) {
                    val scopeNode = tree.getChild(0)
                    val nameNode = tree.getChild(1)
                    val actNode = tree.getChild(2) as? ActionAST
                    if (nameNode != null && actNode != null) {
                        globalNamedAction(scopeNode, nameNode, actNode)
                    }
                } else if (tree.childCount == 2) {
                    val nameNode = tree.getChild(0)
                    val actNode = tree.getChild(1) as? ActionAST
                    if (nameNode != null && actNode != null) {
                        globalNamedAction(null, nameNode, actNode)
                    }
                }
            } finally {
                exitAction(tree)
            }
        }
    }

    open fun rules(tree: GrammarAST) {
        withNode(tree) {
            enterRules(tree)
            try {
                discoverRules(tree)
                for (i in 0 until tree.childCount) {
                    val r = tree.getChild(i) ?: continue
                    if (r.type != ANTLRParserTypes.RULE) continue
                    val nameChild = r.getChild(0)
                    if (nameChild?.type == ANTLRParserTypes.TOKEN_REF) {
                        lexerRule(r)
                    } else if (nameChild?.type == ANTLRParserTypes.RULE_REF) {
                        rule(r)
                    }
                }
                finishRules(tree)
            } finally {
                exitRules(tree)
            }
        }
    }

    open fun mode(tree: GrammarAST) {
        withNode(tree) {
            enterMode(tree)
            try {
                val idNode = tree.getChild(0) ?: return@withNode
                currentModeName = idNode.text
                modeDef(tree, idNode)
                for (i in 1 until tree.childCount) {
                    val lr = tree.getChild(i)
                    if (lr != null && lr.type == ANTLRParserTypes.RULE) {
                        lexerRule(lr)
                    }
                }
            } finally {
                exitMode(tree)
            }
        }
    }

    open fun lexerRule(tree: GrammarAST) {
        val ruleAst = tree as? RuleAST ?: return
        withNode(ruleAst) {
            enterLexerRule(ruleAst)
            val mods = ArrayList<GrammarAST>()
            currentOuterAltNumber = 0
            try {
                val tokenRef = ruleAst.getChild(0) ?: return@withNode
                currentRuleName = tokenRef.text
                currentRuleAST = ruleAst

                var idx = 1
                if (ruleAst.getChild(idx)?.type == ANTLRParserTypes.RULEMODIFIERS) {
                    val modsNode = ruleAst.getChild(idx)
                    if (modsNode != null) {
                        for (mIdx in 0 until modsNode.childCount) {
                            val m = modsNode.getChild(mIdx)
                            if (m != null) {
                                mods.add(m)
                            }
                        }
                    }
                    idx++
                }

                var lastOpts: GrammarAST? = null
                while (idx < ruleAst.childCount && ruleAst.getChild(idx)?.type == ANTLRParserTypes.OPTIONS) {
                    val optsNode = ruleAst.getChild(idx)
                    if (optsNode != null) {
                        lastOpts = optsNode
                        optionsSpec(optsNode)
                    }
                    idx++
                }

                val blockNode = ruleAst.getChild(idx) ?: return@withNode
                discoverLexerRule(ruleAst, tokenRef, mods, lastOpts, blockNode)
                lexerRuleBlock(blockNode)
                finishLexerRule(ruleAst, tokenRef, blockNode)
                currentRuleName = null
                currentRuleAST = null
            } finally {
                exitLexerRule(ruleAst)
            }
        }
    }

    open fun rule(tree: GrammarAST) {
        val ruleAst = tree as? RuleAST ?: return
        withNode(ruleAst) {
            enterRule(ruleAst)
            val mods = ArrayList<GrammarAST>()
            val actions = ArrayList<GrammarAST>()
            currentOuterAltNumber = 0
            try {
                val ruleRefNode = ruleAst.getChild(0) ?: return@withNode
                currentRuleName = ruleRefNode.text
                currentRuleAST = ruleAst

                var idx = 1
                if (ruleAst.getChild(idx)?.type == ANTLRParserTypes.RULEMODIFIERS) {
                    val modsNode = ruleAst.getChild(idx)
                    if (modsNode != null) {
                        for (mIdx in 0 until modsNode.childCount) {
                            val m = modsNode.getChild(mIdx)
                            if (m != null) {
                                ruleModifier(m)
                                mods.add(m)
                            }
                        }
                    }
                    idx++
                }

                var argAction: ActionAST? = null
                if (ruleAst.getChild(idx)?.type == ANTLRParserTypes.ARG_ACTION) {
                    argAction = ruleAst.getChild(idx) as? ActionAST
                    idx++
                }

                var retNode: GrammarAST? = null
                if (ruleAst.getChild(idx)?.type == ANTLRParserTypes.RETURNS) {
                    val r = ruleAst.getChild(idx)
                    if (r != null) {
                        retNode = r
                        ruleReturns(r)
                    }
                    idx++
                }

                var thrNode: GrammarAST? = null
                if (ruleAst.getChild(idx)?.type == ANTLRParserTypes.THROWS) {
                    val th = ruleAst.getChild(idx)
                    if (th != null) {
                        thrNode = th
                        throwsSpec(th)
                    }
                    idx++
                }

                var locNode: GrammarAST? = null
                if (ruleAst.getChild(idx)?.type == ANTLRParserTypes.LOCALS) {
                    val lc = ruleAst.getChild(idx)
                    if (lc != null) {
                        locNode = lc
                        locals(lc)
                    }
                    idx++
                }

                var lastOpts: GrammarAST? = null
                while (idx < ruleAst.childCount) {
                    val child = ruleAst.getChild(idx) ?: break
                    if (child.type == ANTLRParserTypes.OPTIONS) {
                        lastOpts = child
                        optionsSpec(child)
                        idx++
                    } else if (child.type == ANTLRParserTypes.AT) {
                        ruleAction(child)
                        actions.add(child)
                        idx++
                    } else {
                        break
                    }
                }

                val blockNode = ruleAst.getChild(idx) ?: return@withNode
                val retAction = retNode?.getChild(0) as? ActionAST
                val locAction = locNode?.getChild(0) as? ActionAST

                discoverRule(
                    ruleAst,
                    ruleRefNode,
                    mods,
                    argAction,
                    retAction,
                    thrNode,
                    lastOpts,
                    locAction,
                    actions,
                    blockNode,
                )

                ruleBlock(blockNode)
                idx++

                val firstEx = ruleAst.getChild(idx)
                enterExceptionGroup(firstEx)
                try {
                    while (idx < ruleAst.childCount && ruleAst.getChild(idx)?.type == ANTLRParserTypes.CATCH) {
                        val ch = ruleAst.getChild(idx)
                        if (ch != null) {
                            exceptionHandler(ch)
                        }
                        idx++
                    }
                    if (idx < ruleAst.childCount && ruleAst.getChild(idx)?.type == ANTLRParserTypes.FINALLY) {
                        val fn = ruleAst.getChild(idx)
                        if (fn != null) {
                            finallyClause(fn)
                        }
                    }
                } finally {
                    exitExceptionGroup(firstEx)
                }

                finishRule(ruleAst, ruleRefNode, blockNode)
                currentRuleName = null
                currentRuleAST = null
            } finally {
                exitRule(ruleAst)
            }
        }
    }

    open fun exceptionHandler(tree: GrammarAST) {
        withNode(tree) {
            enterExceptionHandler(tree)
            try {
                val argNode = tree.getChild(0)
                val actNode = tree.getChild(1) as? ActionAST
                if (argNode != null && actNode != null) {
                    ruleCatch(argNode, actNode)
                }
            } finally {
                exitExceptionHandler(tree)
            }
        }
    }

    open fun finallyClause(tree: GrammarAST) {
        withNode(tree) {
            enterFinallyClause(tree)
            try {
                val actNode = tree.getChild(0) as? ActionAST
                if (actNode != null) {
                    finallyAction(actNode)
                }
            } finally {
                exitFinallyClause(tree)
            }
        }
    }

    open fun locals(tree: GrammarAST) {
        withNode(tree) {
            enterLocals(tree)
            exitLocals(tree)
        }
    }

    open fun ruleReturns(tree: GrammarAST) {
        withNode(tree) {
            enterRuleReturns(tree)
            exitRuleReturns(tree)
        }
    }

    open fun throwsSpec(tree: GrammarAST) {
        withNode(tree) {
            enterThrowsSpec(tree)
            exitThrowsSpec(tree)
        }
    }

    open fun ruleAction(tree: GrammarAST) {
        withNode(tree) {
            enterRuleAction(tree)
            exitRuleAction(tree)
        }
    }

    open fun ruleModifier(tree: GrammarAST) {
        withNode(tree) {
            enterRuleModifier(tree)
            exitRuleModifier(tree)
        }
    }

    open fun lexerRuleBlock(tree: GrammarAST) {
        withNode(tree) {
            enterLexerRuleBlock(tree)
            try {
                for (i in 0 until tree.childCount) {
                    val alt = tree.getChild(i) ?: continue
                    currentOuterAltRoot = alt
                    currentOuterAltNumber++
                    lexerOuterAlternative(alt)
                }
            } finally {
                exitLexerRuleBlock(tree)
            }
        }
    }

    open fun ruleBlock(tree: GrammarAST) {
        withNode(tree) {
            enterRuleBlock(tree)
            try {
                for (i in 0 until tree.childCount) {
                    val alt = tree.getChild(i) ?: continue
                    currentOuterAltRoot = alt
                    currentOuterAltNumber++
                    outerAlternative(alt)
                }
            } finally {
                exitRuleBlock(tree)
            }
        }
    }

    open fun lexerOuterAlternative(tree: GrammarAST) {
        val altAst = tree as? AltAST ?: return
        withNode(altAst) {
            enterLexerOuterAlternative(altAst)
            discoverOuterAlt(altAst)
            try {
                lexerAlternative(altAst)
            } finally {
                finishOuterAlt(altAst)
                exitLexerOuterAlternative(altAst)
            }
        }
    }

    open fun outerAlternative(tree: GrammarAST) {
        val altAst = tree as? AltAST ?: return
        withNode(altAst) {
            enterOuterAlternative(altAst)
            discoverOuterAlt(altAst)
            try {
                alternative(altAst)
            } finally {
                finishOuterAlt(altAst)
                exitOuterAlternative(altAst)
            }
        }
    }

    open fun lexerAlternative(tree: GrammarAST) {
        withNode(tree) {
            enterLexerAlternative(tree)
            try {
                if (tree.type == ANTLRParserTypes.LEXER_ALT_ACTION) {
                    val elems = tree.getChild(0)
                    if (elems != null) {
                        lexerElements(elems)
                    }
                    for (i in 1 until tree.childCount) {
                        val cmd = tree.getChild(i)
                        if (cmd != null) {
                            lexerCommandRule(cmd)
                        }
                    }
                } else {
                    lexerElements(tree)
                }
            } finally {
                exitLexerAlternative(tree)
            }
        }
    }

    open fun lexerElements(tree: GrammarAST) {
        withNode(tree) {
            enterLexerElements(tree)
            try {
                for (i in 0 until tree.childCount) {
                    val el = tree.getChild(i)
                    if (el != null) {
                        lexerElement(el)
                    }
                }
            } finally {
                exitLexerElements(tree)
            }
        }
    }

    open fun lexerElement(tree: GrammarAST) {
        withNode(tree) {
            enterLexerElement(tree)
            try {
                when (tree.type) {
                    ANTLRParserTypes.OPTIONAL,
                    ANTLRParserTypes.CLOSURE,
                    ANTLRParserTypes.POSITIVE_CLOSURE,
                    ANTLRParserTypes.BLOCK,
                    -> lexerSubrule(tree)
                    ANTLRParserTypes.ACTION -> {
                        if (tree.childCount > 0 && tree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                            val opts = tree.getChild(0)
                            if (opts != null) elementOptions(opts)
                        }
                        if (tree is ActionAST) {
                            actionInAlt(tree)
                        }
                    }
                    ANTLRParserTypes.SEMPRED -> {
                        if (tree.childCount > 0 && tree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                            val opts = tree.getChild(0)
                            if (opts != null) elementOptions(opts)
                        }
                        if (tree is PredAST) {
                            sempredInAlt(tree)
                        }
                    }
                    ANTLRParserTypes.EPSILON -> {}
                    else -> lexerAtom(tree)
                }
            } finally {
                exitLexerElement(tree)
            }
        }
    }

    open fun lexerBlock(tree: GrammarAST) {
        withNode(tree) {
            enterLexerBlock(tree)
            try {
                var idx = 0
                if (tree.getChild(0)?.type == ANTLRParserTypes.OPTIONS) {
                    val opts = tree.getChild(0)
                    if (opts != null) optionsSpec(opts)
                    idx = 1
                }
                while (idx < tree.childCount) {
                    val alt = tree.getChild(idx)
                    if (alt != null) {
                        lexerAlternative(alt)
                    }
                    idx++
                }
            } finally {
                exitLexerBlock(tree)
            }
        }
    }

    open fun lexerAtom(tree: GrammarAST) {
        withNode(tree) {
            enterLexerAtom(tree)
            try {
                when (tree.type) {
                    ANTLRParserTypes.STRING_LITERAL, ANTLRParserTypes.TOKEN_REF -> terminal(tree)
                    ANTLRParserTypes.NOT -> {
                        val inner = tree.getChild(0)
                        if (inner != null) {
                            blockSet(inner)
                        }
                    }
                    ANTLRParserTypes.SET -> blockSet(tree)
                    ANTLRParserTypes.WILDCARD -> {
                        if (tree.childCount > 0 && tree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                            val opts = tree.getChild(0)
                            if (opts != null) elementOptions(opts)
                        }
                    }
                    ANTLRParserTypes.LEXER_CHAR_SET -> {}
                    ANTLRParserTypes.RANGE -> range(tree)
                    ANTLRParserTypes.RULE_REF -> ruleref(tree)
                }
            } finally {
                exitLexerAtom(tree)
            }
        }
    }

    open fun actionElement(tree: GrammarAST) {
        withNode(tree) {
            enterActionElement(tree)
            try {
                if (tree.childCount > 0 && tree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                    val opts = tree.getChild(0)
                    if (opts != null) elementOptions(opts)
                }
            } finally {
                exitActionElement(tree)
            }
        }
    }

    open fun alternative(tree: GrammarAST) {
        val altAst = tree as? AltAST ?: return
        withNode(altAst) {
            enterAlternative(altAst)
            discoverAlt(altAst)
            try {
                var idx = 0
                if (altAst.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                    val opts = altAst.getChild(0)
                    if (opts != null) elementOptions(opts)
                    idx = 1
                }
                while (idx < altAst.childCount) {
                    val child = altAst.getChild(idx)
                    if (child != null && child.type != ANTLRParserTypes.EPSILON) {
                        element(child)
                    }
                    idx++
                }
            } finally {
                finishAlt(altAst)
                exitAlternative(altAst)
            }
        }
    }

    open fun lexerCommandRule(tree: GrammarAST) {
        withNode(tree) {
            enterLexerCommand(tree)
            try {
                if (tree.type == ANTLRParserTypes.LEXER_ACTION_CALL) {
                    val idNode = tree.getChild(0)
                    val exprNode = tree.getChild(1)
                    if (exprNode != null) {
                        lexerCommandExpr(exprNode)
                    }
                    if (idNode != null && exprNode != null) {
                        lexerCallCommand(currentOuterAltNumber, idNode, exprNode)
                    }
                } else if (tree.type == ANTLRParserTypes.ID) {
                    lexerCommand(currentOuterAltNumber, tree)
                }
            } finally {
                exitLexerCommand(tree)
            }
        }
    }

    open fun lexerCommandExpr(tree: GrammarAST) {
        withNode(tree) {
            enterLexerCommandExpr(tree)
            exitLexerCommandExpr(tree)
        }
    }

    open fun element(tree: GrammarAST) {
        withNode(tree) {
            enterElement(tree)
            try {
                when (tree.type) {
                    ANTLRParserTypes.ASSIGN, ANTLRParserTypes.PLUS_ASSIGN -> labeledElement(tree)
                    ANTLRParserTypes.OPTIONAL,
                    ANTLRParserTypes.CLOSURE,
                    ANTLRParserTypes.POSITIVE_CLOSURE,
                    ANTLRParserTypes.BLOCK,
                    -> subrule(tree)
                    ANTLRParserTypes.ACTION -> {
                        if (tree.childCount > 0 && tree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                            val opts = tree.getChild(0)
                            if (opts != null) elementOptions(opts)
                        }
                        if (tree is ActionAST) {
                            actionInAlt(tree)
                        }
                    }
                    ANTLRParserTypes.SEMPRED -> {
                        if (tree.childCount > 0 && tree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                            val opts = tree.getChild(0)
                            if (opts != null) elementOptions(opts)
                        }
                        if (tree is PredAST) {
                            sempredInAlt(tree)
                        }
                    }
                    ANTLRParserTypes.RANGE -> range(tree)
                    ANTLRParserTypes.NOT -> {
                        val inner = tree.getChild(0)
                        if (inner != null) {
                            if (inner.type == ANTLRParserTypes.SET) {
                                blockSet(inner)
                            } else if (inner.type == ANTLRParserTypes.BLOCK) {
                                block(inner)
                            }
                        }
                    }
                    else -> atom(tree)
                }
            } finally {
                exitElement(tree)
            }
        }
    }

    open fun astOperand(tree: GrammarAST) {
        withNode(tree) {
            enterAstOperand(tree)
            try {
                if (tree.type == ANTLRParserTypes.NOT) {
                    val inner = tree.getChild(0)
                    if (inner != null) {
                        if (inner.type == ANTLRParserTypes.SET) {
                            blockSet(inner)
                        } else if (inner.type == ANTLRParserTypes.BLOCK) {
                            block(inner)
                        }
                    }
                } else {
                    atom(tree)
                }
            } finally {
                exitAstOperand(tree)
            }
        }
    }

    open fun labeledElement(tree: GrammarAST) {
        withNode(tree) {
            enterLabeledElement(tree)
            try {
                val idNode = tree.getChild(0)
                val rhsNode = tree.getChild(1)
                if (rhsNode != null) {
                    element(rhsNode)
                }
                if (idNode != null && rhsNode != null) {
                    label(tree, idNode, rhsNode)
                }
            } finally {
                exitLabeledElement(tree)
            }
        }
    }

    open fun subrule(tree: GrammarAST) {
        withNode(tree) {
            enterSubrule(tree)
            try {
                if (tree.type == ANTLRParserTypes.BLOCK) {
                    block(tree)
                } else {
                    blockSuffix(tree)
                    val inner = tree.getChild(0)
                    if (inner != null) {
                        block(inner)
                    }
                }
            } finally {
                exitSubrule(tree)
            }
        }
    }

    open fun lexerSubrule(tree: GrammarAST) {
        withNode(tree) {
            enterLexerSubrule(tree)
            try {
                if (tree.type == ANTLRParserTypes.BLOCK) {
                    lexerBlock(tree)
                } else {
                    blockSuffix(tree)
                    val inner = tree.getChild(0)
                    if (inner != null) {
                        lexerBlock(inner)
                    }
                }
            } finally {
                exitLexerSubrule(tree)
            }
        }
    }

    open fun blockSuffix(tree: GrammarAST) {
        withNode(tree) {
            enterBlockSuffix(tree)
            try {
                ebnfSuffix(tree)
            } finally {
                exitBlockSuffix(tree)
            }
        }
    }

    open fun ebnfSuffix(tree: GrammarAST) {
        withNode(tree) {
            enterEbnfSuffix(tree)
            exitEbnfSuffix(tree)
        }
    }

    open fun atom(tree: GrammarAST) {
        withNode(tree) {
            enterAtom(tree)
            try {
                when (tree.type) {
                    ANTLRParserTypes.DOT -> {
                        val target = tree.getChild(1)
                        if (target != null) {
                            if (target.type == ANTLRParserTypes.RULE_REF) {
                                ruleref(target)
                            } else {
                                terminal(target)
                            }
                        }
                    }
                    ANTLRParserTypes.WILDCARD -> {
                        if (tree.childCount > 0 && tree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                            val opts = tree.getChild(0)
                            if (opts != null) elementOptions(opts)
                        }
                        wildcardRef(tree)
                    }
                    ANTLRParserTypes.STRING_LITERAL, ANTLRParserTypes.TOKEN_REF -> terminal(tree)
                    ANTLRParserTypes.SET -> blockSet(tree)
                    ANTLRParserTypes.RULE_REF -> ruleref(tree)
                }
            } finally {
                exitAtom(tree)
            }
        }
    }

    open fun blockSet(tree: GrammarAST) {
        withNode(tree) {
            enterBlockSet(tree)
            try {
                for (i in 0 until tree.childCount) {
                    val se = tree.getChild(i)
                    if (se != null) {
                        setElement(se)
                    }
                }
            } finally {
                exitBlockSet(tree)
            }
        }
    }

    open fun setElement(tree: GrammarAST) {
        withNode(tree) {
            enterSetElement(tree)
            try {
                when (tree.type) {
                    ANTLRParserTypes.STRING_LITERAL -> {
                        if (tree.childCount > 0 && tree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                            val opts = tree.getChild(0)
                            if (opts != null) elementOptions(opts)
                        }
                        if (tree is TerminalAST) {
                            stringRef(tree)
                        }
                    }
                    ANTLRParserTypes.TOKEN_REF -> {
                        if (tree.childCount > 0 && tree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                            val opts = tree.getChild(0)
                            if (opts != null) elementOptions(opts)
                        }
                        if (tree is TerminalAST) {
                            tokenRef(tree)
                        }
                    }
                    ANTLRParserTypes.RANGE -> {
                        val a = tree.getChild(0) as? TerminalAST
                        val b = tree.getChild(1) as? TerminalAST
                        if (a != null) stringRef(a)
                        if (b != null) stringRef(b)
                    }
                    ANTLRParserTypes.LEXER_CHAR_SET -> {}
                }
            } finally {
                exitSetElement(tree)
            }
        }
    }

    open fun block(tree: GrammarAST) {
        withNode(tree) {
            enterBlock(tree)
            try {
                var idx = 0
                if (tree.getChild(idx)?.type == ANTLRParserTypes.OPTIONS) {
                    val opts = tree.getChild(idx)
                    if (opts != null) optionsSpec(opts)
                    idx++
                }
                while (idx < tree.childCount && tree.getChild(idx)?.type == ANTLRParserTypes.AT) {
                    val ra = tree.getChild(idx)
                    if (ra != null) ruleAction(ra)
                    idx++
                }
                if (idx < tree.childCount && tree.getChild(idx)?.type == ANTLRParserTypes.ACTION) {
                    idx++
                }
                while (idx < tree.childCount) {
                    val alt = tree.getChild(idx)
                    if (alt != null && alt.type == ANTLRParserTypes.ALT) {
                        alternative(alt)
                    }
                    idx++
                }
            } finally {
                exitBlock(tree)
            }
        }
    }

    open fun ruleref(tree: GrammarAST) {
        withNode(tree) {
            enterRuleref(tree)
            try {
                var arg: ActionAST? = null
                var idx = 0
                if (idx < tree.childCount && tree.getChild(idx)?.type == ANTLRParserTypes.ARG_ACTION) {
                    arg = tree.getChild(idx) as? ActionAST
                    idx++
                }
                if (idx < tree.childCount && tree.getChild(idx)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                    val opts = tree.getChild(idx)
                    if (opts != null) elementOptions(opts)
                }
                ruleRef(tree, arg)
                if (arg != null) {
                    actionInAlt(arg)
                }
            } finally {
                exitRuleref(tree)
            }
        }
    }

    open fun range(tree: GrammarAST) {
        withNode(tree) {
            enterRange(tree)
            exitRange(tree)
        }
    }

    open fun terminal(tree: GrammarAST) {
        withNode(tree) {
            enterTerminal(tree)
            try {
                if (tree.childCount > 0 && tree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                    val opts = tree.getChild(0)
                    if (opts != null) elementOptions(opts)
                }
                if (tree is TerminalAST) {
                    if (tree.type == ANTLRParserTypes.STRING_LITERAL) {
                        stringRef(tree)
                    } else if (tree.type == ANTLRParserTypes.TOKEN_REF) {
                        tokenRef(tree)
                    }
                }
            } finally {
                exitTerminal(tree)
            }
        }
    }

    open fun elementOptions(tree: GrammarAST) {
        withNode(tree) {
            enterElementOptions(tree)
            try {
                val targetOwner = tree.parent as? GrammarASTWithOptions
                for (i in 0 until tree.childCount) {
                    val opt = tree.getChild(i)
                    if (opt != null && targetOwner != null) {
                        elementOptionRule(targetOwner, opt)
                    }
                }
            } finally {
                exitElementOptions(tree)
            }
        }
    }

    open fun elementOptionRule(
        t: GrammarASTWithOptions,
        tree: GrammarAST,
    ) {
        withNode(tree) {
            enterElementOption(tree)
            try {
                if (tree.type == ANTLRParserTypes.ID) {
                    elementOption(t, tree, null)
                } else if (tree.type == ANTLRParserTypes.ASSIGN) {
                    val idNode = tree.getChild(0)
                    val valNode = tree.getChild(1)
                    if (idNode != null) {
                        elementOption(t, idNode, valNode)
                    }
                }
            } finally {
                exitElementOption(tree)
            }
        }
    }

    companion object {
        const val DEFAULT_MODE_NAME: String = "DEFAULT_MODE"
    }
}
