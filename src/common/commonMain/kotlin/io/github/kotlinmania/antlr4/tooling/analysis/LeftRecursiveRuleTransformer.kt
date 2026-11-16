/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.analysis

import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarASTWithOptions
import io.github.kotlinmania.antlr4.tooling.ast.GrammarRootAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.misc.OrderedHashMap
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.parse.GrammarASTAdaptor
import io.github.kotlinmania.antlr4.tooling.parse.ScopeParser
import io.github.kotlinmania.antlr4.tooling.parse.ToolANTLRLexer
import io.github.kotlinmania.antlr4.tooling.parse.ToolANTLRParser
import io.github.kotlinmania.antlr4.tooling.semantics.BasicSemanticChecks
import io.github.kotlinmania.antlr4.tooling.semantics.RuleCollector
import io.github.kotlinmania.antlr4.tooling.tool.AttributeDict
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.GrammarTransformPipeline
import io.github.kotlinmania.antlr4.tooling.tool.LabelElementPair
import io.github.kotlinmania.antlr4.tooling.tool.LeftRecursiveRule
import io.github.kotlinmania.antlr4.tooling.tool.Rule
import io.github.kotlinmania.antlr4.tooling.tool.Tool

/**
 * Rewrites direct left-recursive parser rules in [ast] in-place into precedence-climbing
 * `(primary)(op)*` form and attaches `[0]` default precedence options to non-recursive call sites.
 */
class LeftRecursiveRuleTransformer(
    val ast: GrammarRootAST,
    val rules: List<Rule>,
    val g: Grammar,
) {
    val tool: Tool = g.tool

    fun translateLeftRecursiveRules() {
        val language = g.language
        val leftRecursiveRuleNames = ArrayList<String>()
        for (r in rules) {
            if (!Grammar.isTokenName(r.name)) {
                val ruleAst = r.ast ?: continue
                if (LeftRecursiveRuleAnalyzer.hasImmediateRecursiveRuleRefs(ruleAst, r.name)) {
                    val fitsPattern =
                        r is LeftRecursiveRule && translateLeftRecursiveRule(ast, r, language)
                    if (fitsPattern) {
                        leftRecursiveRuleNames.add(r.name)
                    } else {
                        tool.errMgr.grammarError(
                            ErrorType.NONCONFORMING_LR_RULE,
                            g.fileName,
                            ruleAst.getChild(0)?.token,
                            r.name,
                        )
                    }
                }
            }
        }

        for (node in ast.getNodesWithType(ANTLRParserTypes.RULE_REF)) {
            if (node.parent?.type == ANTLRParserTypes.RULE) continue
            val withOptions = node as? GrammarASTWithOptions ?: continue
            if (withOptions.getOptionString(PRECEDENCE_OPTION_NAME) != null) continue
            if (leftRecursiveRuleNames.contains(node.text)) {
                withOptions.setOption(
                    PRECEDENCE_OPTION_NAME,
                    GrammarASTAdaptor().create(ANTLRParserTypes.INT, "0"),
                )
            }
        }
    }

    fun translateLeftRecursiveRule(
        ast: GrammarRootAST,
        r: LeftRecursiveRule,
        language: String?,
    ): Boolean {
        val prevRuleAST = r.ast ?: return false
        val ruleName = prevRuleAST.getChild(0)?.text ?: return false
        val leftRecursiveRuleWalker =
            LeftRecursiveRuleAnalyzer(prevRuleAST, tool, ruleName, language)
        val isLeftRec = leftRecursiveRuleWalker.recRule(prevRuleAST)
        if (!isLeftRec) return false

        val rulesRoot = ast.getFirstChildWithType(ANTLRParserTypes.RULES) ?: return false
        val newRuleText = leftRecursiveRuleWalker.artificialOpPrecRule
        val owningGrammar = (prevRuleAST.g as? Grammar) ?: g
        val t = parseArtificialRule(owningGrammar, newRuleText) ?: return false

        val newNameNode = t.getChild(0)
        val prevNameNode = prevRuleAST.getChild(0)
        if (newNameNode != null && prevNameNode != null) {
            newNameNode.token = prevNameNode.token
        }

        rulesRoot.setChild(prevRuleAST.childIndex, t)
        r.ast = t

        val transform = GrammarTransformPipeline(g, g.tool)
        transform.reduceBlocksToSets(t)
        transform.expandParameterizedLoops(t)

        val ruleCollector = RuleCollector(g)
        ruleCollector.visit(t, "rule")
        val basics = BasicSemanticChecks(g, ruleCollector)
        basics.checkAssocElementOption = false
        basics.visit(t, "rule")

        val primaryAlts = ArrayList(leftRecursiveRuleWalker.prefixAndOtherAlts)
        r.recPrimaryAlts = primaryAlts
        if (primaryAlts.isEmpty()) {
            tool.errMgr.grammarError(
                ErrorType.NO_NON_LR_ALTS,
                g.fileName,
                t.getChild(0)?.token,
                r.name,
            )
        }

        val opAlts = OrderedHashMap<Int, LeftRecursiveRuleAltInfo>()
        opAlts.putAll(leftRecursiveRuleWalker.binaryAlts)
        opAlts.putAll(leftRecursiveRuleWalker.ternaryAlts)
        opAlts.putAll(leftRecursiveRuleWalker.suffixAlts)
        r.orderedRecOpAlts = opAlts

        setAltASTPointers(r, t)

        val arg = t.getFirstChildWithType(ANTLRParserTypes.ARG_ACTION) as? ActionAST
        if (arg != null) {
            val parsedArgs = ScopeParser.parseTypedArgList(arg, arg.text, g)
            parsedArgs.type = AttributeDict.DictType.ARG
            parsedArgs.ast = arg
            r.args = parsedArgs
            arg.resolver = r.alt.getOrNull(1)
        }

        val firstAlt = r.alt.getOrNull(1)
        if (firstAlt != null) {
            for (refLabel in leftRecursiveRuleWalker.leftRecursiveRuleRefLabels) {
                val labelNode = refLabel.labelAST
                val labelOpNode = labelNode.parent ?: continue
                val elementNode = labelOpNode.getChild(1) ?: continue
                val lp = LabelElementPair(g, labelNode, elementNode, labelOpNode.type)
                firstAlt.mutableLabelDefs.map(labelNode.text, lp)
            }
        }
        r.mutableLeftRecursiveRuleRefLabels.clear()
        r.mutableLeftRecursiveRuleRefLabels.addAll(leftRecursiveRuleWalker.leftRecursiveRuleRefLabels)

        tool.log("grammar", "added: ${t.toStringTree()}")
        return true
    }

    fun parseArtificialRule(
        g: Grammar,
        ruleText: String,
    ): RuleAST? {
        val charStream = CharStreams.fromString(ruleText, g.fileName ?: Grammar.GRAMMAR_FROM_STRING_NAME)
        val lexer = ToolANTLRLexer(charStream, tool.errMgr)
        val tokens = CommonTokenStream(lexer)
        val parser = ToolANTLRParser(tokens, tool.errMgr)
        val parseRes = parser.rule()
        val tree = parseRes.tree as? RuleAST
        if (tree == null) {
            tool.errMgr.toolError(
                ErrorType.INTERNAL_ERROR,
                parseRes.start,
                "error parsing rule created during left-recursion detection: $ruleText",
            )
            return null
        }
        GrammarTransformPipeline.setGrammarPtr(g, tree)
        GrammarTransformPipeline.augmentTokensWithOriginalPosition(g, tree)
        return tree
    }

    fun setAltASTPointers(
        r: LeftRecursiveRule,
        t: RuleAST,
    ) {
        val ruleBlk = t.getFirstChildWithType(ANTLRParserTypes.BLOCK) as? BlockAST ?: return
        val mainAlt = ruleBlk.getChild(0) as? AltAST ?: return
        val primaryBlk = mainAlt.getChild(0) as? BlockAST ?: return
        val opsClosure = mainAlt.getChild(1)
        val opsBlk = opsClosure?.getChild(0) as? BlockAST

        val primaries = r.recPrimaryAlts.orEmpty()
        for (i in primaries.indices) {
            val altInfo = primaries[i]
            val createdAlt = primaryBlk.getChild(i) as? AltAST
            altInfo.altAST = createdAlt
            createdAlt?.leftRecursiveAltInfo = altInfo
            altInfo.originalAltAST?.leftRecursiveAltInfo = altInfo
        }

        val ops = r.orderedRecOpAlts
        if (ops != null && opsBlk != null) {
            for (i in 0 until ops.size) {
                val altInfo = ops.getElement(i) ?: continue
                val createdAlt = opsBlk.getChild(i) as? AltAST
                altInfo.altAST = createdAlt
                createdAlt?.leftRecursiveAltInfo = altInfo
                altInfo.originalAltAST?.leftRecursiveAltInfo = altInfo
            }
        }
    }

    companion object {
        const val PRECEDENCE_OPTION_NAME: String = "p"
        const val TOKENINDEX_OPTION_NAME: String = "tokenIndex"
    }
}
