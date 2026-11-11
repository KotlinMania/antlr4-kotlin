/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenSourceCharStream
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.tool.Attribute
import io.github.kotlinmania.antlr4.tooling.tool.AttributeDict
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.GrammarASTOwner

/**
 * Strongly-typed declaration slice and character offset within an action argument list.
 */
class DeclSpan(
    val text: String,
    val offset: Int,
)

/**
 * Strongly-typed half-open character span `[start, stop)` within a declaration string.
 */
class IntSpan(
    val start: Int,
    val stop: Int,
)

/**
 * Parses `arg`, `returns`, and `locals` declaration lists (`[...]`) across C, C++, Java,
 * Kotlin, Swift, Go, and Python type notations.
 */
object ScopeParser {
    private val LINE_COMMENT_PATTERN: Regex = Regex("//[^\\n]*")

    fun parseTypedArgList(
        action: ActionAST?,
        s: String?,
        g: GrammarASTOwner?,
    ): AttributeDict = parse(action, s, ',', g)

    fun parse(
        action: ActionAST?,
        s: String?,
        separator: Char,
        g: GrammarASTOwner?,
    ): AttributeDict {
        val dict = AttributeDict()
        val decls = splitDecls(s, separator.code)
        for (decl in decls) {
            if (decl.text.trim { it <= ' ' }.isNotEmpty()) {
                val a = parseAttributeDef(action, decl, g)
                dict.add(a)
            }
        }
        return dict
    }

    /**
     * For decls like `"String foo"` or `"char *foo32[]"` compute the ID and type
     * declarations, handling `int x=3` and `T a = new T()` initializers as well as
     * postfix `x:int` declarations.
     */
    fun parseAttributeDef(
        action: ActionAST?,
        decl: DeclSpan,
        g: GrammarASTOwner?,
    ): Attribute {
        val attr = Attribute()
        var rightEdgeOfDeclarator = decl.text.length - 1
        val equalsIndex = decl.text.indexOf('=')
        if (equalsIndex > 0) {
            attr.initValue = decl.text.substring(equalsIndex + 1).trim { it <= ' ' }
            rightEdgeOfDeclarator = equalsIndex - 1
        }

        val declarator = decl.text.substring(0, rightEdgeOfDeclarator + 1)
        val textWithoutDoubleColon = declarator.replace("::", "")
        val span: IntSpan =
            if (textWithoutDoubleColon.contains(":")) {
                parsePostfixDecl(attr, declarator, action, g)
            } else {
                parsePrefixDecl(attr, declarator, action, g)
            }
        val idStart = span.start
        val idStop = span.stop

        attr.decl = decl.text

        if (action != null) {
            val actionToken = action.token
            val actionText = action.text
            if (actionToken != null && actionText.isNotEmpty() && decl.offset in actionText.indices) {
                val lines = IntArray(actionText.length)
                val charPositionInLines = IntArray(actionText.length)
                var line0 = 0
                var col0 = 0
                for (i in actionText.indices) {
                    lines[i] = line0
                    charPositionInLines[i] = col0
                    if (actionText[i] == '\n') {
                        line0++
                        col0 = -1
                    }
                    col0++
                }

                val charIndexes = IntArray(actionText.length)
                var i = 0
                var j = 0
                while (i < actionText.length) {
                    charIndexes[j] = i
                    if (i < actionText.length - 1 && actionText[i] == '/' && actionText[i + 1] == '/') {
                        while (i < actionText.length && actionText[i] != '\n') {
                            i++
                        }
                    }
                    i++
                    j++
                }

                val mappedIdx = decl.offset.coerceIn(0, j - 1)
                val declOffset = charIndexes[mappedIdx]
                val posInAction = (declOffset + idStart).coerceIn(0, actionText.length - 1)
                val declLine = lines[posInAction]

                val tokenLine = actionToken.line + declLine
                var charPositionInLine = charPositionInLines[posInAction]
                if (declLine == 0) {
                    charPositionInLine += actionToken.charPositionInLine + 1
                }

                val stream = actionToken.inputStream
                val tok: CommonToken =
                    if (stream != null) {
                        val offset = actionToken.startIndex
                        CommonToken(
                            TokenSourceCharStream(actionToken.tokenSource, stream),
                            ANTLRParserTypes.ID,
                            Token.DEFAULT_CHANNEL,
                            offset + declOffset + idStart + 1,
                            offset + declOffset + idStop,
                        )
                    } else {
                        CommonToken(ANTLRParserTypes.ID, attr.name)
                    }
                tok.line = tokenLine
                tok.charPositionInLine = charPositionInLine
                attr.token = tok
            }
        }

        return attr
    }

    fun parsePrefixDecl(
        attr: Attribute,
        decl: String,
        a: ActionAST?,
        g: GrammarASTOwner?,
    ): IntSpan {
        var inId = false
        var start = -1
        for (i in decl.length - 1 downTo 0) {
            val ch = decl[i]
            if (!inId && ch.isLetterOrDigit()) {
                inId = true
            } else if (inId && !(ch.isLetterOrDigit() || ch == '_')) {
                start = i + 1
                break
            }
        }
        if (start < 0 && inId) {
            start = 0
        }
        if (start < 0) {
            g?.errorManager?.grammarError(ErrorType.CANNOT_FIND_ATTRIBUTE_NAME_IN_DECL, g.fileName, a?.token, decl)
            start = 0
        }

        var stop = -1
        for (i in start until decl.length) {
            val ch = decl[i]
            if (!(ch.isLetterOrDigit() || ch == '_')) {
                stop = i
                break
            }
            if (i == decl.length - 1) {
                stop = i + 1
            }
        }
        if (stop < start) {
            stop = start
        }

        attr.name = decl.substring(start, stop)

        val prefix = decl.substring(0, start)
        val suffix = if (stop <= decl.length - 1) decl.substring(stop) else ""
        val combinedType = (prefix + suffix).trim { it <= ' ' }
        attr.type = combinedType.ifEmpty { null }

        return IntSpan(start, stop)
    }

    fun parsePostfixDecl(
        attr: Attribute,
        decl: String,
        a: ActionAST?,
        g: GrammarASTOwner?,
    ): IntSpan {
        var start = -1
        var stop = -1
        val colon = decl.indexOf(':')
        val namePartEnd = if (colon == -1) decl.length else colon

        for (i in 0 until namePartEnd) {
            val ch = decl[i]
            if (ch.isLetterOrDigit() || ch == '_') {
                start = i
                break
            }
        }
        if (start == -1) {
            start = 0
            g?.errorManager?.grammarError(ErrorType.CANNOT_FIND_ATTRIBUTE_NAME_IN_DECL, g.fileName, a?.token, decl)
        }

        for (i in start until namePartEnd) {
            val ch = decl[i]
            if (!(ch.isLetterOrDigit() || ch == '_')) {
                stop = i
                break
            }
            if (i == namePartEnd - 1) {
                stop = i + 1
            }
        }
        if (stop == -1) {
            stop = start
        }

        attr.name = decl.substring(start, stop)

        val rawType = if (colon == -1) "" else decl.substring(colon + 1)
        val trimmedType = rawType.trim { it <= ' ' }
        attr.type = trimmedType.ifEmpty { null }

        return IntSpan(start, stop)
    }

    /**
     * Given an argument list like
     * `x, (*a).foo(21,33), 3.2+1, '\n', "a,oo\nick", {bl, "fdkj"eck}, ["cat\n,", x, 43]`
     * convert to a list of [DeclSpan] elements supporting nested parentheses, braces,
     * angle brackets, brackets, and quoted strings.
     */
    fun splitDecls(
        s: String?,
        separatorChar: Int,
    ): List<DeclSpan> {
        val args = ArrayList<DeclSpan>()
        val cleaned = s?.replace(LINE_COMMENT_PATTERN, "")
        splitArgumentList(cleaned, 0, -1, separatorChar, args)
        return args
    }

    internal fun splitArgumentList(
        actionText: String?,
        start: Int,
        targetChar: Int,
        separatorChar: Int,
        args: MutableList<DeclSpan>,
    ): Int {
        if (actionText == null) {
            return -1
        }

        val n = actionText.length
        var p = start
        var index = p
        while (p < n) {
            val c = actionText[p].code
            if (c == targetChar) {
                break
            }
            when (c) {
                '\''.code -> {
                    p++
                    while (p < n && actionText[p] != '\'') {
                        if (actionText[p] == '\\' && p + 1 < n && actionText[p + 1] == '\'') {
                            p++
                        }
                        p++
                    }
                    p++
                }
                '"'.code -> {
                    p++
                    while (p < n && actionText[p] != '\"') {
                        if (actionText[p] == '\\' && p + 1 < n && actionText[p + 1] == '\"') {
                            p++
                        }
                        p++
                    }
                    p++
                }
                '('.code -> {
                    p = splitArgumentList(actionText, p + 1, ')'.code, separatorChar, args)
                }
                '{'.code -> {
                    p = splitArgumentList(actionText, p + 1, '}'.code, separatorChar, args)
                }
                '<'.code -> {
                    p =
                        if (actionText.indexOf('>', p + 1) >= p) {
                            splitArgumentList(actionText, p + 1, '>'.code, separatorChar, args)
                        } else {
                            p + 1
                        }
                }
                '['.code -> {
                    p = splitArgumentList(actionText, p + 1, ']'.code, separatorChar, args)
                }
                else -> {
                    if (c == separatorChar && targetChar == -1) {
                        val arg = actionText.substring(index, p)
                        var startIdx = index
                        while (startIdx < p && actionText[startIdx].isWhitespace()) {
                            startIdx++
                        }
                        args.add(DeclSpan(arg.trim { it <= ' ' }, startIdx))
                        index = p + 1
                    }
                    p++
                }
            }
        }
        if (targetChar == -1 && p <= n) {
            val arg = actionText.substring(index, p).trim { it <= ' ' }
            if (arg.isNotEmpty()) {
                var startIdx = index
                while (startIdx < p && actionText[startIdx].isWhitespace()) {
                    startIdx++
                }
                args.add(DeclSpan(arg, startIdx))
            }
        }
        p++
        return p
    }
}
