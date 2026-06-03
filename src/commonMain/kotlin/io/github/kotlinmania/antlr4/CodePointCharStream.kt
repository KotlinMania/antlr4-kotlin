/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.misc.Interval

/**
 * Alternative to [ANTLRInputStream] which treats the input as a series of
 * Unicode code points instead of UTF-16 code units.
 */
abstract class CodePointCharStream private constructor(
    position: Int,
    remaining: Int,
    private val name: String,
) : CharStream {
    protected val size: Int = remaining
    protected var position: Int = position

    init {
        require(position == 0)
    }

    abstract fun getInternalStorage(): Any

    override fun consume() {
        if (size - position == 0) {
            assert(LA(1) == IntStream.EOF)
            throw IllegalStateException("cannot consume EOF")
        }
        position += 1
    }

    override fun index(): Int = position

    override fun size(): Int = size

    override fun mark(): Int = -1

    override fun release(marker: Int) {
    }

    override fun seek(index: Int) {
        position = index
    }

    override val sourceName: String
        get() = name.ifEmpty { IntStream.UNKNOWN_SOURCE_NAME }

    override fun toString(): String = getText(Interval.of(0, size - 1)) ?: ""

    private class CodePoint8BitCharStream(
        position: Int,
        remaining: Int,
        name: String,
        private val byteArray: ByteArray,
        arrayOffset: Int,
    ) : CodePointCharStream(position, remaining, name) {
        init {
            require(arrayOffset == 0)
        }

        override fun getText(interval: Interval?): String? {
            val (startIdx, len) = intervalBounds(interval)
            return latin1BytesToString(byteArray, startIdx, len)
        }

        override fun LA(i: Int): Int {
            val offset: Int
            when (signum(i)) {
                -1 -> {
                    offset = position + i
                    if (offset < 0) return IntStream.EOF
                    return byteArray[offset].toInt() and 0xFF
                }

                0 -> return 0

                1 -> {
                    offset = position + i - 1
                    if (offset >= size) return IntStream.EOF
                    return byteArray[offset].toInt() and 0xFF
                }
            }
            error("unreachable")
        }

        override fun getInternalStorage(): Any = byteArray
    }

    private class CodePoint16BitCharStream(
        position: Int,
        remaining: Int,
        name: String,
        private val charArray: CharArray,
        arrayOffset: Int,
    ) : CodePointCharStream(position, remaining, name) {
        init {
            require(arrayOffset == 0)
        }

        override fun getText(interval: Interval?): String? {
            val (startIdx, len) = intervalBounds(interval)
            return charArray.concatToString(startIdx, startIdx + len)
        }

        override fun LA(i: Int): Int {
            val offset: Int
            when (signum(i)) {
                -1 -> {
                    offset = position + i
                    if (offset < 0) return IntStream.EOF
                    return charArray[offset].code and 0xFFFF
                }

                0 -> return 0

                1 -> {
                    offset = position + i - 1
                    if (offset >= size) return IntStream.EOF
                    return charArray[offset].code and 0xFFFF
                }
            }
            error("unreachable")
        }

        override fun getInternalStorage(): Any = charArray
    }

    private class CodePoint32BitCharStream(
        position: Int,
        remaining: Int,
        name: String,
        private val intArray: IntArray,
        arrayOffset: Int,
    ) : CodePointCharStream(position, remaining, name) {
        init {
            require(arrayOffset == 0)
        }

        override fun getText(interval: Interval?): String? {
            val (startIdx, len) = intervalBounds(interval)
            return codePointsToString(intArray, startIdx, len)
        }

        override fun LA(i: Int): Int {
            val offset: Int
            when (signum(i)) {
                -1 -> {
                    offset = position + i
                    if (offset < 0) return IntStream.EOF
                    return intArray[offset]
                }

                0 -> return 0

                1 -> {
                    offset = position + i - 1
                    if (offset >= size) return IntStream.EOF
                    return intArray[offset]
                }
            }
            error("unreachable")
        }

        override fun getInternalStorage(): Any = intArray
    }

    protected fun intervalBounds(interval: Interval?): Pair<Int, Int> {
        val iv = interval ?: return 0 to 0
        val startIdx = minOf(maxOf(iv.a, 0), size)
        val len = maxOf(0, minOf(iv.b - iv.a + 1, size - startIdx))
        return startIdx to len
    }

    companion object {
        fun fromBuffer(codePointBuffer: CodePointBuffer): CodePointCharStream =
            fromBuffer(codePointBuffer, IntStream.UNKNOWN_SOURCE_NAME)

        fun fromBuffer(
            codePointBuffer: CodePointBuffer,
            name: String,
        ): CodePointCharStream =
            when (codePointBuffer.type) {
                CodePointBuffer.Type.BYTE ->
                    CodePoint8BitCharStream(
                        codePointBuffer.position(),
                        codePointBuffer.remaining(),
                        name,
                        codePointBuffer.byteArray(),
                        codePointBuffer.arrayOffset(),
                    )

                CodePointBuffer.Type.CHAR ->
                    CodePoint16BitCharStream(
                        codePointBuffer.position(),
                        codePointBuffer.remaining(),
                        name,
                        codePointBuffer.charArray(),
                        codePointBuffer.arrayOffset(),
                    )

                CodePointBuffer.Type.INT ->
                    CodePoint32BitCharStream(
                        codePointBuffer.position(),
                        codePointBuffer.remaining(),
                        name,
                        codePointBuffer.intArray(),
                        codePointBuffer.arrayOffset(),
                    )
            }
    }
}
