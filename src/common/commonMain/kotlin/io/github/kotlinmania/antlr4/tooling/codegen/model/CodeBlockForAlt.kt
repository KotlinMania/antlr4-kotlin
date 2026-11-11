/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock

/** Scoped code block corresponding to a subrule or outer rule alternative. */
open class CodeBlockForAlt(
    factory: OutputModelFactory,
) : CodeBlock(
        factory = factory,
        treeLevel = factory.treeLevel,
        altNum = factory.currentOuterMostAlt,
    )
