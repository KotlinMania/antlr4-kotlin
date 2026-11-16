/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.misc.OrderedHashSet
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.Decl

/** Output model operation matching a single token type from the input stream. */
open class MatchToken :
    RuleElement,
    LabeledOp {
    val name: String
    val escapedName: String
    val ttype: Int

    internal val mutableLabels: OrderedHashSet<Decl> = OrderedHashSet()

    override val labels: List<Decl>
        get() = mutableLabels.elements()

    constructor(
        factory: OutputModelFactory,
        ast: GrammarAST,
    ) : super(factory, ast) {
        val g = factory.grammar
        val gen = factory.generator
        ttype = g.getTokenType(ast.text)
        name = gen.target.getTokenTypeAsTargetLabel(g, ttype)
        escapedName = gen.target.escapeIfNeeded(name)
    }

    constructor(factory: OutputModelFactory) : super(factory) {
        ttype = 0
        name = ""
        escapedName = ""
    }

    override fun addLabel(decl: Decl) {
        mutableLabels.add(decl)
    }
}
