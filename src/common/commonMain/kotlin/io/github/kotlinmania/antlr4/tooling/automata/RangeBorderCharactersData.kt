/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.automata

import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar

/**
 * Case-folding boundary metadata for character ranges (`'a'..'z'`, `'A'..'z'`, etc.) under `caseInsensitive`.
 */
class RangeBorderCharactersData(
    val lowerFrom: Int,
    val upperFrom: Int,
    val lowerTo: Int,
    val upperTo: Int,
    val mixOfLowerAndUpperCharCase: Boolean,
) {
    val isSingleRange: Boolean
        get() =
            (lowerFrom == upperFrom && lowerTo == upperTo) ||
                mixOfLowerAndUpperCharCase ||
                lowerTo - lowerFrom != upperTo - upperFrom

    companion object {
        fun getAndCheckCharactersData(
            from: Int,
            to: Int,
            grammar: Grammar,
            tree: GrammarAST,
            reportRangeContainsNotImpliedCharacters: Boolean,
        ): RangeBorderCharactersData {
            val lowerFrom = toLowerCodePoint(from)
            val upperFrom = toUpperCodePoint(from)
            val lowerTo = toLowerCodePoint(to)
            val upperTo = toUpperCodePoint(to)

            val isLowerFrom = lowerFrom == from
            val isLowerTo = lowerTo == to
            val mixOfLowerAndUpperCharCase = (isLowerFrom && !isLowerTo) || (!isLowerFrom && isLowerTo)
            if (reportRangeContainsNotImpliedCharacters && mixOfLowerAndUpperCharCase && from <= 0x7F && to <= 0x7F) {
                val notImpliedCharacters = StringBuilder()
                for (i in from until to) {
                    if (!isAsciiAlphabetic(i)) {
                        notImpliedCharacters.append(i.toChar())
                    }
                }
                if (notImpliedCharacters.isNotEmpty()) {
                    grammar.tool.errMgr.grammarError(
                        ErrorType.RANGE_PROBABLY_CONTAINS_NOT_IMPLIED_CHARACTERS,
                        grammar.fileName,
                        tree.token,
                        from.toChar(),
                        to.toChar(),
                        notImpliedCharacters.toString(),
                    )
                }
            }
            return RangeBorderCharactersData(
                lowerFrom = lowerFrom,
                upperFrom = upperFrom,
                lowerTo = lowerTo,
                upperTo = upperTo,
                mixOfLowerAndUpperCharCase = mixOfLowerAndUpperCharCase,
            )
        }

        internal fun toLowerCodePoint(codePoint: Int): Int {
            if (codePoint in 0..0xFFFF) {
                return codePoint.toChar().lowercaseChar().code
            }
            val folded = codePointToString(codePoint).lowercase()
            return firstCodePoint(folded, fallback = codePoint)
        }

        internal fun toUpperCodePoint(codePoint: Int): Int {
            if (codePoint in 0..0xFFFF) {
                return codePoint.toChar().uppercaseChar().code
            }
            val folded = codePointToString(codePoint).uppercase()
            return firstCodePoint(folded, fallback = codePoint)
        }

        private fun isAsciiAlphabetic(cp: Int): Boolean =
            cp in 'A'.code..'Z'.code || cp in 'a'.code..'z'.code

        private fun codePointToString(cp: Int): String {
            val offset = cp - 0x10000
            val high = ((offset ushr 10) + 0xD800).toChar()
            val low = ((offset and 0x3FF) + 0xDC00).toChar()
            return charArrayOf(high, low).concatToString()
        }

        private fun firstCodePoint(
            s: String,
            fallback: Int,
        ): Int {
            if (s.isEmpty()) return fallback
            val first = s[0]
            if (first.isHighSurrogate() && s.length >= 2 && s[1].isLowSurrogate()) {
                return ((first.code - 0xD800) shl 10) + (s[1].code - 0xDC00) + 0x10000
            }
            return first.code
        }
    }
}
