/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.chunk

import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.StructDecl

/** Base action chunk referencing a named symbol with target-identifier escaping. */
abstract class SymbolRefChunk(
    ctx: StructDecl?,
    val name: String,
    val escapedName: String,
) : ActionChunk(ctx)
