/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.CharStream
import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.RuntimeMetaData
import io.github.kotlinmania.antlr4.atn.ATNSerializer
import io.github.kotlinmania.antlr4.internal.withHostFileSystemOrNull
import io.github.kotlinmania.antlr4.tooling.analysis.AnalysisPipeline
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarRootAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.automata.ATNFactory
import io.github.kotlinmania.antlr4.tooling.automata.LexerATNFactory
import io.github.kotlinmania.antlr4.tooling.automata.ParserATNFactory
import io.github.kotlinmania.antlr4.tooling.codegen.CodeGenPipeline
import io.github.kotlinmania.antlr4.tooling.misc.Graph
import io.github.kotlinmania.antlr4.tooling.misc.LogManager
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.parse.GrammarTreeVisitor
import io.github.kotlinmania.antlr4.tooling.parse.ToolANTLRLexer
import io.github.kotlinmania.antlr4.tooling.parse.ToolANTLRParser
import io.github.kotlinmania.antlr4.tooling.semantics.SemanticPipeline
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.writeString

/**
 * Pure-Kotlin ANTLR 4 grammar compiler & analyzer coordinator (`Tool`), orchestrating `.g4` parsing,
 * imported-grammar merging, semantic validation, left-recursion rewriting, ATN/DFA construction,
 * `.interp` / `.dot` / dependency emission, and code generation.
 */
class Tool(
    val args: List<String> = emptyList(),
) : ToolErrorContext {
    enum class OptionArgType {
        NONE,
        STRING,
    }

    class Option(
        val fieldName: String,
        val name: String,
        val argType: OptionArgType,
        val description: String,
    ) {
        constructor(
            fieldName: String,
            name: String,
            description: String,
        ) : this(fieldName, name, OptionArgType.NONE, description)
    }

    var inputDirectory: String? = null
    var outputDirectory: String? = null
    var libDirectory: String? = null
    var generateATNDot: Boolean = false
    var grammarEncoding: String? = null
    var msgFormat: String = "antlr"
    var launchSTInspector: Boolean = false
    var stInspectorWaitForClose: Boolean = false
    var forceAtn: Boolean = false
    var logEnabled: Boolean = false
    var genListener: Boolean = true
    var genVisitor: Boolean = false
    var genSplitParser: Boolean = false
    var genDependencies: Boolean = false
    var genPackage: String? = null
    private val mutableGrammarOptions: MutableMap<String, String> = LinkedHashMap()
    val grammarOptions: Map<String, String>
        get() = mutableGrammarOptions
    var warningsAreErrors: Boolean = false
    override var longMessages: Boolean = false
    var exactOutputDir: Boolean = false
    var writeFilesToDisk: Boolean = true

    var haveOutputDir: Boolean = false
        private set
    var returnDontExit: Boolean = false
    var lastExitCode: Int? = null
        private set

    private val mutableGrammarFiles: MutableList<String> = ArrayList()
    val grammarFiles: List<String>
        get() = mutableGrammarFiles

    override val errMgr: ErrorManager = ErrorManager(this)
    val logMgr: LogManager = LogManager()

    private val mutableListeners: MutableList<ANTLRToolListener> = ArrayList()
    val listeners: List<ANTLRToolListener>
        get() = mutableListeners.toList()

    val defaultListener: DefaultToolListener = DefaultToolListener(errMgr)

    private val importedGrammarsCache: MutableMap<String, Grammar> = HashMap()
    private val virtualGrammars: MutableMap<String, String> = LinkedHashMap()
    private val virtualTokenVocabs: MutableMap<String, String> = LinkedHashMap()
    private val mutableVirtualOutputs: MutableMap<String, String> = LinkedHashMap()
    val virtualOutputs: Map<String, String>
        get() = mutableVirtualOutputs

    init {
        errMgr.setFormat("antlr")
        handleArgs()
        errMgr.setFormat(msgFormat)
    }

    fun registerVirtualGrammar(
        fileName: String,
        content: String,
    ) {
        virtualGrammars[fileName] = content
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        if (base != fileName) {
            virtualGrammars[base] = content
        }
    }

    fun registerVirtualTokenVocab(
        vocabName: String,
        content: String,
    ) {
        val normalized = vocabName.removeSuffix(".tokens")
        virtualTokenVocabs[normalized] = content
    }

    fun getVirtualTokenVocab(vocabName: String): String? =
        virtualTokenVocabs[vocabName.removeSuffix(".tokens")]

    private fun handleArgs() {
        var i = 0
        while (i < args.size) {
            val arg = args[i++]
            if (arg.startsWith("-D")) {
                handleOptionSetArg(arg)
                continue
            }
            if (arg.isEmpty() || arg[0] != '-') {
                if (!mutableGrammarFiles.contains(arg)) {
                    mutableGrammarFiles.add(arg)
                }
                continue
            }
            val matched = optionDefs.firstOrNull { it.name == arg }
            if (matched == null) {
                errMgr.toolError(ErrorType.INVALID_CMDLINE_ARG, arg)
                continue
            }
            var argValue: String? = null
            if (matched.argType == OptionArgType.STRING) {
                if (i >= args.size) {
                    errMgr.toolError(ErrorType.INVALID_CMDLINE_ARG, arg)
                    continue
                }
                argValue = args[i++]
            }
            applyOption(matched.fieldName, arg, argValue)
        }

        val currentOut = outputDirectory
        if (currentOut != null) {
            val trimmed =
                if (currentOut.length > 1 && (currentOut.endsWith("/") || currentOut.endsWith("\\"))) {
                    currentOut.substring(0, currentOut.length - 1)
                } else {
                    currentOut
                }
            outputDirectory = trimmed
            haveOutputDir = true
            val isRegularFile =
                withHostFileSystemOrNull {
                    val p = Path(trimmed)
                    SystemFileSystem.exists(p) && SystemFileSystem.metadataOrNull(p)?.isRegularFile == true
                } ?: false
            if (isRegularFile) {
                errMgr.toolError(ErrorType.OUTPUT_DIR_IS_FILE, trimmed)
                outputDirectory = "."
            }
        } else {
            outputDirectory = "."
        }

        val currentLib = libDirectory
        if (currentLib != null) {
            val trimmed =
                if (currentLib.length > 1 && (currentLib.endsWith("/") || currentLib.endsWith("\\"))) {
                    currentLib.substring(0, currentLib.length - 1)
                } else {
                    currentLib
                }
            libDirectory = trimmed
            val existsOnHost =
                withHostFileSystemOrNull {
                    SystemFileSystem.exists(Path(trimmed))
                }
            if (existsOnHost == false && virtualGrammars.isEmpty() && virtualTokenVocabs.isEmpty()) {
                errMgr.toolError(ErrorType.DIR_NOT_FOUND, trimmed)
                libDirectory = "."
            }
        } else {
            libDirectory = "."
        }

        if (launchSTInspector) {
            returnDontExit = true
        }
    }

    private fun applyOption(
        fieldName: String,
        flag: String,
        argValue: String?,
    ) {
        val boolVal = !flag.startsWith("-no-")
        when (fieldName) {
            "outputDirectory" -> outputDirectory = argValue
            "libDirectory" -> libDirectory = argValue
            "generateATNDot" -> generateATNDot = boolVal
            "grammarEncoding" -> grammarEncoding = argValue
            "msgFormat" -> if (argValue != null) msgFormat = argValue
            "longMessages" -> longMessages = boolVal
            "genListener" -> genListener = boolVal
            "genVisitor" -> genVisitor = boolVal
            "genPackage" -> genPackage = argValue
            "genDependencies" -> genDependencies = boolVal
            "genSplitParser" -> genSplitParser = boolVal
            "warningsAreErrors" -> warningsAreErrors = boolVal
            "launchSTInspector" -> launchSTInspector = boolVal
            "stInspectorWaitForClose" -> stInspectorWaitForClose = boolVal
            "forceAtn" -> forceAtn = boolVal
            "logEnabled" -> logEnabled = boolVal
            "exactOutputDir" -> exactOutputDir = boolVal
        }
    }

    private fun handleOptionSetArg(arg: String) {
        val eq = arg.indexOf('=')
        if (eq > 0 && arg.length > 3) {
            val option = arg.substring(2, eq)
            val value = arg.substring(eq + 1)
            if (value.isEmpty()) {
                errMgr.toolError(ErrorType.BAD_OPTION_SET_SYNTAX, arg)
                return
            }
            if (Grammar.parserOptions.contains(option) || Grammar.lexerOptions.contains(option)) {
                mutableGrammarOptions[option] = value
            } else {
                errMgr.grammarError(ErrorType.ILLEGAL_OPTION, null, null, option)
            }
        } else {
            errMgr.toolError(ErrorType.BAD_OPTION_SET_SYNTAX, arg)
        }
    }

    fun setGrammarOption(
        key: String,
        value: String,
    ) {
        mutableGrammarOptions[key] = value
    }

    fun processGrammarsOnCommandLine() {
        val sortedGrammars = sortGrammarByTokenVocab(mutableGrammarFiles)
        for (t in sortedGrammars) {
            val g = createGrammar(t)
            g.fileName = t.fileName
            if (genDependencies) {
                val dep = BuildDependencyGenerator(this, g)
                println(dep.renderDependencies())
            } else if (errMgr.numErrors == 0) {
                process(g, true)
            }
        }
    }

    fun process(
        g: Grammar,
        gencode: Boolean,
    ) {
        g.loadImportedGrammars()

        val transform = GrammarTransformPipeline(g, this)
        transform.process()

        val rootAst = g.ast
        if (rootAst != null && rootAst.grammarType == ANTLRParserTypes.COMBINED && !rootAst.hasErrors) {
            val lexerAST = transform.extractImplicitLexer(g)
            if (lexerAST != null) {
                if (mutableGrammarOptions.isNotEmpty()) {
                    lexerAST.cmdLineOptions = LinkedHashMap(mutableGrammarOptions)
                }
                val lexerg = LexerGrammar(this, lexerAST)
                lexerg.fileName = g.fileName
                lexerg.originalGrammar = g
                g.implicitLexer = lexerg
                lexerg.implicitLexerOwner = g
                processNonCombinedGrammar(lexerg, gencode)
            }
        }
        val impLexer = g.implicitLexer
        if (impLexer != null) {
            g.importVocab(impLexer)
        }
        processNonCombinedGrammar(g, gencode)
    }

    fun processNonCombinedGrammar(
        g: Grammar,
        gencode: Boolean,
    ) {
        val rootAst = g.ast
        if (rootAst == null || rootAst.hasErrors) return

        val ruleFail = checkForRuleIssues(g)
        if (ruleFail) return

        val prevErrors = errMgr.numErrors
        val sem = SemanticPipeline(g)
        sem.process()

        if (errMgr.numErrors > prevErrors) return

        val targetLang = g.language ?: "Kotlin"
        if (!isSupportedTargetLanguage(targetLang)) {
            errMgr.toolError(ErrorType.CANNOT_CREATE_TARGET_GENERATOR, targetLang)
            return
        }

        val factory: ATNFactory =
            if (g.isLexer) {
                LexerATNFactory(g as LexerGrammar)
            } else {
                ParserATNFactory(g)
            }
        g.atn = factory.createATN()

        if (generateATNDot) {
            generateATNs(g)
        }

        if (gencode && g.tool.numErrors == 0) {
            val interpFile = generateInterpreterData(g)
            val interpName = "${g.name ?: "Grammar"}.interp"
            writeOutputFile(g, interpName, interpFile)
        }

        val anal = AnalysisPipeline(g)
        anal.process()

        if (g.tool.numErrors > prevErrors) return

        if (gencode) {
            val gen = CodeGenPipeline(g)
            gen.process()
        }
    }

    fun checkForRuleIssues(g: Grammar): Boolean {
        val rootAst = g.ast ?: return false
        val rulesNode = rootAst.getFirstChildWithType(ANTLRParserTypes.RULES) ?: return false
        val rules = ArrayList<GrammarAST>(rulesNode.getAllChildrenWithType(ANTLRParserTypes.RULE))
        for (mode in rootAst.getAllChildrenWithType(ANTLRParserTypes.MODE)) {
            rules.addAll(mode.getAllChildrenWithType(ANTLRParserTypes.RULE))
        }

        var redefinition = false
        val ruleToAST = LinkedHashMap<String, RuleAST>()
        for (r in rules) {
            val ruleAST = r as? RuleAST ?: continue
            val idNode = ruleAST.getChild(0) ?: continue
            val ruleName = idNode.text
            val prev = ruleToAST[ruleName]
            if (prev != null) {
                val prevChild = prev.getChild(0)
                g.tool.errMgr.grammarError(
                    ErrorType.RULE_REDEFINITION,
                    g.fileName,
                    idNode.token,
                    ruleName,
                    prevChild?.token?.line ?: 0,
                )
                redefinition = true
                continue
            }
            ruleToAST[ruleName] = ruleAST
        }

        var badref = false
        val undefChecker =
            object : GrammarTreeVisitor() {
                override fun getErrorManager(): ErrorManager = errMgr

                override fun tokenRef(ref: TerminalAST) {
                    if (ref.text == "EOF") {
                        return
                    }
                    if (g.isLexer) {
                        ruleRef(ref, null)
                    }
                }

                override fun ruleRef(
                    ref: GrammarAST,
                    arg: ActionAST?,
                ) {
                    val ruleAST = ruleToAST[ref.text]
                    val refFileName = ref.token?.inputStream?.sourceName ?: g.fileName
                    val enclosingRule = currentRuleName
                    if (
                        !enclosingRule.isNullOrEmpty() &&
                        enclosingRule[0].isUpperCase() &&
                        ref.text.isNotEmpty() &&
                        ref.text[0].isLowerCase()
                    ) {
                        badref = true
                        errMgr.grammarError(
                            ErrorType.PARSER_RULE_REF_IN_LEXER_RULE,
                            refFileName,
                            ref.token,
                            ref.text,
                            enclosingRule,
                        )
                    } else if (ruleAST == null) {
                        badref = true
                        errMgr.grammarError(
                            ErrorType.UNDEFINED_RULE_REF,
                            refFileName,
                            ref.token,
                            ref.text,
                        )
                    }
                }
            }
        undefChecker.visitGrammar(rootAst)

        return redefinition || badref
    }

    fun sortGrammarByTokenVocab(fileNames: List<String>): List<GrammarRootAST> {
        val graph = Graph<String>()
        val roots = ArrayList<GrammarRootAST>()
        for (fileName in fileNames) {
            val t = parseGrammar(fileName)
            if (t == null) continue
            if (t.hasErrors) continue
            roots.add(t)
            t.fileName = fileName
            val grammarName = t.getChild(0)?.text ?: continue

            val tokenVocabNode = findOptionValueAST(t, "tokenVocab")
            if (tokenVocabNode != null) {
                var vocabName = tokenVocabNode.text
                if (vocabName.length >= 2 && vocabName.startsWith("'") && vocabName.endsWith("'")) {
                    vocabName = vocabName.substring(1, vocabName.length - 1)
                }
                val lastSlash = vocabName.lastIndexOf('/')
                if (lastSlash >= 0) {
                    vocabName = vocabName.substring(lastSlash + 1)
                }
                graph.addEdge(grammarName, vocabName)
            }
            graph.addEdge(grammarName, grammarName)
        }

        val sortedGrammarNames = graph.sort()
        val sortedRoots = ArrayList<GrammarRootAST>()
        for (grammarName in sortedGrammarNames) {
            for (root in roots) {
                if (root.grammarName == grammarName) {
                    sortedRoots.add(root)
                    break
                }
            }
        }
        return sortedRoots
    }

    fun createGrammar(ast: GrammarRootAST): Grammar {
        val g =
            if (ast.grammarType == ANTLRParserTypes.LEXER) {
                LexerGrammar(this, ast)
            } else {
                Grammar(this, ast)
            }
        GrammarTransformPipeline.setGrammarPtr(g, ast)
        return g
    }

    fun parseGrammar(fileName: String): GrammarRootAST? {
        val virtual = virtualGrammars[fileName] ?: virtualGrammars[fileName.substringAfterLast('/')]
        if (virtual != null) {
            return parse(fileName, CharStreams.fromString(virtual, fileName))
        }
        val resolvedPath = resolveInputGrammarPath(fileName)
        val exists =
            withHostFileSystemOrNull {
                SystemFileSystem.exists(Path(resolvedPath))
            } ?: false
        if (!exists) {
            errMgr.toolError(ErrorType.CANNOT_OPEN_FILE, fileName)
            return null
        }
        val stream = CharStreams.fromFileName(resolvedPath, grammarEncoding)
        return parse(fileName, stream)
    }

    private fun resolveInputGrammarPath(fileName: String): String {
        val inDir = inputDirectory
        if (inDir.isNullOrEmpty() || isAbsolutePath(fileName)) {
            return fileName
        }
        return joinPath(inDir, fileName)
    }

    fun loadGrammar(fileName: String): Grammar? {
        val grammarRootAST = parseGrammar(fileName) ?: return null
        val g = createGrammar(grammarRootAST)
        g.fileName = fileName
        process(g, false)
        return g
    }

    fun loadImportedGrammar(
        g: Grammar,
        nameNode: GrammarAST,
    ): Grammar? {
        val name = nameNode.text
        var imported = importedGrammarsCache[name]
        if (imported == null) {
            g.tool.log("grammar", "load $name from ${g.fileName}")
            for (extension in ALL_GRAMMAR_EXTENSIONS) {
                val candidateName = "$name$extension"
                val virtualContent = virtualGrammars[candidateName]
                if (virtualContent != null) {
                    val root = parse(candidateName, CharStreams.fromString(virtualContent, candidateName)) ?: return null
                    imported = createGrammar(root)
                    imported.fileName = candidateName
                    val gName = root.grammarName
                    if (gName != null) {
                        importedGrammarsCache[gName] = imported
                    }
                    return imported
                }
            }

            var importedPath: String? = null
            for (extension in ALL_GRAMMAR_EXTENSIONS) {
                importedPath = getImportedGrammarFile(g, "$name$extension")
                if (importedPath != null) {
                    break
                }
            }
            if (importedPath == null) {
                errMgr.grammarError(ErrorType.CANNOT_FIND_IMPORTED_GRAMMAR, g.fileName, nameNode.token, name)
                return null
            }

            val input = CharStreams.fromFileName(importedPath, grammarEncoding)
            val root = parse(importedPath, input) ?: return null
            imported = createGrammar(root)
            imported.fileName = importedPath
            val gName = root.grammarName
            if (gName != null) {
                importedGrammarsCache[gName] = imported
            }
        }
        return imported
    }

    fun parseGrammarFromString(grammar: String): GrammarRootAST? =
        parse(Grammar.GRAMMAR_FROM_STRING_NAME, CharStreams.fromString(grammar, Grammar.GRAMMAR_FROM_STRING_NAME))

    fun parse(
        fileName: String?,
        input: CharStream,
    ): GrammarRootAST? {
        val lexer = ToolANTLRLexer(input, errMgr)
        val tokens = CommonTokenStream(lexer)
        val p = ToolANTLRParser(tokens, errMgr)
        val spec = p.grammarSpec()
        val root = spec.tree
        root.fileName = fileName
        root.hasErrors = lexer.syntaxErrors > 0 || p.numberOfSyntaxErrors > 0
        if (mutableGrammarOptions.isNotEmpty()) {
            root.cmdLineOptions = LinkedHashMap(mutableGrammarOptions)
        }
        return root
    }

    fun generateATNs(g: Grammar) {
        val dotGenerator = DOTGenerator(g)
        val grammars = ArrayList<Grammar>()
        grammars.add(g)
        val imported = g.allImportedGrammars
        if (imported != null) {
            grammars.addAll(imported)
        }
        val builtAtn = g.atn ?: return
        for (ig in grammars) {
            for (r in ig.rules.values) {
                val startState = builtAtn.ruleToStartState.getOrNull(r.index) ?: continue
                val dot = dotGenerator.getDOT(startState, g.isLexer)
                if (dot != null) {
                    writeDOTFile(g, r, dot)
                }
            }
        }
    }

    fun writeOutputFile(
        g: Grammar,
        fileName: String,
        content: String,
    ) {
        val outDir = getOutputDirectory(g.fileName)
        val fullPath = if (outDir == "." || outDir.isEmpty()) fileName else joinPath(outDir, fileName)
        mutableVirtualOutputs[fileName] = content
        mutableVirtualOutputs[fullPath] = content

        if (writeFilesToDisk && outputDirectory != null) {
            withHostFileSystemOrNull {
                val dirPath = Path(outDir)
                if (outDir != "." && outDir.isNotEmpty() && !SystemFileSystem.exists(dirPath)) {
                    SystemFileSystem.createDirectories(dirPath, mustCreate = false)
                }
                SystemFileSystem.sink(Path(fullPath)).buffered().use { sink ->
                    sink.writeString(content)
                }
            }
        }
    }

    fun getImportedGrammarFile(
        g: Grammar,
        fileName: String,
    ): String? =
        withHostFileSystemOrNull {
            val candidates = ArrayList<String>()
            val inDir = inputDirectory
            candidates.add(if (inDir.isNullOrEmpty()) fileName else joinPath(inDir, fileName))
            val gFile = g.fileName
            if (gFile != null) {
                val parent = parentDirectoryOf(gFile)
                if (parent != null) {
                    candidates.add(joinPath(parent, fileName))
                }
            }
            val libDir = libDirectory
            if (!libDir.isNullOrEmpty()) {
                candidates.add(joinPath(libDir, fileName))
            }
            candidates.firstOrNull { SystemFileSystem.exists(Path(it)) }
        }

    fun getOutputDirectory(fileNameWithPath: String?): String {
        if (exactOutputDir && fileNameWithPath != null) {
            return exactGetOutputDirectory(fileNameWithPath)
        }
        val fileDirectory: String =
            if (fileNameWithPath == null || (!fileNameWithPath.contains('/') && !fileNameWithPath.contains('\\'))) {
                "."
            } else {
                val sep = maxOf(fileNameWithPath.lastIndexOf('/'), fileNameWithPath.lastIndexOf('\\'))
                fileNameWithPath.substring(0, sep)
            }

        val configuredOut = outputDirectory ?: "."
        return if (haveOutputDir) {
            if (isAbsolutePath(fileDirectory) || fileDirectory.startsWith("~") || fileDirectory == ".") {
                configuredOut
            } else {
                joinPath(configuredOut, fileDirectory)
            }
        } else {
            fileDirectory
        }
    }

    private fun exactGetOutputDirectory(fileNameWithPath: String): String {
        val fileDirectory =
            if (!fileNameWithPath.contains('/') && !fileNameWithPath.contains('\\')) {
                "."
            } else {
                val sep = maxOf(fileNameWithPath.lastIndexOf('/'), fileNameWithPath.lastIndexOf('\\'))
                fileNameWithPath.substring(0, sep)
            }
        return if (haveOutputDir) {
            outputDirectory ?: "."
        } else {
            fileDirectory
        }
    }

    protected fun writeDOTFile(
        g: Grammar,
        r: Rule,
        dot: String,
    ) {
        writeDOTFile(g, "${r.g.name}.${r.name}", dot)
    }

    protected fun writeDOTFile(
        g: Grammar,
        name: String,
        dot: String,
    ) {
        writeOutputFile(g, "$name.dot", dot)
    }

    fun help() {
        info("ANTLR Parser Generator  Version $VERSION")
        for (o in optionDefs) {
            val optName = o.name + if (o.argType != OptionArgType.NONE) " ___" else ""
            val padded = optName.padEnd(19, ' ')
            info(" $padded ${o.description}")
        }
    }

    fun log(
        component: String?,
        msg: String,
    ) {
        logMgr.log(component, msg)
    }

    fun log(msg: String) {
        log(null, msg)
    }

    val numErrors: Int
        get() = errMgr.numErrors

    fun addListener(tl: ANTLRToolListener?) {
        if (tl != null) {
            mutableListeners.add(tl)
        }
    }

    fun removeListener(tl: ANTLRToolListener?) {
        if (tl != null) {
            mutableListeners.remove(tl)
        }
    }

    fun removeListeners() {
        mutableListeners.clear()
    }

    override fun info(msg: String?) {
        if (mutableListeners.isEmpty()) {
            defaultListener.info(msg)
            return
        }
        for (l in mutableListeners) {
            l.info(msg)
        }
    }

    override fun error(msg: ANTLRMessage) {
        if (mutableListeners.isEmpty()) {
            defaultListener.error(msg)
            return
        }
        for (l in mutableListeners) {
            l.error(msg)
        }
    }

    override fun warning(msg: ANTLRMessage) {
        if (mutableListeners.isEmpty()) {
            defaultListener.warning(msg)
        } else {
            for (l in mutableListeners) {
                l.warning(msg)
            }
        }

        if (warningsAreErrors) {
            errMgr.emit(ErrorType.WARNING_TREATED_AS_ERROR, ANTLRMessage(ErrorType.WARNING_TREATED_AS_ERROR))
        }
    }

    fun version() {
        info("ANTLR Parser Generator  Version $VERSION")
    }

    fun exit(code: Int) {
        lastExitCode = code
    }

    fun panic(): Nothing =
        throw IllegalStateException("ANTLR panic")

    private fun isAbsolutePath(path: String): Boolean =
        path.startsWith("/") || (path.length >= 2 && path[1] == ':')

    private fun parentDirectoryOf(path: String): String? {
        val sep = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        return if (sep > 0) path.substring(0, sep) else null
    }

    private fun joinPath(
        dir: String,
        name: String,
    ): String =
        if (dir.endsWith("/") || dir.endsWith("\\")) "$dir$name" else "$dir/$name"

    companion object {
        val VERSION: String = RuntimeMetaData.VERSION

        const val GRAMMAR_EXTENSION: String = ".g4"
        const val LEGACY_GRAMMAR_EXTENSION: String = ".g"

        val ALL_GRAMMAR_EXTENSIONS: List<String> = listOf(GRAMMAR_EXTENSION, LEGACY_GRAMMAR_EXTENSION)

        val SUPPORTED_LANGUAGES: Set<String> =
            setOf(
                "Kotlin",
                "Java",
                "CSharp",
                "Python2",
                "Python3",
                "JavaScript",
                "TypeScript",
                "Cpp",
                "Go",
                "Swift",
                "PHP",
                "Dart",
            )

        fun isSupportedTargetLanguage(language: String): Boolean =
            SUPPORTED_LANGUAGES.contains(language)

        val optionDefs: List<Option> =
            listOf(
                Option("outputDirectory", "-o", OptionArgType.STRING, "specify output directory where all output is generated"),
                Option("libDirectory", "-lib", OptionArgType.STRING, "specify location of grammars, tokens files"),
                Option("generateATNDot", "-atn", "generate rule augmented transition network diagrams"),
                Option("grammarEncoding", "-encoding", OptionArgType.STRING, "specify grammar file encoding; e.g., euc-jp"),
                Option("msgFormat", "-message-format", OptionArgType.STRING, "specify output style for messages in antlr, gnu, vs2005"),
                Option("longMessages", "-long-messages", "show exception details when available for errors and warnings"),
                Option("genListener", "-listener", "generate parse tree listener (default)"),
                Option("genListener", "-no-listener", "don't generate parse tree listener"),
                Option("genVisitor", "-visitor", "generate parse tree visitor"),
                Option("genVisitor", "-no-visitor", "don't generate parse tree visitor (default)"),
                Option("genPackage", "-package", OptionArgType.STRING, "specify a package/namespace for the generated code"),
                Option("genDependencies", "-depend", "generate file dependencies"),
                Option("genSplitParser", "-split-parser", "generate separate files for parser serialized DFA and context classes"),
                Option("genSplitParser", "-no-split-parser", "generate parser serialized DFA and context classes locally (default)"),
                Option("", "-D<option>=value", "set/override a grammar-level option"),
                Option("warningsAreErrors", "-Werror", "treat warnings as errors"),
                Option("launchSTInspector", "-XdbgST", "launch StringTemplate visualizer on generated code"),
                Option("stInspectorWaitForClose", "-XdbgSTWait", "wait for STViz to close before continuing"),
                Option("forceAtn", "-Xforce-atn", "use the ATN simulator for all predictions"),
                Option("logEnabled", "-Xlog", "dump lots of logging info to antlr-timestamp.log"),
                Option("exactOutputDir", "-Xexact-output-dir", "all output goes into -o dir regardless of paths/package"),
            )

        fun main(args: List<String>) {
            val antlr = Tool(args)
            if (args.isEmpty()) {
                antlr.help()
                antlr.exit(0)
                return
            }
            try {
                antlr.processGrammarsOnCommandLine()
            } finally {
                if (antlr.logEnabled) {
                    val logName = antlr.logMgr.save()
                    println("wrote $logName")
                }
            }
            if (antlr.returnDontExit) return
            if (antlr.errMgr.numErrors > 0) {
                antlr.exit(1)
            } else {
                antlr.exit(0)
            }
        }

        fun findOptionValueAST(
            root: GrammarRootAST,
            option: String,
        ): GrammarAST? {
            val options = root.getFirstChildWithType(ANTLRParserTypes.OPTIONS)
            if (options != null && options.childCount > 0) {
                for (c in options.children) {
                    if (c.type == ANTLRParserTypes.ASSIGN && c.getChild(0)?.text == option) {
                        return c.getChild(1)
                    }
                }
            }
            return null
        }

        fun generateInterpreterData(g: Grammar): String {
            val content = StringBuilder()

            content.append("token literal names:\n")
            for (name in g.tokenLiteralNames) {
                content.append(name.toString()).append('\n')
            }
            content.append('\n')

            content.append("token symbolic names:\n")
            for (name in g.tokenSymbolicNames) {
                content.append(name.toString()).append('\n')
            }
            content.append('\n')

            content.append("rule names:\n")
            for (name in g.ruleNames) {
                content.append(name).append('\n')
            }
            content.append('\n')

            if (g.isLexer) {
                content.append("channel names:\n")
                content.append("DEFAULT_TOKEN_CHANNEL\n")
                content.append("HIDDEN\n")
                for (channel in g.channelValueToNameList) {
                    content.append(channel.toString()).append('\n')
                }
                content.append('\n')

                content.append("mode names:\n")
                for (mode in (g as LexerGrammar).modes.keys) {
                    content.append(mode).append('\n')
                }
            }
            content.append('\n')

            val builtAtn = requireNotNull(g.atn) { "Grammar ATN has not been constructed for ${g.name}" }
            val serializedATN = ATNSerializer.getSerialized(builtAtn)
            content.append("atn:\n")
            content.append(serializedATN.toString())

            return content.toString()
        }
    }
}
