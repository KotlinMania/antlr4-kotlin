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
    internal var mutablePreamble: MutableList<SrcOp>? = null
    internal var mutableOps: MutableList<SrcOp>? = null

    @ModelElement
    val locals: List<Decl>?
        get() = mutableLocals?.elements()

    @ModelElement
    val preamble: List<SrcOp>?
        get() = mutablePreamble

    @ModelElement
    val ops: List<SrcOp>?
        get() = mutableOps

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
        var list = mutablePreamble
        if (list == null) {
            list = ArrayList()
            mutablePreamble = list
        }
        list.add(op)
    }

    fun addOp(op: SrcOp) {
        var list = mutableOps
        if (list == null) {
            list = ArrayList()
            mutableOps = list
        }
        list.add(op)
    }

    fun insertOp(
        i: Int,
        op: SrcOp,
    ) {
        var list = mutableOps
        if (list == null) {
            list = ArrayList()
            mutableOps = list
        }
        list.add(i, op)
    }

    fun addOps(newOps: List<SrcOp>) {
        var list = mutableOps
        if (list == null) {
            list = ArrayList()
            mutableOps = list
        }
        list.addAll(newOps)
    }
}
