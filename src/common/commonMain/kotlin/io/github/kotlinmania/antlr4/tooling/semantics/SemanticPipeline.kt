/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.semantics

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.analysis.LeftRecursiveRuleTransformer
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.automata.LexerATNFactory
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/**
 * Orchestrates rule collection, basic semantic checks, left-recursion transformation, symbol
 * collection, token/channel type assignment, attribute validation, and action use-def tracking.
 */
class SemanticPipeline(
    val g: Grammar,
) {
    fun process() {
        val rootAst = g.ast ?: return

        val ruleCollector = RuleCollector(g)
        ruleCollector.process(rootAst)

        var prevErrors = g.tool.errMgr.numErrors
        val basics = BasicSemanticChecks(g, ruleCollector)
        basics.process()
        if (g.tool.errMgr.numErrors > prevErrors) return

        prevErrors = g.tool.errMgr.numErrors
        val lrtrans = LeftRecursiveRuleTransformer(rootAst, ruleCollector.orderedRules.values.toList(), g)
        lrtrans.translateLeftRecursiveRules()

        if (g.tool.errMgr.numErrors > prevErrors) return

        for (r in ruleCollector.orderedRules.values) {
            g.defineRule(r)
        }

        val collector = SymbolCollector(g)
        collector.process(g.ast)

        val symcheck = SymbolChecks(g, collector)
        symcheck.process()

        for (a in collector.namedActions) {
            g.defineAction(a)
        }

        for (r in g.rules.values) {
            for (i in 1..r.numberOfAlts) {
                val alt = r.alt[i] ?: continue
                alt.ast?.alt = alt
            }
        }

        g.importTokensFromTokensFile()
        if (g.isLexer) {
            assignLexerTokenTypes(g, collector.tokensDefs)
        } else {
            assignTokenTypes(
                g,
                collector.tokensDefs,
                collector.tokenIDRefs,
                collector.terminals,
            )
        }

        symcheck.checkForModeConflicts(g)
        symcheck.checkForUnreachableTokens(g)

        assignChannelTypes(g, collector.channelDefs)

        symcheck.checkRuleArgs(g, collector.rulerefs)
        identifyStartRules(collector)
        symcheck.checkForQualifiedRuleIssues(g, collector.qualifiedRulerefs)

        if (g.tool.numErrors > 0) return

        AttributeChecks.checkAllAttributeExpressions(g)
        UseDefAnalyzer.trackTokenRuleRefsInActions(g)
    }

    fun identifyStartRules(collector: SymbolCollector) {
        for (ref in collector.rulerefs) {
            val r = g.getRule(ref.text)
            if (r != null) {
                r.isStartRule = false
            }
        }
    }

    fun assignLexerTokenTypes(
        g: Grammar,
        tokensDefs: List<GrammarAST>,
    ) {
        val outermost = g.outermostGrammar
        for (def in tokensDefs) {
            if (Grammar.isTokenName(def.text)) {
                outermost.defineTokenName(def.text)
            }
        }

        for (r in g.rules.values) {
            if (!r.isFragment && !hasTypeOrMoreCommand(r)) {
                outermost.defineTokenName(r.name)
            }
        }

        val rootAst = g.ast ?: return
        val litAliases = Grammar.getStringLiteralAliasesFromLexerRules(rootAst)
        val conflictingLiterals = HashSet<String>()
        if (litAliases != null) {
            for (alias in litAliases) {
                val nameAST = alias.nameAST
                val litAST = alias.literalAST
                val litText = litAST.text
                if (!outermost.mutableStringLiteralToTypeMap.containsKey(litText)) {
                    outermost.defineTokenAlias(nameAST.text, litText)
                } else {
                    conflictingLiterals.add(litText)
                }
            }
            for (lit in conflictingLiterals) {
                val value = outermost.mutableStringLiteralToTypeMap.remove(lit)
                if (
                    value != null &&
                    value > 0 &&
                    value < outermost.mutableTypeToStringLiteralList.size &&
                    lit == outermost.mutableTypeToStringLiteralList[value]
                ) {
                    outermost.mutableTypeToStringLiteralList[value] = null
                }
            }
        }
    }

    fun hasTypeOrMoreCommand(r: Rule): Boolean {
        val ast = r.ast ?: return false
        val altActionAst = ast.getFirstDescendantWithType(ANTLRParserTypes.LEXER_ALT_ACTION) ?: return false
        for (i in 1 until altActionAst.childCount) {
            val node = altActionAst.getChild(i) ?: continue
            if (node.type == ANTLRParserTypes.LEXER_ACTION_CALL) {
                if (node.getChild(0)?.text == "type") {
                    return true
                }
            } else if (node.text == "more") {
                return true
            }
        }
        return false
    }

    fun assignTokenTypes(
        g: Grammar,
        tokensDefs: List<GrammarAST>,
        tokenIDs: List<GrammarAST>,
        terminals: List<GrammarAST>,
    ) {
        for (alias in tokensDefs) {
            if (g.getTokenType(alias.text) != Token.INVALID_TYPE) {
                g.tool.errMgr.grammarError(
                    ErrorType.TOKEN_NAME_REASSIGNMENT,
                    g.fileName,
                    alias.token,
                    alias.text,
                )
            }
            g.defineTokenName(alias.text)
        }

        for (idAST in tokenIDs) {
            if (g.getTokenType(idAST.text) == Token.INVALID_TYPE) {
                g.tool.errMgr.grammarError(
                    ErrorType.IMPLICIT_TOKEN_DEFINITION,
                    g.fileName,
                    idAST.token,
                    idAST.text,
                )
            }
            g.defineTokenName(idAST.text)
        }

        for (termAST in terminals) {
            if (termAST.type != ANTLRParserTypes.STRING_LITERAL) {
                continue
            }
            if (g.getTokenType(termAST.text) == Token.INVALID_TYPE) {
                g.tool.errMgr.grammarError(
                    ErrorType.IMPLICIT_STRING_DEFINITION,
                    g.fileName,
                    termAST.token,
                    termAST.text,
                )
            }
        }

        g.tool.log("semantics", "tokens=${g.tokenNameToTypeMap}")
        g.tool.log("semantics", "strings=${g.stringLiteralToTypeMap}")
    }

    fun assignChannelTypes(
        g: Grammar,
        channelDefs: List<GrammarAST>,
    ) {
        val outermost = g.outermostGrammar
        for (channel in channelDefs) {
            val channelName = channel.text

            if (g.getTokenType(channelName) != Token.INVALID_TYPE) {
                g.tool.errMgr.grammarError(
                    ErrorType.CHANNEL_CONFLICTS_WITH_TOKEN,
                    g.fileName,
                    channel.token,
                    channelName,
                )
            }

            if (LexerATNFactory.COMMON_CONSTANTS.containsKey(channelName)) {
                g.tool.errMgr.grammarError(
                    ErrorType.CHANNEL_CONFLICTS_WITH_COMMON_CONSTANTS,
                    g.fileName,
                    channel.token,
                    channelName,
                )
            }

            if (outermost is LexerGrammar && outermost.modes.containsKey(channelName)) {
                g.tool.errMgr.grammarError(
                    ErrorType.CHANNEL_CONFLICTS_WITH_MODE,
                    g.fileName,
                    channel.token,
                    channelName,
                )
            }

            outermost.defineChannelName(channelName)
        }
    }
}
