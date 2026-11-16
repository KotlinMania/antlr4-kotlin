/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Output model node representing a `catch [...] { ... }` clause on a parser rule. */
class ExceptionClause(
    factory: OutputModelFactory,
    catchArg: ActionAST,
    catchAction: ActionAST,
) : SrcOp(factory, catchArg) {
    @ModelElement
    val catchArg: Action = Action(factory, catchArg)

    @ModelElement
    val catchAction: Action = Action(factory, catchAction)
}
