/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.PredAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.automata.ATNFactory

/**
 * Pure-Kotlin tree walker implementing `ATNBuilder.g` over [GrammarAST] rule/subrule subtrees
 * and driving an [ATNFactory] (`ParserATNFactory` or `LexerATNFactory`).
 */
class ATNBuilder(
    val factory: ATNFactory,
) {
    fun ruleBlock(
        blockNode: BlockAST,
        ebnfRoot: GrammarAST? = null,
    ): ATNFactory.Handle {
        val alts = ArrayList<ATNFactory.Handle>()
        var alt = 1
        factory.setCurrentOuterAlt(alt)
        for (child in blockNode.children) {
            if (child.type == ANTLRParserTypes.OPTIONS) {
                continue
            }
            if (child.type == ANTLRParserTypes.ALT || child.type == ANTLRParserTypes.LEXER_ALT_ACTION) {
                alts.add(alternative(child))
                alt++
                factory.setCurrentOuterAlt(alt)
            }
        }
        return factory.block(blockNode, ebnfRoot, alts)
    }

    fun block(
        blockNode: BlockAST,
        ebnfRoot: GrammarAST?,
    ): ATNFactory.Handle {
        val alts = ArrayList<ATNFactory.Handle>()
        for (child in blockNode.children) {
            if (child.type == ANTLRParserTypes.OPTIONS) {
                continue
            }
            if (child.type == ANTLRParserTypes.ALT || child.type == ANTLRParserTypes.LEXER_ALT_ACTION) {
                alts.add(alternative(child))
            }
        }
        return factory.block(blockNode, ebnfRoot, alts)
    }

    fun alternative(altNode: GrammarAST): ATNFactory.Handle {
        if (altNode.type == ANTLRParserTypes.LEXER_ALT_ACTION) {
            val innerAlt = requireNotNull(altNode.getChild(0)) { "LEXER_ALT_ACTION missing inner ALT" }
            val altHandle = alternative(innerAlt)
            val cmdsHandle = lexerCommands(altNode, startIndex = 1)
            return factory.lexerAltCommands(altHandle, cmdsHandle)
        }

        require(altNode.type == ANTLRParserTypes.ALT) {
            "Expected ALT or LEXER_ALT_ACTION node, got ${ANTLRParserTypes.tokenName(altNode.type)}"
        }

        var idx = 0
        if (idx < altNode.childCount && altNode.getChild(idx)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
            idx++
        }
        if (idx < altNode.childCount && altNode.getChild(idx)?.type == ANTLRParserTypes.EPSILON) {
            return factory.epsilon(altNode.getChild(idx))
        }

        val els = ArrayList<ATNFactory.Handle>()
        while (idx < altNode.childCount) {
            val child = altNode.getChild(idx)
            if (child != null) {
                els.add(element(child))
            }
            idx++
        }
        return if (els.isEmpty()) {
            factory.epsilon(altNode)
        } else {
            factory.alt(els)
        }
    }

    fun lexerCommands(
        parentNode: GrammarAST,
        startIndex: Int = 0,
    ): ATNFactory.Handle {
        val cmds = ArrayList<ATNFactory.Handle>()
        for (i in startIndex until parentNode.childCount) {
            val c = parentNode.getChild(i) ?: continue
            val cmd = lexerCommand(c)
            if (cmd != null) {
                cmds.add(cmd)
            }
        }
        return factory.alt(cmds)
    }

    fun lexerCommand(cmdNode: GrammarAST): ATNFactory.Handle? =
        if (cmdNode.type == ANTLRParserTypes.LEXER_ACTION_CALL) {
            val id = requireNotNull(cmdNode.getChild(0)) { "LEXER_ACTION_CALL missing command ID" }
            val arg = requireNotNull(cmdNode.getChild(1)) { "LEXER_ACTION_CALL missing command argument" }
            factory.lexerCallCommand(id, arg)
        } else {
            factory.lexerCommand(cmdNode)
        }

    fun element(node: GrammarAST): ATNFactory.Handle =
        when (node.type) {
            ANTLRParserTypes.ASSIGN -> {
                val rhs = requireNotNull(node.getChild(1)) { "ASSIGN missing element operand" }
                factory.label(element(rhs))
            }

            ANTLRParserTypes.PLUS_ASSIGN -> {
                val rhs = requireNotNull(node.getChild(1)) { "PLUS_ASSIGN missing element operand" }
                factory.listLabel(element(rhs))
            }

            ANTLRParserTypes.OPTIONAL,
            ANTLRParserTypes.CLOSURE,
            ANTLRParserTypes.POSITIVE_CLOSURE,
            ANTLRParserTypes.BLOCK,
            -> subrule(node)

            ANTLRParserTypes.ACTION -> factory.action(node as ActionAST)

            ANTLRParserTypes.SEMPRED -> factory.sempred(node as PredAST)

            ANTLRParserTypes.NOT -> {
                val setChild = requireNotNull(node.getChild(0)) { "NOT missing blockSet child" }
                blockSet(setChild, invert = true)
            }

            ANTLRParserTypes.LEXER_CHAR_SET -> factory.charSetLiteral(node)

            else -> atom(node)
        }

    fun subrule(node: GrammarAST): ATNFactory.Handle =
        when (node.type) {
            ANTLRParserTypes.OPTIONAL,
            ANTLRParserTypes.CLOSURE,
            ANTLRParserTypes.POSITIVE_CLOSURE,
            -> {
                val innerBlock =
                    requireNotNull(node.getChild(0) as? BlockAST) {
                        "EBNF ${ANTLRParserTypes.tokenName(node.type)} missing BlockAST child"
                    }
                block(innerBlock, node)
            }

            ANTLRParserTypes.BLOCK -> block(node as BlockAST, null)

            else -> error("Unexpected subrule node: ${ANTLRParserTypes.tokenName(node.type)}")
        }

    fun blockSet(
        setNode: GrammarAST,
        invert: Boolean,
    ): ATNFactory.Handle {
        require(setNode.type == ANTLRParserTypes.SET) {
            "Expected SET node, got ${ANTLRParserTypes.tokenName(setNode.type)}"
        }
        val alts = ArrayList<GrammarAST>(setNode.childCount)
        for (child in setNode.children) {
            alts.add(child)
        }
        return factory.set(setNode, alts, invert)
    }

    fun atom(node: GrammarAST): ATNFactory.Handle =
        when (node.type) {
            ANTLRParserTypes.RANGE -> {
                val a = requireNotNull(node.getChild(0)) { "RANGE missing left endpoint" }
                val b = requireNotNull(node.getChild(1)) { "RANGE missing right endpoint" }
                factory.range(a, b)
            }

            ANTLRParserTypes.DOT -> {
                val target = requireNotNull(node.getChild(1)) { "DOT missing qualified member" }
                if (target.type == ANTLRParserTypes.RULE_REF) {
                    ruleref(target)
                } else {
                    terminal(target)
                }
            }

            ANTLRParserTypes.WILDCARD -> factory.wildcard(node)

            ANTLRParserTypes.SET -> blockSet(node, invert = false)

            ANTLRParserTypes.RULE_REF -> ruleref(node)

            ANTLRParserTypes.STRING_LITERAL,
            ANTLRParserTypes.TOKEN_REF,
            -> terminal(node)

            else -> error("Unexpected atom node: ${ANTLRParserTypes.tokenName(node.type)} ($node)")
        }

    fun ruleref(node: GrammarAST): ATNFactory.Handle = factory.ruleRef(node)

    fun terminal(node: GrammarAST): ATNFactory.Handle =
        when (node.type) {
            ANTLRParserTypes.STRING_LITERAL -> factory.stringLiteral(node as TerminalAST)
            ANTLRParserTypes.TOKEN_REF -> factory.tokenRef(node as TerminalAST)
            else -> error("Unexpected terminal node: ${ANTLRParserTypes.tokenName(node.type)}")
        }
}
