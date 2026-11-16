/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.decl

import io.github.kotlinmania.antlr4.misc.OrderedHashSet
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.ModelElement
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp

/** Scoped block of local declarations, preamble statements, and matching operations inside a rule. */
open class CodeBlock(
    factory: OutputModelFactory,
    val treeLevel: Int = 0,
    val altNum: Int = 0,
) : SrcOp(factory) {
    internal var mutableLocals: OrderedHashSet<Decl>? = null
    internal val mutablePreamble: MutableList<SrcOp> = ArrayList()
    internal val mutableOps: MutableList<SrcOp> = ArrayList()

    @ModelElement
    val locals: List<Decl>?
        get() = mutableLocals?.elements()

    @ModelElement
    val preamble: List<SrcOp>?
        get() = mutablePreamble.ifEmpty { null }

    @ModelElement
    val ops: List<SrcOp>?
        get() = mutableOps.ifEmpty { null }

    fun addLocalDecl(d: Decl) {
        var set = mutableLocals
        if (set == null) {
            set = OrderedHashSet()
            mutableLocals = set
        }
        set.add(d)
        d.isImplicit = true
    }

    fun addPreambleOp(op: SrcOp) {
        mutablePreamble.add(op)
    }

    fun addOp(op: SrcOp) {
        mutableOps.add(op)
    }

    fun insertOp(
        i: Int,
        op: SrcOp,
    ) {
        mutableOps.add(i, op)
    }

    fun addOps(newOps: List<SrcOp>) {
        mutableOps.addAll(newOps)
    }
}
