/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.misc.IntervalSet

internal fun String.codePointAtCompat(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < this.length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
        }
    }
    return high.code
}

internal fun charCountCompat(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1

internal fun StringBuilder.appendCodePointCompat(codePoint: Int): StringBuilder {
    if (codePoint <= 0xFFFF) {
        append(codePoint.toChar())
    } else {
        val offset = codePoint - 0x10000
        append((0xD800 + (offset ushr 10)).toChar())
        append((0xDC00 + (offset and 0x3FF)).toChar())
    }
    return this
}

private fun isHexDigit(ch: Char): Boolean =
    ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'

object CharSupport {
    /**
     * When converting ANTLR char and string literals, here is the
     * value set of escape chars.
     */
    val ANTLRLiteralEscapedCharValue: IntArray = IntArray(255)

    /** Given a char, we need to be able to show as an ANTLR literal. */
    internal val ANTLRLiteralCharValueEscape: Array<String?> = arrayOfNulls(255)

    init {
        ANTLRLiteralEscapedCharValue['n'.code] = '\n'.code
        ANTLRLiteralEscapedCharValue['r'.code] = '\r'.code
        ANTLRLiteralEscapedCharValue['t'.code] = '\t'.code
        ANTLRLiteralEscapedCharValue['b'.code] = '\b'.code
        ANTLRLiteralEscapedCharValue['f'.code] = '\u000C'.code
        ANTLRLiteralEscapedCharValue['\\'.code] = '\\'.code
        ANTLRLiteralCharValueEscape['\n'.code] = "\\n"
        ANTLRLiteralCharValueEscape['\r'.code] = "\\r"
        ANTLRLiteralCharValueEscape['\t'.code] = "\\t"
        ANTLRLiteralCharValueEscape['\b'.code] = "\\b"
        ANTLRLiteralCharValueEscape['\u000C'.code] = "\\f"
        ANTLRLiteralCharValueEscape['\\'.code] = "\\\\"
    }

    /**
     * Return a string representing the escaped char for code [c]. E.g., if [c]
     * has value `0x100`, you will get `'\\u0100'`. Printable Basic Latin gets
     * the usual char (non-hex) representation. Non-ASCII characters are emitted
     * as `\\uXXXX` or `\\u{XXXXXX}` escapes.
     */
    fun getANTLRCharLiteralForChar(c: Int): String {
        val result: String =
            if (c < Lexer.MIN_CHAR_VALUE) {
                "<INVALID>"
            } else {
                val charValueEscape =
                    if (c < ANTLRLiteralCharValueEscape.size) ANTLRLiteralCharValueEscape[c] else null
                when {
                    charValueEscape != null -> charValueEscape
                    c in 0x20..0x7E ->
                        when (c) {
                            '\\'.code -> "\\\\"
                            '\''.code -> "\\'"
                            else -> c.toChar().toString()
                        }
                    c <= 0xFFFF -> "\\u" + c.toString(16).uppercase().padStart(4, '0')
                    else -> "\\u{" + c.toString(16).uppercase().padStart(6, '0') + "}"
                }
            }
        return "'$result'"
    }

    /**
     * Given a literal like (the 3 char sequence with single quotes) `'a'`,
     * return the int value of `'a'`. Convert escape sequences here also.
     * Return `-1` if not single char.
     */
    fun getCharValueFromGrammarCharLiteral(literal: String?): Int {
        if (literal == null || literal.length < 3) return -1
        return getCharValueFromCharInGrammarLiteral(literal.substring(1, literal.length - 1))
    }

    fun getStringFromGrammarStringLiteral(literal: String): String? {
        val buf = StringBuilder()
        var i = 1 // skip first quote
        val n = literal.length - 1 // skip last quote
        while (i < n) {
            var end = i + 1
            if (literal[i] == '\\') {
                end = i + 2
                if (i + 1 < n && literal[i + 1] == 'u') {
                    if (i + 2 < n && literal[i + 2] == '{') {
                        end = i + 3
                        while (true) {
                            if (end + 1 > n) return null
                            val charAt = literal[end++]
                            if (charAt == '}') {
                                break
                            }
                            if (!isHexDigit(charAt)) {
                                return null
                            }
                        }
                    } else {
                        end = i + 2
                        while (end < i + 6) {
                            if (end > n) return null
                            val charAt = literal[end]
                            if (!isHexDigit(charAt)) {
                                return null
                            }
                            end++
                        }
                    }
                }
            }
            if (end > n) return null
            val esc = literal.substring(i, end)
            val c = getCharValueFromCharInGrammarLiteral(esc)
            if (c == -1) {
                return null
            } else {
                buf.appendCodePointCompat(c)
            }
            i = end
        }
        return buf.toString()
    }

    /**
     * Given char `x` or `\\t` or `\\u1234`, return the char value;
     * unnecessary escapes like `\\{` yield `-1`.
     */
    fun getCharValueFromCharInGrammarLiteral(cstr: String): Int {
        return when (cstr.length) {
            1 -> cstr[0].code
            2 -> {
                if (cstr[0] != '\\') return -1
                val escChar = cstr[1]
                if (escChar == '\'') return escChar.code
                if (escChar.code >= ANTLRLiteralEscapedCharValue.size) return -1
                val charVal = ANTLRLiteralEscapedCharValue[escChar.code]
                if (charVal == 0) -1 else charVal
            }
            6 -> {
                if (!cstr.startsWith("\\u")) return -1
                val startOff: Int
                val endOff: Int
                if (cstr[2] == '{') {
                    startOff = 3
                    endOff = cstr.indexOf('}')
                } else {
                    startOff = 2
                    endOff = cstr.length
                }
                parseHexValue(cstr, startOff, endOff)
            }
            else -> {
                if (cstr.startsWith("\\u{")) {
                    parseHexValue(cstr, 3, cstr.indexOf('}'))
                } else {
                    -1
                }
            }
        }
    }

    fun parseHexValue(
        cstr: String,
        startOff: Int,
        endOff: Int,
    ): Int {
        if (startOff < 0 || endOff < 0 || startOff >= endOff || endOff > cstr.length) {
            return -1
        }
        for (i in startOff..<endOff) {
            if (!isHexDigit(cstr[i])) return -1
        }
        return cstr.substring(startOff, endOff).toIntOrNull(16) ?: -1
    }

    fun capitalize(s: String): String {
        if (s.isEmpty()) return s
        return s[0].uppercaseChar() + s.substring(1)
    }

    fun getIntervalSetEscapedString(intervalSet: IntervalSet): String {
        val buf = StringBuilder()
        val iter = intervalSet.getIntervals().iterator()
        while (iter.hasNext()) {
            val interval = iter.next()
            buf.append(getRangeEscapedString(interval.a, interval.b))
            if (iter.hasNext()) {
                buf.append(" | ")
            }
        }
        return buf.toString()
    }

    fun getRangeEscapedString(
        codePointStart: Int,
        codePointEnd: Int,
    ): String =
        if (codePointStart != codePointEnd) {
            getANTLRCharLiteralForChar(codePointStart) + ".." + getANTLRCharLiteralForChar(codePointEnd)
        } else {
            getANTLRCharLiteralForChar(codePointStart)
        }
}
