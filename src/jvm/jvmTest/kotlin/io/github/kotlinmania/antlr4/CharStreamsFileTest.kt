package io.github.kotlinmania.antlr4

import kotlin.test.Test

class CharStreamsFileTest {
    @Test
    fun readsUtf8FileAndPreservesSourcePath() =
        CharStreamsFileAssertions.readsUtf8FileAndPreservesSourcePath()

    @Test
    fun readsFileNameThroughTheSameFileSystem() =
        CharStreamsFileAssertions.readsFileNameThroughTheSameFileSystem()

    @Test
    fun missingFileRaisesTheActualIoError() =
        CharStreamsFileAssertions.missingFileRaisesTheActualIoError()
}
