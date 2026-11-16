/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.StarLoopEntryState
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** General ATN `adaptivePredict` Kleene-star subrule (`(...)*`). */
class StarBlock(
    factory: OutputModelFactory,
    blkOrEbnfRootAST: GrammarAST,
    alts: List<CodeBlockForAlt>,
) : Loop(factory, blkOrEbnfRootAST, alts) {
    val loopLabelName: String = factory.generator.target.getLoopLabel(blkOrEbnfRootAST)

    init {
        val star = blkOrEbnfRootAST.atnState as? StarLoopEntryState
        if (star != null) {
            loopBackStateNumber = star.loopBackState?.stateNumber ?: -1
            decision = star.decision
        }
    }
}
