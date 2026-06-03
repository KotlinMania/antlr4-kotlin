/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.misc.Interval

abstract class UnbufferedCharStream(
    bufferSize: Int = 256,
) : CharStream {
    protected var data: IntArray = IntArray(bufferSize)
    protected var n: Int = 0
    protected var p: Int = 0
    protected var numMarkers: Int = 0
    protected var lastChar: Int = -1
    protected var lastCharBufferStart: Int = 0
    protected var currentCharIndex: Int = 0
    var name: String? = null

    override fun consume() {
        check(LA(1) != IntStream.EOF) { "cannot consume EOF" }

        lastChar = data[p]

        if (p == n - 1 && numMarkers == 0) {
            n = 0
            p = -1
            lastCharBufferStart = lastChar
        }

        p++
        currentCharIndex++
        sync(1)
    }

    protected fun sync(want: Int) {
        val need = (p + want - 1) - n + 1
        if (need > 0) {
            fill(need)
        }
    }

    protected fun fill(n: Int): Int {
        for (i in 0..<n) {
            if (this.n > 0 && data[this.n - 1] == IntStream.EOF) {
                return i
            }

            val c = nextChar()
            if (c > Char.MAX_VALUE.code || c == IntStream.EOF) {
                add(c)
            } else {
                val ch = c.toChar()
                when {
                    isLowSurrogate(ch) ->
                        throw RuntimeException("Invalid UTF-16: low surrogate with no preceding high surrogate")

                    isHighSurrogate(ch) -> {
                        val lowSurrogate = nextChar()
                        when {
                            lowSurrogate > Char.MAX_VALUE.code ->
                                throw RuntimeException("Invalid UTF-16: high surrogate followed by code point > U+FFFF")

                            lowSurrogate == IntStream.EOF ->
                                throw RuntimeException("Invalid UTF-16: dangling high surrogate at end of file")

                            isLowSurrogate(lowSurrogate.toChar()) ->
                                add(toCodePoint(ch, lowSurrogate.toChar()))

                            else ->
                                throw RuntimeException("Invalid UTF-16: dangling high surrogate")
                        }
                    }

                    else -> add(c)
                }
            }
        }

        return n
    }

    protected open fun nextChar(): Int = IntStream.EOF

    protected fun add(c: Int) {
        if (n >= data.size) {
            data = data.copyOf(data.size * 2)
        }
        data[n++] = c
    }

    override fun LA(i: Int): Int {
        if (i == -1) return lastChar

        sync(i)
        val index = p + i - 1
        if (index < 0) throw IndexOutOfBoundsException()
        if (index >= n) return IntStream.EOF
        return data[index]
    }

    override fun mark(): Int {
        if (numMarkers == 0) {
            lastCharBufferStart = lastChar
        }

        val mark = -numMarkers - 1
        numMarkers++
        return mark
    }

    override fun release(marker: Int) {
        val expectedMark = -numMarkers
        check(marker == expectedMark) { "release() called with an invalid marker." }

        numMarkers--
        if (numMarkers == 0 && p > 0) {
            data.copyInto(data, 0, p, n)
            n -= p
            p = 0
            lastCharBufferStart = lastChar
        }
    }

    override fun index(): Int = currentCharIndex

    override fun seek(index: Int) {
        var targetIndex = index
        if (targetIndex == currentCharIndex) {
            return
        }

        if (targetIndex > currentCharIndex) {
            sync(targetIndex - currentCharIndex)
            targetIndex = minOf(targetIndex, bufferStartIndex + n - 1)
        }

        val i = targetIndex - bufferStartIndex
        require(i >= 0) { "cannot seek to negative index $targetIndex" }
        if (i >= n) {
            throw UnsupportedOperationException(
                "seek to index outside buffer: $targetIndex not in $bufferStartIndex..${bufferStartIndex + n}",
            )
        }

        p = i
        currentCharIndex = targetIndex
        lastChar = if (p == 0) lastCharBufferStart else data[p - 1]
    }

    override fun size(): Int = throw UnsupportedOperationException("Unbuffered stream cannot know its size")

    override val sourceName: String
        get() {
            val currentName = name
            return if (currentName.isNullOrEmpty()) IntStream.UNKNOWN_SOURCE_NAME else currentName
        }

    override fun getText(interval: Interval?): String? {
        val iv = interval ?: throw NullPointerException("interval")
        require(!(iv.a < 0 || iv.b < iv.a - 1)) { "invalid interval" }

        val bufferStartIndex = this.bufferStartIndex
        if (n > 0 && data[n - 1] == IntStream.EOF) {
            require(!(iv.a + iv.length() > bufferStartIndex + n)) { "the interval extends past the end of the stream" }
        }

        if (iv.a < bufferStartIndex || iv.b >= bufferStartIndex + n) {
            throw UnsupportedOperationException(
                "interval $iv outside buffer: $bufferStartIndex..${bufferStartIndex + n - 1}",
            )
        }

        val i = iv.a - bufferStartIndex
        val len = iv.length()
        val buf = StringBuilder(len)
        for (j in 0..<len) {
            appendCodePoint(buf, data[i + j])
        }
        return buf.toString()
    }

    protected val bufferStartIndex: Int
        get() = currentCharIndex - p
}
