/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.chunk

import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.StructDecl

/** Action chunk referencing an implicit token field (`$ID`) on the active rule context. */
class TokenRef(
    ctx: StructDecl?,
    name: String,
    escapedName: String,
) : SymbolRefChunk(ctx, name, escapedName)
