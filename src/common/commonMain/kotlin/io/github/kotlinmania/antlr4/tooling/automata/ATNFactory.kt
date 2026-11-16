/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.automata

import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.PredAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST

/**
 * Multi-entry/single-exit Thompson NFA/ATN construction contract invoked while walking a [GrammarAST].
 */
interface ATNFactory {
    /** Pair of `(left, right)` entry/exit [ATNState] endpoints representing a constructed subnetwork. */
    class Handle(
        var left: ATNState,
        var right: ATNState,
    ) {
        override fun toString(): String = "($left,$right)"
    }

    fun createATN(): ATN

    fun setCurrentRuleName(name: String)

    fun setCurrentOuterAlt(alt: Int)

    fun rule(
        ruleAST: GrammarAST,
        name: String,
        blk: Handle,
    ): Handle

    fun newState(): ATNState

    fun label(t: Handle): Handle

    fun listLabel(t: Handle): Handle

    fun tokenRef(node: TerminalAST): Handle

    fun set(
        associatedAST: GrammarAST,
        alts: List<GrammarAST>,
        invert: Boolean,
    ): Handle

    fun charSetLiteral(charSetAST: GrammarAST): Handle

    fun range(
        a: GrammarAST,
        b: GrammarAST,
    ): Handle

    fun stringLiteral(stringLiteralAST: TerminalAST): Handle

    fun ruleRef(node: GrammarAST): Handle

    fun epsilon(node: GrammarAST?): Handle

    fun sempred(pred: PredAST): Handle

    fun action(action: ActionAST): Handle

    fun actionFromText(action: String): Handle

    fun alt(els: List<Handle>): Handle

    fun block(
        blkAST: BlockAST,
        ebnfRoot: GrammarAST?,
        alts: List<Handle>,
    ): Handle

    fun optional(
        optAST: GrammarAST,
        blk: Handle,
    ): Handle

    fun plus(
        plusAST: GrammarAST,
        blk: Handle,
    ): Handle

    fun star(
        starAST: GrammarAST,
        blk: Handle,
    ): Handle

    fun wildcard(ast: GrammarAST): Handle

    fun lexerAltCommands(
        alt: Handle,
        cmds: Handle,
    ): Handle

    fun lexerCallCommand(
        id: GrammarAST,
        arg: GrammarAST,
    ): Handle?

    fun lexerCommand(id: GrammarAST): Handle?
}
