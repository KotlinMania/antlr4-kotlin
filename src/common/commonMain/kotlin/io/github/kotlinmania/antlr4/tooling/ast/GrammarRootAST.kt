/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenStream

/**
 * Root AST node representing an entire ANTLR `.g4` grammar specification.
 */
class GrammarRootAST : GrammarASTWithOptions {
    var grammarType: Int = 0
    var hasErrors: Boolean = false
    val tokenStream: TokenStream
    var cmdLineOptions: Map<String, String>? = null
    var fileName: String? = null

    constructor(node: GrammarRootAST) : super(node) {
        this.grammarType = node.grammarType
        this.hasErrors = node.hasErrors
        this.tokenStream = node.tokenStream
        this.cmdLineOptions = node.cmdLineOptions
        this.fileName = node.fileName
    }

    constructor(
        t: Token?,
        tokenStream: TokenStream,
    ) : super(t) {
        this.tokenStream = tokenStream
    }

    constructor(
        type: Int,
        t: Token?,
        tokenStream: TokenStream,
    ) : super(type, t) {
        this.tokenStream = tokenStream
    }

    constructor(
        type: Int,
        t: Token?,
        text: String?,
        tokenStream: TokenStream,
    ) : super(type, t, text) {
        this.tokenStream = tokenStream
    }

    val grammarName: String?
        get() = getChild(0)?.text

    override fun getOptionString(key: String): String? {
        val cliOpts = cmdLineOptions
        if (cliOpts != null && cliOpts.containsKey(key)) {
            return cliOpts[key]
        }
        return super.getOptionString(key) ?: defaultOptions[key]
    }

    override fun visit(v: GrammarASTVisitor): Any? = v.visit(this)

    override fun dupNode(): GrammarRootAST = GrammarRootAST(this)

    companion object {
        val defaultOptions: Map<String, String> = mapOf("language" to "Kotlin")
    }
}
