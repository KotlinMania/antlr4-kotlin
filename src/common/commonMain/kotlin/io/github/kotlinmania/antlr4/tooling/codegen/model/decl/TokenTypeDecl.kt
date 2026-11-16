/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.decl

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Declaration node for an integer lookahead token-type local variable (`laToken`). */
class TokenTypeDecl(
    factory: OutputModelFactory,
    varName: String,
) : Decl(factory, varName)
