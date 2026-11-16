/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.misc.CommonUtils
import io.github.kotlinmania.io.RawSource
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.readByteArray
import io.github.kotlinmania.io.readString

object CharStreams {
    private const val DEFAULT_BUFFER_SIZE = 4096

    /** Reads a UTF-8 file completely, retaining its path as the source name. */
    fun fromPath(path: Path): CodePointCharStream =
        fromStream(SystemFileSystem.source(path), path.toString())

    /** Reads a file using the specified character encoding, retaining its path as the source name. */
    fun fromPath(
        path: Path,
        encoding: String?,
    ): CodePointCharStream =
        fromStream(SystemFileSystem.source(path), encoding, path.toString())

    fun fromFileName(fileName: String): CodePointCharStream = fromPath(Path(fileName))

    fun fromFileName(
        fileName: String,
        encoding: String?,
    ): CodePointCharStream = fromPath(Path(fileName), encoding)

    /** Reads UTF-8 input completely and closes the supplied source. */
    fun fromStream(
        source: RawSource,
        sourceName: String = IntStream.UNKNOWN_SOURCE_NAME,
    ): CodePointCharStream = source.buffered().use { fromString(it.readString(), sourceName) }

    /** Reads input using the specified character encoding and closes the supplied source. */
    fun fromStream(
        source: RawSource,
        encoding: String?,
        sourceName: String = IntStream.UNKNOWN_SOURCE_NAME,
    ): CodePointCharStream {
        if (encoding == null) {
            return fromStream(source, sourceName)
        }
        val bytes = source.buffered().use { it.readByteArray() }
        return fromString(CommonUtils.decodeBytes(bytes, encoding), sourceName)
    }

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
