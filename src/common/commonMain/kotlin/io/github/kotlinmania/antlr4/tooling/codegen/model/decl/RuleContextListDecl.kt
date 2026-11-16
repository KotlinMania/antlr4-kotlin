/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.decl

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Declaration node for a list-of-subrule-contexts label field (`es+=expr`). */
class RuleContextListDecl(
    factory: OutputModelFactory,
    name: String,
    ctxName: String,
) : RuleContextDecl(factory, name, ctxName) {
    init {
        isImplicit = false
    }
}
