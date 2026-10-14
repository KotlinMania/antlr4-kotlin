/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNDeserializationOptions
import io.github.kotlinmania.antlr4.atn.ATNDeserializer
import io.github.kotlinmania.antlr4.atn.ATNSimulator
import io.github.kotlinmania.antlr4.atn.ParserATNSimulator
import io.github.kotlinmania.antlr4.misc.IntStack
import io.github.kotlinmania.antlr4.tree.ErrorNode
import io.github.kotlinmania.antlr4.tree.ErrorNodeImpl
import io.github.kotlinmania.antlr4.tree.ParseTreeListener
import io.github.kotlinmania.antlr4.tree.TerminalNode
import io.github.kotlinmania.antlr4.tree.TerminalNodeImpl

/** This is all the parsing support code essentially; most of it is error recovery stuff.  */
abstract class Parser(
    input: TokenStream?,
) : Recognizer<Token, ParserATNSimulator>() {
    override val grammarFileName: String
        get() = "unknown"

    open inner class TraceListener : ParseTreeListener {
        override fun enterEveryRule(ctx: ParserRuleContext?) {
            println(
                "enter   " + ruleNames?.get(ctx?.ruleIndex ?: 0) +
                    ", LT(1)=" + inputField?.LT(1)?.text,
            )
        }

        override fun visitTerminal(node: TerminalNode?) {
            println(
                "consume " + node?.symbol + " rule " +
                    ruleNames?.get(ctx?.ruleIndex ?: 0),
            )
        }

        override fun visitErrorNode(node: ErrorNode?) {}

        override fun exitEveryRule(ctx: ParserRuleContext?) {
            println(
                "exit    " + ruleNames?.get(ctx?.ruleIndex ?: 0) +
                    ", LT(1)=" + inputField?.LT(1)?.text,
            )
        }
    }

    open class TrimToSizeListener : ParseTreeListener {
        override fun enterEveryRule(ctx: ParserRuleContext?) {
        }

        override fun visitTerminal(node: TerminalNode?) {
        }

        override fun visitErrorNode(node: ErrorNode?) {}

        override fun exitEveryRule(ctx: ParserRuleContext?) {
            ctx?.trimChildrenToSize()
        }

        companion object {
            val INSTANCE: TrimToSizeListener = TrimToSizeListener()
        }
    }

    /** The input stream (set via constructor or setInputStream). */
    protected var inputField: TokenStream? = null

    /**
     * Consume the current symbol and advance to the next.
     * This is the primary method for advancing the parser during matching.
     */
    open fun consume(): Token {
        val o: Token = currentToken
        if (o.type != IntStream.EOF) {
            inputField?.consume()
        }
        val hasListener: Boolean = _parseListeners.isNotEmpty()
        if (buildParseTree || hasListener) {
            if (errorHandler.inErrorRecoveryMode(this)) {
                val node = ctx?.addErrorNode(createErrorNode(ctx, o))
                if (_parseListeners.isNotEmpty()) {
                    for (listener in _parseListeners) {
                        listener.visitErrorNode(node!!)
                    }
                }
            } else {
                val node = ctx?.addChild(TerminalNodeImpl(o))
                if (_parseListeners.isNotEmpty()) {
                    for (listener in _parseListeners) {
                        listener.visitTerminal(node!!)
                    }
                }
            }
        }
        return o
    }

    /** Create error node during recovery. */
    open fun createErrorNode(
        ctx: ParserRuleContext?,
        t: Token,
    ): ErrorNode = ErrorNodeImpl(t)

    /** Serialized ATN for bypass-alts cache. */
    open override val serializedATN: String get() = throw UnsupportedOperationException("there is no serialized ATN")

    /** Bypass alts ATN cache (lazy, unsynchronized). */
    private var bypassAltsAtnCache: ATN? = null

    /** Current input token. */
    val currentToken: Token
        get() = inputField?.LT(1) ?: throw IllegalStateException("No current token available")

    protected val precedenceStack: IntStack

    init {
        precedenceStack = IntStack()
        precedenceStack.push(0)
    }

    /**
     * The [ParserRuleContext] object for the currently executing rule.
     * This is always non-null during the parsing process.
     */
    internal var ctx: ParserRuleContext? = null

    /**
     * Track the [ParserRuleContext] objects during the parse and hook
     * them up using the [ParserRuleContext.children] list so that it
     * forms a parse tree. The [ParserRuleContext] returned from the start
     * rule represents the root of the parse tree.
     *
     * Note that if we are not building parse trees, rule contexts only point
     * upwards. When a rule exits, it returns the context but that gets garbage
     * collected if nobody holds a reference. It points upwards but nobody
     * points at it.
     *
     * When we build parse trees, we are adding all of these contexts to
     * [ParserRuleContext.children] list. Contexts are then not candidates
     * for garbage collection.
     *
     * Specifies whether or not the parser should construct a parse tree during
     * the parsing process. The default value is `true`.
     */
    var buildParseTree: Boolean = true

    /**
     * When [.setTrace]`(true)` is called, a reference to the
     * [TraceListener] is stored here so it can be easily removed in a
     * later call to [.setTrace]`(false)`. The listener itself is
     * implemented as a parser listener so this field is not directly used by
     * other parser methods.
     */
    private var tracer: TraceListener? = null

    /**
     * The list of [ParseTreeListener] listeners registered to receive
     * events during the parse.
     *
     * @see .addParseListener
     */
    private val _parseListeners: MutableList<ParseTreeListener> = ArrayList()

    /**
     * The number of syntax errors reported during parsing. This value is
     * incremented each time [.notifyErrorListeners] is called.
     *
     * @see .notifyErrorListeners
     */
    var numberOfSyntaxErrors: Int = 0
        protected set

    /** Indicates parser has match()ed EOF token. See [.exitRule].  */
    var isMatchedEOF: Boolean = false
        protected set

    init {
        setInputStream(input)
    }

    /** reset the parser's state  */
    open fun reset() {
        if (this.inputStream != null) this.inputStream?.seek(0)
        errorHandler.reset(this)
        ctx = null
        this.numberOfSyntaxErrors = 0
        this.isMatchedEOF = false
        setTrace(false)
        precedenceStack.clear()
        precedenceStack.push(0)
        val interp: ATNSimulator? = this.interpreter
        if (interp != null) {
            interp.reset()
        }
    }

    /**
     * Match current input symbol against `ttype`. If the symbol type
     * matches, [ANTLRErrorStrategy.reportMatch] and [.consume] are
     * called to complete the match process.
     *
     *
     * If the symbol type does not match,
     * [ANTLRErrorStrategy.recoverInline] is called on the current error
     * strategy to attempt recovery. If [.getBuildParseTree] is
     * `true` and the token index of the symbol returned by
     * [ANTLRErrorStrategy.recoverInline] is -1, the symbol is added to
     * the parse tree by calling [.createErrorNode] then
     * [ParserRuleContext.addErrorNode].
     *
     * @param ttype the token type to match
     * @return the matched symbol
     * @throws RecognitionException if the current input symbol did not match
     * `ttype` and the error strategy could not recover from the
     * mismatched symbol
     */
    fun match(ttype: Int): Token {
        var t: Token = this.currentToken
        if (t.type == ttype) {
            if (ttype == Token.EOF) {
                this.isMatchedEOF = true
            }
            errorHandler.reportMatch(this)
            consume()
        } else {
            t = errorHandler.recoverInline(this)!!
            if (this.buildParseTree && t.tokenIndex == -1) {
                // we must have conjured up a new token during single token insertion
                // if it's not the current symbol
                ctx?.addErrorNode(createErrorNode(ctx, t))
            }
        }
        return t
    }

    /**
     * Match current input symbol as a wildcard. If the symbol type matches
     * (i.e. has a value greater than 0), [ANTLRErrorStrategy.reportMatch]
     * and [.consume] are called to complete the match process.
     *
     *
     * If the symbol type does not match,
     * [ANTLRErrorStrategy.recoverInline] is called on the current error
     * strategy to attempt recovery. If [.getBuildParseTree] is
     * `true` and the token index of the symbol returned by
     * [ANTLRErrorStrategy.recoverInline] is -1, the symbol is added to
     * the parse tree by calling [createErrorNode]. then
     * [ParserRuleContext.addErrorNode]
     *
     * @return the matched symbol
     * @throws RecognitionException if the current input symbol did not match
     * a wildcard and the error strategy could not recover from the mismatched
     * symbol
     */
    fun matchWildcard(): Token {
        var t: Token = this.currentToken
        if (t.type > 0) {
            errorHandler.reportMatch(this)
            consume()
        } else {
            t = errorHandler.recoverInline(this)!!
            if (this.buildParseTree && t.tokenIndex == -1) {
                // we must have conjured up a new token during single token insertion
                // if it's not the current symbol
                ctx?.addErrorNode(createErrorNode(ctx, t))
            }
        }

        return t
    }

    val trimParseTree: Boolean
        get() = parseListeners.contains(Parser.TrimToSizeListener.INSTANCE)

    fun setTrimParseTree(trimParseTrees: Boolean) {
        if (trimParseTrees) {
            if (trimParseTree) return
            addParseListener(Parser.TrimToSizeListener.INSTANCE)
        } else {
            removeParseListener(Parser.TrimToSizeListener.INSTANCE)
        }
    }

    val parseListeners: List<ParseTreeListener>
        get() = _parseListeners.toList()

    /**
     * Registers `listener` to receive events during the parsing process.
     *
     *
     * To support output-preserving grammar transformations (including but not
     * limited to left-recursion removal, automated left-factoring, and
     * optimized code generation), calls to listener methods during the parse
     * may differ substantially from calls made by
     * [ParseTreeWalker.DEFAULT] used after the parse is complete. In
     * particular, rule entry and exit events may occur in a different order
     * during the parse than after the parser. In addition, calls to certain
     * rule entry methods may be omitted.
     *
     *
     * With the following specific exceptions, calls to listener events are
     * *deterministic*, i.e. for identical input the calls to listener
     * methods will be the same.
     *
     *
     *  * Alterations to the grammar used to generate code may change the
     * behavior of the listener calls.
     *  * Alterations to the command line options passed to ANTLR 4 when
     * generating the parser may change the behavior of the listener calls.
     *  * Changing the version of the ANTLR Tool used to generate the parser
     * may change the behavior of the listener calls.
     *
     *
     * @param listener the listener to add
     *
     * @throws NullPointerException if `` listener is `null`
     */
    fun addParseListener(listener: ParseTreeListener) {
        _parseListeners.add(listener)
    }

    /**
     * Remove `listener` from the list of parse listeners.
     *
     *
     * If `listener` is `null` or has not been added as a parse
     * listener, this method does nothing.
     *
     * @see .addParseListener
     *
     *
     * @param listener the listener to remove
     */
    fun removeParseListener(listener: ParseTreeListener?) {
        if (listener != null) {
            _parseListeners.remove(listener)
        }
    }

    /**
     * Remove all parse listeners.
     *
     * @see .addParseListener
     */
    fun removeParseListeners() {
        _parseListeners.clear()
    }

    /**
     * Notify any parse listeners of an enter rule event.
     *
     * @see .addParseListener
     */
    protected fun triggerEnterRuleEvent() {
        for (listener in _parseListeners) {
            listener.enterEveryRule(ctx)
            ctx?.enterRule(listener)
        }
    }

    /**
     * Notify any parse listeners of an exit rule event.
     *
     * @see .addParseListener
     */
    protected fun triggerExitRuleEvent() {
        for (i in _parseListeners.size - 1 downTo 0) {
            val listener = _parseListeners[i]
            ctx?.exitRule(listener)
            listener.exitEveryRule(ctx)
        }
    }

    override val tokenFactory: TokenFactory<*>?
        get() = inputField?.tokenSource?.tokenFactory

    /** Tell our token source and error strategy about a new way to create tokens.  */
    override fun setTokenFactory(factory: TokenFactory<*>?) {
        inputField?.tokenSource?.setTokenFactory(factory)
    }

    override fun setInputStream(input: IntStream?) {
        inputField = input as? TokenStream
    }

    override val inputStream: IntStream?
        get() = inputField ?: throw IllegalStateException("No input stream available")

    /**
     * Notify error listeners of a syntax error.
     */
    fun notifyErrorListeners(msg: String) {
        notifyErrorListeners(currentToken, msg, null)
    }

    /**
     * Notify error listeners of a syntax error with a specific token and exception.
     */
    fun notifyErrorListeners(
        offendingToken: Token?,
        msg: String?,
        e: RecognitionException?,
    ) {
        numberOfSyntaxErrors++
        val line = offendingToken?.line ?: 0
        val charPositionInLine = offendingToken?.charPositionInLine ?: 0
        errorListenerDispatch.syntaxError(this, offendingToken, line, charPositionInLine, msg, e)
    }

    /**
     * The ATN with bypass alternatives is expensive to create so we create it
     * lazily.
     *
     * @throws UnsupportedOperationException if the current parser does not
     * implement the [getSerializedATN] method.
     */
    val aTNWithBypassAlts: ATN?
        get() {
            val serializedAtn = serializedATN
            if (bypassAltsAtnCache == null) {
                val deserializationOptions: ATNDeserializationOptions = ATNDeserializationOptions()
                deserializationOptions.setGenerateRuleBypassTransitions(true)
                bypassAltsAtnCache = ATNDeserializer(deserializationOptions).deserialize(serializedAtn.toCharArray())
            }
            return bypassAltsAtnCache
        }

    /** Get the precedence level for the top-most precedence rule.
     * @return The precedence level for the top-most precedence rule, or -1 if
     * the parser context is not nested within a precedence rule.
     */
    val precedence: Int
        get() {
            if ((precedenceStack.size() == 0)) {
                return -1
            }
            return precedenceStack.peek()
        }

    open fun enterRecursionRule(
        localctx: ParserRuleContext?,
        state: Int,
        ruleIndex: Int,
        precedence: Int,
    ) {
        precedenceStack.push(precedence)
        this.state = state
        ctx = localctx
        ctx?.start = inputField?.LT(1)
    }

    open fun unrollRecursionContexts(parentctx: ParserRuleContext?) {
        precedenceStack.pop()
        ctx?.stop = inputField?.LT(-1)
        val retctx = ctx
        if (_parseListeners.isNotEmpty()) {
            while (ctx != parentctx) {
                triggerExitRuleEvent()
                ctx = ctx?.parent as ParserRuleContext
            }
        } else {
            ctx = parentctx
        }
        retctx?.setParent(parentctx!! as RuleContext)
        if (buildParseTree && parentctx != null) {
            parentctx.addChild(retctx!!)
        }
    }

    override fun precpred(
        localctx: RuleContext?,
        precedence: Int,
    ): Boolean = precedence >= precedenceStack.peek()

    val tokenStream: TokenStream
        get() = inputField ?: throw IllegalStateException("No input stream available")

    fun getRuleInvocationStack(): List<String> = getRuleInvocationStack(ctx)

    fun getRuleInvocationStack(p: RuleContext?): List<String> {
        val ruleNames = ruleNames ?: return emptyList()
        val stack = mutableListOf<String>()
        var currentCtx: RuleContext? = p
        while (currentCtx != null) {
            val ruleIndex = currentCtx.ruleIndex
            if (ruleIndex < 0) {
                stack.add("n/a")
            } else {
                stack.add(ruleNames[ruleIndex])
            }
            currentCtx = currentCtx.parent as? RuleContext
        }
        return stack
    }

    fun enterRule(
        localctx: ParserRuleContext,
        state: Int,
        ruleIndex: Int,
    ) {
        this.state = state
        ctx = localctx
        ctx?.start = inputField?.LT(1)
        if (buildParseTree) addContextToParseTree()
        if (_parseListeners.isNotEmpty()) triggerEnterRuleEvent()
    }

    fun exitRule() {
        if (isMatchedEOF) {
            ctx?.stop = inputField?.LT(1)
        } else {
            ctx?.stop = inputField?.LT(-1)
        }
        if (_parseListeners.isNotEmpty()) triggerExitRuleEvent()
        state = ctx?.invokingState ?: -1
        ctx = ctx?.parent as? ParserRuleContext
    }

    fun pushNewRecursionContext(
        localctx: ParserRuleContext,
        state: Int,
        ruleIndex: Int,
    ) {
        val previous = ctx
        previous?.setParent(localctx)
        previous?.invokingState = state
        ctx = localctx
        ctx?.start = previous?.start
        if (buildParseTree) {
            (previous?.parent as? ParserRuleContext)?.addChild(previous)
        }
    }

    fun getContext(): ParserRuleContext? = ctx

    fun setTrace(trace: Boolean) {
        if (!trace) {
            tracer?.let { removeParseListener(it) }
            tracer = null
        } else {
            tracer?.let { removeParseListener(it) }
            if (tracer == null) tracer = TraceListener()
            tracer?.let { addParseListener(it) }
        }
    }

    fun isTrace(): Boolean = tracer != null

    protected fun addContextToParseTree() {
        if (ctx?.parent !is ParserRuleContext) return
        val currentCtx = ctx
        state = currentCtx?.invokingState ?: -1
    }
}
