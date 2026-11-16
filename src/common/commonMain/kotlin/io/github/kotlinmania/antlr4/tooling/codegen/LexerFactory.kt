/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.tooling.codegen.model.Lexer
import io.github.kotlinmania.antlr4.tooling.codegen.model.LexerFile

/** Concrete [OutputModelFactory] building [LexerFile] and [Lexer] models for lexer grammars. */
open class LexerFactory(
    gen: CodeGenerator,
) : DefaultOutputModelFactory(gen) {
    override fun lexerFile(fileName: String): LexerFile = LexerFile(this, fileName)

    override fun lexer(file: LexerFile): Lexer = Lexer(this, file)
}
