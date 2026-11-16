/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.decl

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Context getter method returning a single subrule `ParserRuleContext`. */
class ContextRuleGetterDecl(
    factory: OutputModelFactory,
    name: String,
    val ctxName: String,
    val optional: Boolean,
) : ContextGetterDecl(factory, name)
