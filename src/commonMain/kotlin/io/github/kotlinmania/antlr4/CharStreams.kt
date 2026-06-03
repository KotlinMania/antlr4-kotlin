/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

object CharStreams {
    private const val DEFAULT_BUFFER_SIZE = 4096

    fun fromString(s: String): CodePointCharStream = fromString(s, IntStream.UNKNOWN_SOURCE_NAME)

    fun fromString(
        s: String,
        sourceName: String,
    ): CodePointCharStream {
        val codePointBufferBuilder = CodePointBuffer.builder(maxOf(DEFAULT_BUFFER_SIZE, s.length))
        codePointBufferBuilder.append(s)
        return CodePointCharStream.fromBuffer(codePointBufferBuilder.build(), sourceName)
    }

    fun fromChars(
        chars: CharArray,
        sourceName: String = IntStream.UNKNOWN_SOURCE_NAME,
    ): CodePointCharStream {
        val codePointBufferBuilder = CodePointBuffer.builder(maxOf(DEFAULT_BUFFER_SIZE, chars.size))
        codePointBufferBuilder.append(chars)
        return CodePointCharStream.fromBuffer(codePointBufferBuilder.build(), sourceName)
    }

    fun fromLatin1Bytes(
        bytes: ByteArray,
        sourceName: String = IntStream.UNKNOWN_SOURCE_NAME,
    ): CodePointCharStream = CodePointCharStream.fromBuffer(CodePointBuffer.withBytes(bytes), sourceName)

    fun fromCodePoints(
        codePoints: IntArray,
        sourceName: String = IntStream.UNKNOWN_SOURCE_NAME,
    ): CodePointCharStream = CodePointCharStream.fromBuffer(CodePointBuffer.withInts(codePoints), sourceName)
}
