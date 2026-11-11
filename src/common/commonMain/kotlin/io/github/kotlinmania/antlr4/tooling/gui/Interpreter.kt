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
import io.github.kotlinmania.antlr4.atn.DecisionInfo
import io.github.kotlinmania.antlr4.atn.ParseInfo
import io.github.kotlinmania.antlr4.tooling.tool.ANTLRToolListener
import io.github.kotlinmania.antlr4.tooling.tool.DefaultToolListener
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.GrammarParserInterpreter
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.writeString

/**
 * Pure-Kotlin ANTLR 4 grammar interpreter (`Interpreter`), loading combined or split `.g4`
 * grammars, lexing/parsing input via [Grammar.createLexerInterpreter] and
 * [Grammar.createGrammarParserInterpreter], printing tokens and LISP parse trees, and
 * exporting decision-level profiling statistics as CSV.
 */
class Interpreter(
    args: List<String>,
) {
    private class IgnoreTokenVocabGrammar(
        fileName: String,
        grammarText: String,
        tokenVocabSource: Grammar?,
        listener: ANTLRToolListener?,
    ) : Grammar(fileName, grammarText, tokenVocabSource, listener) {
        override fun doImportTokensFromTokensFile() {
            // Split grammars passed directly to Interpreter supply LexerGrammar explicitly.
        }
    }

    var grammarFileName: String? = null
        private set
    var parserGrammarFileName: String? = null
        private set
    var lexerGrammarFileName: String? = null
        private set
    var startRuleName: String? = null
        private set
    var printTree: Boolean = false
        private set
    var gui: Boolean = false
        private set
    var trace: Boolean = false
        private set
    var encoding: String? = null
        private set
    var showTokens: Boolean = false
        private set
    var profileFileName: String? = null
        private set
    var inputFileName: String? = null
        private set

    var lastRenderedTree: String? = null
        private set
    var lastParser: GrammarParserInterpreter? = null
        private set
    private val mutableLastTokens: MutableList<String> = ArrayList()
    val lastTokens: List<String>
        get() = mutableLastTokens

    init {
        if (args.size < 2) {
            println(USAGE_MESSAGE)
        } else {
            var i = 0
            grammarFileName = args[i]
            i++
            if (args[i].endsWith(".g4")) {
                var pFile = grammarFileName
                var lFile: String? = args[i]
                i++
                grammarFileName = null

                if (pFile != null && pFile.lowercase().endsWith("lexer.g4")) {
                    val save = pFile
                    pFile = lFile
                    lFile = save
                }
                parserGrammarFileName = pFile
                lexerGrammarFileName = lFile
            }
            if (i < args.size) {
                startRuleName = args[i]
                i++
            }
            while (i < args.size) {
                val arg = args[i]
                i++
                if (arg.isEmpty() || arg[0] != '-') {
                    inputFileName = arg
                } else if (arg == "-tree") {
                    printTree = true
                } else if (arg == "-gui") {
                    gui = true
                } else if (arg == "-tokens") {
                    showTokens = true
                } else if (arg == "-trace") {
                    trace = true
                } else if (arg == "-profile") {
                    if (i >= args.size || args[i].startsWith("-")) {
                        println("missing CSV filename on -profile (ignoring -profile)")
                        break
                    }
                    val csvFile = args[i]
                    profileFileName = csvFile
                    if (!csvFile.endsWith(".csv")) {
                        println("warning: missing '.csv' suffix on -profile filename: $csvFile")
                    }
                    i++
                } else if (arg == "-encoding") {
                    if (i >= args.size) {
                        println("missing encoding on -encoding")
                        break
                    }
                    encoding = args[i]
                    i++
                }
            }
        }
    }

    fun interp(): ParseInfo? {
        val targetInput = inputFileName ?: return null
        if (!SystemFileSystem.exists(Path(targetInput))) {
            println("Can't find input file $targetInput")
            return null
        }
        val charStream = CharStreams.fromFileName(targetInput, encoding)
        return interp(charStream)
    }

    fun interp(charStream: CharStream): ParseInfo? {
        val combinedFile = grammarFileName
        val parserFile = parserGrammarFileName
        val lexerFile = lexerGrammarFileName
        if (combinedFile == null && (parserFile == null || lexerFile == null)) {
            return null
        }

        val tool = Tool()
        val listener = DefaultToolListener(tool.errMgr)
        val g: Grammar
        var lg: LexerGrammar? = null

        if (combinedFile != null) {
            val grammarContent = CharStreams.fromFileName(combinedFile, encoding).toString()
            g = IgnoreTokenVocabGrammar(combinedFile, grammarContent, null, listener)
        } else {
            val nonNullLexerFile = lexerFile ?: return null
            val nonNullParserFile = parserFile ?: return null
            val lexerGrammarContent = CharStreams.fromFileName(nonNullLexerFile, encoding).toString()
            lg = LexerGrammar(nonNullLexerFile, lexerGrammarContent, listener)
            val parserGrammarContent = CharStreams.fromFileName(nonNullParserFile, encoding).toString()
            g = IgnoreTokenVocabGrammar(nonNullParserFile, parserGrammarContent, lg, listener)
        }

        return interpLoadedGrammar(g, lg, charStream)
    }

    fun interpGrammarText(
        grammarText: String,
        lexerGrammarText: String? = null,
        charStream: CharStream,
        enableProfile: Boolean = false,
    ): ParseInfo? {
        val tool = Tool()
        val listener = DefaultToolListener(tool.errMgr)
        val effectiveLexerFile = lexerGrammarFileName ?: Grammar.GRAMMAR_FROM_STRING_NAME
        val effectiveParserFile = grammarFileName ?: parserGrammarFileName ?: Grammar.GRAMMAR_FROM_STRING_NAME
        val lg = lexerGrammarText?.let { LexerGrammar(effectiveLexerFile, it, listener) }
        val g = IgnoreTokenVocabGrammar(effectiveParserFile, grammarText, lg, listener)
        return interpLoadedGrammar(g, lg, charStream, enableProfile)
    }

    private fun interpLoadedGrammar(
        g: Grammar,
        lg: LexerGrammar?,
        charStream: CharStream,
        enableProfile: Boolean = false,
    ): ParseInfo? {
        val lexEngine =
            if (lg != null) {
                lg.createLexerInterpreter(charStream)
            } else {
                g.createLexerInterpreter(charStream)
            }

        val tokens = CommonTokenStream(lexEngine)
        tokens.fill()

        mutableLastTokens.clear()
        if (showTokens) {
            for (tok in tokens.allTokens()) {
                val rendered =
                    if (tok is CommonToken) {
                        tok.toString(lexEngine)
                    } else {
                        tok.toString()
                    }
                mutableLastTokens.add(rendered)
                println(rendered)
            }
        }

        val parser = g.createGrammarParserInterpreter(tokens)
        lastParser = parser
        if (profileFileName != null || enableProfile) {
            parser.setProfile(true)
        }
        parser.setTrace(trace)

        val targetRule = startRuleName
        val r = if (targetRule != null) g.orderedRules[targetRule] else null
        if (r == null) {
            println("No such start rule: $targetRule")
            return null
        }
        val t = parser.parse(r.index)
        val parseInfo = parser.parseInfo

        val treeStr = t?.toStringTree(parser).orEmpty()
        lastRenderedTree = treeStr
        if (printTree || gui) {
            println(treeStr)
        }
        if (profileFileName != null && parseInfo != null) {
            dumpProfilerCSV(parser, parseInfo)
        }
        return parseInfo
    }

    fun formatProfilerCSV(
        parser: GrammarParserInterpreter,
        parseInfo: ParseInfo,
    ): String {
        val ruleNamesList = parser.ruleNames.orEmpty()
        val decisionCount = parser.atn.decisionToState.size
        val ruleNamesByDecision =
            List(decisionCount) { i ->
                val ruleIndex = parser.atn.getDecisionState(i)?.ruleIndex ?: -1
                ruleNamesList.getOrNull(ruleIndex) ?: "null"
            }

        val decisionInfo = parseInfo.decisionInfo
        return buildString {
            append(profilerColumnNames.joinToString(",")).append('\n')
            for (decision in decisionInfo.indices) {
                for (col in profilerColumnNames.indices) {
                    if (col > 0) append(',')
                    append(getValue(decisionInfo[decision], ruleNamesByDecision, decision, col))
                }
                append('\n')
            }
        }
    }

    private fun dumpProfilerCSV(
        parser: GrammarParserInterpreter,
        parseInfo: ParseInfo,
    ) {
        val targetProfile = profileFileName ?: return
        val csvText = formatProfilerCSV(parser, parseInfo)
        val sink = SystemFileSystem.sink(Path(targetProfile)).buffered()
        try {
            sink.writeString(csvText)
            sink.flush()
        } finally {
            sink.close()
        }
    }

    companion object {
        private const val USAGE_MESSAGE: String =
            "Interpreter [X.g4|XParser.g4 XLexer.g4] startRuleName\n" +
                "  [-tokens] [-tree] [-gui] [-encoding encodingname]\n" +
                "  [-trace] [-profile filename.csv] [input-filename(s)]"

        val profilerColumnNames: List<String> =
            listOf(
                "Rule",
                "Invocations",
                "Time (ms)",
                "Total k",
                "Max k",
                "Ambiguities",
                "DFA cache miss",
            )

        fun getValue(
            decisionInfo: DecisionInfo,
            ruleNamesByDecision: List<String>,
            decision: Int,
            col: Int,
        ): String =
            when (col) {
                0 -> "${ruleNamesByDecision.getOrNull(decision)}:$decision"
                1 -> decisionInfo.invocations.toString()
                2 -> (decisionInfo.timeInPrediction / (1000.0 * 1000.0)).toString()
                3 -> (decisionInfo.LL_TotalLook + decisionInfo.SLL_TotalLook).toString()
                4 -> maxOf(decisionInfo.LL_MaxLook, decisionInfo.SLL_MaxLook).toString()
                5 -> decisionInfo.ambiguities.size.toString()
                6 -> (decisionInfo.SLL_ATNTransitions + decisionInfo.LL_ATNTransitions).toString()
                else -> "n/a"
            }

        fun main(args: List<String>) {
            val interpreter = Interpreter(args)
            interpreter.interp()
        }
    }
}
