/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.misc.DoubleKeyMap
import io.github.kotlinmania.antlr4.tooling.analysis.LeftRecursiveRuleTransformer
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarASTWithOptions
import io.github.kotlinmania.antlr4.tooling.ast.GrammarRootAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.parse.BlockSetTransformer
import io.github.kotlinmania.antlr4.tooling.parse.GrammarASTAdaptor
import io.github.kotlinmania.antlr4.tooling.parse.GrammarToken

/**
 * Performs AST-level grammar transformations: delegate grammar merging, single-token block-to-set
 * reduction, and implicit lexer extraction for combined grammars.
 */
class GrammarTransformPipeline(
    var g: Grammar,
    var tool: Tool,
) {
    fun process() {
        val root = g.ast ?: return
        tool.log("grammar", "before: ${root.toStringTree()}")

        integrateImportedGrammars(g)
        reduceBlocksToSets(root)
        expandParameterizedLoops(root)

        tool.log("grammar", "after: ${root.toStringTree()}")
    }

    fun reduceBlocksToSets(root: GrammarAST) {
        val transformer = BlockSetTransformer(g)
        transformer.downup(root)
    }

    fun expandParameterizedLoops(root: GrammarAST) {
        val work = ArrayDeque<GrammarAST>()
        work.addLast(root)
        while (work.isNotEmpty()) {
            val current = work.removeFirst()
            for (child in current.children) {
                work.addLast(child)
            }
        }
    }

    fun integrateImportedGrammars(rootGrammar: Grammar) {
        val imports = rootGrammar.allImportedGrammars ?: return
        val root = rootGrammar.ast ?: return
        root.getChild(0) ?: return
        val adaptor = GrammarASTAdaptor()

        var channelsRoot = root.getFirstChildWithType(ANTLRParserTypes.CHANNELS)
        var tokensRoot = root.getFirstChildWithType(ANTLRParserTypes.TOKENS_SPEC)
        val actionRoots = root.getNodesWithType(ANTLRParserTypes.AT)

        val rulesContainer = root.getFirstChildWithType(ANTLRParserTypes.RULES) ?: return
        val rootRuleNames = HashSet<String>()
        for (r in rulesContainer.getNodesWithType(ANTLRParserTypes.RULE)) {
            val rName = r.getChild(0)?.text
            if (rName != null) rootRuleNames.add(rName)
        }

        val rootModes = root.getNodesWithType(ANTLRParserTypes.MODE).toMutableList()
        val rootModeNames = HashSet<String>()
        for (m in rootModes) {
            val mName = m.getChild(0)?.text
            if (mName != null) rootModeNames.add(mName)
        }

        for (imp in imports) {
            val impAst = imp.ast ?: continue

            // COPY CHANNELS
            val impChannelRoot = impAst.getFirstChildWithType(ANTLRParserTypes.CHANNELS)
            if (impChannelRoot != null) {
                rootGrammar.tool.log("grammar", "imported channels: ${impChannelRoot.children}")
                val existingChannelsRoot = channelsRoot
                if (existingChannelsRoot == null) {
                    val copied = impChannelRoot.dupTree()
                    setGrammarPtr(rootGrammar, copied)
                    channelsRoot = copied
                    root.insertChild(1, copied)
                } else {
                    for (c in 0 until impChannelRoot.childCount) {
                        val impChannelNode = impChannelRoot.getChild(c) ?: continue
                        val channel = impChannelNode.text
                        val alreadyInRoot =
                            (0 until existingChannelsRoot.childCount).any { rc ->
                                existingChannelsRoot.getChild(rc)?.text == channel
                            }
                        if (!alreadyInRoot) {
                            val dup = impChannelNode.dupNode()
                            dup.g = rootGrammar
                            existingChannelsRoot.addChild(dup)
                        }
                    }
                }
            }

            // COPY TOKENS
            val impTokensRoot = impAst.getFirstChildWithType(ANTLRParserTypes.TOKENS_SPEC)
            if (impTokensRoot != null) {
                rootGrammar.tool.log("grammar", "imported tokens: ${impTokensRoot.children}")
                var targetTokensRoot = tokensRoot
                if (targetTokensRoot == null) {
                    targetTokensRoot = adaptor.create(ANTLRParserTypes.TOKENS_SPEC, "TOKENS")
                    targetTokensRoot.g = rootGrammar
                    tokensRoot = targetTokensRoot
                    root.insertChild(1, targetTokensRoot)
                }
                for (child in impTokensRoot.children) {
                    targetTokensRoot.addChild(child.dupTree())
                }
            }

            // COPY ACTIONS
            val impActionRoots = impAst.getAllChildrenWithType(ANTLRParserTypes.AT)
            if (impActionRoots.isNotEmpty()) {
                val allActionRoots = ArrayList<GrammarAST>()
                allActionRoots.addAll(actionRoots)
                allActionRoots.addAll(impActionRoots)

                val namedActions = DoubleKeyMap<String, String, GrammarAST>()
                rootGrammar.tool.log("grammar", "imported actions: $impActionRoots")
                for (at in allActionRoots) {
                    var scopeName = rootGrammar.defaultActionScope ?: "parser"
                    val nameNode: GrammarAST
                    val actionNode: GrammarAST
                    if (at.childCount > 2) {
                        val scopeNode = at.getChild(0) ?: continue
                        scopeName = scopeNode.text
                        nameNode = at.getChild(1) ?: continue
                        actionNode = at.getChild(2) ?: continue
                    } else {
                        nameNode = at.getChild(0) ?: continue
                        actionNode = at.getChild(1) ?: continue
                    }
                    val prevAction = namedActions.get(scopeName, nameNode.text)
                    if (prevAction == null) {
                        namedActions.put(scopeName, nameNode.text, actionNode)
                    } else {
                        if (prevAction.g === at.g) {
                            rootGrammar.tool.errMgr.grammarError(
                                ErrorType.ACTION_REDEFINITION,
                                at.g?.fileName,
                                nameNode.token,
                                nameNode.text,
                            )
                        } else {
                            val s1Raw = prevAction.text
                            val s1 = if (s1Raw.length >= 2) s1Raw.substring(1, s1Raw.length - 1) else s1Raw
                            val s2Raw = actionNode.text
                            val s2 = if (s2Raw.length >= 2) s2Raw.substring(1, s2Raw.length - 1) else s2Raw
                            prevAction.text = "{$s1\n$s2}"
                        }
                    }
                }
                for (scopeName in namedActions.keySet()) {
                    val innerKeys = namedActions.keySet(scopeName) ?: continue
                    for (actName in innerKeys) {
                        val actNode = namedActions.get(scopeName, actName) ?: continue
                        if (actNode.g !== rootGrammar) {
                            root.insertChild(1, actNode.parent)
                        }
                    }
                }
            }

            // COPY MODES
            val modes = impAst.getNodesWithType(ANTLRParserTypes.MODE)
            for (m in modes) {
                rootGrammar.tool.log("grammar", "imported mode: ${m.toStringTree()}")
                val modeName = m.getChild(0)?.text ?: continue
                val rootAlreadyHasMode = rootModeNames.contains(modeName)
                var destinationAST: GrammarAST? = null
                if (rootAlreadyHasMode) {
                    destinationAST = rootModes.firstOrNull { it.getChild(0)?.text == modeName }
                } else {
                    val copiedMode = m.dupNode()
                    copiedMode.addChild(m.getChild(0)?.dupNode())
                    destinationAST = copiedMode
                }
                if (destinationAST != null) {
                    var addedRules = 0
                    for (r in m.getAllChildrenWithType(ANTLRParserTypes.RULE)) {
                        rootGrammar.tool.log("grammar", "imported rule: ${r.toStringTree()}")
                        val rName = r.getChild(0)?.text ?: continue
                        if (!rootRuleNames.contains(rName)) {
                            destinationAST.addChild(r)
                            addedRules++
                            rootRuleNames.add(rName)
                        }
                    }
                    if (!rootAlreadyHasMode && addedRules > 0) {
                        root.addChild(destinationAST)
                        rootModeNames.add(modeName)
                        rootModes.add(destinationAST)
                    }
                }
            }

            // COPY RULES
            val impRules = impAst.getNodesWithType(ANTLRParserTypes.RULE)
            for (r in impRules) {
                rootGrammar.tool.log("grammar", "imported rule: ${r.toStringTree()}")
                val rName = r.getChild(0)?.text ?: continue
                if (!rootRuleNames.contains(rName)) {
                    rulesContainer.addChild(r)
                    rootRuleNames.add(rName)
                }
            }

            val optionsRoot = impAst.getFirstChildWithType(ANTLRParserTypes.OPTIONS)
            if (optionsRoot != null) {
                var hasNewOption = false
                for (optKey in impAst.options.keys) {
                    val importOption = impAst.getOptionString(optKey) ?: continue
                    val rootOption = root.getOptionString(optKey)
                    if (importOption != rootOption) {
                        hasNewOption = true
                        break
                    }
                }
                if (hasNewOption) {
                    rootGrammar.tool.errMgr.grammarError(
                        ErrorType.OPTIONS_IN_DELEGATE,
                        optionsRoot.g?.fileName,
                        optionsRoot.token,
                        imp.name,
                    )
                }
            }
        }
        rootGrammar.tool.log("grammar", "Grammar: ${root.toStringTree()}")
    }

    fun extractImplicitLexer(combinedGrammar: Grammar): GrammarRootAST? {
        val combinedAST = combinedGrammar.ast ?: return null
        val adaptor = GrammarASTAdaptor()
        val elements = combinedAST.children.toList()

        val lexerName = "${combinedAST.getChild(0)?.text.orEmpty()}Lexer"
        val lexerRootToken = CommonToken(ANTLRParserTypes.GRAMMAR, "LEXER_GRAMMAR")
        val lexerAST = GrammarRootAST(lexerRootToken, combinedAST.tokenStream)
        lexerAST.grammarType = ANTLRParserTypes.LEXER
        lexerAST.addChild(adaptor.create(ANTLRParserTypes.ID, lexerName))

        val optionsRoot = combinedAST.getFirstChildWithType(ANTLRParserTypes.OPTIONS)
        if (optionsRoot != null && optionsRoot.childCount != 0) {
            val lexerOptionsRoot = adaptor.dupNode(optionsRoot) ?: optionsRoot.dupNode()
            lexerAST.addChild(lexerOptionsRoot)
            for (o in optionsRoot.children.toList()) {
                val optionName = o.getChild(0)?.text ?: continue
                if (Grammar.lexerOptions.contains(optionName) &&
                    !Grammar.doNotCopyOptionsToLexer.contains(optionName)
                ) {
                    val optionTree = adaptor.dupTree(o) ?: o.dupTree()
                    lexerOptionsRoot.addChild(optionTree)
                    lexerAST.setOption(optionName, optionTree.getChild(1))
                }
            }
        }

        val actionsWeMoved = ArrayList<GrammarAST>()
        for (e in elements) {
            if (e.type == ANTLRParserTypes.AT) {
                lexerAST.addChild(adaptor.dupTree(e))
                if (e.getChild(0)?.text == "lexer") {
                    actionsWeMoved.add(e)
                }
            }
        }
        for (moved in actionsWeMoved) {
            combinedAST.deleteChild(moved)
        }

        val combinedRulesRoot = combinedAST.getFirstChildWithType(ANTLRParserTypes.RULES) ?: return lexerAST
        val lexerRulesRoot = adaptor.create(ANTLRParserTypes.RULES, "RULES")
        lexerAST.addChild(lexerRulesRoot)

        val rulesWeMoved = ArrayList<GrammarAST>()
        for (r in combinedRulesRoot.children.toList()) {
            val ruleName = r.getChild(0)?.text ?: continue
            if (Grammar.isTokenName(ruleName)) {
                lexerRulesRoot.addChild(adaptor.dupTree(r))
                rulesWeMoved.add(r)
            }
        }
        for (moved in rulesWeMoved) {
            combinedRulesRoot.deleteChild(moved)
        }

        val litAliases = Grammar.getStringLiteralAliasesFromLexerRules(lexerAST)
        val stringLiterals = combinedGrammar.stringLiterals

        var insertIndex = 0
        nextLit@ for (lit in stringLiterals) {
            if (litAliases != null) {
                for (alias in litAliases) {
                    if (lit == alias.literalAST.text) {
                        continue@nextLit
                    }
                }
            }
            val rname = combinedGrammar.getStringLiteralLexerRuleName(lit)
            val litRule = RuleAST(ANTLRParserTypes.RULE)
            val blk = BlockAST(ANTLRParserTypes.BLOCK)
            val alt = AltAST(ANTLRParserTypes.ALT)
            val slit = TerminalAST(CommonToken(ANTLRParserTypes.STRING_LITERAL, lit))
            alt.addChild(slit)
            blk.addChild(alt)
            val idToken = CommonToken(ANTLRParserTypes.TOKEN_REF, rname)
            litRule.addChild(TerminalAST(idToken))
            litRule.addChild(blk)
            lexerRulesRoot.insertChild(insertIndex, litRule)
            lexerRulesRoot.freshenParentAndChildIndexes()
            insertIndex++
        }

        lexerAST.sanityCheckParentAndChildIndexes()
        combinedAST.sanityCheckParentAndChildIndexes()

        combinedGrammar.tool.log("grammar", "after extract implicit lexer =${combinedAST.toStringTree()}")
        combinedGrammar.tool.log("grammar", "lexer =${lexerAST.toStringTree()}")

        if (lexerRulesRoot.childCount == 0) return null
        return lexerAST
    }

    companion object {
        fun setGrammarPtr(
            g: Grammar,
            tree: GrammarAST?,
        ) {
            if (tree == null) return
            val work = ArrayDeque<GrammarAST>()
            work.addLast(tree)
            while (work.isNotEmpty()) {
                val current = work.removeFirst()
                current.g = g
                for (child in current.children) {
                    work.addLast(child)
                }
            }
        }

        fun augmentTokensWithOriginalPosition(
            g: Grammar,
            tree: GrammarAST?,
        ) {
            if (tree == null) return
            val optionsSubTrees = tree.getNodesWithType(ANTLRParserTypes.ELEMENT_OPTIONS)
            for (t in optionsSubTrees) {
                val elWithOpt = t.parent
                if (elWithOpt is GrammarASTWithOptions) {
                    val options = elWithOpt.options
                    val tokIndexAst = options[LeftRecursiveRuleTransformer.TOKENINDEX_OPTION_NAME]
                    val origTok = elWithOpt.token
                    if (tokIndexAst != null && origTok != null) {
                        val newTok = GrammarToken(g, origTok)
                        newTok.originalTokenIndex = tokIndexAst.text.toIntOrNull() ?: -1
                        elWithOpt.token = newTok

                        val originalNode = g.ast?.getNodeWithTokenIndex(newTok.tokenIndex)
                        if (originalNode != null) {
                            elWithOpt.tokenStartIndex = originalNode.tokenStartIndex
                            elWithOpt.tokenStopIndex = originalNode.tokenStopIndex
                        } else {
                            elWithOpt.tokenStartIndex = newTok.tokenIndex
                            elWithOpt.tokenStopIndex = newTok.tokenIndex
                        }
                    }
                }
            }
        }
    }
}
