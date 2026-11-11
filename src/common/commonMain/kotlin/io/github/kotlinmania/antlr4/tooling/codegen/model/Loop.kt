/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Base output model node for `(...)*` and `(...)+` EBNF loops. */
open class Loop(
    factory: OutputModelFactory,
    blkOrEbnfRootAST: GrammarAST,
    alts: List<CodeBlockForAlt>,
) : Choice(factory, blkOrEbnfRootAST, alts) {
    var blockStartStateNumber: Int = -1
    var loopBackStateNumber: Int = -1
    val exitAlt: Int

    internal val mutableIteration: MutableList<SrcOp> = ArrayList()

    @ModelElement
    val iteration: List<SrcOp>
        get() = mutableIteration

    init {
        val nongreedy =
            (blkOrEbnfRootAST as? io.github.kotlinmania.antlr4.tooling.ast.QuantifierAST)?.isGreedy == false
        exitAlt = if (nongreedy) 1 else alts.size + 1
    }

    fun addIterationOp(op: SrcOp) {
        mutableIteration.add(op)
    }
}
