/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.Token

/**
 * Exposes structured properties referenced in ANTLR4 error message templates
 * (`<arg.name>`, `<arg.typeString>`, `<arg2.recognizerName>`, `<if(arg2.implicitLexerOwner)>`).
 */
interface ErrorTemplateObject {
    val name: String?
    val typeString: String?
        get() = null
    val recognizerName: String?
        get() = null
    val hasImplicitLexerOwner: Boolean
        get() = false
    val startToken: Token?
        get() = null
}

/**
 * Pure-Kotlin evaluator for ANTLR4 tool message templates and diagnostic report layouts
 * (`antlr`, `gnu`, and `vs2005`).
 */
object MessageRenderer {
    enum class MessageLayout(
        val formatName: String,
        val wantsSingleLine: Boolean,
    ) {
        ANTLR("antlr", false),
        GNU("gnu", true),
        VS2005("vs2005", true),
        ;

        fun formatLocation(
            file: String?,
            line: Int,
            column: Int,
        ): String {
            val fileText = file ?: ""
            val lineText = if (line != -1) line.toString() else ""
            val colText = if (column != -1) column.toString() else ""
            return when (this) {
                ANTLR, GNU -> "$fileText:$lineText:$colText:"
                VS2005 -> "$fileText($lineText,$colText)"
            }
        }

        fun formatMessage(
            id: Int,
            text: String,
        ): String =
            when (this) {
                ANTLR -> "($id) $text"
                GNU -> "$text [error $id]"
                VS2005 -> "error $id : $text"
            }

        fun formatReport(
            location: String?,
            severityText: String,
            id: Int,
            messageText: String,
        ): String {
            val locText = location ?: ""
            return when (this) {
                ANTLR -> "$severityText($id): $locText $messageText"
                GNU -> "$locText $severityText: ${formatMessage(id, messageText)}"
                VS2005 -> "$locText : $severityText $id : $messageText"
            }
        }

        companion object {
            fun fromName(name: String): MessageLayout? =
                when (name) {
                    "antlr" -> ANTLR
                    "gnu" -> GNU
                    "vs2005" -> VS2005
                    else -> null
                }
        }
    }

    internal fun renderTemplate(
        template: String,
        bindings: Map<String, Any?>,
    ): String {
        val sb = StringBuilder()
        var i = 0
        while (i < template.length) {
            val ch = template[i]
            if (ch == '\\' && i + 1 < template.length && (template[i + 1] == '<' || template[i + 1] == '>')) {
                sb.append(template[i + 1])
                i += 2
                continue
            }
            if (ch != '<') {
                sb.append(ch)
                i++
                continue
            }
            val closeIdx = findMatchingAngleBracket(template, i)
            if (closeIdx < 0) {
                sb.append(ch)
                i++
                continue
            }
            val tagContent = template.substring(i + 1, closeIdx)
            if (tagContent.startsWith("if(") && tagContent.endsWith(")")) {
                val condExpr = tagContent.substring(3, tagContent.length - 1).trim()
                val endifSpan = findMatchingEndif(template, closeIdx + 1)
                if (endifSpan != null) {
                    val innerBody = template.substring(closeIdx + 1, endifSpan.first)
                    if (evaluateCondition(condExpr, bindings)) {
                        sb.append(renderTemplate(innerBody, bindings))
                    }
                    i = endifSpan.second
                    continue
                }
            }
            sb.append(evaluateTag(tagContent, bindings))
            i = closeIdx + 1
        }
        return sb.toString()
    }

    private fun findMatchingAngleBracket(
        s: String,
        openIdx: Int,
    ): Int {
        var depth = 0
        var inQuotes = false
        var idx = openIdx
        while (idx < s.length) {
            val c = s[idx]
            if (c == '\\' && idx + 1 < s.length) {
                idx += 2
                continue
            }
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (c == '<') {
                    depth++
                } else if (c == '>') {
                    depth--
                    if (depth == 0) return idx
                }
            }
            idx++
        }
        return -1
    }

    private fun findMatchingEndif(
        s: String,
        searchStart: Int,
    ): Pair<Int, Int>? {
        var depth = 1
        var idx = searchStart
        while (idx < s.length) {
            if (s[idx] == '<') {
                val close = findMatchingAngleBracket(s, idx)
                if (close > idx) {
                    val inner = s.substring(idx + 1, close)
                    if (inner.startsWith("if(") && inner.endsWith(")")) {
                        depth++
                    } else if (inner == "endif") {
                        depth--
                        if (depth == 0) {
                            return idx to (close + 1)
                        }
                    }
                    idx = close + 1
                    continue
                }
            }
            idx++
        }
        return null
    }

    private fun evaluateCondition(
        expr: String,
        bindings: Map<String, Any?>,
    ): Boolean {
        if (expr.contains("&&")) {
            return expr.split("&&").all { part -> evaluateCondition(part.trim(), bindings) }
        }
        if (expr.contains("||")) {
            return expr.split("||").any { part -> evaluateCondition(part.trim(), bindings) }
        }
        if (expr.startsWith("!")) {
            return !evaluateCondition(expr.substring(1).trim(), bindings)
        }
        val resolved = resolveValue(expr, bindings)
        return when (resolved) {
            null -> false
            is Boolean -> resolved
            is Collection<*> -> resolved.isNotEmpty()
            else -> true
        }
    }

    private fun evaluateTag(
        tag: String,
        bindings: Map<String, Any?>,
    ): String {
        val mapStart = findTopLevelColonBrace(tag)
        if (mapStart >= 0) {
            val targetExpr = tag.substring(0, mapStart).trim()
            val braceClose = findMatchingCurlyBrace(tag, mapStart + 1)
            if (braceClose > mapStart + 1) {
                val lambdaSpec = tag.substring(mapStart + 2, braceClose)
                val pipeIdx = lambdaSpec.indexOf('|')
                if (pipeIdx > 0) {
                    val paramName = lambdaSpec.substring(0, pipeIdx).trim()
                    val rawBody = lambdaSpec.substring(pipeIdx + 1)
                    val bodyTemplate =
                        when {
                            rawBody.startsWith("\r\n") -> rawBody.substring(2)
                            rawBody.startsWith(" ") || rawBody.startsWith("\t") || rawBody.startsWith("\n") ->
                                rawBody.substring(1)
                            else -> rawBody
                        }
                    val remainder = tag.substring(braceClose + 1).trim()
                    val separator = parseSeparatorOption(remainder)
                    val targetVal = resolveValue(targetExpr, bindings)
                    val items =
                        when (targetVal) {
                            null -> emptyList()
                            is Iterable<*> -> targetVal.toList()
                            is Array<*> -> targetVal.toList()
                            else -> listOf(targetVal)
                        }
                    return items.joinToString(separator) { elem ->
                        val childBindings = LinkedHashMap(bindings)
                        childBindings[paramName] = elem
                        renderTemplate(bodyTemplate, childBindings)
                    }
                }
            }
        }

        val semiIdx = findTopLevelSemicolon(tag)
        val exprPart = if (semiIdx >= 0) tag.substring(0, semiIdx).trim() else tag.trim()
        val optionPart = if (semiIdx >= 0) tag.substring(semiIdx).trim() else ""
        val separator = parseSeparatorOption(optionPart)
        val value = resolveValue(exprPart, bindings) ?: return ""
        return when (value) {
            is Iterable<*> -> value.filterNotNull().joinToString(separator) { it.toString() }
            is Array<*> -> value.filterNotNull().joinToString(separator) { it.toString() }
            else -> value.toString()
        }
    }

    private fun findTopLevelColonBrace(s: String): Int {
        var angleDepth = 0
        var inQuotes = false
        var idx = 0
        while (idx + 1 < s.length) {
            val c = s[idx]
            if (c == '\\') {
                idx += 2
                continue
            }
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (c == '<') angleDepth++
                else if (c == '>') angleDepth--
                else if (angleDepth == 0 && c == ':' && s[idx + 1] == '{') return idx
            }
            idx++
        }
        return -1
    }

    private fun findMatchingCurlyBrace(
        s: String,
        openBraceIdx: Int,
    ): Int {
        var curlyDepth = 0
        var angleDepth = 0
        var inQuotes = false
        var idx = openBraceIdx
        while (idx < s.length) {
            val c = s[idx]
            if (c == '\\' && idx + 1 < s.length) {
                idx += 2
                continue
            }
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (c == '<') {
                    angleDepth++
                } else if (c == '>') {
                    angleDepth--
                } else if (angleDepth == 0) {
                    if (c == '{') {
                        curlyDepth++
                    } else if (c == '}') {
                        curlyDepth--
                        if (curlyDepth == 0) return idx
                    }
                }
            }
            idx++
        }
        return -1
    }

    private fun findTopLevelSemicolon(s: String): Int {
        var angleDepth = 0
        var curlyDepth = 0
        var inQuotes = false
        var idx = 0
        while (idx < s.length) {
            val c = s[idx]
            if (c == '\\' && idx + 1 < s.length) {
                idx += 2
                continue
            }
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                when (c) {
                    '<' -> angleDepth++
                    '>' -> angleDepth--
                    '{' -> curlyDepth++
                    '}' -> curlyDepth--
                    ';' -> if (angleDepth == 0 && curlyDepth == 0) return idx
                }
            }
            idx++
        }
        return -1
    }

    private fun parseSeparatorOption(optionPart: String): String {
        val trimmed = optionPart.removePrefix(";").trim()
        if (!trimmed.startsWith("separator")) return ""
        val eqIdx = trimmed.indexOf('=')
        if (eqIdx < 0) return ""
        val rawVal = trimmed.substring(eqIdx + 1).trim()
        if (rawVal.length >= 2 && rawVal.startsWith('"') && rawVal.endsWith('"')) {
            return unescapeSeparator(rawVal.substring(1, rawVal.length - 1))
        }
        return rawVal
    }

    private fun unescapeSeparator(raw: String): String {
        val sb = StringBuilder()
        var idx = 0
        while (idx < raw.length) {
            val c = raw[idx]
            if (c == '\\' && idx + 1 < raw.length) {
                when (val next = raw[idx + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(next)
                }
                idx += 2
            } else {
                sb.append(c)
                idx++
            }
        }
        return sb.toString()
    }

    private fun resolveValue(
        expr: String,
        bindings: Map<String, Any?>,
    ): Any? {
        val parts = expr.split('.')
        var current: Any? = bindings[parts[0]]
        var idx = 1
        while (idx < parts.size && current != null) {
            val prop = parts[idx]
            current =
                when (val obj = current) {
                    is ErrorTemplateObject ->
                        when (prop) {
                            "name" -> obj.name
                            "typeString" -> obj.typeString
                            "recognizerName" -> obj.recognizerName
                            "implicitLexerOwner" -> if (obj.hasImplicitLexerOwner) true else null
                            else -> null
                        }
                    is Map<*, *> -> obj[prop]
                    else -> null
                }
            idx++
        }
        return current
    }
}
