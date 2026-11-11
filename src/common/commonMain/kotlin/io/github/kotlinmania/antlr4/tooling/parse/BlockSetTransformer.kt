/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.SetAST
import io.github.kotlinmania.antlr4.tooling.misc.CharSupport
import io.github.kotlinmania.antlr4.tooling.tool.GrammarASTOwner

/**
 * Pure-Kotlin AST rewriter implementing `BlockSetTransformer.g` to collapse multi-alternative
 * single-atom blocks `(A | B | C)` into compact `(SET A B C)` AST subtrees.
 */
class BlockSetTransformer(
    val g: GrammarASTOwner?,
) {
    var treeAdaptor: GrammarASTAdaptor = GrammarASTAdaptor()
    var currentRuleName: String? = null
    var currentAlt: GrammarAST? = null

    fun downup(root: GrammarAST?): GrammarAST? {
        if (root == null) return null
        return visitTopdown(root)
    }

    private fun visitTopdown(node: GrammarAST): GrammarAST {
        var current: GrammarAST = node

        if (current.type == ANTLRParserTypes.RULE) {
            val nameChild = current.getChild(0)
            if (
                nameChild != null &&
                (nameChild.type == ANTLRParserTypes.TOKEN_REF || nameChild.type == ANTLRParserTypes.RULE_REF)
            ) {
                currentRuleName = nameChild.text
            }
        } else if (current.type == ANTLRParserTypes.ALT) {
            currentAlt = current
        }

        val transformedEbnf = tryTransformEbnfBlockSet(current)
        if (transformedEbnf != null) {
            current = transformedEbnf
        } else {
            val transformedBlock = tryTransformBlockSet(current)
            if (transformedBlock != null) {
                current = transformedBlock
            }
        }

        var i = 0
        while (i < current.childCount) {
            val child = current.getChild(i)
            if (child != null) {
                val newChild = visitTopdown(child)
                if (newChild !== child) {
                    current.setChild(i, newChild)
                }
            }
            i++
        }
        return current
    }

    /**
     * `ebnfBlockSet : ^(ebnfSuffix blockSet) -> ^(ebnfSuffix ^(BLOCK<BlockAST> ^(ALT<AltAST> blockSet))) ;`
     */
    private fun tryTransformEbnfBlockSet(node: GrammarAST): GrammarAST? {
        if (
            node.type != ANTLRParserTypes.OPTIONAL &&
            node.type != ANTLRParserTypes.CLOSURE &&
            node.type != ANTLRParserTypes.POSITIVE_CLOSURE
        ) {
            return null
        }
        if (node.childCount != 1) {
            return null
        }
        val innerBlock = node.getChild(0) ?: return null
        if (innerBlock.type != ANTLRParserTypes.BLOCK) {
            return null
        }
        val setAst = buildSetFromBlock(innerBlock, inContextRule = false) ?: return null
        val wrappedBlock = BlockAST(ANTLRParserTypes.BLOCK, innerBlock.token, "BLOCK")
        val wrappedAlt = AltAST(ANTLRParserTypes.ALT, innerBlock.token, "ALT")
        wrappedAlt.addChild(setAst)
        wrappedBlock.addChild(wrappedAlt)
        treeAdaptor.setTokenBoundaries(wrappedAlt, setAst.token, setAst.token)
        wrappedAlt.tokenStartIndex = innerBlock.tokenStartIndex
        wrappedAlt.tokenStopIndex = innerBlock.tokenStopIndex
        wrappedBlock.tokenStartIndex = innerBlock.tokenStartIndex
        wrappedBlock.tokenStopIndex = innerBlock.tokenStopIndex
        node.setChild(0, wrappedBlock)
        assignGrammarRecursive(wrappedBlock)
        return node
    }

    /**
     * ```
     * blockSet :
     *   {inContext("RULE")}? ^(BLOCK alt1=setAlt setAlt+) -> ^(BLOCK ^(ALT SET[alt+]))
     * | {!inContext("RULE")}? ^(BLOCK setAlt setAlt+) -> SET[alt+]
     * ```
     */
    private fun tryTransformBlockSet(node: GrammarAST): GrammarAST? {
        if (node.type != ANTLRParserTypes.BLOCK) {
            return null
        }
        val inContextRule = node.parent?.type == ANTLRParserTypes.RULE
        val setAst = buildSetFromBlock(node, inContextRule) ?: return null
        val replacement: GrammarAST =
            if (inContextRule) {
                val wrappedBlock = BlockAST(ANTLRParserTypes.BLOCK, node.token, "BLOCK")
                val wrappedAlt = AltAST(ANTLRParserTypes.ALT, node.token, "ALT")
                wrappedAlt.addChild(setAst)
                wrappedBlock.addChild(wrappedAlt)
                wrappedAlt.tokenStartIndex = node.tokenStartIndex
                wrappedAlt.tokenStopIndex = node.tokenStopIndex
                wrappedBlock.tokenStartIndex = node.tokenStartIndex
                wrappedBlock.tokenStopIndex = node.tokenStopIndex
                wrappedBlock
            } else {
                setAst
            }
        assignGrammarRecursive(replacement)
        val parentNode = node.parent
        val idx = node.childIndex
        if (parentNode != null && idx in 0 until parentNode.childCount) {
            parentNode.setChild(idx, replacement)
        }
        return replacement
    }

    private fun buildSetFromBlock(
        blockNode: GrammarAST,
        inContextRule: Boolean,
    ): SetAST? {
        if (blockNode.childCount < 2) {
            return null
        }
        val inLexer = ANTLRParserTypes.isTokenName(currentRuleName ?: "")
        val collectedElements = ArrayList<GrammarAST>(blockNode.childCount)

        for (i in 0 until blockNode.childCount) {
            val alt = blockNode.getChild(i) ?: return null
            if (alt.type != ANTLRParserTypes.ALT) {
                return null
            }
            if (inContextRule && (alt as? AltAST)?.altLabel != null) {
                return null
            }
            currentAlt = alt
            val elem = extractSingleSetElementFromAlt(alt, inLexer) ?: return null
            collectedElements.add(elem)
        }

        val setTok = blockNode.token
        val setAst = SetAST(ANTLRParserTypes.SET, setTok, "SET")
        setAst.tokenStartIndex = blockNode.tokenStartIndex
        setAst.tokenStopIndex = blockNode.tokenStopIndex
        for (elem in collectedElements) {
            setAst.addChild(elem.dupTree())
        }
        return setAst
    }

    private fun extractSingleSetElementFromAlt(
        alt: GrammarAST,
        inLexer: Boolean,
    ): GrammarAST? {
        val elem: GrammarAST =
            when (alt.childCount) {
                1 -> alt.getChild(0) ?: return null
                2 -> {
                    val first = alt.getChild(0) ?: return null
                    if (first.type != ANTLRParserTypes.ELEMENT_OPTIONS) {
                        return null
                    }
                    alt.getChild(1) ?: return null
                }
                else -> return null
            }
        return if (isValidSetElement(elem, inLexer)) elem else null
    }

    private fun isValidSetElement(
        elem: GrammarAST,
        inLexer: Boolean,
    ): Boolean {
        when (elem.type) {
            ANTLRParserTypes.STRING_LITERAL -> {
                if (!hasAtMostElementOptionsChild(elem)) {
                    return false
                }
                return !inLexer || CharSupport.getCharValueFromGrammarCharLiteral(elem.text) != -1
            }
            ANTLRParserTypes.TOKEN_REF -> {
                if (inLexer) {
                    return false
                }
                return hasAtMostElementOptionsChild(elem)
            }
            ANTLRParserTypes.RANGE -> {
                if (!inLexer || elem.childCount != 2) {
                    return false
                }
                val a = elem.getChild(0) ?: return false
                val b = elem.getChild(1) ?: return false
                if (
                    a.type != ANTLRParserTypes.STRING_LITERAL ||
                    b.type != ANTLRParserTypes.STRING_LITERAL ||
                    a.childCount != 0 ||
                    b.childCount != 0
                ) {
                    return false
                }
                return CharSupport.getCharValueFromGrammarCharLiteral(a.text) != -1 &&
                    CharSupport.getCharValueFromGrammarCharLiteral(b.text) != -1
            }
            else -> return false
        }
    }

    private fun hasAtMostElementOptionsChild(elem: GrammarAST): Boolean {
        if (elem.childCount == 0) return true
        if (elem.childCount == 1 && elem.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
            return true
        }
        return false
    }

    private fun assignGrammarRecursive(node: GrammarAST) {
        val owner = g ?: return
        node.g = owner
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                assignGrammarRecursive(child)
            }
        }
    }
}
