package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.misc.Interval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ANTLRInputStreamTest {
    @Test
    fun emptyStreamHasSize0() {
        val stream = ANTLRInputStream("")

        assertEquals(0, stream.size())
        assertEquals(0, stream.index())
        assertEquals("", stream.toString())
    }

    @Test
    fun emptyStreamLookAheadReturnsEof() {
        val stream = ANTLRInputStream("")

        assertEquals(IntStream.EOF, stream.LA(1))
        assertEquals(0, stream.index())
    }

    @Test
    fun consumingEmptyStreamThrows() {
        val stream = ANTLRInputStream("")

        val exception =
            assertFailsWith<IllegalStateException> {
                stream.consume()
            }
        assertEquals("cannot consume EOF", exception.message)
    }

    @Test
    fun multipleLatinCharsLookAheadReturnsCharCodes() {
        val stream = ANTLRInputStream("XYZ")

        assertEquals('X'.code, stream.LA(1))
        assertEquals(0, stream.index())
        assertEquals('Y'.code, stream.LA(2))
        assertEquals(0, stream.index())
        assertEquals('Z'.code, stream.LA(3))
        assertEquals(0, stream.index())
    }

    @Test
    fun bmpCharLookAheadReturnsCharCode() {
        val stream = ANTLRInputStream("\u611B")

        assertEquals(0x611B, stream.LA(1))
        assertEquals(0, stream.index())
    }

    @Test
    fun textUsesInclusiveIntervals() {
        val stream = ANTLRInputStream("0123456789")

        assertEquals("34567", stream.getText(Interval.of(3, 7)))
    }

    @Test
    fun textClampsStopAtStreamEnd() {
        val stream = ANTLRInputStream("0123456789")

        assertEquals("789", stream.getText(Interval.of(7, 30)))
    }

    @Test
    fun textStartingPastStreamEndIsEmpty() {
        val stream = ANTLRInputStream("0123456789")

        assertEquals("", stream.getText(Interval.of(30, 40)))
    }

    @Test
    fun seekAndLookBehindUseCurrentIndex() {
        val stream = ANTLRInputStream("0123456789")

        stream.seek(6)

        assertEquals(6, stream.index())
        assertEquals('5'.code, stream.LA(-1))
        assertEquals('6'.code, stream.LA(1))
    }
}
