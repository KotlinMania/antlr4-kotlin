package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.io.Buffer
import io.github.kotlinmania.io.RawSource
import io.github.kotlinmania.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharStreamsIoTest {
    @Test
    fun readsUtf8AndClosesSource() {
        val source = TrackingSource("aΩ💩")
        val stream = CharStreams.fromStream(source, "unicode.g4")
        assertEquals(3, stream.size())
        assertEquals('a'.code, stream.LA(1))
        assertEquals('Ω'.code, stream.LA(2))
        assertEquals(0x1F4A9, stream.LA(3))
        assertEquals("aΩ💩", stream.getText(Interval.of(0, 2)))
        assertEquals("unicode.g4", stream.sourceName)
        assertTrue(source.closed)
    }

    @Test
    fun emptyStreamKeepsUnknownSourceName() {
        val source = TrackingSource("")
        val stream = CharStreams.fromStream(source)
        assertEquals(0, stream.size())
        assertEquals(IntStream.EOF, stream.LA(1))
        assertEquals(IntStream.UNKNOWN_SOURCE_NAME, stream.sourceName)
        assertTrue(source.closed)
    }

    private class TrackingSource(text: String) : RawSource {
        private val buffer = Buffer().apply { writeString(text) }
        var closed = false
            private set

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long =
            buffer.readAtMostTo(sink, byteCount)

        override fun close() {
            closed = true
            buffer.close()
        }
    }
}
