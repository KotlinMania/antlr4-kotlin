/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.AltLabelStructDecl

/** Scoped code block corresponding to a top-level alternative of a parser rule. */
class CodeBlockForOuterMostAlt(
    factory: OutputModelFactory,
    val alt: io.github.kotlinmania.antlr4.tooling.tool.Alternative,
) : CodeBlockForAlt(factory) {
    val altLabelStr: String? = alt.ast?.altLabel?.text
    var altLabel: AltLabelStructDecl? = null
}
