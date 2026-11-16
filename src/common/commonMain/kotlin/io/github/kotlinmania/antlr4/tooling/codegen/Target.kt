/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleFunction
import io.github.kotlinmania.antlr4.tooling.misc.CharSupport
import io.github.kotlinmania.antlr4.tooling.misc.EscapeSequenceParsing
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.Rule
import io.github.kotlinmania.antlr4.tooling.tool.Tool

/**
 * Target-language specification governing identifier escaping, string-literal formatting,
 * implicit label naming, context struct naming, and serialized ATN layout.
 */
abstract class Target(
    val gen: CodeGenerator,
) {
    val language: String
        get() = gen.language

    abstract val badWords: Set<String>

    open val defaultTargetCharValueEscape: Map<Char, String> =
        mapOf(
            '\n' to "\\n",
            '\r' to "\\r",
            '\t' to "\\t",
            '\b' to "\\b",
            '\u000C' to "\\f",
            '\\' to "\\\\",
            '\'' to "\\'",
            '"' to "\\\"",
        )

    open val targetCharValueEscape: Map<Char, String>
        get() = defaultTargetCharValueEscape

    open val version: String
        get() = Tool.VERSION

    open val inlineTestSetWordSize: Int
        get() = 64

    open val lookaheadTempVarName: String
        get() = "laToken"

    open val fileExtension: String
        get() =
            when (language) {
                "Kotlin" -> ".kt"
                "Java" -> ".java"
                "CSharp" -> ".cs"
                "Python2", "Python3" -> ".py"
                "JavaScript" -> ".js"
                "TypeScript" -> ".ts"
                "Cpp" -> ".cpp"
                "Go" -> ".go"
                "Swift" -> ".swift"
                "PHP" -> ".php"
                "Dart" -> ".dart"
                else -> ".kt"
            }

    open fun wantsBaseListener(): Boolean = true

    open fun wantsBaseVisitor(): Boolean = true

    open fun supportsOverloadedMethods(): Boolean = true

    open fun wantsJavaSerializedATN(): Boolean = false

    open fun supportsSplitParser(): Boolean = false

    open fun isATNSerializedAsInts(): Boolean = !wantsJavaSerializedATN()

    protected open fun escapeWord(word: String): String = "${word}Symbol"

    open fun escapeIfNeeded(word: String): String =
        if (badWords.contains(word)) escapeWord(word) else word

    open fun getRecognizerFileName(header: Boolean = false): String {
        val recName = gen.grammar.recognizerName ?: gen.grammar.name ?: "Recognizer"
        return "$recName$fileExtension"
    }

    open fun getRecognizerFileName(sourceType: SourceType): String {
        val recName = gen.grammar.recognizerName ?: gen.grammar.name ?: "Recognizer"
        return when (sourceType) {
            SourceType.SOURCE_CONTEXTS -> "${recName}Contexts$fileExtension"
            SourceType.SOURCE_DFA -> "${recName}DFA$fileExtension"
            else -> getRecognizerFileName(sourceType == SourceType.HEADER)
        }
    }

    open fun getListenerFileName(header: Boolean = false): String {
        val gName = gen.grammar.name ?: "Grammar"
        return "${gName}Listener$fileExtension"
    }

    open fun getVisitorFileName(header: Boolean = false): String {
        val gName = gen.grammar.name ?: "Grammar"
        return "${gName}Visitor$fileExtension"
    }

    open fun getBaseListenerFileName(header: Boolean = false): String {
        val gName = gen.grammar.name ?: "Grammar"
        return "${gName}BaseListener$fileExtension"
    }

    open fun getBaseVisitorFileName(header: Boolean = false): String {
        val gName = gen.grammar.name ?: "Grammar"
        return "${gName}BaseVisitor$fileExtension"
    }

    open fun getTargetStringLiteralFromString(
        s: String?,
        quoted: Boolean = false,
    ): String {
        if (s == null) return ""
        val sb = StringBuilder()
        if (quoted) {
            sb.append('"')
        }
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            val codePoint: Int
            val step: Int
            if (ch.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
                codePoint =
                    0x10000 +
                    ((ch.code - 0xD800) shl 10) +
                    (s[i + 1].code - 0xDC00)
                step = 2
            } else {
                codePoint = ch.code
                step = 1
            }
            val mapped = if (step == 1 && ch != '\'') targetCharValueEscape[ch] else null
            if (mapped != null) {
                sb.append(mapped)
            } else if (shouldUseUnicodeEscapeForCodePointInDoubleQuotedString(codePoint)) {
                appendUnicodeEscapedCodePoint(codePoint, sb)
            } else {
                sb.append(s, i, i + step)
            }
            i += step
        }
        if (quoted) {
            sb.append('"')
        }
        return sb.toString()
    }

    open fun getTargetStringLiteralFromANTLRStringLiteral(
        generator: CodeGenerator,
        literal: String,
        addQuotes: Boolean = true,
        escapeSpecial: Boolean = false,
    ): String {
        val sb = StringBuilder()
        if (addQuotes) {
            sb.append('"')
        }
        var i = 1
        val end = literal.length - 1
        while (i < end) {
            val codePoint: Int
            if (literal[i] == '\\') {
                val parsed = EscapeSequenceParsing.parseEscape(literal, i)
                codePoint =
                    if (parsed.type == EscapeSequenceParsing.ResultKind.CODE_POINT && parsed.parseLength > 0) {
                        i += parsed.parseLength
                        parsed.codePoint
                    } else if (i + 1 < end) {
                        val twoChar = CharSupport.getCharValueFromCharInGrammarLiteral(literal.substring(i, i + 2))
                        i += 2
                        if (twoChar >= 0) twoChar else literal[i - 1].code
                    } else {
                        i++
                        '\\'.code
                    }
            } else {
                val ch = literal[i]
                if (ch.isHighSurrogate() && i + 1 < end && literal[i + 1].isLowSurrogate()) {
                    codePoint =
                        0x10000 +
                        ((ch.code - 0xD800) shl 10) +
                        (literal[i + 1].code - 0xDC00)
                    i += 2
                } else {
                    codePoint = ch.code
                    i++
                }
            }

            val chOrNull = if (codePoint in 0..0xFFFF) codePoint.toChar() else null
            val escaped = if (chOrNull != null && chOrNull != '\'') targetCharValueEscape[chOrNull] else null
            if (escaped != null && (escapeSpecial || chOrNull == '"' || chOrNull == '\\')) {
                sb.append(escaped)
            } else if (shouldUseUnicodeEscapeForCodePointInDoubleQuotedString(codePoint)) {
                appendUnicodeEscapedCodePoint(codePoint, sb)
            } else if (codePoint in 0x10000..0x10FFFF) {
                val offset = codePoint - 0x10000
                sb.append(((offset ushr 10) + 0xD800).toChar())
                sb.append(((offset and 0x3FF) + 0xDC00).toChar())
            } else {
                sb.append(codePoint.toChar())
            }
        }
        if (addQuotes) {
            sb.append('"')
        }
        return sb.toString()
    }

    protected open fun shouldUseUnicodeEscapeForCodePointInDoubleQuotedString(codePoint: Int): Boolean =
        codePoint < 0x20 || codePoint == 0x7F || codePoint in 0xD800..0xDFFF || codePoint > 0x7F

    protected open fun appendUnicodeEscapedCodePoint(
        codePoint: Int,
        sb: StringBuilder,
    ) {
        UnicodeEscapes.appendEscapedCodePoint(sb, codePoint, language)
    }

    open fun encodeInt16AsCharEscape(v: Int): String {
        require(v in Char.MIN_VALUE.code..Char.MAX_VALUE.code) {
            "Cannot encode out-of-range 16-bit unsigned integer: $v"
        }
        val ch = v.toChar()
        val special = defaultTargetCharValueEscape[ch]
        if (special != null && ch != '\'') {
            return special
        }
        if (v in 0x20..0x7E) {
            return ch.toString()
        }
        return UnicodeEscapes.escapeCodePoint(v, language)
    }

    open fun getLoopLabel(ast: GrammarAST): String =
        "loop${ast.token?.tokenIndex?.coerceAtLeast(0) ?: ast.hashCode().ushr(1)}"

    open fun getLoopCounter(ast: GrammarAST): String =
        "cnt${ast.token?.tokenIndex?.coerceAtLeast(0) ?: ast.hashCode().ushr(1)}"

    open fun getListLabel(label: String): String = escapeIfNeeded(label)

    open fun getRuleFunctionContextStructName(r: Rule): String =
        if (r.g.isLexer) {
            "LexerRuleContext"
        } else {
            "${r.name.replaceFirstChar { it.uppercaseChar() }}Context"
        }

    open fun getRuleFunctionContextStructName(function: RuleFunction): String =
        getRuleFunctionContextStructName(function.rule)

    open fun getAltLabelContextStructName(altLabel: String): String =
        "${altLabel.replaceFirstChar { it.uppercaseChar() }}Context"

    open fun getImplicitTokenLabel(tokenName: String): String {
        if (tokenName.startsWith("'")) {
            return "s${gen.grammar.getTokenType(tokenName)}"
        }
        return escapeIfNeeded(tokenName)
    }

    open fun getImplicitSetLabel(id: String): String = "s$id"

    open fun getImplicitRuleLabel(ruleName: String): String = escapeIfNeeded(ruleName)

    open fun getElementListName(name: String): String = escapeIfNeeded(name)

    open fun getElementName(name: String): String {
        if (name == ".") return "wildcard"
        if (name.startsWith("'")) {
            val tokenType = gen.grammar.getTokenType(name)
            return "s$tokenType"
        }
        return escapeIfNeeded(name)
    }

    open fun getTokenTypeAsTargetLabel(
        g: Grammar,
        ttype: Int,
    ): String {
        if (ttype == Token.EOF) {
            return "EOF"
        }
        val name = g.getTokenName(ttype)
        if (name == Grammar.INVALID_TOKEN_NAME) {
            return ttype.toString()
        }
        return escapeIfNeeded(name)
    }

    open fun visibleGrammarSymbolCausesIssueInGeneratedCode(idNode: GrammarAST): Boolean {
        if (idNode.hasAncestor(ANTLRParserTypes.OPTIONS) || idNode.hasAncestor(ANTLRParserTypes.ELEMENT_OPTIONS)) {
            return false
        }
        val parent = idNode.parent
        when (parent?.type) {
            ANTLRParserTypes.AT,
            ANTLRParserTypes.IMPORT,
            ANTLRParserTypes.GRAMMAR,
            -> return false

            ANTLRParserTypes.LEXER_ACTION_CALL -> {
                if (idNode.childIndex == 0) return false
            }

            ANTLRParserTypes.LEXER_ALT_ACTION -> {
                if (idNode.childIndex > 0) return false
            }
        }
        return grammarSymbolCausesIssueInGeneratedCode(idNode)
    }

    protected open fun grammarSymbolCausesIssueInGeneratedCode(idNode: GrammarAST): Boolean = false
}
