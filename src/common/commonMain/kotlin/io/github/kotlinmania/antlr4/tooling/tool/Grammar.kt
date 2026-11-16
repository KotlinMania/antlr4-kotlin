/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.CharStream
import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.LexerInterpreter
import io.github.kotlinmania.antlr4.ParserInterpreter
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.Vocabulary
import io.github.kotlinmania.antlr4.VocabularyImpl
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNDeserializer
import io.github.kotlinmania.antlr4.atn.ATNSerializer
import io.github.kotlinmania.antlr4.atn.SemanticContext
import io.github.kotlinmania.antlr4.dfa.DFA
import io.github.kotlinmania.antlr4.misc.IntSet
import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.misc.MultiMap
import io.github.kotlinmania.antlr4.tooling.analysis.LeftRecursiveRuleTransformer
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarASTWithOptions
import io.github.kotlinmania.antlr4.tooling.ast.GrammarRootAST
import io.github.kotlinmania.antlr4.tooling.ast.PredAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.automata.ParserATNFactory
import io.github.kotlinmania.antlr4.tooling.misc.CharSupport
import io.github.kotlinmania.antlr4.tooling.misc.OrderedHashMap
import io.github.kotlinmania.antlr4.tooling.misc.Utils
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.parse.GrammarTreeVisitor
import io.github.kotlinmania.antlr4.tooling.parse.TokenVocabParser

/**
 * Association between a lexer rule token-ref AST node and its single string-literal AST node (`IF : 'if' ;`).
 */
class LexerRuleStringLiteralAlias(
    val nameAST: GrammarAST,
    val literalAST: GrammarAST,
)

/**
 * ANTLR 4 grammar model representing a `parser`, `lexer`, or `combined` `.g4` specification,
 * its rules, token/channel symbol tables, imported grammars, ATN, and lookahead DFAs.
 */
open class Grammar :
    GrammarASTOwner,
    AttributeResolver,
    ErrorTemplateObject {
    override var name: String? = null
    override var ast: GrammarRootAST? = null

    val tokenStream: TokenStream?
    override var originalTokenStream: TokenStream? = null

    var text: String? = null
    override var fileName: String? = null

    var implicitLexer: LexerGrammar? = null
    var originalGrammar: Grammar? = null
    var parent: Grammar? = null

    internal var mutableImportedGrammars: List<Grammar>? = null
    val importedGrammars: List<Grammar>?
        get() = mutableImportedGrammars

    internal val orderedRules: OrderedHashMap<String, Rule> = OrderedHashMap()
    val rules: Map<String, Rule>
        get() = orderedRules.asMap()

    internal val mutableIndexToRule: MutableList<Rule> = ArrayList()
    val indexToRule: List<Rule>
        get() = mutableIndexToRule

    var ruleNumber: Int = 0
    var stringLiteralRuleNumber: Int = 0

    var atn: ATN? = null

    internal var stateToGrammarRegionMap: Map<Int, Interval>? = null

    internal val mutableDecisionDFAs: MutableMap<Int, DFA> = LinkedHashMap()
    val decisionDFAs: Map<Int, DFA>
        get() = mutableDecisionDFAs

    internal var decisionLOOK: List<List<IntervalSet?>>? = null

    val tool: Tool

    var maxTokenType: Int = Token.MIN_USER_TOKEN_TYPE - 1

    internal val mutableTokenNameToTypeMap: MutableMap<String, Int> = LinkedHashMap()
    val tokenNameToTypeMap: Map<String, Int>
        get() = mutableTokenNameToTypeMap

    internal val mutableStringLiteralToTypeMap: MutableMap<String, Int> = LinkedHashMap()
    val stringLiteralToTypeMap: Map<String, Int>
        get() = mutableStringLiteralToTypeMap

    internal val mutableTypeToStringLiteralList: MutableList<String?> = ArrayList()
    val typeToStringLiteralList: List<String?>
        get() = mutableTypeToStringLiteralList

    internal val mutableTypeToTokenList: MutableList<String?> = ArrayList()
    val typeToTokenList: List<String?>
        get() = mutableTypeToTokenList

    var maxChannelType: Int = Token.MIN_USER_CHANNEL_VALUE - 1

    internal val mutableChannelNameToValueMap: MutableMap<String, Int> = LinkedHashMap()
    val channelNameToValueMap: Map<String, Int>
        get() = mutableChannelNameToValueMap

    internal val mutableChannelValueToNameList: MutableList<String?> = ArrayList()
    val channelValueToNameList: List<String?>
        get() = mutableChannelValueToNameList

    internal val mutableNamedActions: MutableMap<String, ActionAST> = LinkedHashMap()
    val namedActions: Map<String, ActionAST>
        get() = mutableNamedActions

    internal val mutableLexerActions: LinkedHashMap<ActionAST, Int> = LinkedHashMap()
    val lexerActions: Map<ActionAST, Int>
        get() = mutableLexerActions

    internal val mutableSempreds: LinkedHashMap<PredAST, Int> = LinkedHashMap()
    val sempreds: Map<PredAST, Int>
        get() = mutableSempreds

    internal val mutableModes: MultiMap<String, Rule> = MultiMap()

    private val importedVocabGrammarNames: MutableSet<String> = HashSet()

    private var indexToPredMapCache: Map<Int, PredAST>? = null

    override val errorManager: ErrorManager
        get() = tool.errMgr

    override val libDirectory: String?
        get() = tool.libDirectory

    override val outputDirectory: String?
        get() = tool.outputDirectory

    override val grammarEncoding: String?
        get() = tool.grammarEncoding

    constructor(
        tool: Tool,
        ast: GrammarRootAST,
    ) {
        this.tool = tool
        this.ast = ast
        this.name = ast.getChild(0)?.text
        this.tokenStream = ast.tokenStream
        this.originalTokenStream = this.tokenStream
        initTokenSymbolTables()
    }

    constructor(grammarText: String) : this(GRAMMAR_FROM_STRING_NAME, grammarText, null, null)

    constructor(
        grammarText: String,
        tokenVocabSource: LexerGrammar?,
    ) : this(GRAMMAR_FROM_STRING_NAME, grammarText, tokenVocabSource, null)

    constructor(
        grammarText: String,
        listener: ANTLRToolListener?,
    ) : this(GRAMMAR_FROM_STRING_NAME, grammarText, null, listener)

    constructor(
        fileName: String,
        grammarText: String,
    ) : this(fileName, grammarText, null, null)

    constructor(
        fileName: String,
        grammarText: String,
        listener: ANTLRToolListener?,
    ) : this(fileName, grammarText, null, listener)

    constructor(
        fileName: String,
        grammarText: String,
        tokenVocabSource: Grammar?,
        listener: ANTLRToolListener?,
    ) {
        this.text = grammarText
        this.fileName = fileName
        this.tool = Tool()
        val hush =
            object : ANTLRToolListener {
                override fun info(msg: String?) {}

                override fun error(msg: ANTLRMessage) {}

                override fun warning(msg: ANTLRMessage) {}
            }
        this.tool.addListener(hush)
        if (listener != null) {
            this.tool.addListener(listener)
        }
        val input = CharStreams.fromString(grammarText, fileName)
        val parsedAst = this.tool.parse(fileName, input) ?: throw UnsupportedOperationException("Failed to parse grammar $fileName")
        this.ast = parsedAst
        this.name = parsedAst.getChild(0)?.text
        this.tokenStream = parsedAst.tokenStream
        this.originalTokenStream = this.tokenStream

        GrammarTransformPipeline.setGrammarPtr(this, parsedAst)
        initTokenSymbolTables()

        if (tokenVocabSource != null) {
            importVocab(tokenVocabSource)
        }

        this.tool.process(this, false)
    }

    protected fun initTokenSymbolTables() {
        mutableTokenNameToTypeMap["EOF"] = Token.EOF
        mutableTypeToTokenList.add(null)
    }

    override fun getRegisteredTokenVocabContent(vocabName: String): String? =
        tool.getVirtualTokenVocab(vocabName)

    override fun log(
        component: String,
        msg: String,
    ) {
        tool.log(component, msg)
    }

    fun loadImportedGrammars() {
        loadImportedGrammarsRecursive(HashSet())
    }

    private fun loadImportedGrammarsRecursive(visited: MutableSet<String>) {
        val rootAst = ast ?: return
        val importNode = rootAst.getFirstChildWithType(ANTLRParserTypes.IMPORT) ?: return
        val selfName = name
        if (selfName != null) {
            visited.add(selfName)
        }
        val list = ArrayList<Grammar>()
        mutableImportedGrammars = list
        for (c in importNode.children) {
            var targetNode = c
            var importedGrammarName: String? = null
            if (targetNode.type == ANTLRParserTypes.ASSIGN) {
                val rhs = targetNode.getChild(1)
                if (rhs != null) {
                    targetNode = rhs
                    importedGrammarName = targetNode.text
                }
            } else if (targetNode.type == ANTLRParserTypes.ID) {
                importedGrammarName = targetNode.text
            }
            if (importedGrammarName == null || visited.contains(importedGrammarName)) {
                continue
            }
            val imported = tool.loadImportedGrammar(this, targetNode) ?: continue
            imported.parent = this
            list.add(imported)
            imported.loadImportedGrammarsRecursive(visited)
        }
    }

    fun defineAction(atAST: GrammarAST) {
        if (atAST.childCount == 2) {
            val actionName = atAST.getChild(0)?.text ?: return
            val actionNode = atAST.getChild(1) as? ActionAST ?: return
            mutableNamedActions[actionName] = actionNode
        } else if (atAST.childCount >= 3) {
            val scope = atAST.getChild(0)?.text ?: return
            val gtype = typeString
            if (scope == gtype || (scope == "parser" && gtype == "combined")) {
                val actionName = atAST.getChild(1)?.text ?: return
                val actionNode = atAST.getChild(2) as? ActionAST ?: return
                mutableNamedActions[actionName] = actionNode
            }
        }
    }

    fun defineRule(r: Rule): Boolean = doDefineRule(r)

    internal open fun doDefineRule(r: Rule): Boolean {
        if (orderedRules[r.name] != null) {
            return false
        }
        orderedRules.put(r.name, r)
        r.index = ruleNumber++
        mutableIndexToRule.add(r)
        return true
    }

    fun undefineRule(r: Rule): Boolean = doUndefineRule(r)

    internal open fun doUndefineRule(r: Rule): Boolean {
        if (r.index < 0 || r.index >= mutableIndexToRule.size || mutableIndexToRule[r.index] !== r) {
            return false
        }
        orderedRules.remove(r.name)
        mutableIndexToRule.removeAt(r.index)
        for (i in r.index until mutableIndexToRule.size) {
            mutableIndexToRule[i].index--
        }
        ruleNumber--
        return true
    }

    fun getRule(name: String?): Rule? {
        if (name == null) return null
        return orderedRules[name]
    }

    fun getATN(): ATN {
        val current = atn
        if (current != null) return current
        val factory = ParserATNFactory(this)
        val built = factory.createATN()
        atn = built
        return built
    }

    fun getRule(index: Int): Rule? =
        if (index in mutableIndexToRule.indices) mutableIndexToRule[index] else null

    fun getRule(
        grammarName: String?,
        ruleName: String?,
    ): Rule? {
        if (grammarName != null) {
            val imported = getImportedGrammar(grammarName) ?: return null
            return imported.getRule(ruleName)
        }
        return getRule(ruleName)
    }

    val allImportedGrammars: List<Grammar>?
        get() {
            val direct = mutableImportedGrammars ?: return null
            val delegates = LinkedHashMap<String, Grammar>()
            for (d in direct) {
                val key = d.fileName ?: d.name ?: continue
                delegates[key] = d
                val nested = d.allImportedGrammars
                if (nested != null) {
                    for (imp in nested) {
                        val impKey = imp.fileName ?: imp.name ?: continue
                        delegates[impKey] = imp
                    }
                }
            }
            return delegates.values.toList()
        }

    val grammarAncestors: List<Grammar>?
        get() {
            val root = outermostGrammar
            if (this === root) return null
            val grammars = ArrayList<Grammar>()
            var p = this.parent
            while (p != null) {
                grammars.add(0, p)
                p = p.parent
            }
            return grammars
        }

    val outermostGrammar: Grammar
        get() {
            val p = parent ?: return this
            return p.outermostGrammar
        }

    override val recognizerName: String?
        get() {
            var suffix = ""
            val grammarsFromRootToMe = outermostGrammar.grammarAncestors
            var qualifiedName = name
            if (grammarsFromRootToMe != null) {
                val buf = StringBuilder()
                for (g in grammarsFromRootToMe) {
                    buf.append(g.name)
                    buf.append('_')
                }
                buf.append(name)
                qualifiedName = buf.toString()
            }
            if (isCombined || (isLexer && implicitLexer != null)) {
                suffix = getGrammarTypeToFileNameSuffix(type)
            }
            return qualifiedName.orEmpty() + suffix
        }

    fun getStringLiteralLexerRuleName(lit: String?): String =
        "$AUTO_GENERATED_TOKEN_NAME_PREFIX${stringLiteralRuleNumber++}"

    fun getImportedGrammar(name: String?): Grammar? {
        val imports = mutableImportedGrammars ?: return null
        for (g in imports) {
            if (g.name == name) return g
        }
        return null
    }

    fun getTokenType(token: String): Int {
        if (token.isEmpty()) return Token.INVALID_TYPE
        val resolved =
            if (token[0] == '\'') {
                mutableStringLiteralToTypeMap[token]
            } else {
                mutableTokenNameToTypeMap[token]
            }
        return resolved ?: Token.INVALID_TYPE
    }

    fun getTokenName(literal: String): String? {
        var grammar: Grammar? = this
        while (grammar != null) {
            val mappedType = grammar.mutableStringLiteralToTypeMap[literal]
            if (mappedType != null) {
                return grammar.getTokenName(mappedType)
            }
            grammar = grammar.parent
        }
        return null
    }

    fun getTokenDisplayName(ttype: Int): String {
        if (isLexer && ttype in Lexer.MIN_CHAR_VALUE..Lexer.MAX_CHAR_VALUE) {
            return CharSupport.getANTLRCharLiteralForChar(ttype)
        }
        if (ttype == Token.EOF) {
            return "EOF"
        }
        if (ttype == Token.INVALID_TYPE) {
            return INVALID_TOKEN_NAME
        }
        if (ttype in mutableTypeToStringLiteralList.indices) {
            val lit = mutableTypeToStringLiteralList[ttype]
            if (lit != null) return lit
        }
        if (ttype in mutableTypeToTokenList.indices) {
            val tok = mutableTypeToTokenList[ttype]
            if (tok != null) return tok
        }
        return ttype.toString()
    }

    fun getTokenName(ttype: Int): String {
        if (isLexer && ttype in Lexer.MIN_CHAR_VALUE..Lexer.MAX_CHAR_VALUE) {
            return CharSupport.getANTLRCharLiteralForChar(ttype)
        }
        if (ttype == Token.EOF) {
            return "EOF"
        }
        if (ttype in mutableTypeToTokenList.indices) {
            val tok = mutableTypeToTokenList[ttype]
            if (tok != null) return tok
        }
        return INVALID_TOKEN_NAME
    }

    fun getChannelValue(channel: String): Int =
        mutableChannelNameToValueMap[channel] ?: -1

    val ruleNames: List<String>
        get() {
            val result = MutableList(orderedRules.size) { INVALID_RULE_NAME }
            for (rule in orderedRules.values) {
                if (rule.index in result.indices) {
                    result[rule.index] = rule.name
                }
            }
            return result
        }

    val tokenNames: List<String>
        get() {
            val numTokens = currentMaxTokenType()
            return List(numTokens + 1) { i -> getTokenName(i) }
        }

    val tokenDisplayNames: List<String>
        get() {
            val numTokens = currentMaxTokenType()
            return List(numTokens + 1) { i -> getTokenDisplayName(i) }
        }

    val tokenLiteralNames: List<String?>
        get() {
            val numTokens = currentMaxTokenType()
            val literalNames = MutableList<String?>(numTokens + 1) { null }
            val limit = minOf(literalNames.size, mutableTypeToStringLiteralList.size)
            for (i in 0 until limit) {
                literalNames[i] = mutableTypeToStringLiteralList[i]
            }
            for ((lit, value) in mutableStringLiteralToTypeMap) {
                if (value in literalNames.indices && literalNames[value] == null) {
                    literalNames[value] = lit
                }
            }
            return literalNames
        }

    val tokenSymbolicNames: List<String?>
        get() {
            val numTokens = currentMaxTokenType()
            val symbolicNames = MutableList<String?>(numTokens + 1) { null }
            val limit = minOf(symbolicNames.size, mutableTypeToTokenList.size)
            for (i in 0 until limit) {
                val tok = mutableTypeToTokenList[i]
                if (tok == null || tok.startsWith(AUTO_GENERATED_TOKEN_NAME_PREFIX)) {
                    continue
                }
                symbolicNames[i] = tok
            }
            return symbolicNames
        }

    fun getVocabulary(): Vocabulary =
        VocabularyImpl(
            tokenLiteralNames.map { it.orEmpty() },
            tokenSymbolicNames.map { it.orEmpty() },
        )

    fun getSemanticContextDisplayString(semctx: SemanticContext): String =
        when (semctx) {
            is SemanticContext.Predicate -> getPredicateDisplayString(semctx)
            is SemanticContext.AND -> joinPredicateOperands(semctx, " and ")
            is SemanticContext.OR -> joinPredicateOperands(semctx, " or ")
            else -> semctx.toString()
        }

    fun joinPredicateOperands(
        op: SemanticContext.Operator,
        separator: String,
    ): String =
        op.operands.joinToString(separator) { getSemanticContextDisplayString(it) }

    val indexToPredicateMap: Map<Int, PredAST>
        get() {
            val map = LinkedHashMap<Int, PredAST>()
            for (r in orderedRules.values) {
                for (a in r.mutableActions) {
                    if (a is PredAST) {
                        val predIdx = mutableSempreds[a]
                        if (predIdx != null) {
                            map[predIdx] = a
                        }
                    }
                }
            }
            return map
        }

    fun getPredicateDisplayString(pred: SemanticContext.Predicate): String {
        val cache =
            indexToPredMapCache ?: LinkedHashMap(indexToPredicateMap).also {
                indexToPredMapCache = it
            }
        return cache[pred.predIndex]?.text ?: pred.toString()
    }

    val maxCharValue: Int
        get() = Lexer.MAX_CHAR_VALUE

    val tokenTypes: IntSet
        get() =
            if (isLexer) {
                allCharValues
            } else {
                IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, currentMaxTokenType())
            }

    val allCharValues: IntSet
        get() = IntervalSet.of(Lexer.MIN_CHAR_VALUE, maxCharValue)

    fun currentMaxTokenType(): Int = mutableTypeToTokenList.size - 1

    val newTokenType: Int
        get() {
            maxTokenType++
            return maxTokenType
        }

    val newChannelNumber: Int
        get() {
            maxChannelType++
            return maxChannelType
        }

    fun importTokensFromTokensFile() {
        doImportTokensFromTokensFile()
    }

    internal open fun doImportTokensFromTokensFile() {
        val vocab = getOptionString("tokenVocab")
        if (vocab != null && !importedVocabGrammarNames.contains(vocab)) {
            val vparser = TokenVocabParser(this)
            val tokens = vparser.load()
            tool.log("grammar", "tokens=$tokens")
            for ((t, ttype) in tokens) {
                if (t.isNotEmpty() && t[0] == '\'') {
                    defineStringLiteral(t, ttype)
                } else {
                    defineTokenName(t, ttype)
                }
            }
        }
    }

    fun importVocab(importG: Grammar) {
        val importedName = importG.name
        if (importedName != null) {
            importedVocabGrammarNames.add(importedName)
        }
        for ((tokenName, ttype) in importG.mutableTokenNameToTypeMap) {
            defineTokenName(tokenName, ttype)
        }
        for ((lit, ttype) in importG.mutableStringLiteralToTypeMap) {
            defineStringLiteral(lit, ttype)
        }
        for ((channelName, channelVal) in importG.mutableChannelNameToValueMap) {
            defineChannelName(channelName, channelVal)
        }
        var max = maxOf(mutableTypeToTokenList.size, importG.mutableTypeToTokenList.size)
        Utils.setSize(mutableTypeToTokenList, max)
        for (ttype in importG.mutableTypeToTokenList.indices) {
            maxTokenType = maxOf(maxTokenType, ttype)
            mutableTypeToTokenList[ttype] = importG.mutableTypeToTokenList[ttype]
        }
        max = maxOf(mutableChannelValueToNameList.size, importG.mutableChannelValueToNameList.size)
        Utils.setSize(mutableChannelValueToNameList, max)
        for (channelValue in importG.mutableChannelValueToNameList.indices) {
            maxChannelType = maxOf(maxChannelType, channelValue)
            mutableChannelValueToNameList[channelValue] = importG.mutableChannelValueToNameList[channelValue]
        }
    }

    fun defineTokenName(name: String): Int {
        val prev = mutableTokenNameToTypeMap[name]
        return prev ?: defineTokenName(name, newTokenType)
    }

    fun defineTokenName(
        name: String,
        ttype: Int,
    ): Int {
        val prev = mutableTokenNameToTypeMap[name]
        if (prev != null) return prev
        mutableTokenNameToTypeMap[name] = ttype
        setTokenForType(ttype, name)
        maxTokenType = maxOf(maxTokenType, ttype)
        return ttype
    }

    fun defineStringLiteral(lit: String): Int {
        val prev = mutableStringLiteralToTypeMap[lit]
        if (prev != null) return prev
        return defineStringLiteral(lit, newTokenType)
    }

    fun defineStringLiteral(
        lit: String,
        ttype: Int,
    ): Int {
        if (!mutableStringLiteralToTypeMap.containsKey(lit)) {
            mutableStringLiteralToTypeMap[lit] = ttype
            if (ttype >= mutableTypeToStringLiteralList.size) {
                Utils.setSize(mutableTypeToStringLiteralList, ttype + 1)
            }
            mutableTypeToStringLiteralList[ttype] = lit
            setTokenForType(ttype, lit)
            return ttype
        }
        return Token.INVALID_TYPE
    }

    fun defineTokenAlias(
        name: String,
        lit: String,
    ): Int {
        val ttype = defineTokenName(name)
        mutableStringLiteralToTypeMap[lit] = ttype
        setTokenForType(ttype, name)
        return ttype
    }

    fun setTokenForType(
        ttype: Int,
        text: String,
    ) {
        if (ttype == Token.EOF) {
            return
        }
        if (ttype >= mutableTypeToTokenList.size) {
            Utils.setSize(mutableTypeToTokenList, ttype + 1)
        }
        val prevToken = mutableTypeToTokenList[ttype]
        if (prevToken == null || (prevToken.isNotEmpty() && prevToken[0] == '\'')) {
            mutableTypeToTokenList[ttype] = text
        }
    }

    fun defineChannelName(name: String): Int {
        val prev = mutableChannelNameToValueMap[name]
        return prev ?: defineChannelName(name, newChannelNumber)
    }

    fun defineChannelName(
        name: String,
        value: Int,
    ): Int {
        val prev = mutableChannelNameToValueMap[name]
        if (prev != null) return prev
        mutableChannelNameToValueMap[name] = value
        setChannelNameForValue(value, name)
        maxChannelType = maxOf(maxChannelType, value)
        return value
    }

    fun setChannelNameForValue(
        channelValue: Int,
        name: String,
    ) {
        if (channelValue >= mutableChannelValueToNameList.size) {
            Utils.setSize(mutableChannelValueToNameList, channelValue + 1)
        }
        if (mutableChannelValueToNameList[channelValue] == null) {
            mutableChannelValueToNameList[channelValue] = name
        }
    }

    override fun resolveToAttribute(
        x: String?,
        node: ActionAST?,
    ): Attribute? = null

    override fun resolveToAttribute(
        x: String?,
        y: String?,
        node: ActionAST?,
    ): Attribute? = null

    override fun resolvesToLabel(
        x: String?,
        node: ActionAST?,
    ): Boolean = false

    override fun resolvesToListLabel(
        x: String?,
        node: ActionAST?,
    ): Boolean = false

    override fun resolvesToToken(
        x: String?,
        node: ActionAST?,
    ): Boolean = false

    override fun resolvesToAttributeDict(
        x: String?,
        node: ActionAST?,
    ): Boolean = false

    val defaultActionScope: String?
        get() =
            when (type) {
                ANTLRParserTypes.LEXER -> "lexer"
                ANTLRParserTypes.PARSER, ANTLRParserTypes.COMBINED -> "parser"
                else -> null
            }

    val type: Int
        get() = ast?.grammarType ?: 0

    override val isLexer: Boolean
        get() = type == ANTLRParserTypes.LEXER

    val isParser: Boolean
        get() = type == ANTLRParserTypes.PARSER

    val isCombined: Boolean
        get() = type == ANTLRParserTypes.COMBINED

    override val typeString: String?
        get() {
            if (ast == null) return null
            return ANTLRParserTypes.tokenName(type).lowercase()
        }

    val language: String?
        get() = getOptionString("language")

    override fun getOptionString(key: String): String? = ast?.getOptionString(key)

    val stringLiterals: Set<String>
        get() {
            val strings = LinkedHashSet<String>()
            val collector =
                object : GrammarTreeVisitor() {
                    override fun stringRef(ref: TerminalAST) {
                        strings.add(ref.text)
                    }

                    override fun getErrorManager(): ErrorManager = tool.errMgr
                }
            val rootAst = ast
            if (rootAst != null) {
                collector.visitGrammar(rootAst)
            }
            return strings
        }

    fun setLookaheadDFA(
        decision: Int,
        lookaheadDFA: DFA,
    ) {
        mutableDecisionDFAs[decision] = lookaheadDFA
    }

    fun getStateToGrammarRegion(atnStateNumber: Int): Interval {
        var map = stateToGrammarRegionMap
        if (map == null) {
            map = getStateToGrammarRegionMap(ast, null)
            stateToGrammarRegionMap = map
        }
        return map[atnStateNumber] ?: Interval.INVALID
    }

    fun createLexerInterpreter(input: CharStream?): LexerInterpreter {
        check(!isParser) { "A lexer interpreter can only be created for a lexer or combined grammar." }
        if (isCombined) {
            val extracted = requireNotNull(implicitLexer) { "Combined grammar has no extracted implicit lexer." }
            return extracted.createLexerInterpreter(input)
        }
        val allChannels = ArrayList<String>()
        allChannels.add("DEFAULT_TOKEN_CHANNEL")
        allChannels.add("HIDDEN")
        for (ch in mutableChannelValueToNameList) {
            allChannels.add(ch ?: "null")
        }
        val builtAtn = requireNotNull(atn) { "Lexer ATN has not been constructed." }
        val serialized = ATNSerializer.getSerialized(builtAtn)
        val deserializedATN = ATNDeserializer().deserialize(serialized.toArray())
        val lexerModeNames = (this as LexerGrammar).mutableModes.keys.toList()
        return LexerInterpreter(
            fileName ?: GRAMMAR_FROM_STRING_NAME,
            getVocabulary(),
            ruleNames,
            allChannels,
            lexerModeNames,
            deserializedATN,
            input,
        )
    }

    fun createGrammarParserInterpreter(tokenStream: TokenStream?): GrammarParserInterpreter {
        check(!isLexer) { "A parser interpreter can only be created for a parser or combined grammar." }
        val builtAtn = getATN()
        val serialized = ATNSerializer.getSerialized(builtAtn)
        val deserializedATN = ATNDeserializer().deserialize(serialized.toArray())
        return GrammarParserInterpreter(this, deserializedATN, tokenStream)
    }

    fun createParserInterpreter(tokenStream: TokenStream?): ParserInterpreter {
        check(!isLexer) { "A parser interpreter can only be created for a parser or combined grammar." }
        val builtAtn = getATN()
        val serialized = ATNSerializer.getSerialized(builtAtn)
        val deserializedATN = ATNDeserializer().deserialize(serialized.toArray())
        return ParserInterpreter(
            fileName ?: GRAMMAR_FROM_STRING_NAME,
            getVocabulary(),
            ruleNames,
            deserializedATN,
            tokenStream,
        )
    }

    companion object {
        const val GRAMMAR_FROM_STRING_NAME: String = "<string>"
        const val INVALID_TOKEN_NAME: String = "<INVALID>"
        const val INVALID_RULE_NAME: String = "<invalid>"
        const val CASE_INSENSITIVE_OPTION_NAME: String = "caseInsensitive"
        const val AUTO_GENERATED_TOKEN_NAME_PREFIX: String = "T__"

        val parserOptions: Set<String> =
            setOf(
                "superClass",
                "contextSuperClass",
                "TokenLabelType",
                "tokenVocab",
                "language",
                "accessLevel",
                "exportMacro",
                CASE_INSENSITIVE_OPTION_NAME,
            )

        val lexerOptions: Set<String> = parserOptions

        val lexerRuleOptions: Set<String> = setOf(CASE_INSENSITIVE_OPTION_NAME)

        val parseRuleOptions: Set<String> = emptySet()

        val parserBlockOptions: Set<String> = emptySet()

        val lexerBlockOptions: Set<String> = emptySet()

        val ruleRefOptions: Set<String> =
            setOf(
                LeftRecursiveRuleTransformer.PRECEDENCE_OPTION_NAME,
                LeftRecursiveRuleTransformer.TOKENINDEX_OPTION_NAME,
            )

        val tokenOptions: Set<String> =
            setOf(
                "assoc",
                LeftRecursiveRuleTransformer.TOKENINDEX_OPTION_NAME,
            )

        val actionOptions: Set<String> = emptySet()

        val semPredOptions: Set<String> =
            setOf(
                LeftRecursiveRuleTransformer.PRECEDENCE_OPTION_NAME,
                "fail",
            )

        val doNotCopyOptionsToLexer: Set<String> =
            setOf(
                "superClass",
                "TokenLabelType",
                "tokenVocab",
            )

        val grammarAndLabelRefTypeToScope: Map<String, AttributeDict> =
            mapOf(
                "parser:RULE_LABEL" to Rule.predefinedRulePropertiesDict,
                "parser:TOKEN_LABEL" to AttributeDict.predefinedTokenDict,
                "combined:RULE_LABEL" to Rule.predefinedRulePropertiesDict,
                "combined:TOKEN_LABEL" to AttributeDict.predefinedTokenDict,
            )

        fun load(fileName: String): Grammar? {
            val antlr = Tool()
            return antlr.loadGrammar(fileName)
        }

        fun isTokenName(id: String): Boolean =
            id.isNotEmpty() && id[0].isUpperCase()

        fun getGrammarTypeToFileNameSuffix(type: Int): String =
            when (type) {
                ANTLRParserTypes.LEXER -> "Lexer"
                ANTLRParserTypes.PARSER -> "Parser"
                ANTLRParserTypes.COMBINED -> "Parser"
                else -> "<invalid>"
            }

        fun setNodeOptions(
            node: GrammarAST?,
            options: GrammarAST?,
        ) {
            if (node == null || options == null) return
            val target = node as? GrammarASTWithOptions ?: return
            if (target.childCount == 0 || options.childCount == 0) return
            for (c in options.children) {
                if (c.type == ANTLRParserTypes.ASSIGN) {
                    val key = c.getChild(0)?.text ?: continue
                    target.setOption(key, c.getChild(1))
                } else {
                    target.setOption(c.text, null)
                }
            }
        }

        private val LEXER_ALIAS_PATTERNS: List<String> =
            listOf(
                "(RULE %name:TOKEN_REF (BLOCK (ALT %lit:STRING_LITERAL)))",
                "(RULE %name:TOKEN_REF (BLOCK (ALT %lit:STRING_LITERAL ACTION)))",
                "(RULE %name:TOKEN_REF (BLOCK (ALT %lit:STRING_LITERAL SEMPRED)))",
                "(RULE %name:TOKEN_REF (BLOCK (LEXER_ALT_ACTION (ALT %lit:STRING_LITERAL) .)))",
                "(RULE %name:TOKEN_REF (BLOCK (LEXER_ALT_ACTION (ALT %lit:STRING_LITERAL) . .)))",
                "(RULE %name:TOKEN_REF (BLOCK (LEXER_ALT_ACTION (ALT %lit:STRING_LITERAL) (LEXER_ACTION_CALL . .))))",
                "(RULE %name:TOKEN_REF (BLOCK (LEXER_ALT_ACTION (ALT %lit:STRING_LITERAL) . (LEXER_ACTION_CALL . .))))",
                "(RULE %name:TOKEN_REF (BLOCK (LEXER_ALT_ACTION (ALT %lit:STRING_LITERAL) (LEXER_ACTION_CALL . .) .)))",
            )

        fun getStringLiteralAliasesFromLexerRules(ast: GrammarRootAST): List<LexerRuleStringLiteralAlias>? {
            val ruleNodes = ast.getNodesWithType(ANTLRParserTypes.RULE)
            if (ruleNodes.isEmpty()) return null
            val aliases = ArrayList<LexerRuleStringLiteralAlias>()
            for (r in ruleNodes) {
                val firstChild = r.getChild(0) ?: continue
                if (firstChild.type == ANTLRParserTypes.TOKEN_REF) {
                    for (pattern in LEXER_ALIAS_PATTERNS) {
                        if (defAlias(r, pattern, aliases)) {
                            break
                        }
                    }
                }
            }
            return aliases
        }

        private fun defAlias(
            r: GrammarAST,
            pattern: String,
            aliases: MutableList<LexerRuleStringLiteralAlias>,
        ): Boolean {
            val parsedPattern = parseTreePattern(pattern) ?: return false
            val captures = HashMap<String, GrammarAST>()
            if (matchTreePatternNode(r, parsedPattern, captures)) {
                val nameNode = captures["name"]
                val litNode = captures["lit"]
                if (nameNode != null && litNode != null) {
                    aliases.add(LexerRuleStringLiteralAlias(nameNode, litNode))
                    return true
                }
            }
            return false
        }

        private class TreePatternNode(
            val isWildcard: Boolean,
            val tokenType: Int,
            val label: String?,
            val children: List<TreePatternNode>,
        )

        private fun parseTreePattern(pattern: String): TreePatternNode? {
            var idx = 0

            fun skipWs() {
                while (idx < pattern.length && pattern[idx].isWhitespace()) idx++
            }

            fun parseAtomSpec(): Pair<String?, Int>? {
                skipWs()
                if (idx >= pattern.length) return null
                if (pattern[idx] == '.') {
                    idx++
                    return null to -1
                }
                var label: String? = null
                if (pattern[idx] == '%') {
                    idx++
                    val labelStart = idx
                    while (idx < pattern.length && pattern[idx] != ':' && !pattern[idx].isWhitespace() && pattern[idx] != ')' && pattern[idx] != '(') {
                        idx++
                    }
                    label = pattern.substring(labelStart, idx)
                    if (idx < pattern.length && pattern[idx] == ':') {
                        idx++
                    }
                }
                val typeStart = idx
                while (idx < pattern.length && !pattern[idx].isWhitespace() && pattern[idx] != '(' && pattern[idx] != ')') {
                    idx++
                }
                val typeName = pattern.substring(typeStart, idx)
                val ttype = tokenTypeByName(typeName)
                return label to ttype
            }

            fun parseSubtree(): TreePatternNode? {
                skipWs()
                if (idx >= pattern.length) return null
                return if (pattern[idx] == '(') {
                    idx++
                    val head = parseAtomSpec() ?: return null
                    val kids = ArrayList<TreePatternNode>()
                    while (true) {
                        skipWs()
                        if (idx >= pattern.length) return null
                        if (pattern[idx] == ')') {
                            idx++
                            break
                        }
                        val child = parseSubtree() ?: return null
                        kids.add(child)
                    }
                    TreePatternNode(
                        isWildcard = head.second == -1,
                        tokenType = head.second,
                        label = head.first,
                        children = kids,
                    )
                } else {
                    val spec = parseAtomSpec() ?: return null
                    TreePatternNode(
                        isWildcard = spec.second == -1,
                        tokenType = spec.second,
                        label = spec.first,
                        children = emptyList(),
                    )
                }
            }

            return parseSubtree()
        }

        private fun tokenTypeByName(name: String): Int {
            val names = ANTLRParserTypes.tokenNames
            for (i in names.indices) {
                if (names[i] == name) return i
            }
            return Token.INVALID_TYPE
        }

        private fun matchTreePatternNode(
            ast: GrammarAST,
            pattern: TreePatternNode,
            captures: MutableMap<String, GrammarAST>,
        ): Boolean {
            if (!pattern.isWildcard && ast.type != pattern.tokenType) {
                return false
            }
            if (pattern.label != null) {
                captures[pattern.label] = ast
            }
            if (ast.childCount != pattern.children.size) {
                return false
            }
            for (i in pattern.children.indices) {
                val child = ast.getChild(i) ?: return false
                if (!matchTreePatternNode(child, pattern.children[i], captures)) {
                    return false
                }
            }
            return true
        }

        fun getStateToGrammarRegionMap(
            ast: GrammarRootAST?,
            grammarTokenTypes: IntervalSet?,
        ): Map<Int, Interval> {
            val map = HashMap<Int, Interval>()
            if (ast == null) return map
            val nodes = ast.getNodesWithType(grammarTokenTypes)
            for (n in nodes) {
                val st = n.atnState ?: continue
                var tokenRegion = Interval.of(n.tokenStartIndex, n.tokenStopIndex)
                val ruleNode: GrammarAST? =
                    when (n.type) {
                        ANTLRParserTypes.RULE -> n
                        ANTLRParserTypes.BLOCK, ANTLRParserTypes.CLOSURE -> n.getAncestor(ANTLRParserTypes.RULE)
                        else -> null
                    }
                if (ruleNode is RuleAST) {
                    val ruleName = ruleNode.ruleName
                    val owningGrammar = ast.g as? Grammar
                    val r = owningGrammar?.getRule(ruleName)
                    if (r is LeftRecursiveRule) {
                        val origAst = r.originalAST
                        tokenRegion = Interval.of(origAst.tokenStartIndex, origAst.tokenStopIndex)
                    }
                }
                map[st.stateNumber] = tokenRegion
            }
            return map
        }
    }
}
