package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNType
import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.antlr4.misc.LogManager
import io.github.kotlinmania.antlr4.misc.TestRig as RuntimeTestRig
import io.github.kotlinmania.antlr4.misc.Utils
import io.github.kotlinmania.antlr4.tooling.gui.GrammarRegistration
import io.github.kotlinmania.antlr4.tooling.gui.LexerFactory
import io.github.kotlinmania.antlr4.tooling.gui.ParserFactory
import io.github.kotlinmania.antlr4.tooling.gui.StartRuleInvoker
import io.github.kotlinmania.antlr4.tooling.gui.TestRig as ToolTestRig
import io.github.kotlinmania.antlr4.tree.TerminalNodeImpl
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.FileNotFoundException
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.writeString
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

            val fileStream = ANTLRFileStream(path.toString())
            assertEquals(text.length, fileStream.size())
            assertEquals(text, fileStream.getText(Interval.of(0, fileStream.size() - 1)))
        }
    }

    fun missingFileRaisesTheActualIoError() {
        val path = Path("tmp", "missing-antlr-${Random.nextLong().toULong().toString(16)}.g4")
        assertFailsWith<FileNotFoundException> { CharStreams.fromPath(path) }
    }

    fun testRigAndLogManagerWriteRealArtifactsToDisk() {
        val directory = Path("tmp")
        SystemFileSystem.createDirectories(directory)
        val suffix = Random.nextLong().toULong().toString(16)
        val inputPath = Path(directory, "testrig-in-$suffix.txt")
        val psPath = Path(directory, "testrig-out-$suffix.ps")
        val logPath = Path(directory, "testrig-log-$suffix.log")

        class WordLexer(
            input: CharStream?,
        ) : Lexer(input) {
            override val ruleNames: List<String> = listOf("WORD")
            override val grammarFileName: String = "Word.g4"
            override val vocabulary: Vocabulary = VocabularyImpl(listOf("", ""), listOf("", "WORD"))
            override val atn: ATN = ATN(ATNType.LEXER, 1)

            override fun nextToken(): Token {
                val s = inputStream ?: return CommonToken(Token.EOF, "<EOF>")
                if (s.LA(1) == IntStream.EOF) return CommonToken(Token.EOF, "<EOF>")
                val buf = StringBuilder()
                val start = s.index()
                while (s.LA(1) != IntStream.EOF) {
                    buf.append(s.LA(1).toChar())
                    s.consume()
                }
                return CommonToken(1, buf.toString()).also {
                    it.startIndex = start
                    it.stopIndex = s.index() - 1
                }
            }
        }

        class WordParser(
            tokens: TokenStream?,
        ) : Parser(tokens) {
            override val tokenNames: List<String> = listOf("<INVALID>", "WORD")
            override val ruleNames: List<String> = listOf("word")
            override val grammarFileName: String = "Word.g4"
            override val vocabulary: Vocabulary = VocabularyImpl(listOf("", ""), listOf("", "WORD"))
            override val atn: ATN = ATN(ATNType.PARSER, 1)

            fun word(): ParserRuleContext {
                val ctx =
                    object : ParserRuleContext(null, -1) {
                        override var ruleIndex: Int = 0
                    }
                val tok = tokenStream.LT(1)!!
                ctx.start = tok
                ctx.stop = tok
                ctx.addChild(TerminalNodeImpl(tok))
                tokenStream.consume()
                return ctx
            }
        }

        try {
            Utils.writeFile(inputPath.toString(), "hello", "UTF-8")
            ToolTestRig.registerGrammar(
                GrammarRegistration(
                    name = "Word_$suffix",
                    lexerFactory = LexerFactory { s -> WordLexer(s) },
                    parserFactory = ParserFactory { t -> WordParser(t) },
                    rules = mapOf("word" to StartRuleInvoker { p -> (p as WordParser).word() }),
                ),
            )
            val results =
                RuntimeTestRig.run(
                    listOf(
                        "Word_$suffix",
                        "word",
                        "-tokens",
                        "-tree",
                        "-ps",
                        psPath.toString(),
                        inputPath.toString(),
                    ),
                )
            assertEquals(1, results.size)
            assertEquals("(word hello)", results[0].treeText)
            val savedPs = Utils.readFile(psPath.toString()).concatToString()
            assertTrue(savedPs.startsWith("%!PS-Adobe-3.0 EPSF-3.0"))
            assertTrue(savedPs.contains("(word) show"))

            val logManager = LogManager()
            logManager.log("testrig", "completed")
            logManager.save(logPath.toString())
            val savedLog = Utils.readFile(logPath.toString()).concatToString()
            assertTrue(savedLog.contains("testrig"))
            assertTrue(savedLog.contains("completed"))
        } finally {
            ToolTestRig.unregisterGrammar("Word_$suffix")
            SystemFileSystem.delete(inputPath, mustExist = false)
            SystemFileSystem.delete(psPath, mustExist = false)
            SystemFileSystem.delete(logPath, mustExist = false)
        }
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
