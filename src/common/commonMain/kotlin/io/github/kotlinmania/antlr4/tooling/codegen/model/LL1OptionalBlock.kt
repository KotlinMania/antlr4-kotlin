/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** LL(1) multi-alternative optional subrule (`(a | b)?`). */
class LL1OptionalBlock(
    factory: OutputModelFactory,
    blkAST: GrammarAST,
    alts: List<CodeBlockForAlt>,
) : LL1AltBlock(factory, blkAST, alts)
