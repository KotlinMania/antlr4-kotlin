/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Base model node for LL(1) single-token lookahead decisions (`when (tokenStream.LA(1))`). */
abstract class LL1Choice(
    factory: OutputModelFactory,
    blkAST: GrammarAST,
    alts: List<CodeBlockForAlt>,
    val altLook: List<List<TokenInfo>>,
    @ModelElement var error: ThrowNoViableAlt,
) : Choice(factory, blkAST, alts)
