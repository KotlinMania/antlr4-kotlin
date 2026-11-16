/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.SetTransition
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenTypeDecl

/** Output model operation matching any token inside an [io.github.kotlinmania.antlr4.misc.IntervalSet]. */
open class MatchSet(
    factory: OutputModelFactory,
    ast: GrammarAST,
) : MatchToken(factory) {
    @ModelElement
    var expr: TestSetInline

    @ModelElement
    var capture: CaptureNextTokenType

    init {
        this.ast = ast
        this.stateNumber = ast.atnState?.stateNumber ?: -1
        val st = requireNotNull(ast.atnState?.transition(0) as? SetTransition) { "Expected SetTransition on MatchSet AST." }
        val wordSize = factory.generator.target.inlineTestSetWordSize
        expr = TestSetInline(factory, null, st.set, wordSize)
        val d = TokenTypeDecl(factory, expr.varName)
        factory.currentRuleFunction?.addLocalDecl(d)
        capture = CaptureNextTokenType(factory, expr.varName)
    }
}
