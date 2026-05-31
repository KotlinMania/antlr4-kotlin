/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.atn.ATNConfigSet
import io.github.kotlinmania.antlr4.misc.CommonUtils
import io.github.kotlinmania.antlr4.misc.Interval

class LexerNoViableAltException(
    lexer: Lexer?,
    input: CharStream?,
    /** Matching attempted at what input index?  */
    val startIndex: Int,
    deadEndConfigs: ATNConfigSet?,
) : RecognitionException(lexer, input, null) {
    /** Which configurations did we try at input.index() that couldn't match input.LA(1)?  */
    private val deadEndConfigs: ATNConfigSet?

    init {
        this.deadEndConfigs = deadEndConfigs
    }

    val charStream: CharStream?
        get() = super.inputStream as CharStream?

    override fun toString(): String {
        var symbol: String? = ""
        val stream = this.inputStream
        if (stream != null && startIndex >= 0 && startIndex < stream.size()) {
            symbol = (stream as CharStream).getText(Interval.of(startIndex, startIndex))
            symbol = CommonUtils.escapeWhitespace(symbol!!, false)
        }

        return "${io.github.kotlinmania.antlr4.LexerNoViableAltException::class.simpleName!!}('$symbol')"
    }
}
