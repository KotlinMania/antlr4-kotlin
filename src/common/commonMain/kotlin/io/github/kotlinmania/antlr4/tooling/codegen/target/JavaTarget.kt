/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.target

import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.CodeGenerator
import io.github.kotlinmania.antlr4.tooling.codegen.Target

/**
 * Target specification matching upstream ANTLR 4 `JavaTarget` for `language=Java` grammars
 * and action-translation parity tests.
 */
open class JavaTarget(
    gen: CodeGenerator,
) : Target(gen) {
    override val badWords: Set<String> = JAVA_KEYWORDS

    override val lookaheadTempVarName: String
        get() = "_la"

    override fun wantsJavaSerializedATN(): Boolean = true

    override fun escapeWord(word: String): String = "${word}_"

    override fun grammarSymbolCausesIssueInGeneratedCode(idNode: GrammarAST): Boolean =
        badWords.contains(idNode.text)

    companion object {
        val JAVA_KEYWORDS: Set<String> =
            setOf(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch",
                "char", "class", "const", "continue", "default", "do", "double",
                "else", "enum", "extends", "false", "final", "finally", "float",
                "for", "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "null", "package", "private",
                "protected", "public", "return", "short", "static", "strictfp",
                "super", "switch", "synchronized", "this", "throw", "throws",
                "transient", "true", "try", "void", "volatile", "while",
                "rule", "parserRule",
            )
    }
}
