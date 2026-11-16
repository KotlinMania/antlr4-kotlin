/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.Decl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenTypeDecl

/** Base output model node for multi-alternative decisions and EBNF subrules. */
abstract class Choice(
    factory: OutputModelFactory,
    blkOrEbnfRootAST: GrammarAST,
    @ModelElement val alts: List<CodeBlockForAlt>,
) : RuleElement(factory, blkOrEbnfRootAST) {
    var decision: Int = -1

    @ModelElement
    var label: Decl? = null

    internal val mutablePreamble: MutableList<SrcOp> = ArrayList()

    @ModelElement
    val preamble: List<SrcOp>
        get() = mutablePreamble

    fun addPreambleOp(op: SrcOp) {
        mutablePreamble.add(op)
    }

    fun getAltLookaheadAsStringLists(altLookSets: List<IntervalSet>): List<List<TokenInfo>> =
        getAltLookaheadAsStringListsFromNullable(altLookSets)

    internal fun getAltLookaheadAsStringListsFromNullable(altLookSets: List<IntervalSet?>): List<List<TokenInfo>> {
        val ownerFactory = requireNotNull(factory)
        val altLook = ArrayList<List<TokenInfo>>()
        val target = ownerFactory.generator.target
        val grammar = ownerFactory.grammar
        for (s in altLookSets) {
            if (s == null || s.isNil) {
                altLook.add(emptyList())
                continue
            }
            val list = s.toList()
            val info = ArrayList<TokenInfo>(list.size)
            for (ttype in list) {
                info.add(TokenInfo(ttype, target.getTokenTypeAsTargetLabel(grammar, ttype)))
            }
            altLook.add(info)
        }
        return altLook
    }

    fun makeLookaheadExpr(look: IntervalSet): TestSetInline {
        val ownerFactory = requireNotNull(factory)
        return TestSetInline(ownerFactory, null, look, ownerFactory.generator.target.inlineTestSetWordSize)
    }

    fun addCodeForLookaheadTempVar(look: IntervalSet): SrcOp {
        val ownerFactory = requireNotNull(factory)
        val testOps = ownerFactory.getLL1Test(look, ast ?: GrammarAST())
        val expr = testOps.firstOrNull { it is TestSetInline } as? TestSetInline
        if (expr != null) {
            val d = TokenTypeDecl(ownerFactory, expr.varName)
            ownerFactory.currentRuleFunction?.addLocalDecl(d)
            val nextType = CaptureNextTokenType(ownerFactory, expr.varName)
            addPreambleOp(nextType)
            return expr
        }
        return makeLookaheadExpr(look)
    }
}
