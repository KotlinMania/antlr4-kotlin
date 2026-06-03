/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.misc.Interval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CodePointCharStreamTest {
    @Test
    fun emptyBytesHasSize0() {
        val stream = CharStreams.fromString("")

        assertEquals(0, stream.size())
        assertEquals(0, stream.index())
        assertEquals("", stream.toString())
    }

    @Test
    fun emptyBytesLookAheadReturnsEof() {
        val stream = CharStreams.fromString("")

        assertEquals(IntStream.EOF, stream.LA(1))
        assertEquals(0, stream.index())
    }

    @Test
    fun consumingEmptyStreamShouldThrow() {
        val stream = CharStreams.fromString("")

        val exception =
            assertFailsWith<IllegalStateException> {
                stream.consume()
            }
        assertEquals("cannot consume EOF", exception.message)
    }

    @Test
    fun multipleLatinCodePointsLookAheadShouldReturnCodePoints() {
        val stream = CharStreams.fromString("XYZ")

        assertEquals('X'.code, stream.LA(1))
        assertEquals(0, stream.index())
        assertEquals('Y'.code, stream.LA(2))
        assertEquals(0, stream.index())
        assertEquals('Z'.code, stream.LA(3))
        assertEquals(0, stream.index())
    }

    @Test
    fun singleCjkCodePointLookAheadShouldReturnCodePoint() {
        val stream = CharStreams.fromString("\u611B")

        assertEquals(0x611B, stream.LA(1))
        assertEquals(0, stream.index())
    }

    @Test
    fun singleEmojiCodePointLookAheadShouldReturnCodePoint() {
        val stream = CharStreams.fromString(codePointString(0x1F4A9))

        assertEquals(0x1F4A9, stream.LA(1))
        assertEquals(0, stream.index())
    }

    @Test
    fun textWithMixedCodePointWidths() {
        val emoji = codePointString(0x1F522)
        val stream = CharStreams.fromString("01234" + emoji + "6789")

        assertEquals("34" + emoji + "67", stream.getText(Interval.of(3, 7)))
    }

    @Test
    fun seekAndLookBehindWithEmoji() {
        val stream = CharStreams.fromString("01234" + codePointString(0x1F522) + "6789")

        stream.seek(6)

        assertEquals(0x1F522, stream.LA(-1))
        assertEquals('6'.code, stream.LA(1))
    }

    @Test
    fun asciiContentsShouldUse8BitBuffer() {
        val stream = CharStreams.fromString("hello")

        assertIs<ByteArray>(stream.getInternalStorage())
        assertEquals(5, stream.size())
    }

    @Test
    fun bmpContentsShouldUse16BitBuffer() {
        val stream = CharStreams.fromString("hello \u4E16\u754C")

        assertIs<CharArray>(stream.getInternalStorage())
        assertEquals(8, stream.size())
    }

    @Test
    fun smpContentsShouldUse32BitBuffer() {
        val stream = CharStreams.fromString("hello " + codePointString(0x1F30D))

        assertIs<IntArray>(stream.getInternalStorage())
        assertEquals(7, stream.size())
    }

    private fun codePointString(codePoint: Int): String {
        if (codePoint <= 0xFFFF) return codePoint.toChar().toString()

        val value = codePoint - 0x10000
        val high = ((value ushr 10) + 0xD800).toChar()
        val low = ((value and 0x3FF) + 0xDC00).toChar()
        return "$high$low"
    }
}
