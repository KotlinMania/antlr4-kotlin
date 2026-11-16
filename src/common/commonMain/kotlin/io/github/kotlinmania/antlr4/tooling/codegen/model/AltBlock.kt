/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.BlockStartState
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** General ATN `adaptivePredict` multi-alternative block (`(a | b | c)`). */
open class AltBlock(
    factory: OutputModelFactory,
    blkOrEbnfRootAST: GrammarAST,
    alts: List<CodeBlockForAlt>,
) : Choice(factory, blkOrEbnfRootAST, alts) {
    @ModelElement
    var error: ThrowNoViableAlt? = null

    init {
        decision = (blkOrEbnfRootAST.atnState as? BlockStartState)?.decision ?: -1
    }
}
