/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.tooling.ast.GrammarRootAST

/**
 * Grammar context contract exposed to [io.github.kotlinmania.antlr4.tooling.ast.GrammarAST] nodes,
 * [LabelElementPair], and `.g4` parse/transform helpers.
 */
interface GrammarASTOwner {
    val name: String?
        get() = null

    val fileName: String?

    val isLexer: Boolean

    val errorManager: ErrorManager?

    val ast: GrammarRootAST?
        get() = null

    val originalTokenStream: TokenStream?
        get() = ast?.tokenStream

    val libDirectory: String?
        get() = null

    val outputDirectory: String?
        get() = null

    val grammarEncoding: String?
        get() = null

    fun getOptionString(key: String): String? = ast?.getOptionString(key)

    fun getRegisteredTokenVocabContent(vocabName: String): String? = null

    fun log(
        component: String,
        msg: String,
    ) {
    }
}
