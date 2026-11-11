/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.chunk

import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.StructDecl

/** Action chunk referencing an attribute on an dynamically enclosing rule context (`$r::x`). */
class NonLocalAttrRef(
    ctx: StructDecl?,
    val ruleName: String,
    name: String,
    escapedName: String,
    val ruleIndex: Int,
) : SymbolRefChunk(ctx, name, escapedName)
