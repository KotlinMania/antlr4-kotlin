/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.gui

import io.github.kotlinmania.antlr4.CharStream
import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.DiagnosticErrorListener
import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.LexerInterpreter
import io.github.kotlinmania.antlr4.Parser
import io.github.kotlinmania.antlr4.ParserInterpreter
import io.github.kotlinmania.antlr4.ParserRuleContext
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.atn.PredictionMode
import io.github.kotlinmania.antlr4.misc.InterpreterDataReader
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem

fun interface LexerFactory {
    fun create(input: CharStream?): Lexer
}

fun interface ParserFactory {
    fun create(tokens: TokenStream?): Parser
}

fun interface StartRuleInvoker {
    fun invoke(parser: Parser): ParserRuleContext
}

class GrammarRegistration(
    val name: String,
    val lexerFactory: LexerFactory,
    val parserFactory: ParserFactory? = null,
    val rules: Map<String, StartRuleInvoker> = emptyMap(),
)

class TestRigRunResult(
    val tokens: List<String>,
    val treeText: String?,
    val postScript: String?,
)

/**
 * Pure-Kotlin ANTLR4 TestRig (`grun`), executing compiled grammars registered via
 * [registerGrammar], `.interp` interpreter-data bundles, or direct [Lexer]/[Parser]
 * instances with token dumping, LISP parse-tree printing, diagnostics, SLL mode,
 * trace logging, and EPS PostScript parse-tree generation.
 */
class TestRig(
    args: List<String>,
) {
    var grammarName: String? = null
        private set
    var startRuleName: String? = null
        private set
    private val mutableInputFiles: MutableList<String> = mutableListOf()
    val inputFiles: List<String>
        get() = mutableInputFiles

    var printTree: Boolean = false
        private set
    var gui: Boolean = false
        private set
    var psFile: String? = null
        private set
    var showTokens: Boolean = false
        private set
    var trace: Boolean = false
        private set
    var diagnostics: Boolean = false
        private set
    var encoding: String? = null
        private set
    var sll: Boolean = false
        private set

    init {
        if (args.size < 2) {
            println(USAGE_MESSAGE)
        } else {
            var i = 0
            grammarName = args[i++]
            startRuleName = args[i++]
            while (i < args.size) {
                val arg = args[i++]
                if (!arg.startsWith("-")) {
                    mutableInputFiles.add(arg)
                    continue
                }
                when (arg) {
                    "-tree" -> printTree = true
                    "-gui" -> gui = true
                    "-tokens" -> showTokens = true
                    "-trace" -> trace = true
                    "-SLL" -> sll = true
                    "-diagnostics" -> diagnostics = true
                    "-encoding" -> {
                        if (i >= args.size) {
                            throw IllegalArgumentException("missing encoding on -encoding")
                        }
                        encoding = args[i++]
                    }

                    "-ps" -> {
                        if (i >= args.size) {
                            throw IllegalArgumentException("missing filename on -ps")
                        }
                        psFile = args[i++]
                    }
                }
            }
        }
    }

    fun process(): List<TestRigRunResult> {
        val gName = grammarName ?: return emptyList()
        val ruleName = startRuleName ?: return emptyList()

        val registration = registeredGrammars[gName]
        if (registration != null) {
            return processRegistered(registration, ruleName)
        }

        val interpPair = resolveInterpreterData(gName, ruleName)
        if (interpPair != null) {
            return processInterpreterData(interpPair.first, interpPair.second, ruleName)
        }

        if (gName.endsWith(".g4") && SystemFileSystem.exists(Path(gName))) {
            return processGrammarFile(gName, ruleName)
        }

        val dynamicRunner = grammarFileRunner
        if (dynamicRunner != null && gName.endsWith(".g4")) {
            return dynamicRunner(this, gName, ruleName)
        }

        throw IllegalArgumentException(
            "Grammar '$gName' is neither registered via TestRig.registerGrammar nor backed by '$gName.interp' / '${gName}Lexer.interp'.",
        )
    }

    fun processInput(
        lexer: Lexer,
        parser: Parser?,
        input: CharStream,
        startRule: StartRuleInvoker? = null,
        emitStdout: Boolean = true,
    ): TestRigRunResult {
        lexer.setInputStream(input)
        val tokenStream = CommonTokenStream(lexer)
        tokenStream.fill()

        val formattedTokens = mutableListOf<String>()
        if (showTokens) {
            for (tok in tokenStream.allTokens()) {
                val rendered =
                    if (tok is CommonToken) {
                        tok.toString(lexer)
                    } else {
                        tok.toString()
                    }
                formattedTokens.add(rendered)
                if (emitStdout) {
                    println(rendered)
                }
            }
        }

        if (startRuleName == LEXER_START_RULE_NAME || parser == null) {
            return TestRigRunResult(formattedTokens, null, null)
        }

        if (diagnostics) {
            parser.addErrorListener(DiagnosticErrorListener())
            parser.interpreter?.setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION)
        }

        if (printTree || gui || psFile != null) {
            parser.buildParseTree = true
        }

        if (sll) {
            parser.interpreter?.setPredictionMode(PredictionMode.SLL)
        }

        parser.setInputStream(tokenStream)
        parser.reset()
        parser.setTrace(trace)

        val tree: ParserRuleContext =
            when {
                startRule != null -> startRule.invoke(parser)
                parser is ParserInterpreter -> {
                    val targetRule = startRuleName.orEmpty()
                    val ruleIndex = parser.ruleNames?.indexOf(targetRule) ?: -1
                    require(ruleIndex >= 0) { "No rule named '$targetRule' in ${parser.grammarFileName}" }
                    checkNotNull(parser.parse(ruleIndex)) { "ParserInterpreter returned null parse tree for '$targetRule'" }
                }

                else -> throw IllegalArgumentException("No start rule invoker supplied for '${startRuleName.orEmpty()}'.")
            }

        val renderedTree =
            if (printTree || gui) {
                val text = tree.toStringTree(parser)
                if (printTree && emitStdout) {
                    println(text)
                }
                text
            } else {
                null
            }

        val renderedPs =
            psFile?.let { targetPs ->
                val psContent = Trees.getPS(tree, parser.ruleNames)
                Trees.save(tree, parser, targetPs)
                psContent
            }

        return TestRigRunResult(formattedTokens, renderedTree, renderedPs)
    }

    private fun processRegistered(
        registration: GrammarRegistration,
        ruleName: String,
    ): List<TestRigRunResult> {
        val lexer = registration.lexerFactory.create(null)
        val isLexerOnly = ruleName == LEXER_START_RULE_NAME
        val parser =
            if (isLexerOnly) {
                null
            } else {
                registration.parserFactory?.create(null)
                    ?: throw IllegalArgumentException("Grammar '${registration.name}' has no parserFactory for rule '$ruleName'.")
            }
        val ruleInvoker =
            if (isLexerOnly) {
                null
            } else {
                registration.rules[ruleName]
                    ?: if (parser is ParserInterpreter) {
                        null
                    } else {
                        throw IllegalArgumentException("No method for rule $ruleName in registered grammar '${registration.name}'.")
                    }
            }

        return runAcrossInputFiles(lexer, parser, ruleInvoker)
    }

    private fun processInterpreterData(
        lexerData: InterpreterDataReader.InterpreterData,
        parserData: InterpreterDataReader.InterpreterData?,
        ruleName: String,
    ): List<TestRigRunResult> {
        val lexer =
            LexerInterpreter(
                grammarName ?: "Lexer.interp",
                lexerData.vocabulary ?: throw IllegalStateException("Missing lexer vocabulary"),
                lexerData.ruleNames.orEmpty(),
                lexerData.channels ?: listOf("DEFAULT_TOKEN_CHANNEL", "HIDDEN"),
                lexerData.modes ?: listOf("DEFAULT_MODE"),
                lexerData.atn ?: throw IllegalStateException("Missing lexer ATN"),
                CharStreams.fromString(""),
            )
        val parser =
            if (ruleName == LEXER_START_RULE_NAME || parserData == null) {
                null
            } else {
                ParserInterpreter(
                    grammarName ?: "Parser.interp",
                    parserData.vocabulary ?: throw IllegalStateException("Missing parser vocabulary"),
                    parserData.ruleNames.orEmpty(),
                    parserData.atn ?: throw IllegalStateException("Missing parser ATN"),
                    CommonTokenStream(lexer),
                )
            }
        return runAcrossInputFiles(lexer, parser, null)
    }

    private fun processGrammarFile(
        grammarFile: String,
        ruleName: String,
    ): List<TestRigRunResult> {
        val loaded =
            Grammar.load(grammarFile)
                ?: throw IllegalArgumentException("Failed to load grammar file '$grammarFile'.")
        if (!loaded.isLexer && loaded.implicitLexer == null && loaded.isCombined) {
            throw IllegalStateException("Combined grammar '$grammarFile' has no implicit lexer.")
        }
        val lexer: Lexer =
            when {
                loaded is LexerGrammar -> loaded.createLexerInterpreter(CharStreams.fromString(""))
                loaded.isCombined -> loaded.createLexerInterpreter(CharStreams.fromString(""))
                else -> {
                    val siblingLexerPath = grammarFile.replace("Parser.g4", "Lexer.g4")
                    require(siblingLexerPath != grammarFile && SystemFileSystem.exists(Path(siblingLexerPath))) {
                        "Parser grammar '$grammarFile' requires companion '$siblingLexerPath' for TestRig execution."
                    }
                    val lexerGrammar =
                        Grammar.load(siblingLexerPath) as? LexerGrammar
                            ?: throw IllegalStateException("Failed to load companion lexer '$siblingLexerPath'.")
                    lexerGrammar.createLexerInterpreter(CharStreams.fromString(""))
                }
            }
        val parser =
            if (ruleName == LEXER_START_RULE_NAME || loaded.isLexer) {
                null
            } else {
                loaded.createGrammarParserInterpreter(CommonTokenStream(lexer))
            }
        return runAcrossInputFiles(lexer, parser, null)
    }

    private fun runAcrossInputFiles(
        lexer: Lexer,
        parser: Parser?,
        startRule: StartRuleInvoker?,
    ): List<TestRigRunResult> {
        require(mutableInputFiles.isNotEmpty()) {
            "TestRig requires at least one input file path when invoked via process()."
        }
        val results = mutableListOf<TestRigRunResult>()
        for (inputFile in mutableInputFiles) {
            val stream = CharStreams.fromFileName(inputFile, encoding)
            results.add(processInput(lexer, parser, stream, startRule, emitStdout = true))
        }
        return results
    }

    private fun resolveInterpreterData(
        baseName: String,
        ruleName: String,
    ): Pair<InterpreterDataReader.InterpreterData, InterpreterDataReader.InterpreterData?>? {
        val directInterp = if (baseName.endsWith(".interp")) baseName else "$baseName.interp"
        val lexerInterp = "${baseName.removeSuffix(".interp")}Lexer.interp"
        val parserInterp = "${baseName.removeSuffix(".interp")}Parser.interp"

        val fs = SystemFileSystem
        val lexerFile =
            when {
                fs.exists(Path(lexerInterp)) -> lexerInterp
                fs.exists(Path(directInterp)) -> directInterp
                else -> return null
            }
        val lexerData = InterpreterDataReader.parseFile(lexerFile)
        if (ruleName == LEXER_START_RULE_NAME) {
            return lexerData to null
        }
        val parserFile =
            when {
                fs.exists(Path(parserInterp)) -> parserInterp
                lexerFile != directInterp && fs.exists(Path(directInterp)) -> directInterp
                else -> throw IllegalArgumentException("Missing parser .interp file for '$baseName' (expected '$parserInterp' or '$directInterp').")
            }
        return lexerData to InterpreterDataReader.parseFile(parserFile)
    }

    companion object {
        const val LEXER_START_RULE_NAME: String = "tokens"

        private const val USAGE_MESSAGE: String =
            "TestRig GrammarName startRuleName\n" +
                "  [-tokens] [-tree] [-gui] [-ps file.ps] [-encoding encodingname]\n" +
                "  [-trace] [-diagnostics] [-SLL]\n" +
                "  [input-filename(s)]\n" +
                "Use startRuleName='tokens' if GrammarName is a lexer grammar."

        private val registeredGrammars: MutableMap<String, GrammarRegistration> = mutableMapOf()

        internal var grammarFileRunner: ((TestRig, String, String) -> List<TestRigRunResult>)? = null

        fun registerGrammar(registration: GrammarRegistration) {
            registeredGrammars[registration.name] = registration
        }

        fun unregisterGrammar(name: String) {
            registeredGrammars.remove(name)
        }

        fun clearRegisteredGrammars() {
            registeredGrammars.clear()
        }

        fun main(args: List<String>) {
            val rig = TestRig(args)
            if (args.size >= 2) {
                rig.process()
            }
        }
    }
}
