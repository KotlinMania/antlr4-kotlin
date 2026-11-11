/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.target

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.codegen.CodeGenerator
import io.github.kotlinmania.antlr4.tooling.codegen.Target
import io.github.kotlinmania.antlr4.tooling.tool.Grammar

/**
 * Pure-Kotlin Multiplatform code generation [Target] producing idiomatic, warning-free,
 * reflection-free Kotlin lexers, parsers, listeners, and visitors targeting
 * `io.github.kotlinmania.antlr4`.
 */
open class KotlinTarget(
    gen: CodeGenerator,
) : Target(gen) {
    override val badWords: Set<String> = KOTLIN_KEYWORDS

    override val targetCharValueEscape: Map<Char, String> = KOTLIN_CHAR_ESCAPES

    override val lookaheadTempVarName: String
        get() = "laToken"

    override fun wantsJavaSerializedATN(): Boolean = false

    override fun escapeWord(word: String): String = "`$word`"

    override fun getImplicitTokenLabel(tokenName: String): String {
        if (tokenName.startsWith("'")) {
            return "implicitToken${gen.grammar.getTokenType(tokenName)}"
        }
        val normalized =
            tokenName.lowercase().replaceFirstChar { it.uppercaseChar() }.replace("_", "")
        return "implicit$normalized"
    }

    override fun getImplicitSetLabel(id: String): String = "implicitSet$id"

    override fun getImplicitRuleLabel(ruleName: String): String {
        val normalized = ruleName.replaceFirstChar { it.uppercaseChar() }.replace("_", "")
        return "implicit$normalized"
    }

    override fun getTokenTypeAsTargetLabel(
        g: Grammar,
        ttype: Int,
    ): String {
        if (ttype == Token.EOF) {
            return "Token.EOF"
        }
        val name = g.getTokenName(ttype)
        if (name == Grammar.INVALID_TOKEN_NAME || name.startsWith("'")) {
            return ttype.toString()
        }
        return "Tokens.${escapeIfNeeded(name)}"
    }

    companion object {
        val KOTLIN_CHAR_ESCAPES: Map<Char, String> =
            mapOf(
                '\n' to "\\n",
                '\r' to "\\r",
                '\t' to "\\t",
                '\b' to "\\b",
                '\u000C' to "\\u000C",
                '\\' to "\\\\",
                '\'' to "\\'",
                '"' to "\\\"",
                '$' to "\\$",
            )

        val KOTLIN_KEYWORDS: Set<String> =
            setOf(
                "as", "break", "class", "continue", "do", "else", "false", "for",
                "fun", "if", "in", "interface", "is", "null", "object", "package",
                "return", "super", "this", "throw", "true", "try", "typealias",
                "typeof", "val", "var", "when", "while",
                // ParserRuleContext / Recognizer member names that could collide
                "ctx", "state", "ruleIndex", "ruleNames", "vocabulary", "atn",
                "serializedATN", "grammarFileName", "tokenStream", "inputStream",
                "errorHandler", "interpreter", "parent", "invokingState", "children",
                "start", "stop", "exception", "text", "type", "channel", "mode",
            )
    }
}
