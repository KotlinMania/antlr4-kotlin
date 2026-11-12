/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.PlusBlockStartState
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** General ATN `adaptivePredict` positive-closure subrule (`(...)+`). */
class PlusBlock(
    factory: OutputModelFactory,
    plusRoot: GrammarAST,
    alts: List<CodeBlockForAlt>,
    @ModelElement var error: ThrowNoViableAlt,
) : Loop(factory, plusRoot, alts) {
    val loopLabelName: String = factory.generator.target.getLoopLabel(plusRoot)

    init {
        val blkAST = plusRoot.getChild(0) as? BlockAST
        val blkStart = blkAST?.atnState as? PlusBlockStartState
        val loop = blkStart?.loopBackState
        stateNumber = loop?.stateNumber ?: -1
        blockStartStateNumber = blkStart?.stateNumber ?: -1
        loopBackStateNumber = loop?.stateNumber ?: -1
        decision = loop?.decision ?: -1
    }
}
