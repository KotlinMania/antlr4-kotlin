/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.tooling.ast.GrammarRootAST

/**
 * Lexer-specific [Grammar] subtype organizing lexer rules into named modes (`DEFAULT_MODE`, etc.).
 */
class LexerGrammar : Grammar {
    var implicitLexerOwner: Grammar? = null

    override val hasImplicitLexerOwner: Boolean
        get() = implicitLexerOwner != null

    val modes: Map<String, List<Rule>>
        get() = mutableModes.toReadOnlyMap()

    constructor(
        tool: Tool,
        ast: GrammarRootAST,
    ) : super(tool, ast)

    constructor(grammarText: String) : super(grammarText)

    constructor(
        grammarText: String,
        listener: ANTLRToolListener?,
    ) : super(grammarText, listener)

    constructor(
        fileName: String,
        grammarText: String,
        listener: ANTLRToolListener?,
    ) : super(fileName, grammarText, listener)

    override fun doDefineRule(r: Rule): Boolean {
        if (!super.doDefineRule(r)) {
            return false
        }
        val modeName = r.mode ?: DEFAULT_MODE_NAME
        mutableModes.map(modeName, r)
        return true
    }

    override fun doUndefineRule(r: Rule): Boolean {
        if (!super.doUndefineRule(r)) {
            return false
        }
        val modeName = r.mode ?: DEFAULT_MODE_NAME
        mutableModes[modeName]?.remove(r)
        return true
    }

    companion object {
        const val DEFAULT_MODE_NAME: String = "DEFAULT_MODE"
    }
}
