/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.unicode.UnicodeData

/**
 * Utility class to parse escapes like:
 * - `\\n`
 * - `\\uABCD`
 * - `\\u{10ABCD}`
 * - `\\p{Foo}`
 * - `\\P{Bar}`
 * - `\\p{Baz=Blech}`
 * - `\\P{Baz=Blech}`
 */
object EscapeSequenceParsing {
    enum class ResultKind {
        INVALID,
        CODE_POINT,
        PROPERTY,
    }

    class EscapeParseResult(
        val type: ResultKind,
        val codePoint: Int,
        val propertyIntervalSet: IntervalSet,
        val startOffset: Int,
        val parseLength: Int,
    ) {
        override fun toString(): String =
            "EscapeParseResult type=$type codePoint=$codePoint propertyIntervalSet=$propertyIntervalSet parseLength=$parseLength"

        override fun equals(other: Any?): Boolean {
            if (other !is EscapeParseResult) return false
            if (this === other) return true
            return type == other.type &&
                codePoint == other.codePoint &&
                propertyIntervalSet == other.propertyIntervalSet &&
                parseLength == other.parseLength
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + codePoint
            result = 31 * result + propertyIntervalSet.hashCode()
            result = 31 * result + parseLength
            return result
        }
    }

    /**
     * Parses a single escape sequence starting at [startOff].
     *
     * Returns [ResultKind.INVALID] if no valid escape sequence was found, an [EscapeParseResult] otherwise.
     */
    fun parseEscape(
        s: String,
        startOff: Int,
    ): EscapeParseResult {
        var offset = startOff
        if (offset + 2 > s.length || s.codePointAtCompat(offset) != '\\'.code) {
            return invalid(startOff, s.length - 1)
        }
        // Move past backslash
        offset++
        val escaped = s.codePointAtCompat(offset)
        // Move past escaped code point
        offset += charCountCompat(escaped)
        if (escaped == 'u'.code) {
            // \u{1} is the shortest we support
            if (offset + 3 > s.length) {
                return invalid(startOff, s.length - 1)
            }
            val hexStartOffset: Int
            val hexEndOffset: Int
            if (s.codePointAtCompat(offset) == '{'.code) {
                hexStartOffset = offset + 1
                hexEndOffset = s.indexOf('}', hexStartOffset)
                if (hexEndOffset == -1) {
                    return invalid(startOff, s.length - 1)
                }
                offset = hexEndOffset + 1
            } else {
                if (offset + 4 > s.length) {
                    return invalid(startOff, s.length - 1)
                }
                hexStartOffset = offset
                hexEndOffset = offset + 4
                offset = hexEndOffset
            }
            val codePointValue = CharSupport.parseHexValue(s, hexStartOffset, hexEndOffset)
            if (codePointValue == -1 || codePointValue > Lexer.MAX_CHAR_VALUE) {
                return invalid(startOff, startOff + 6 - 1)
            }
            return EscapeParseResult(
                ResultKind.CODE_POINT,
                codePointValue,
                IntervalSet.EMPTY_SET,
                startOff,
                offset - startOff,
            )
        } else if (escaped == 'p'.code || escaped == 'P'.code) {
            // \p{L} is the shortest we support
            if (offset + 3 > s.length) {
                return invalid(startOff, s.length - 1)
            }
            if (s.codePointAtCompat(offset) != '{'.code) {
                return invalid(startOff, offset)
            }
            val openBraceOffset = offset
            val closeBraceOffset = s.indexOf('}', openBraceOffset)
            if (closeBraceOffset == -1) {
                return invalid(startOff, s.length - 1)
            }
            val propertyName = s.substring(openBraceOffset + 1, closeBraceOffset)
            val basePropertySet = UnicodeData.getPropertyCodePoints(propertyName)
            if (basePropertySet == null || basePropertySet.isNil) {
                return invalid(startOff, closeBraceOffset)
            }
            offset = closeBraceOffset + 1
            val finalSet =
                if (escaped == 'P'.code) {
                    basePropertySet.complement(IntervalSet.COMPLETE_CHAR_SET) ?: IntervalSet.EMPTY_SET
                } else {
                    basePropertySet
                }
            return EscapeParseResult(
                ResultKind.PROPERTY,
                -1,
                finalSet,
                startOff,
                offset - startOff,
            )
        } else if (escaped < CharSupport.ANTLRLiteralEscapedCharValue.size) {
            var codePoint = CharSupport.ANTLRLiteralEscapedCharValue[escaped]
            if (codePoint == 0) {
                if (escaped != ']'.code && escaped != '-'.code) {
                    return invalid(startOff, startOff + 1)
                } else {
                    codePoint = escaped
                }
            }
            return EscapeParseResult(
                ResultKind.CODE_POINT,
                codePoint,
                IntervalSet.EMPTY_SET,
                startOff,
                offset - startOff,
            )
        } else {
            return invalid(startOff, s.length - 1)
        }
    }

    private fun invalid(
        start: Int,
        stop: Int,
    ): EscapeParseResult =
        EscapeParseResult(
            ResultKind.INVALID,
            0,
            IntervalSet.EMPTY_SET,
            start,
            stop - start + 1,
        )
}
