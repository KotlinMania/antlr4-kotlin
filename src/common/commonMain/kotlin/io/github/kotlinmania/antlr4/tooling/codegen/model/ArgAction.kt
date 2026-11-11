/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Output model node representing a `[...]` argument list passed to an invoked subrule. */
class ArgAction(
    factory: OutputModelFactory,
    ast: ActionAST,
    val ctxType: String,
) : Action(factory, ast)
