package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.FileNotFoundException
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.writeString
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal object CharStreamsFileAssertions {
    fun readsUtf8FileAndPreservesSourcePath() {
        withFixture { path, text ->
            val stream = CharStreams.fromPath(path)
            assertEquals(text, stream.getText(Interval.of(0, stream.size() - 1)))
            assertEquals(path.toString(), stream.sourceName)
        }
    }

    fun readsFileNameThroughTheSameFileSystem() {
        withFixture { path, text ->
            val stream = CharStreams.fromFileName(path.toString())
            assertEquals(text, stream.getText(Interval.of(0, stream.size() - 1)))
            assertEquals(path.toString(), stream.sourceName)
        }
    }

    fun missingFileRaisesTheActualIoError() {
        val path = Path("tmp", "missing-antlr-${Random.nextLong().toULong().toString(16)}.g4")
        assertFailsWith<FileNotFoundException> { CharStreams.fromPath(path) }
    }

    private fun withFixture(block: (Path, String) -> Unit) {
        val directory = Path("tmp")
        SystemFileSystem.createDirectories(directory)
        val path = Path(directory, "antlr-io-${Random.nextLong().toULong().toString(16)}.g4")
        val text = "lexer grammar Fixture;\nWORD: 'Ω💩';\n"
        try {
            SystemFileSystem.sink(path).buffered().use { it.writeString(text) }
            block(path, text)
        } finally {
            SystemFileSystem.delete(path, mustExist = false)
        }
    }
}
