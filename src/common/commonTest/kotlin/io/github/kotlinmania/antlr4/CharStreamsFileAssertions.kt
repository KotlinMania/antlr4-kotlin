package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNType
import io.github.kotlinmania.antlr4.misc.InterpreterDataReader
import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.antlr4.misc.LogManager
import io.github.kotlinmania.antlr4.misc.Utils
import io.github.kotlinmania.antlr4.tooling.gui.GrammarRegistration
import io.github.kotlinmania.antlr4.tooling.gui.LexerFactory
import io.github.kotlinmania.antlr4.tooling.gui.ParserFactory
import io.github.kotlinmania.antlr4.tooling.gui.StartRuleInvoker
import io.github.kotlinmania.antlr4.tooling.tool.ANTLRMessage
import io.github.kotlinmania.antlr4.tooling.tool.ANTLRToolListener
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
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
import io.github.kotlinmania.antlr4.misc.TestRig as RuntimeTestRig
import io.github.kotlinmania.antlr4.tooling.gui.TestRig as ToolTestRig

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
                val tok = requireNotNull(tokenStream.LT(1))
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

    fun compositeGrammarLibAndOutdirResolutionAndInterpreterDataFile() {
        val rootTmp = Path("tmp")
        SystemFileSystem.createDirectories(rootTmp)
        val suffix = Random.nextLong().toULong().toString(16)
        val rawBaseDir = Path(rootTmp, "composite-$suffix")
        SystemFileSystem.createDirectories(rawBaseDir)
        val baseDir = SystemFileSystem.resolve(rawBaseDir)
        val subDir = Path(baseDir, "sub")
        val outDir = Path(baseDir, "out")
        val onlyOutDir = Path(baseDir, "onlyOut")
        val interpPath = Path(baseDir, "Calc.interp")
        SystemFileSystem.createDirectories(subDir)
        SystemFileSystem.createDirectories(outDir)
        SystemFileSystem.createDirectories(onlyOutDir)

        val subordinate =
            "parser grammar S;\n" +
                "a : B ;\n"
        val master =
            "grammar M;\n" +
                "import S;\n" +
                "s : a ;\n" +
                "B : 'b' ;\n" +
                "WS : (' '|'\\n') -> skip ;\n"
        val mLexer =
            "lexer grammar MLexer;\n" +
                "B : 'b' ;\n" +
                "WS : (' '|'\\n') -> skip ;\n"
        val mParser =
            "parser grammar MParser;\n" +
                "import S;\n" +
                "options { tokenVocab=MLexer; }\n" +
                "s : a ;\n"

        fun runDiskTool(args: List<String>): Pair<Tool, List<ANTLRMessage>> {
            val errors = ArrayList<ANTLRMessage>()
            val tool = Tool(args)
            tool.removeListeners()
            tool.addListener(
                object : ANTLRToolListener {
                    override fun info(msg: String?) {}

                    override fun error(msg: ANTLRMessage) {
                        errors.add(msg)
                    }

                    override fun warning(msg: ANTLRMessage) {}
                },
            )
            tool.processGrammarsOnCommandLine()
            return tool to errors
        }

        try {
            val g = Grammar("grammar Calc;\ns : ID '+' INT ;\nID : [a-z]+ ;\nINT : [0-9]+ ;\n")
            val interpData = Tool.generateInterpreterData(g)
            Utils.writeFile(interpPath.toString(), interpData, "UTF-8")
            val parsedInterp = InterpreterDataReader.parseFile(interpPath.toString())
            assertEquals(listOf("s"), parsedInterp.ruleNames)
            assertEquals("ID", parsedInterp.vocabulary?.getSymbolicName(2))

            val subPath = Path(subDir, "S.g4")
            val masterPath = Path(baseDir, "M.g4")
            val onlyOutSubPath = Path(onlyOutDir, "S.g4")
            val mLexerPath = Path(baseDir, "MLexer.g4")
            val mParserPath = Path(baseDir, "MParser.g4")

            Utils.writeFile(subPath.toString(), subordinate, "UTF-8")
            Utils.writeFile(masterPath.toString(), master, "UTF-8")
            Utils.writeFile(onlyOutSubPath.toString(), subordinate, "UTF-8")
            Utils.writeFile(mLexerPath.toString(), mLexer, "UTF-8")
            Utils.writeFile(mParserPath.toString(), mParser, "UTF-8")

            val (subTool, subErrors) =
                runDiskTool(listOf("-lib", subDir.toString(), "-o", outDir.toString(), masterPath.toString()))
            assertEquals(0, subErrors.size)
            assertEquals(0, subTool.numErrors)
            assertTrue(SystemFileSystem.exists(Path(outDir, "MParser.kt")))

            val (_, notInOutErrors) =
                runDiskTool(listOf("-o", onlyOutDir.toString(), masterPath.toString()))
            assertTrue(notInOutErrors.any { it.errorType == ErrorType.CANNOT_FIND_IMPORTED_GRAMMAR })

            val (_, lexerErrors) =
                runDiskTool(listOf("-o", outDir.toString(), mLexerPath.toString()))
            assertEquals(0, lexerErrors.size)
            assertTrue(SystemFileSystem.exists(Path(outDir, "MLexer.tokens")))

            val (_, parserErrors) =
                runDiskTool(listOf("-o", outDir.toString(), "-lib", subDir.toString(), mParserPath.toString()))
            assertEquals(0, parserErrors.size)
            assertTrue(SystemFileSystem.exists(Path(outDir, "MParser.kt")))
        } finally {
            fun cleanupTree(target: Path) {
                if (!SystemFileSystem.exists(target)) return
                val meta = SystemFileSystem.metadataOrNull(target)
                if (meta?.isDirectory == true) {
                    for (child in SystemFileSystem.list(target)) {
                        cleanupTree(child)
                    }
                }
                SystemFileSystem.delete(target, mustExist = false)
            }
            cleanupTree(baseDir)
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
