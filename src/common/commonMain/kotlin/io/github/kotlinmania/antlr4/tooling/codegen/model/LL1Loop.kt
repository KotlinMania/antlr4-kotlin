/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenTypeDecl

/** Base model node for LL(1) single-alternative `while` and `do-while` loops. */
abstract class LL1Loop(
    factory: OutputModelFactory,
    blkOrEbnfRootAST: GrammarAST,
    alts: List<CodeBlockForAlt>,
) : Loop(factory, blkOrEbnfRootAST, alts) {
    @ModelElement
    var loopExpr: SrcOp? = null

    @ModelElement
    var blockError: ThrowNoViableAlt? = null

    fun addCodeForLoopLookaheadTempVar(look: IntervalSet): TestSetInline {
        val ownerFactory = requireNotNull(factory)
        val expr = makeLookaheadExpr(look)
        val d = TokenTypeDecl(ownerFactory, expr.varName)
        ownerFactory.currentRuleFunction?.addLocalDecl(d)
        val nextType = CaptureNextTokenType(ownerFactory, expr.varName)
        addIterationOp(nextType)
        return expr
    }
}
