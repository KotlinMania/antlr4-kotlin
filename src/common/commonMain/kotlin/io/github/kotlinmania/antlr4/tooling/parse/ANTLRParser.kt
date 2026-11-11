/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.InputMismatchException
import io.github.kotlinmania.antlr4.NoViableAltException
import io.github.kotlinmania.antlr4.Parser
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.Vocabulary
import io.github.kotlinmania.antlr4.VocabularyImpl
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNType
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarRootAST
import io.github.kotlinmania.antlr4.tooling.ast.NotAST
import io.github.kotlinmania.antlr4.tooling.ast.OptionalBlockAST
import io.github.kotlinmania.antlr4.tooling.ast.PlusBlockAST
import io.github.kotlinmania.antlr4.tooling.ast.PredAST
import io.github.kotlinmania.antlr4.tooling.ast.RangeAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleRefAST
import io.github.kotlinmania.antlr4.tooling.ast.SetAST
import io.github.kotlinmania.antlr4.tooling.ast.StarBlockAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType

/**
 * Strongly-typed return value for [ANTLRParser.grammarSpec].
 */
class GrammarSpecReturn(
    val tree: GrammarRootAST,
    val start: Token?,
    val stop: Token?,
)

/**
 * Strongly-typed return value for non-root [ANTLRParser] grammar rules.
 */
class ParserRuleReturn(
    val tree: GrammarAST?,
    val start: Token?,
    val stop: Token?,
)

internal class ParserControlException(
    val recognitionException: RecognitionException,
    val formattedMessage: String? = null,
) : RuntimeException(formattedMessage ?: recognitionException.message)

/**
 * Pure-Kotlin recursive-descent ANTLR4 `.g4` parser implementing `ANTLRParser.g` directly
 * on top of [Parser] and constructing [GrammarAST] / [GrammarRootAST] trees.
 */
open class ANTLRParser(
    input: TokenStream?,
) : Parser(input) {
    var treeAdaptor: GrammarASTAdaptor = GrammarASTAdaptor()
    internal val paraphrases: ArrayDeque<String> = ArrayDeque()
    private val following: ArrayDeque<IntervalSet> = ArrayDeque()
    private var errorRecovery: Boolean = false
    private var lastErrorIndex: Int = -1
    private var maxLookaheadIndex: Int = -1
    var syntaxErrors: Int = 0
        protected set

    override val grammarFileName: String
        get() = "ANTLRParser.g4"

    override val ruleNames: List<String>
        get() = RULE_NAMES

    override val tokenNames: List<String>
        get() = ANTLRParserTypes.tokenNames

    override val vocabulary: Vocabulary
        get() = VOCABULARY

    override val atn: ATN
        get() = PARSER_ATN

    override fun reset() {
        super.reset()
        paraphrases.clear()
        following.clear()
        errorRecovery = false
        lastErrorIndex = -1
        maxLookaheadIndex = -1
        syntaxErrors = 0
    }

    open fun grammarError(
        etype: ErrorType,
        token: Token?,
        vararg args: Any?,
    ) {
    }

    open fun displayRecognitionError(e: RecognitionException) {
    }

    fun displayRecognitionErrorForTokenNames(
        tokenNames: List<String>,
        e: RecognitionException,
    ) {
        displayRecognitionErrorWithTokenNames(tokenNames, e)
    }

    internal open fun displayRecognitionErrorWithTokenNames(
        tokenNames: List<String>,
        e: RecognitionException,
    ) {
        displayRecognitionError(e)
    }

    internal open fun displayFormattedSyntaxError(
        e: RecognitionException,
        formattedMsg: String?,
    ) {
        if (formattedMsg != null && e !is V4ParserException && e !is NoViableAltException) {
            val wrapped = V4ParserException(formattedMsg, inputField, e.offendingToken)
            displayRecognitionErrorWithTokenNames(tokenNames, wrapped)
        } else {
            displayRecognitionErrorWithTokenNames(tokenNames, e)
        }
    }

    fun getErrorMessage(
        e: RecognitionException,
        tokenNames: List<String>,
    ): String {
        if (e is V4ParserException) {
            return e.msg
        }
        if (e is NoViableAltException) {
            return "${getTokenErrorDisplay(e.offendingToken)} came as a complete surprise to me"
        }
        return e.message ?: "syntax error"
    }

    private fun la(k: Int): Int {
        val tok = lt(k)
        return tok?.type ?: Token.EOF
    }

    private fun lt(k: Int): Token? {
        val stream = tokenStream
        val tok = stream.LT(k)
        if (k > 0 && tok != null && tok.tokenIndex > maxLookaheadIndex) {
            maxLookaheadIndex = tok.tokenIndex
        }
        return tok
    }

    private inline fun <T> withFollow(
        set: IntervalSet,
        block: () -> T,
    ): T {
        following.addLast(set)
        return try {
            block()
        } finally {
            if (following.isNotEmpty()) {
                following.removeLast()
            }
        }
    }

    private fun combineFollows(exact: Boolean): IntervalSet {
        val result = IntervalSet()
        for (i in following.indices.reversed()) {
            val local = following[i]
            result.addAll(local)
            if (exact) {
                if (local.contains(ANTLRParserTypes.EOR_TOKEN_TYPE)) {
                    if (i > 0) {
                        result.remove(ANTLRParserTypes.EOR_TOKEN_TYPE)
                    }
                } else {
                    break
                }
            }
        }
        result.remove(ANTLRParserTypes.EOR_TOKEN_TYPE)
        return result
    }

    private fun reportParserError(
        e: RecognitionException,
        formattedMsg: String? = null,
    ) {
        if (errorRecovery) {
            return
        }
        syntaxErrors++
        errorRecovery = true
        displayFormattedSyntaxError(e, formattedMsg)
    }

    private fun recoverParser(e: RecognitionException) {
        if (lastErrorIndex == tokenStream.index() && la(1) != Token.EOF) {
            consume()
        }
        lastErrorIndex = tokenStream.index()
        val followSet = combineFollows(false)
        consumeUntilSet(followSet)
    }

    private fun consumeUntilSet(set: IntervalSet) {
        var ttype = la(1)
        while (ttype != Token.EOF && !set.contains(ttype)) {
            consume()
            ttype = la(1)
        }
    }

    private fun noViableAlt(token: Token? = lt(1)): ParserControlException {
        val tok = token ?: lt(1)
        val start = tok
        val e = NoViableAltException(this, tokenStream, start, tok, null, ctx)
        e.offendingToken = tok
        return ParserControlException(e)
    }

    private fun matchToken(
        ttype: Int,
        localFollow: IntervalSet?,
    ): Token {
        val current = checkNotNull(lt(1)) { "Expected lookahead token at index ${tokenStream.index()}" }
        if (current.type == ttype) {
            if (ttype == Token.EOF) {
                isMatchedEOF = true
            }
            consume()
            errorRecovery = false
            return current
        }

        // 1. Single-token deletion: LA(2) == ttype
        val expectedName = ANTLRParserTypes.tokenName(ttype)
        if (la(2) == ttype) {
            val unwantedMsg = "extraneous input ${getTokenErrorDisplay(current)} expecting $expectedName"
            val ex = InputMismatchException(this)
            ex.offendingToken = current
            reportParserError(ex, unwantedMsg)
            consume() // delete unexpected token
            val matched = checkNotNull(lt(1)) { "Expected lookahead token after single-token deletion" }
            consume() // consume expected token
            errorRecovery = false
            return matched
        }

        // 2. Single-token insertion: LA(1) is in follow set
        if (localFollow != null && mismatchIsMissingToken(localFollow)) {
            val missingMsg = "missing $expectedName at ${getTokenErrorDisplay(current)}"
            val ex = InputMismatchException(this)
            ex.offendingToken = current
            reportParserError(ex, missingMsg)
            val inserted = CommonToken(current)
            inserted.type = ttype
            inserted.text = "<missing $expectedName>"
            inserted.tokenIndex = -1
            return inserted
        }

        // 3. Cannot recover inline; throw mismatch
        val mismatchMsg = "mismatched input ${getTokenErrorDisplay(current)} expecting $expectedName"
        val ex = InputMismatchException(this)
        ex.offendingToken = current
        throw ParserControlException(ex, mismatchMsg)
    }

    private fun mismatchIsMissingToken(follow: IntervalSet): Boolean {
        val effective = IntervalSet().apply { addAll(follow) }
        if (effective.contains(ANTLRParserTypes.EOR_TOKEN_TYPE)) {
            val viable = combineFollows(true)
            effective.addAll(viable)
            if (following.isNotEmpty()) {
                effective.remove(ANTLRParserTypes.EOR_TOKEN_TYPE)
            }
        }
        val currentLa = la(1)
        return effective.contains(currentLa) || effective.contains(ANTLRParserTypes.EOR_TOKEN_TYPE)
    }

    private fun sync() {
        val followSet = combineFollows(false)
        val currentLa = la(1)
        if (currentLa != Token.EOF && !followSet.contains(currentLa)) {
            val tok = lt(1)
            val ex = NoViableAltException(this, tokenStream, tok, tok, null, ctx)
            ex.offendingToken = tok
            reportParserError(ex)
            consumeUntilSet(followSet)
        }
    }

    private fun finalizeReturn(
        tree: GrammarAST?,
        startTok: Token?,
    ): ParserRuleReturn {
        val stopTok = stopTokenFor(startTok)
        treeAdaptor.setTokenBoundaries(tree, startTok, stopTok)
        return ParserRuleReturn(tree, startTok, stopTok)
    }

    private fun stopTokenFor(startTok: Token?): Token? {
        val prev = lt(-1)
        if (prev != null && startTok != null && prev.tokenIndex >= startTok.tokenIndex) {
            return prev
        }
        return startTok
    }

    // =========================================================================
    // Entry Rules
    // =========================================================================

    fun grammarSpec(): GrammarSpecReturn {
        val startTok = lt(1)
        var root: GrammarRootAST? = null
        try {
            val gType = withFollow(SET_ID_START) { grammarType() }
            root = gType

            val gid = withFollow(setOfTokens(ANTLRParserTypes.SEMI)) { id() }
            if (gid.tree != null) {
                root.addChild(gid.tree)
            }

            matchToken(ANTLRParserTypes.SEMI, FOLLOW_SEMI_IN_GRAMMAR_SPEC)

            withFollow(FOLLOW_PREQUEL_LOOP_IN_GRAMMAR_SPEC) {
                sync()
                while (PREQUEL_START.contains(la(1))) {
                    val p = prequelConstruct()
                    if (p.tree != null) {
                        root.addChild(p.tree)
                    }
                    sync()
                }
            }

            val r = withFollow(FOLLOW_RULES_IN_GRAMMAR_SPEC) { rules() }
            if (r.tree != null) {
                root.addChild(r.tree)
            }

            withFollow(setOfTokens(Token.EOF)) {
                while (la(1) == ANTLRParserTypes.MODE) {
                    val m = modeSpec()
                    if (m.tree != null) {
                        root.addChild(m.tree)
                    }
                }
            }

            matchToken(Token.EOF, null)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            if (root == null) {
                root =
                    GrammarRootAST(ANTLRParserTypes.GRAMMAR, startTok, "COMBINED_GRAMMAR", tokenStream).also {
                        it.grammarType = ANTLRParserTypes.COMBINED
                        it.hasErrors = true
                    }
            } else {
                root.hasErrors = true
            }
        }
        val finalRoot: GrammarRootAST = root
        val options = finalRoot.getFirstChildWithType(ANTLRParserTypes.OPTIONS)
        if (options != null) {
            ANTLRParserTypes.setNodeOptions(finalRoot, options)
        }
        val stopTok = stopTokenFor(startTok)
        treeAdaptor.setTokenBoundaries(finalRoot, startTok, stopTok)
        if (syntaxErrors > 0) {
            finalRoot.hasErrors = true
        }
        return GrammarSpecReturn(finalRoot, startTok, stopTok)
    }

    fun alternativeEntry(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val alt = withFollow(setOfTokens(Token.EOF)) { alternative() }
            matchToken(Token.EOF, null)
            finalizeReturn(alt.tree, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun elementEntry(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val elem = withFollow(setOfTokens(Token.EOF)) { element() }
            matchToken(Token.EOF, null)
            finalizeReturn(elem.tree, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun ruleEntry(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val r = withFollow(setOfTokens(Token.EOF)) { rule() }
            matchToken(Token.EOF, null)
            finalizeReturn(r.tree, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun blockEntry(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val b = withFollow(setOfTokens(Token.EOF)) { block() }
            matchToken(Token.EOF, null)
            finalizeReturn(b.tree, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    // =========================================================================
    // Grammar Header & Prequels
    // =========================================================================

    private fun grammarType(): GrammarRootAST {
        val startTok = lt(1)
        val root: GrammarRootAST
        when (la(1)) {
            ANTLRParserTypes.LEXER -> {
                val t = matchToken(ANTLRParserTypes.LEXER, setOfTokens(ANTLRParserTypes.GRAMMAR))
                val g = matchToken(ANTLRParserTypes.GRAMMAR, SET_ID_START)
                root = GrammarRootAST(ANTLRParserTypes.GRAMMAR, g, "LEXER_GRAMMAR", tokenStream)
                root.grammarType = t.type
            }
            ANTLRParserTypes.PARSER -> {
                val t = matchToken(ANTLRParserTypes.PARSER, setOfTokens(ANTLRParserTypes.GRAMMAR))
                val g = matchToken(ANTLRParserTypes.GRAMMAR, SET_ID_START)
                root = GrammarRootAST(ANTLRParserTypes.GRAMMAR, g, "PARSER_GRAMMAR", tokenStream)
                root.grammarType = t.type
            }
            ANTLRParserTypes.GRAMMAR -> {
                val g = matchToken(ANTLRParserTypes.GRAMMAR, SET_ID_START)
                root = GrammarRootAST(ANTLRParserTypes.GRAMMAR, g, "COMBINED_GRAMMAR", tokenStream)
                root.grammarType = ANTLRParserTypes.COMBINED
            }
            else -> {
                val ce = noViableAlt(startTok)
                reportParserError(ce.recognitionException)
                recoverParser(ce.recognitionException)
                root = GrammarRootAST(ANTLRParserTypes.GRAMMAR, startTok, "COMBINED_GRAMMAR", tokenStream)
                root.grammarType = ANTLRParserTypes.COMBINED
                root.hasErrors = true
            }
        }
        treeAdaptor.setTokenBoundaries(root, startTok, stopTokenFor(startTok))
        return root
    }

    fun prequelConstruct(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            when (la(1)) {
                ANTLRParserTypes.OPTIONS -> optionsSpec()
                ANTLRParserTypes.IMPORT -> delegateGrammars()
                ANTLRParserTypes.TOKENS_SPEC -> tokensSpec()
                ANTLRParserTypes.CHANNELS -> channelsSpec()
                ANTLRParserTypes.AT -> action()
                else -> throw noViableAlt(startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun optionsSpec(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val optTok = matchToken(ANTLRParserTypes.OPTIONS, SET_ID_OR_RBRACE)
            val root = GrammarAST(ANTLRParserTypes.OPTIONS, optTok, "OPTIONS")
            while (SET_ID_START.contains(la(1))) {
                val opt = withFollow(setOfTokens(ANTLRParserTypes.SEMI)) { option() }
                if (opt.tree != null) {
                    root.addChild(opt.tree)
                }
                matchToken(ANTLRParserTypes.SEMI, SET_ID_OR_RBRACE)
            }
            matchToken(ANTLRParserTypes.RBRACE, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun option(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val key = withFollow(setOfTokens(ANTLRParserTypes.ASSIGN)) { id() }
            val assignTok = matchToken(ANTLRParserTypes.ASSIGN, OPTION_VALUE_START)
            val root = GrammarAST(assignTok)
            if (key.tree != null) {
                root.addChild(key.tree)
            }
            val value = withFollow(setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE)) { optionValue() }
            if (value.tree != null) {
                root.addChild(value.tree)
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun optionValue(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val node: GrammarAST? =
                when (la(1)) {
                    ANTLRParserTypes.RULE_REF, ANTLRParserTypes.TOKEN_REF -> qid().tree
                    ANTLRParserTypes.STRING_LITERAL -> {
                        val t = matchToken(ANTLRParserTypes.STRING_LITERAL, null)
                        GrammarAST(t)
                    }
                    ANTLRParserTypes.ACTION -> {
                        val t = matchToken(ANTLRParserTypes.ACTION, null)
                        ActionAST(t)
                    }
                    ANTLRParserTypes.INT -> {
                        val t = matchToken(ANTLRParserTypes.INT, null)
                        GrammarAST(t)
                    }
                    else -> throw noViableAlt(startTok)
                }
            finalizeReturn(node, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun delegateGrammars(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val impTok = matchToken(ANTLRParserTypes.IMPORT, SET_ID_START)
            val root = GrammarAST(impTok)
            val first = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.SEMI)) { delegateGrammar() }
            if (first.tree != null) {
                root.addChild(first.tree)
            }
            while (la(1) == ANTLRParserTypes.COMMA) {
                matchToken(ANTLRParserTypes.COMMA, SET_ID_START)
                val next = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.SEMI)) { delegateGrammar() }
                if (next.tree != null) {
                    root.addChild(next.tree)
                }
            }
            matchToken(ANTLRParserTypes.SEMI, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun delegateGrammar(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val first = withFollow(setOfTokens(ANTLRParserTypes.ASSIGN, ANTLRParserTypes.EOR_TOKEN_TYPE)) { id() }
            if (la(1) == ANTLRParserTypes.ASSIGN) {
                val assignTok = matchToken(ANTLRParserTypes.ASSIGN, SET_ID_START)
                val root = GrammarAST(assignTok)
                if (first.tree != null) {
                    root.addChild(first.tree)
                }
                val second = withFollow(setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE)) { id() }
                if (second.tree != null) {
                    root.addChild(second.tree)
                }
                finalizeReturn(root, startTok)
            } else {
                finalizeReturn(first.tree, startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun tokensSpec(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val tokSpec = matchToken(ANTLRParserTypes.TOKENS_SPEC, SET_ID_OR_RBRACE)
            if (la(1) == ANTLRParserTypes.RBRACE) {
                matchToken(ANTLRParserTypes.RBRACE, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
                finalizeReturn(null, startTok)
            } else {
                val root = GrammarAST(tokSpec)
                val first = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.RBRACE)) { id() }
                if (first.tree != null) {
                    root.addChild(first.tree)
                }
                while (la(1) == ANTLRParserTypes.COMMA) {
                    matchToken(ANTLRParserTypes.COMMA, SET_ID_OR_RBRACE)
                    if (la(1) == ANTLRParserTypes.RBRACE) {
                        val errId = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.RBRACE)) { id() }
                        if (errId.tree != null) {
                            root.addChild(errId.tree)
                        }
                        break
                    }
                    val next = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.RBRACE)) { id() }
                    if (next.tree != null) {
                        root.addChild(next.tree)
                    }
                }
                matchToken(ANTLRParserTypes.RBRACE, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
                finalizeReturn(root, startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun channelsSpec(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val chanTok = matchToken(ANTLRParserTypes.CHANNELS, SET_ID_OR_RBRACE)
            if (la(1) == ANTLRParserTypes.RBRACE) {
                matchToken(ANTLRParserTypes.RBRACE, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
                finalizeReturn(null, startTok)
            } else {
                val root = GrammarAST(chanTok)
                val first = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.RBRACE)) { id() }
                if (first.tree != null) {
                    root.addChild(first.tree)
                }
                while (la(1) == ANTLRParserTypes.COMMA) {
                    matchToken(ANTLRParserTypes.COMMA, SET_ID_START)
                    val next = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.RBRACE)) { id() }
                    if (next.tree != null) {
                        root.addChild(next.tree)
                    }
                }
                matchToken(ANTLRParserTypes.RBRACE, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
                finalizeReturn(root, startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun action(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val atTok = matchToken(ANTLRParserTypes.AT, ACTION_SCOPE_OR_ID_START)
            val root = GrammarAST(atTok)
            if (la(2) == ANTLRParserTypes.COLONCOLON) {
                val scope = withFollow(setOfTokens(ANTLRParserTypes.COLONCOLON)) { actionScopeName() }
                matchToken(ANTLRParserTypes.COLONCOLON, SET_ID_START)
                if (scope.tree != null) {
                    root.addChild(scope.tree)
                }
            }
            val actionId = withFollow(setOfTokens(ANTLRParserTypes.ACTION)) { id() }
            if (actionId.tree != null) {
                root.addChild(actionId.tree)
            }
            val actTok = matchToken(ANTLRParserTypes.ACTION, PREQUEL_START)
            root.addChild(ActionAST(actTok))
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun actionScopeName(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val node: GrammarAST? =
                when (la(1)) {
                    ANTLRParserTypes.RULE_REF, ANTLRParserTypes.TOKEN_REF -> id().tree
                    ANTLRParserTypes.LEXER -> {
                        val t = matchToken(ANTLRParserTypes.LEXER, null)
                        GrammarAST(ANTLRParserTypes.ID, t)
                    }
                    ANTLRParserTypes.PARSER -> {
                        val t = matchToken(ANTLRParserTypes.PARSER, null)
                        GrammarAST(ANTLRParserTypes.ID, t)
                    }
                    else -> throw noViableAlt(startTok)
                }
            finalizeReturn(node, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun modeSpec(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val modeTok = matchToken(ANTLRParserTypes.MODE, SET_ID_START)
            val root = GrammarAST(modeTok)
            val modeId = withFollow(setOfTokens(ANTLRParserTypes.SEMI)) { id() }
            if (modeId.tree != null) {
                root.addChild(modeId.tree)
            }
            matchToken(ANTLRParserTypes.SEMI, MODE_SPEC_LEXER_RULE_SYNC)
            withFollow(MODE_SPEC_LEXER_RULE_SYNC) {
                sync()
                while (la(1) == ANTLRParserTypes.FRAGMENT || la(1) == ANTLRParserTypes.TOKEN_REF) {
                    val lr = lexerRule()
                    if (lr.tree != null) {
                        root.addChild(lr.tree)
                    }
                    sync()
                }
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    // =========================================================================
    // Rules
    // =========================================================================

    fun rules(): ParserRuleReturn {
        val startTok = lt(1)
        val root = GrammarAST(CommonToken(ANTLRParserTypes.RULES, "RULES"))
        try {
            withFollow(RULE_START_OR_EOR) {
                sync()
                while (RULE_START.contains(la(1))) {
                    val r = rule()
                    if (r.tree != null) {
                        root.addChild(r.tree)
                    }
                    sync()
                }
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
        }
        return finalizeReturn(root, startTok)
    }

    fun rule(): ParserRuleReturn {
        val startTok = lt(1)
        return when (la(1)) {
            ANTLRParserTypes.RULE_REF -> parserRule()
            ANTLRParserTypes.FRAGMENT, ANTLRParserTypes.TOKEN_REF -> lexerRule()
            else -> {
                val ce = noViableAlt(startTok)
                reportParserError(ce.recognitionException)
                recoverParser(ce.recognitionException)
                finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
            }
        }
    }

    fun parserRule(): ParserRuleReturn {
        val startTok = lt(1)
        paraphrases.addLast("matching a rule")
        val root = RuleAST(CommonToken(ANTLRParserTypes.RULE, "RULE"))
        try {
            val nameTok = matchToken(ANTLRParserTypes.RULE_REF, PARSER_RULE_AFTER_NAME)
            root.addChild(GrammarAST(nameTok))

            if (la(1) == ANTLRParserTypes.ARG_ACTION) {
                val argTok = matchToken(ANTLRParserTypes.ARG_ACTION, null)
                root.addChild(ActionAST(argTok))
            }
            if (la(1) == ANTLRParserTypes.RETURNS) {
                val ret = withFollow(PARSER_RULE_AFTER_RETURNS) { ruleReturns() }
                if (ret.tree != null) {
                    root.addChild(ret.tree)
                }
            }
            if (la(1) == ANTLRParserTypes.THROWS) {
                val thr = withFollow(PARSER_RULE_AFTER_THROWS) { throwsSpec() }
                if (thr.tree != null) {
                    root.addChild(thr.tree)
                }
            }
            if (la(1) == ANTLRParserTypes.LOCALS) {
                val loc = withFollow(PARSER_RULE_AFTER_LOCALS) { localsSpec() }
                if (loc.tree != null) {
                    root.addChild(loc.tree)
                }
            }

            val prequels = withFollow(setOfTokens(ANTLRParserTypes.COLON)) { rulePrequels() }
            for (p in prequels) {
                root.addChild(p)
            }

            val colonTok = matchToken(ANTLRParserTypes.COLON, RULE_BLOCK_START)

            val blk = withFollow(setOfTokens(ANTLRParserTypes.SEMI)) { ruleBlock(colonTok) }
            if (blk.tree != null) {
                root.addChild(blk.tree)
            }

            matchToken(ANTLRParserTypes.SEMI, EXCEPTION_GROUP_OR_EOR)

            val exNodes = exceptionGroup()
            for (exNode in exNodes) {
                root.addChild(exNode)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            return finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        } finally {
            if (paraphrases.isNotEmpty()) {
                paraphrases.removeLast()
            }
            val options = root.getFirstChildWithType(ANTLRParserTypes.OPTIONS)
            if (options != null) {
                ANTLRParserTypes.setNodeOptions(root, options)
            }
        }
        return finalizeReturn(root, startTok)
    }

    private fun exceptionGroup(): List<GrammarAST> {
        val list = ArrayList<GrammarAST>()
        while (la(1) == ANTLRParserTypes.CATCH) {
            val h = withFollow(EXCEPTION_GROUP_OR_EOR) { exceptionHandler() }
            if (h.tree != null) {
                list.add(h.tree)
            }
        }
        if (la(1) == ANTLRParserTypes.FINALLY) {
            val f = withFollow(setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE)) { finallyClause() }
            if (f.tree != null) {
                list.add(f.tree)
            }
        }
        return list
    }

    fun exceptionHandler(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val catchTok = matchToken(ANTLRParserTypes.CATCH, setOfTokens(ANTLRParserTypes.ARG_ACTION))
            val argTok = matchToken(ANTLRParserTypes.ARG_ACTION, setOfTokens(ANTLRParserTypes.ACTION))
            val actTok = matchToken(ANTLRParserTypes.ACTION, EXCEPTION_GROUP_OR_EOR)
            val root = GrammarAST(catchTok)
            root.addChild(ActionAST(argTok))
            root.addChild(ActionAST(actTok))
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun finallyClause(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val finTok = matchToken(ANTLRParserTypes.FINALLY, setOfTokens(ANTLRParserTypes.ACTION))
            val actTok = matchToken(ANTLRParserTypes.ACTION, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
            val root = GrammarAST(finTok)
            root.addChild(ActionAST(actTok))
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    private fun rulePrequels(): List<GrammarAST> {
        val result = ArrayList<GrammarAST>()
        paraphrases.addLast("matching rule preamble")
        try {
            withFollow(RULE_PREQUEL_SYNC) {
                sync()
                while (la(1) == ANTLRParserTypes.OPTIONS || la(1) == ANTLRParserTypes.AT) {
                    val p = rulePrequel()
                    if (p.tree != null) {
                        result.add(p.tree)
                    }
                    sync()
                }
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
        } finally {
            if (paraphrases.isNotEmpty()) {
                paraphrases.removeLast()
            }
        }
        return result
    }

    fun rulePrequel(): ParserRuleReturn =
        if (la(1) == ANTLRParserTypes.OPTIONS) {
            optionsSpec()
        } else {
            ruleAction()
        }

    fun ruleReturns(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val retTok = matchToken(ANTLRParserTypes.RETURNS, setOfTokens(ANTLRParserTypes.ARG_ACTION))
            val argTok = matchToken(ANTLRParserTypes.ARG_ACTION, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
            val root = GrammarAST(retTok)
            root.addChild(ActionAST(argTok))
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun throwsSpec(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val thrTok = matchToken(ANTLRParserTypes.THROWS, SET_ID_START)
            val root = GrammarAST(thrTok)
            val first = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.EOR_TOKEN_TYPE)) { qid() }
            if (first.tree != null) {
                root.addChild(first.tree)
            }
            while (la(1) == ANTLRParserTypes.COMMA) {
                matchToken(ANTLRParserTypes.COMMA, SET_ID_START)
                val next = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.EOR_TOKEN_TYPE)) { qid() }
                if (next.tree != null) {
                    root.addChild(next.tree)
                }
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun localsSpec(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val locTok = matchToken(ANTLRParserTypes.LOCALS, setOfTokens(ANTLRParserTypes.ARG_ACTION))
            val argTok = matchToken(ANTLRParserTypes.ARG_ACTION, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
            val root = GrammarAST(locTok)
            root.addChild(ActionAST(argTok))
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun ruleAction(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val atTok = matchToken(ANTLRParserTypes.AT, SET_ID_START)
            val name = withFollow(setOfTokens(ANTLRParserTypes.ACTION)) { id() }
            val actTok = matchToken(ANTLRParserTypes.ACTION, RULE_PREQUEL_LOCAL_FOLLOW)
            val root = GrammarAST(atTok)
            if (name.tree != null) {
                root.addChild(name.tree)
            }
            root.addChild(ActionAST(actTok))
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    private fun ruleBlock(colonTok: Token?): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val root = BlockAST(ANTLRParserTypes.BLOCK, colonTok, "BLOCK")
            val alts = ruleAltList()
            for (alt in alts) {
                root.addChild(alt)
            }
            finalizeReturn(root, startTok)
        } catch (_: ResyncToEndOfRuleBlock) {
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), null), startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    private fun ruleAltList(): List<GrammarAST> {
        val list = ArrayList<GrammarAST>()
        val first = withFollow(setOfTokens(ANTLRParserTypes.OR, ANTLRParserTypes.EOR_TOKEN_TYPE)) { labeledAlt() }
        if (first.tree != null) {
            list.add(first.tree)
        }
        while (la(1) == ANTLRParserTypes.OR) {
            matchToken(ANTLRParserTypes.OR, RULE_BLOCK_START)
            val next = withFollow(setOfTokens(ANTLRParserTypes.OR, ANTLRParserTypes.EOR_TOKEN_TYPE)) { labeledAlt() }
            if (next.tree != null) {
                list.add(next.tree)
            }
        }
        return list
    }

    fun labeledAlt(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val alt = withFollow(setOfTokens(ANTLRParserTypes.POUND, ANTLRParserTypes.EOR_TOKEN_TYPE)) { alternative() }
            if (la(1) == ANTLRParserTypes.POUND) {
                matchToken(ANTLRParserTypes.POUND, SET_ID_START)
                val labelId = withFollow(setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE)) { id() }
                (alt.tree as? AltAST)?.altLabel = labelId.tree
            }
            finalizeReturn(alt.tree, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun lexerRule(): ParserRuleReturn {
        val startTok = lt(1)
        paraphrases.addLast("matching a lexer rule")
        val root = RuleAST(CommonToken(ANTLRParserTypes.RULE, "RULE"))
        try {
            var fragTok: Token? = null
            if (la(1) == ANTLRParserTypes.FRAGMENT) {
                fragTok = matchToken(ANTLRParserTypes.FRAGMENT, setOfTokens(ANTLRParserTypes.TOKEN_REF))
            }
            val tokRef =
                matchToken(
                    ANTLRParserTypes.TOKEN_REF,
                    setOfTokens(ANTLRParserTypes.OPTIONS, ANTLRParserTypes.COLON),
                )
            root.addChild(GrammarAST(tokRef))
            if (fragTok != null) {
                val mods = GrammarAST(CommonToken(ANTLRParserTypes.RULEMODIFIERS, "RULEMODIFIERS"))
                mods.addChild(GrammarAST(fragTok))
                root.addChild(mods)
            }
            if (la(1) == ANTLRParserTypes.OPTIONS) {
                val opts = withFollow(setOfTokens(ANTLRParserTypes.COLON)) { optionsSpec() }
                if (opts.tree != null) {
                    root.addChild(opts.tree)
                }
            }
            val colonTok = matchToken(ANTLRParserTypes.COLON, LEXER_RULE_BLOCK_NONEMPTY_START)
            val blk = withFollow(setOfTokens(ANTLRParserTypes.SEMI)) { lexerRuleBlock(colonTok) }
            if (blk.tree != null) {
                root.addChild(blk.tree)
            }
            matchToken(ANTLRParserTypes.SEMI, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            return finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        } finally {
            if (paraphrases.isNotEmpty()) {
                paraphrases.removeLast()
            }
        }
        return finalizeReturn(root, startTok)
    }

    private fun lexerRuleBlock(colonTok: Token?): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val root = BlockAST(ANTLRParserTypes.BLOCK, colonTok, "BLOCK")
            val alts = lexerAltList()
            for (alt in alts) {
                root.addChild(alt)
            }
            finalizeReturn(root, startTok)
        } catch (_: ResyncToEndOfRuleBlock) {
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), null), startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    private fun lexerAltList(): List<GrammarAST> {
        val list = ArrayList<GrammarAST>()
        val first = withFollow(setOfTokens(ANTLRParserTypes.OR, ANTLRParserTypes.EOR_TOKEN_TYPE)) { lexerAlt() }
        if (first.tree != null) {
            list.add(first.tree)
        }
        while (la(1) == ANTLRParserTypes.OR) {
            matchToken(ANTLRParserTypes.OR, LEXER_ALT_START_OR_END)
            val next = withFollow(setOfTokens(ANTLRParserTypes.OR, ANTLRParserTypes.EOR_TOKEN_TYPE)) { lexerAlt() }
            if (next.tree != null) {
                list.add(next.tree)
            }
        }
        return list
    }

    fun lexerAlt(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val elems = withFollow(setOfTokens(ANTLRParserTypes.RARROW, ANTLRParserTypes.EOR_TOKEN_TYPE)) { lexerElements() }
            if (la(1) == ANTLRParserTypes.RARROW) {
                val cmds = lexerCommands()
                val root = AltAST(ANTLRParserTypes.LEXER_ALT_ACTION, startTok, "LEXER_ALT_ACTION")
                if (elems.tree != null) {
                    root.addChild(elems.tree)
                }
                for (cmd in cmds) {
                    root.addChild(cmd)
                }
                finalizeReturn(root, startTok)
            } else {
                finalizeReturn(elems.tree, startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun lexerElements(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val root = AltAST(ANTLRParserTypes.ALT, startTok, "ALT")
            if (LEXER_ELEMENT_START.contains(la(1))) {
                while (LEXER_ELEMENT_START.contains(la(1))) {
                    val elem = withFollow(LEXER_ELEMENT_FOLLOW) { lexerElement() }
                    if (elem.tree != null) {
                        root.addChild(elem.tree)
                    }
                }
            } else if (LEXER_ALT_END.contains(la(1))) {
                root.addChild(GrammarAST(CommonToken(ANTLRParserTypes.EPSILON, "EPSILON")))
            } else {
                val elem = withFollow(LEXER_ELEMENT_FOLLOW) { lexerElement() }
                if (elem.tree != null) {
                    root.addChild(elem.tree)
                }
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun lexerElement(): ParserRuleReturn {
        val startTok = lt(1)
        paraphrases.addLast("looking for lexer rule element")
        try {
            val resultNode: GrammarAST? =
                when {
                    la(1) == ANTLRParserTypes.LPAREN -> {
                        val blk = lexerBlock()
                        if (EBNF_SUFFIX_START.contains(la(1))) {
                            val suf = ebnfSuffix()
                            val root = checkNotNull(suf.tree) { "ebnfSuffix tree cannot be null" }
                            if (blk.tree != null) {
                                root.addChild(blk.tree)
                            }
                            root
                        } else if (LEXER_ELEMENT_FOLLOW.contains(la(1))) {
                            blk.tree
                        } else {
                            throw noViableAlt(lt(1))
                        }
                    }
                    la(1) == ANTLRParserTypes.ACTION || la(1) == ANTLRParserTypes.SEMPRED -> {
                        val act = actionElement()
                        if (!LEXER_ELEMENT_FOLLOW.contains(la(1)) && !EBNF_SUFFIX_START.contains(la(1))) {
                            throw noViableAlt(lt(1))
                        }
                        if (EBNF_SUFFIX_START.contains(la(1))) {
                            val suf = ebnfSuffix()
                            val root = checkNotNull(suf.tree) { "ebnfSuffix tree cannot be null" }
                            val blk = BlockAST(ANTLRParserTypes.BLOCK, act.start, "BLOCK")
                            val alt = AltAST(ANTLRParserTypes.ALT, act.start, "ALT")
                            if (act.tree != null) {
                                alt.addChild(act.tree)
                            }
                            blk.addChild(alt)
                            root.addChild(blk)
                            root
                        } else {
                            act.tree
                        }
                    }
                    LEXER_ATOM_START.contains(la(1)) -> {
                        val atomRes = lexerAtom()
                        if (EBNF_SUFFIX_START.contains(la(1))) {
                            val suf = ebnfSuffix()
                            val root = checkNotNull(suf.tree) { "ebnfSuffix tree cannot be null" }
                            val blk = BlockAST(ANTLRParserTypes.BLOCK, atomRes.start, "BLOCK")
                            val alt = AltAST(ANTLRParserTypes.ALT, atomRes.start, "ALT")
                            if (atomRes.tree != null) {
                                alt.addChild(atomRes.tree)
                            }
                            blk.addChild(alt)
                            root.addChild(blk)
                            root
                        } else if (LEXER_ELEMENT_FOLLOW.contains(la(1))) {
                            atomRes.tree
                        } else {
                            throw noViableAlt(lt(1))
                        }
                    }
                    else -> throw noViableAlt(startTok)
                }
            return finalizeReturn(resultNode, startTok)
        } catch (ce: ParserControlException) {
            val re = ce.recognitionException
            val errNode = treeAdaptor.errorNode(tokenStream, startTok, lt(-1), re)
            val rangeIdx = maxOf(tokenStream.index(), maxLookaheadIndex)
            val rangeTok = if (rangeIdx in 0 until tokenStream.size()) tokenStream.get(rangeIdx) else lt(1)
            val ttype = rangeTok?.type ?: Token.INVALID_TYPE
            if (
                ttype == ANTLRParserTypes.COLON ||
                ttype == ANTLRParserTypes.RETURNS ||
                ttype == ANTLRParserTypes.CATCH ||
                ttype == ANTLRParserTypes.FINALLY ||
                ttype == ANTLRParserTypes.AT ||
                ttype == Token.EOF
            ) {
                val t1 = lt(1)
                val t2 = lt(2)
                val missingSemi =
                    V4ParserException(
                        "unterminated rule (missing ';') detected at '${t1?.text.orEmpty()} ${t2?.text.orEmpty()}'",
                        tokenStream,
                        t1,
                    )
                reportParserError(missingSemi)
                if (ttype == Token.EOF) {
                    val nextIdx = (tokenStream.index() + 1).coerceAtMost(tokenStream.size() - 1)
                    tokenStream.seek(nextIdx)
                } else if (ttype == ANTLRParserTypes.CATCH || ttype == ANTLRParserTypes.FINALLY) {
                    tokenStream.seek(rangeIdx)
                } else if (ttype == ANTLRParserTypes.RETURNS || ttype == ANTLRParserTypes.AT) {
                    var p = tokenStream.index()
                    while (p > 0) {
                        val pt = tokenStream.get(p)?.type ?: Token.INVALID_TYPE
                        if (pt == ANTLRParserTypes.RULE_REF || pt == ANTLRParserTypes.TOKEN_REF) {
                            break
                        }
                        p--
                    }
                    tokenStream.seek(p)
                }
                throw ResyncToEndOfRuleBlock()
            }
            reportParserError(re, ce.formattedMessage)
            recoverParser(re)
            return finalizeReturn(errNode, startTok)
        } finally {
            if (paraphrases.isNotEmpty()) {
                paraphrases.removeLast()
            }
        }
    }

    fun lexerBlock(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val lparen = matchToken(ANTLRParserTypes.LPAREN, LEXER_BLOCK_INNER_START)
            val root = BlockAST(ANTLRParserTypes.BLOCK, lparen, "BLOCK")
            if (la(1) == ANTLRParserTypes.OPTIONS) {
                val opts = withFollow(setOfTokens(ANTLRParserTypes.COLON)) { optionsSpec() }
                if (opts.tree != null) {
                    root.addChild(opts.tree)
                }
                matchToken(ANTLRParserTypes.COLON, LEXER_ALT_START_OR_END)
            }
            val alts = withFollow(setOfTokens(ANTLRParserTypes.RPAREN)) { lexerAltList() }
            for (alt in alts) {
                root.addChild(alt)
            }
            matchToken(ANTLRParserTypes.RPAREN, LEXER_ELEMENT_FOLLOW_OR_SUFFIX)
            val options = root.getFirstChildWithType(ANTLRParserTypes.OPTIONS)
            if (options != null) {
                ANTLRParserTypes.setNodeOptions(root, options)
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    private fun lexerCommands(): List<GrammarAST> {
        val list = ArrayList<GrammarAST>()
        matchToken(ANTLRParserTypes.RARROW, LEXER_COMMAND_NAME_START)
        val first = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.EOR_TOKEN_TYPE)) { lexerCommand() }
        if (first.tree != null) {
            list.add(first.tree)
        }
        while (la(1) == ANTLRParserTypes.COMMA) {
            matchToken(ANTLRParserTypes.COMMA, LEXER_COMMAND_NAME_START)
            val next = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.EOR_TOKEN_TYPE)) { lexerCommand() }
            if (next.tree != null) {
                list.add(next.tree)
            }
        }
        return list
    }

    fun lexerCommand(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val cmdName = withFollow(setOfTokens(ANTLRParserTypes.LPAREN, ANTLRParserTypes.EOR_TOKEN_TYPE)) { lexerCommandName() }
            if (la(1) == ANTLRParserTypes.LPAREN) {
                matchToken(ANTLRParserTypes.LPAREN, LEXER_COMMAND_EXPR_START)
                val expr = withFollow(setOfTokens(ANTLRParserTypes.RPAREN)) { lexerCommandExpr() }
                matchToken(ANTLRParserTypes.RPAREN, setOfTokens(ANTLRParserTypes.EOR_TOKEN_TYPE))
                val root = GrammarAST(CommonToken(ANTLRParserTypes.LEXER_ACTION_CALL, "LEXER_ACTION_CALL"))
                if (cmdName.tree != null) {
                    root.addChild(cmdName.tree)
                }
                if (expr.tree != null) {
                    root.addChild(expr.tree)
                }
                finalizeReturn(root, startTok)
            } else {
                finalizeReturn(cmdName.tree, startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun lexerCommandExpr(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            when (la(1)) {
                ANTLRParserTypes.RULE_REF, ANTLRParserTypes.TOKEN_REF -> id()
                ANTLRParserTypes.INT -> {
                    val intTok = matchToken(ANTLRParserTypes.INT, null)
                    finalizeReturn(GrammarAST(intTok), startTok)
                }
                else -> throw noViableAlt(startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun lexerCommandName(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            when (la(1)) {
                ANTLRParserTypes.RULE_REF, ANTLRParserTypes.TOKEN_REF -> id()
                ANTLRParserTypes.MODE -> {
                    val m = matchToken(ANTLRParserTypes.MODE, null)
                    finalizeReturn(GrammarAST(ANTLRParserTypes.ID, m), startTok)
                }
                else -> throw noViableAlt(startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    // =========================================================================
    // Parser Alternatives & Elements
    // =========================================================================

    private fun altList(): List<GrammarAST> {
        val list = ArrayList<GrammarAST>()
        val first = withFollow(setOfTokens(ANTLRParserTypes.OR, ANTLRParserTypes.EOR_TOKEN_TYPE)) { alternative() }
        if (first.tree != null) {
            list.add(first.tree)
        }
        while (la(1) == ANTLRParserTypes.OR) {
            matchToken(ANTLRParserTypes.OR, RULE_BLOCK_START)
            val next = withFollow(setOfTokens(ANTLRParserTypes.OR, ANTLRParserTypes.EOR_TOKEN_TYPE)) { alternative() }
            if (next.tree != null) {
                list.add(next.tree)
            }
        }
        return list
    }

    fun alternative(): ParserRuleReturn {
        val startTok = lt(1)
        paraphrases.addLast("matching alternative")
        var opts: ParserRuleReturn? = null
        val root = AltAST(ANTLRParserTypes.ALT, startTok, "ALT")
        try {
            if (la(1) == ANTLRParserTypes.LT) {
                opts = withFollow(ELEMENT_START_OR_ALT_END) { elementOptions() }
                if (opts.tree != null) {
                    root.addChild(opts.tree)
                }
            }
            if (ELEMENT_START.contains(la(1))) {
                while (ELEMENT_START.contains(la(1))) {
                    val elem = withFollow(ELEMENT_FOLLOW) { element() }
                    if (elem.tree != null) {
                        root.addChild(elem.tree)
                    }
                }
            } else if (ALT_END.contains(la(1))) {
                root.addChild(GrammarAST(CommonToken(ANTLRParserTypes.EPSILON, "EPSILON")))
            } else {
                throw noViableAlt(lt(1))
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            return finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        } finally {
            if (paraphrases.isNotEmpty()) {
                paraphrases.removeLast()
            }
            ANTLRParserTypes.setNodeOptions(root, opts?.tree)
        }
        return finalizeReturn(root, startTok)
    }

    fun element(): ParserRuleReturn {
        val startTok = lt(1)
        paraphrases.addLast("looking for rule element")
        try {
            val la1 = la(1)
            val resultNode: GrammarAST? =
                when {
                    (la1 == ANTLRParserTypes.RULE_REF || la1 == ANTLRParserTypes.TOKEN_REF) &&
                        (la(2) == ANTLRParserTypes.ASSIGN || la(2) == ANTLRParserTypes.PLUS_ASSIGN) -> {
                        val labeled = labeledElement()
                        wrapOptionalElementSuffix(labeled)
                    }
                    la1 == ANTLRParserTypes.RULE_REF -> {
                        val la2 = la(2)
                        if (
                            la2 != ANTLRParserTypes.ARG_ACTION &&
                            la2 != ANTLRParserTypes.LT &&
                            !EBNF_SUFFIX_START.contains(la2) &&
                            !ELEMENT_FOLLOW.contains(la2)
                        ) {
                            throw noViableAlt(startTok)
                        }
                        val atomRes = atom()
                        wrapOptionalElementSuffix(atomRes)
                    }
                    la1 == ANTLRParserTypes.TOKEN_REF -> {
                        val la2 = la(2)
                        if (
                            la2 != ANTLRParserTypes.LT &&
                            !EBNF_SUFFIX_START.contains(la2) &&
                            !ELEMENT_FOLLOW.contains(la2)
                        ) {
                            throw noViableAlt(startTok)
                        }
                        val atomRes = atom()
                        wrapOptionalElementSuffix(atomRes)
                    }
                    ATOM_NON_ID_START.contains(la1) -> {
                        val atomRes = atom()
                        wrapOptionalElementSuffix(atomRes)
                    }
                    la1 == ANTLRParserTypes.LPAREN -> {
                        val eb = ebnf()
                        if (!ELEMENT_FOLLOW.contains(la(1))) {
                            throw noViableAlt(lt(1))
                        }
                        eb.tree
                    }
                    la1 == ANTLRParserTypes.ACTION || la1 == ANTLRParserTypes.SEMPRED -> {
                        val act = actionElement()
                        if (!ELEMENT_FOLLOW.contains(la(1))) {
                            throw noViableAlt(lt(1))
                        }
                        act.tree
                    }
                    else -> throw noViableAlt(startTok)
                }
            return finalizeReturn(resultNode, startTok)
        } catch (ce: ParserControlException) {
            val re = ce.recognitionException
            val errNode = treeAdaptor.errorNode(tokenStream, startTok, lt(-1), re)
            val rangeIdx = maxOf(tokenStream.index(), maxLookaheadIndex)
            val rangeTok = if (rangeIdx in 0 until tokenStream.size()) tokenStream.get(rangeIdx) else lt(1)
            val ttype = rangeTok?.type ?: Token.INVALID_TYPE
            if (
                ttype == ANTLRParserTypes.COLON ||
                ttype == ANTLRParserTypes.RETURNS ||
                ttype == ANTLRParserTypes.CATCH ||
                ttype == ANTLRParserTypes.FINALLY ||
                ttype == ANTLRParserTypes.AT
            ) {
                val t1 = lt(1)
                val t2 = lt(2)
                val missingSemi =
                    V4ParserException(
                        "unterminated rule (missing ';') detected at '${t1?.text.orEmpty()} ${t2?.text.orEmpty()}'",
                        tokenStream,
                        t1,
                    )
                reportParserError(missingSemi)
                if (ttype == ANTLRParserTypes.CATCH || ttype == ANTLRParserTypes.FINALLY) {
                    tokenStream.seek(rangeIdx)
                }
                if (ttype == ANTLRParserTypes.RETURNS || ttype == ANTLRParserTypes.AT) {
                    var p = tokenStream.index()
                    while (p > 0) {
                        val pt = tokenStream.get(p)?.type ?: Token.INVALID_TYPE
                        if (pt == ANTLRParserTypes.RULE_REF || pt == ANTLRParserTypes.TOKEN_REF) {
                            break
                        }
                        p--
                    }
                    tokenStream.seek(p)
                }
                throw ResyncToEndOfRuleBlock()
            }
            reportParserError(re, ce.formattedMessage)
            recoverParser(re)
            return finalizeReturn(errNode, startTok)
        } finally {
            if (paraphrases.isNotEmpty()) {
                paraphrases.removeLast()
            }
        }
    }

    private fun wrapOptionalElementSuffix(inner: ParserRuleReturn): GrammarAST? =
        if (EBNF_SUFFIX_START.contains(la(1))) {
            val suf = ebnfSuffix()
            if (!ELEMENT_FOLLOW.contains(la(1))) {
                throw noViableAlt(lt(1))
            }
            val root = checkNotNull(suf.tree) { "ebnfSuffix tree cannot be null" }
            val blk = BlockAST(ANTLRParserTypes.BLOCK, inner.start, "BLOCK")
            val alt = AltAST(ANTLRParserTypes.ALT, inner.start, "ALT")
            if (inner.tree != null) {
                alt.addChild(inner.tree)
            }
            blk.addChild(alt)
            root.addChild(blk)
            root
        } else if (ELEMENT_FOLLOW.contains(la(1))) {
            inner.tree
        } else {
            throw noViableAlt(lt(1))
        }

    fun actionElement(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val root: GrammarAST =
                if (la(1) == ANTLRParserTypes.ACTION) {
                    val t = matchToken(ANTLRParserTypes.ACTION, null)
                    ActionAST(t)
                } else {
                    val t = matchToken(ANTLRParserTypes.SEMPRED, null)
                    PredAST(t)
                }
            if (la(1) == ANTLRParserTypes.LT) {
                val opts = elementOptions()
                if (opts.tree != null) {
                    root.addChild(opts.tree)
                }
            }
            val options = root.getFirstChildWithType(ANTLRParserTypes.ELEMENT_OPTIONS)
            if (options != null) {
                ANTLRParserTypes.setNodeOptions(root, options)
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun labeledElement(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val label = id()
            val assignTok =
                if (la(1) == ANTLRParserTypes.ASSIGN) {
                    matchToken(ANTLRParserTypes.ASSIGN, ATOM_OR_LPAREN_START)
                } else {
                    matchToken(ANTLRParserTypes.PLUS_ASSIGN, ATOM_OR_LPAREN_START)
                }
            val root = GrammarAST(assignTok)
            if (label.tree != null) {
                root.addChild(label.tree)
            }
            val rhs =
                if (la(1) == ANTLRParserTypes.LPAREN) {
                    block()
                } else {
                    atom()
                }
            if (rhs.tree != null) {
                root.addChild(rhs.tree)
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun ebnf(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val blk = block()
            if (EBNF_SUFFIX_START.contains(la(1))) {
                val suf = ebnfSuffix()
                val root = checkNotNull(suf.tree) { "ebnfSuffix tree cannot be null" }
                if (blk.tree != null) {
                    root.addChild(blk.tree)
                }
                finalizeReturn(root, startTok)
            } else {
                finalizeReturn(blk.tree, startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun ebnfSuffix(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val node: GrammarAST =
                when (la(1)) {
                    ANTLRParserTypes.QUESTION -> {
                        val q = matchToken(ANTLRParserTypes.QUESTION, null)
                        val ng = if (la(1) == ANTLRParserTypes.QUESTION) matchToken(ANTLRParserTypes.QUESTION, null) else null
                        OptionalBlockAST(ANTLRParserTypes.OPTIONAL, q, ng)
                    }
                    ANTLRParserTypes.STAR -> {
                        val s = matchToken(ANTLRParserTypes.STAR, null)
                        val ng = if (la(1) == ANTLRParserTypes.QUESTION) matchToken(ANTLRParserTypes.QUESTION, null) else null
                        StarBlockAST(ANTLRParserTypes.CLOSURE, s, ng)
                    }
                    ANTLRParserTypes.PLUS -> {
                        val p = matchToken(ANTLRParserTypes.PLUS, null)
                        val ng = if (la(1) == ANTLRParserTypes.QUESTION) matchToken(ANTLRParserTypes.QUESTION, null) else null
                        PlusBlockAST(ANTLRParserTypes.POSITIVE_CLOSURE, p, ng)
                    }
                    else -> throw noViableAlt(startTok)
                }
            finalizeReturn(node, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun lexerAtom(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            when (la(1)) {
                ANTLRParserTypes.STRING_LITERAL -> {
                    if (la(2) == ANTLRParserTypes.RANGE) {
                        range()
                    } else {
                        terminal()
                    }
                }
                ANTLRParserTypes.TOKEN_REF -> terminal()
                ANTLRParserTypes.RULE_REF -> {
                    val r = matchToken(ANTLRParserTypes.RULE_REF, null)
                    finalizeReturn(RuleRefAST(r), startTok)
                }
                ANTLRParserTypes.NOT -> notSet()
                ANTLRParserTypes.DOT -> wildcard()
                ANTLRParserTypes.LEXER_CHAR_SET -> {
                    val cs = matchToken(ANTLRParserTypes.LEXER_CHAR_SET, null)
                    finalizeReturn(GrammarAST(cs), startTok)
                }
                else -> throw noViableAlt(startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    // `atom` intentionally propagates ParserControlException upward to `element` per ANTLRParser.g:739.
    fun atom(): ParserRuleReturn {
        val startTok = lt(1)
        return when (la(1)) {
            ANTLRParserTypes.STRING_LITERAL -> {
                if (la(2) == ANTLRParserTypes.RANGE) {
                    range()
                } else {
                    terminal()
                }
            }
            ANTLRParserTypes.TOKEN_REF -> terminal()
            ANTLRParserTypes.RULE_REF -> ruleref()
            ANTLRParserTypes.NOT -> notSet()
            ANTLRParserTypes.DOT -> wildcard()
            else -> throw noViableAlt(startTok)
        }
    }

    fun wildcard(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val dot = matchToken(ANTLRParserTypes.DOT, null)
            val root = TerminalAST(ANTLRParserTypes.WILDCARD, dot)
            if (la(1) == ANTLRParserTypes.LT) {
                val opts = elementOptions()
                if (opts.tree != null) {
                    root.addChild(opts.tree)
                }
            }
            val options = root.getFirstChildWithType(ANTLRParserTypes.ELEMENT_OPTIONS)
            if (options != null) {
                ANTLRParserTypes.setNodeOptions(root, options)
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun notSet(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val notTok = matchToken(ANTLRParserTypes.NOT, NOT_TARGET_START)
            val root = NotAST(ANTLRParserTypes.NOT, notTok)
            if (la(1) == ANTLRParserTypes.LPAREN) {
                val bs = blockSet()
                if (bs.tree != null) {
                    root.addChild(bs.tree)
                }
            } else {
                val se = setElement()
                val setWrapper = SetAST(ANTLRParserTypes.SET, se.start, "SET")
                if (se.tree != null) {
                    setWrapper.addChild(se.tree)
                }
                treeAdaptor.setTokenBoundaries(setWrapper, se.start, se.stop)
                root.addChild(setWrapper)
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun blockSet(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val lparen = matchToken(ANTLRParserTypes.LPAREN, SET_ELEMENT_START)
            val root = SetAST(ANTLRParserTypes.SET, lparen, "SET")
            val first = setElement()
            if (first.tree != null) {
                root.addChild(first.tree)
            }
            while (la(1) == ANTLRParserTypes.OR) {
                matchToken(ANTLRParserTypes.OR, SET_ELEMENT_START)
                val next = setElement()
                if (next.tree != null) {
                    root.addChild(next.tree)
                }
            }
            matchToken(ANTLRParserTypes.RPAREN, null)
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun setElement(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            when (la(1)) {
                ANTLRParserTypes.TOKEN_REF -> {
                    val t = matchToken(ANTLRParserTypes.TOKEN_REF, null)
                    val root = TerminalAST(t)
                    if (la(1) == ANTLRParserTypes.LT) {
                        val opts = elementOptions()
                        if (opts.tree != null) {
                            root.addChild(opts.tree)
                        }
                    }
                    finalizeReturn(root, startTok)
                }
                ANTLRParserTypes.STRING_LITERAL -> {
                    if (la(2) == ANTLRParserTypes.RANGE) {
                        range()
                    } else {
                        val s = matchToken(ANTLRParserTypes.STRING_LITERAL, null)
                        val root = TerminalAST(s)
                        if (la(1) == ANTLRParserTypes.LT) {
                            val opts = elementOptions()
                            if (opts.tree != null) {
                                root.addChild(opts.tree)
                            }
                        }
                        finalizeReturn(root, startTok)
                    }
                }
                ANTLRParserTypes.LEXER_CHAR_SET -> {
                    val cs = matchToken(ANTLRParserTypes.LEXER_CHAR_SET, null)
                    finalizeReturn(GrammarAST(cs), startTok)
                }
                else -> throw noViableAlt(startTok)
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun block(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val lparen = matchToken(ANTLRParserTypes.LPAREN, BLOCK_INNER_START)
            val root = BlockAST(ANTLRParserTypes.BLOCK, lparen, "BLOCK")
            if (
                la(1) == ANTLRParserTypes.OPTIONS ||
                la(1) == ANTLRParserTypes.AT ||
                la(1) == ANTLRParserTypes.COLON
            ) {
                if (la(1) == ANTLRParserTypes.OPTIONS) {
                    val opts = withFollow(setOfTokens(ANTLRParserTypes.AT, ANTLRParserTypes.COLON)) { optionsSpec() }
                    if (opts.tree != null) {
                        root.addChild(opts.tree)
                    }
                }
                while (la(1) == ANTLRParserTypes.AT) {
                    val ra = withFollow(setOfTokens(ANTLRParserTypes.AT, ANTLRParserTypes.COLON)) { ruleAction() }
                    if (ra.tree != null) {
                        root.addChild(ra.tree)
                    }
                }
                matchToken(ANTLRParserTypes.COLON, RULE_BLOCK_START)
            }
            val alts = withFollow(setOfTokens(ANTLRParserTypes.RPAREN)) { altList() }
            for (alt in alts) {
                root.addChild(alt)
            }
            matchToken(ANTLRParserTypes.RPAREN, ELEMENT_FOLLOW_OR_SUFFIX)
            val options = root.getFirstChildWithType(ANTLRParserTypes.OPTIONS)
            if (options != null) {
                ANTLRParserTypes.setNodeOptions(root, options)
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    // `ruleref` intentionally propagates ParserControlException upward to `element` per ANTLRParser.g:819.
    fun ruleref(): ParserRuleReturn {
        val startTok = lt(1)
        val r = matchToken(ANTLRParserTypes.RULE_REF, null)
        val root = RuleRefAST(r)
        if (la(1) == ANTLRParserTypes.ARG_ACTION) {
            val arg = matchToken(ANTLRParserTypes.ARG_ACTION, null)
            root.addChild(ActionAST(arg))
        }
        if (la(1) == ANTLRParserTypes.LT) {
            val opts = elementOptions()
            if (opts.tree != null) {
                root.addChild(opts.tree)
            }
        }
        val options = root.getFirstChildWithType(ANTLRParserTypes.ELEMENT_OPTIONS)
        if (options != null) {
            ANTLRParserTypes.setNodeOptions(root, options)
        }
        return finalizeReturn(root, startTok)
    }

    fun range(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val left = matchToken(ANTLRParserTypes.STRING_LITERAL, setOfTokens(ANTLRParserTypes.RANGE))
            val dots = matchToken(ANTLRParserTypes.RANGE, setOfTokens(ANTLRParserTypes.STRING_LITERAL))
            val right = matchToken(ANTLRParserTypes.STRING_LITERAL, null)
            val root = RangeAST(dots)
            root.addChild(TerminalAST(left))
            root.addChild(TerminalAST(right))
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun terminal(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val tok =
                if (la(1) == ANTLRParserTypes.TOKEN_REF) {
                    matchToken(ANTLRParserTypes.TOKEN_REF, null)
                } else {
                    matchToken(ANTLRParserTypes.STRING_LITERAL, null)
                }
            val root = TerminalAST(tok)
            if (la(1) == ANTLRParserTypes.LT) {
                val opts = elementOptions()
                if (opts.tree != null) {
                    root.addChild(opts.tree)
                }
            }
            val options = root.getFirstChildWithType(ANTLRParserTypes.ELEMENT_OPTIONS)
            if (options != null) {
                ANTLRParserTypes.setNodeOptions(root, options)
            }
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun elementOptions(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            val ltTok = matchToken(ANTLRParserTypes.LT, SET_ID_OR_GT)
            val root = GrammarAST(ANTLRParserTypes.ELEMENT_OPTIONS, ltTok, "ELEMENT_OPTIONS")
            if (SET_ID_START.contains(la(1))) {
                val first = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.GT)) { elementOption() }
                if (first.tree != null) {
                    root.addChild(first.tree)
                }
                while (la(1) == ANTLRParserTypes.COMMA) {
                    matchToken(ANTLRParserTypes.COMMA, SET_ID_START)
                    val next = withFollow(setOfTokens(ANTLRParserTypes.COMMA, ANTLRParserTypes.GT)) { elementOption() }
                    if (next.tree != null) {
                        root.addChild(next.tree)
                    }
                }
            }
            matchToken(ANTLRParserTypes.GT, null)
            finalizeReturn(root, startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun elementOption(): ParserRuleReturn {
        val startTok = lt(1)
        return try {
            if (la(2) == ANTLRParserTypes.ASSIGN) {
                val key = id()
                val assignTok = matchToken(ANTLRParserTypes.ASSIGN, OPTION_VALUE_START)
                val root = GrammarAST(assignTok)
                if (key.tree != null) {
                    root.addChild(key.tree)
                }
                val v = optionValue()
                if (v.tree != null) {
                    root.addChild(v.tree)
                }
                finalizeReturn(root, startTok)
            } else {
                qid()
            }
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        }
    }

    fun id(): ParserRuleReturn {
        val startTok = lt(1)
        paraphrases.addLast("looking for an identifier")
        try {
            val tok =
                when (la(1)) {
                    ANTLRParserTypes.RULE_REF -> matchToken(ANTLRParserTypes.RULE_REF, null)
                    ANTLRParserTypes.TOKEN_REF -> matchToken(ANTLRParserTypes.TOKEN_REF, null)
                    else -> throw noViableAlt(startTok)
                }
            return finalizeReturn(GrammarAST(ANTLRParserTypes.ID, tok), startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            return finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        } finally {
            if (paraphrases.isNotEmpty()) {
                paraphrases.removeLast()
            }
        }
    }

    fun qid(): ParserRuleReturn {
        val startTok = lt(1)
        paraphrases.addLast("looking for a qualified identifier")
        try {
            val first = id()
            var lastTok = first.stop ?: startTok
            while (la(1) == ANTLRParserTypes.DOT) {
                matchToken(ANTLRParserTypes.DOT, SET_ID_START)
                val next = id()
                lastTok = next.stop ?: lastTok
            }
            val fullText =
                if (startTok != null && lastTok != null) {
                    tokenStream.getText(startTok, lastTok) ?: first.tree?.text.orEmpty()
                } else {
                    first.tree?.text.orEmpty()
                }
            return finalizeReturn(GrammarAST(ANTLRParserTypes.ID, startTok, fullText), startTok)
        } catch (ce: ParserControlException) {
            reportParserError(ce.recognitionException, ce.formattedMessage)
            recoverParser(ce.recognitionException)
            return finalizeReturn(treeAdaptor.errorNode(tokenStream, startTok, lt(-1), ce.recognitionException), startTok)
        } finally {
            if (paraphrases.isNotEmpty()) {
                paraphrases.removeLast()
            }
        }
    }

    companion object {
        private fun setOfTokens(vararg types: Int): IntervalSet =
            IntervalSet().apply {
                for (t in types) {
                    add(t)
                }
            }

        private val SET_ID_START: IntervalSet =
            setOfTokens(ANTLRParserTypes.RULE_REF, ANTLRParserTypes.TOKEN_REF)

        private val SET_ID_OR_RBRACE: IntervalSet =
            setOfTokens(ANTLRParserTypes.RULE_REF, ANTLRParserTypes.TOKEN_REF, ANTLRParserTypes.RBRACE)

        private val SET_ID_OR_GT: IntervalSet =
            setOfTokens(ANTLRParserTypes.RULE_REF, ANTLRParserTypes.TOKEN_REF, ANTLRParserTypes.GT)

        private val OPTION_VALUE_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.RULE_REF,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.STRING_LITERAL,
                ANTLRParserTypes.ACTION,
                ANTLRParserTypes.INT,
            )

        private val ACTION_SCOPE_OR_ID_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.RULE_REF,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.LEXER,
                ANTLRParserTypes.PARSER,
            )

        private val PREQUEL_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.OPTIONS,
                ANTLRParserTypes.IMPORT,
                ANTLRParserTypes.TOKENS_SPEC,
                ANTLRParserTypes.CHANNELS,
                ANTLRParserTypes.AT,
            )

        private val RULE_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.FRAGMENT,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.RULE_REF,
            )

        private val RULE_START_OR_EOR: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.FRAGMENT,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.RULE_REF,
                ANTLRParserTypes.EOR_TOKEN_TYPE,
            )

        private val FOLLOW_SEMI_IN_GRAMMAR_SPEC: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.OPTIONS,
                ANTLRParserTypes.IMPORT,
                ANTLRParserTypes.TOKENS_SPEC,
                ANTLRParserTypes.CHANNELS,
                ANTLRParserTypes.AT,
                ANTLRParserTypes.FRAGMENT,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.RULE_REF,
                ANTLRParserTypes.MODE,
                Token.EOF,
            )

        private val FOLLOW_PREQUEL_LOOP_IN_GRAMMAR_SPEC: IntervalSet =
            FOLLOW_SEMI_IN_GRAMMAR_SPEC

        private val FOLLOW_RULES_IN_GRAMMAR_SPEC: IntervalSet =
            setOfTokens(ANTLRParserTypes.MODE, Token.EOF)

        private val MODE_SPEC_LEXER_RULE_SYNC: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.FRAGMENT,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.MODE,
                Token.EOF,
            )

        private val PARSER_RULE_AFTER_NAME: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.ARG_ACTION,
                ANTLRParserTypes.RETURNS,
                ANTLRParserTypes.THROWS,
                ANTLRParserTypes.LOCALS,
                ANTLRParserTypes.OPTIONS,
                ANTLRParserTypes.AT,
                ANTLRParserTypes.COLON,
            )

        private val PARSER_RULE_AFTER_RETURNS: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.THROWS,
                ANTLRParserTypes.LOCALS,
                ANTLRParserTypes.OPTIONS,
                ANTLRParserTypes.AT,
                ANTLRParserTypes.COLON,
            )

        private val PARSER_RULE_AFTER_THROWS: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.LOCALS,
                ANTLRParserTypes.OPTIONS,
                ANTLRParserTypes.AT,
                ANTLRParserTypes.COLON,
            )

        private val PARSER_RULE_AFTER_LOCALS: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.OPTIONS,
                ANTLRParserTypes.AT,
                ANTLRParserTypes.COLON,
            )

        private val RULE_PREQUEL_SYNC: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.OPTIONS,
                ANTLRParserTypes.AT,
                ANTLRParserTypes.EOR_TOKEN_TYPE,
            )

        private val RULE_PREQUEL_LOCAL_FOLLOW: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.OPTIONS,
                ANTLRParserTypes.AT,
            )

        private val EBNF_SUFFIX_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.QUESTION,
                ANTLRParserTypes.STAR,
                ANTLRParserTypes.PLUS,
            )

        private val ATOM_NON_ID_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.STRING_LITERAL,
                ANTLRParserTypes.NOT,
                ANTLRParserTypes.DOT,
            )

        private val ATOM_OR_LPAREN_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.RULE_REF,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.STRING_LITERAL,
                ANTLRParserTypes.NOT,
                ANTLRParserTypes.DOT,
                ANTLRParserTypes.LPAREN,
            )

        private val ELEMENT_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.RULE_REF,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.STRING_LITERAL,
                ANTLRParserTypes.NOT,
                ANTLRParserTypes.DOT,
                ANTLRParserTypes.LPAREN,
                ANTLRParserTypes.ACTION,
                ANTLRParserTypes.SEMPRED,
            )

        private val ALT_END: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.OR,
                ANTLRParserTypes.RPAREN,
                ANTLRParserTypes.SEMI,
                ANTLRParserTypes.POUND,
                Token.EOF,
            )

        private val ELEMENT_START_OR_ALT_END: IntervalSet =
            IntervalSet().apply {
                addAll(ELEMENT_START)
                addAll(ALT_END)
            }

        private val ELEMENT_FOLLOW: IntervalSet = ELEMENT_START_OR_ALT_END

        private val ELEMENT_FOLLOW_OR_SUFFIX: IntervalSet =
            IntervalSet().apply {
                addAll(ELEMENT_FOLLOW)
                addAll(EBNF_SUFFIX_START)
            }

        private val RULE_BLOCK_START: IntervalSet =
            IntervalSet().apply {
                addAll(ELEMENT_START_OR_ALT_END)
                add(ANTLRParserTypes.LT)
            }

        private val BLOCK_INNER_START: IntervalSet =
            IntervalSet().apply {
                addAll(RULE_BLOCK_START)
                add(ANTLRParserTypes.OPTIONS)
                add(ANTLRParserTypes.AT)
                add(ANTLRParserTypes.COLON)
            }

        private val EXCEPTION_GROUP_OR_EOR: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.CATCH,
                ANTLRParserTypes.FINALLY,
                ANTLRParserTypes.EOR_TOKEN_TYPE,
            )

        private val LEXER_ATOM_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.STRING_LITERAL,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.RULE_REF,
                ANTLRParserTypes.NOT,
                ANTLRParserTypes.DOT,
                ANTLRParserTypes.LEXER_CHAR_SET,
            )

        private val LEXER_ELEMENT_START: IntervalSet =
            IntervalSet().apply {
                addAll(LEXER_ATOM_START)
                add(ANTLRParserTypes.LPAREN)
                add(ANTLRParserTypes.ACTION)
                add(ANTLRParserTypes.SEMPRED)
            }

        private val LEXER_ALT_END: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.RARROW,
                ANTLRParserTypes.OR,
                ANTLRParserTypes.RPAREN,
                ANTLRParserTypes.SEMI,
                Token.EOF,
            )

        private val LEXER_ELEMENT_FOLLOW: IntervalSet =
            IntervalSet().apply {
                addAll(LEXER_ELEMENT_START)
                addAll(LEXER_ALT_END)
            }

        private val LEXER_ELEMENT_FOLLOW_OR_SUFFIX: IntervalSet =
            IntervalSet().apply {
                addAll(LEXER_ELEMENT_FOLLOW)
                addAll(EBNF_SUFFIX_START)
            }

        private val LEXER_ALT_START_OR_END: IntervalSet = LEXER_ELEMENT_FOLLOW

        private val LEXER_RULE_BLOCK_NONEMPTY_START: IntervalSet =
            IntervalSet().apply {
                addAll(LEXER_ELEMENT_START)
                add(ANTLRParserTypes.RARROW)
                add(ANTLRParserTypes.OR)
            }

        private val LEXER_BLOCK_INNER_START: IntervalSet =
            IntervalSet().apply {
                addAll(LEXER_ALT_START_OR_END)
                add(ANTLRParserTypes.OPTIONS)
            }

        private val LEXER_COMMAND_NAME_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.RULE_REF,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.MODE,
            )

        private val LEXER_COMMAND_EXPR_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.RULE_REF,
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.INT,
            )

        private val SET_ELEMENT_START: IntervalSet =
            setOfTokens(
                ANTLRParserTypes.TOKEN_REF,
                ANTLRParserTypes.STRING_LITERAL,
                ANTLRParserTypes.LEXER_CHAR_SET,
            )

        private val NOT_TARGET_START: IntervalSet =
            IntervalSet().apply {
                addAll(SET_ELEMENT_START)
                add(ANTLRParserTypes.LPAREN)
            }

        private val RULE_NAMES: List<String> =
            listOf(
                "grammarSpec",
                "grammarType",
                "prequelConstruct",
                "optionsSpec",
                "option",
                "optionValue",
                "delegateGrammars",
                "delegateGrammar",
                "tokensSpec",
                "channelsSpec",
                "action",
                "actionScopeName",
                "modeSpec",
                "rules",
                "sync",
                "rule",
                "parserRule",
                "exceptionGroup",
                "exceptionHandler",
                "finallyClause",
                "rulePrequels",
                "rulePrequel",
                "ruleReturns",
                "throwsSpec",
                "localsSpec",
                "ruleAction",
                "ruleBlock",
                "ruleAltList",
                "labeledAlt",
                "lexerRule",
                "lexerRuleBlock",
                "lexerAltList",
                "lexerAlt",
                "lexerElements",
                "lexerElement",
                "lexerBlock",
                "lexerCommands",
                "lexerCommand",
                "lexerCommandExpr",
                "lexerCommandName",
                "altList",
                "alternative",
                "element",
                "actionElement",
                "labeledElement",
                "ebnf",
                "blockSuffix",
                "ebnfSuffix",
                "lexerAtom",
                "atom",
                "wildcard",
                "notSet",
                "blockSet",
                "setElement",
                "block",
                "ruleref",
                "range",
                "terminal",
                "elementOptions",
                "elementOption",
                "id",
                "qid",
            )

        private val PARSER_ATN: ATN = ATN(ATNType.PARSER, ANTLRParserTypes.ERRCHAR)

        private val VOCABULARY: Vocabulary =
            VocabularyImpl(
                emptyList(),
                ANTLRParserTypes.tokenNames,
                emptyList(),
            )
    }
}
