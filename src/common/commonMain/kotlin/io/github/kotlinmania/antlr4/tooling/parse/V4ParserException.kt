/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.IntStream
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenStream

/**
 * Grammar parser diagnostic carrying an explicit formatted syntax error message
 * (such as missing semicolon detection inside `.g4` rule elements).
 */
open class V4ParserException(
    val msg: String,
    input: IntStream?,
    token: Token? = (input as? TokenStream)?.LT(1),
) : RecognitionException(msg, null, input, null) {
    init {
        this.offendingToken = token
    }
}
