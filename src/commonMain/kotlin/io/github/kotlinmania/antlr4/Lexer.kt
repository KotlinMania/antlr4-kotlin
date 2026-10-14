/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.atn.LexerATNSimulator
import io.github.kotlinmania.antlr4.misc.IntStack
import io.github.kotlinmania.antlr4.misc.Interval

/** A lexer is recognizer that draws input symbols from a character stream.
 * lexer grammars result in a subclass of this object. A Lexer object
 * uses simplified match() and error recovery mechanisms in the interest
 * of speed.
 */
abstract class Lexer :
    Recognizer<Int, LexerATNSimulator>,
    TokenSource {
    var input: CharStream? = null
    protected var tokenFactorySourcePair: TokenSourceCharStream? = null

    /** How to create token objects  */
    protected var factory: TokenFactory<*> = CommonTokenFactory.DEFAULT

    /** The goal of all lexer rules/methods is to create a token object.
     * This is an instance variable as multiple rules may collaborate to
     * create a single token.  nextToken will return this object after
     * matching lexer rule(s).  If you subclass to allow multiple token
     * emissions, then set this to the last token to be matched or
     * something nonnull so that the auto token emit mechanism will not
     * emit another token.
     */
    private var _token: Token? = null

    /** What character index in the stream did the current token start at?
     * Needed, for example, to get the text for current token.  Set at
     * the start of nextToken.
     */
    var tokenStartCharIndex: Int = -1

    /** The line on which the first character of the token resides  */
    var tokenStartLine: Int = 0

    /** The character position of first character within the line  */
    var tokenStartCharPositionInLine: Int = 0

    /** Once we see EOF on char stream, next token will be EOF.
     * If you have DONE : EOF ; then you see DONE EOF.
     */
    var hitEOF: Boolean = false

    /** The channel number for the current token  */
    var channel: Int = 0

    /** The token type for the current token  */
    var type: Int = 0

    val modeStack: IntStack = IntStack()
    var mode: Int = io.github.kotlinmania.antlr4.Lexer.Companion.DEFAULT_MODE

    /** You can set the text for the current token to override what is in
     * the input char buffer.  Use setText() or can set this instance var.
     */
    private var _text: String? = null

    constructor()

    constructor(input: CharStream?) {
        this.input = input
        this.tokenFactorySourcePair = TokenSourceCharStream(this, input)
    }

    open fun reset() {
        // wack Lexer state variables
        input?.seek(0) // rewind the input
        _token = null
        this.type = Token.INVALID_TYPE
        this.channel = Token.DEFAULT_CHANNEL
        tokenStartCharIndex = -1
        tokenStartCharPositionInLine = -1
        tokenStartLine = -1
        _text = null

        hitEOF = false
        mode = io.github.kotlinmania.antlr4.Lexer.Companion.DEFAULT_MODE
        modeStack.clear()

        interpreter!!.reset()
    }

    /** Return a token from this source; i.e., match a token on the char
     * stream.
     */
    override fun nextToken(): Token {
        checkNotNull(input) { "nextToken requires a non-null input stream." }

        // Mark start location in char stream so unbuffered streams are
        // guaranteed at least have text of current token
        val tokenStartMarker: Int = input!!.mark()
        try {
            outer@ while (true) {
                if (hitEOF) {
                    emitEOF()
                    return _token!!
                }

                _token = null
                this.channel = Token.DEFAULT_CHANNEL
                tokenStartCharIndex = input!!.index()
                tokenStartCharPositionInLine = interpreter!!.charPositionInLine
                tokenStartLine = interpreter!!.line
                _text = null
                do {
                    this.type = Token.INVALID_TYPE
                    // 				println("nextToken line "+tokenStartLine+" at "+((char)input.LA(1))+
// 								   " in mode "+mode+
// 								   " at index "+input.index());
                    var ttype: Int
                    try {
                        ttype = interpreter!!.match(input!!, mode)
                    } catch (control: RecognitionControlException) {
                        val e = control.recognitionException
                        if (e !is LexerNoViableAltException) throw control
                        notifyListeners(e) // report error
                        recover(e)
                        ttype = io.github.kotlinmania.antlr4.Lexer.Companion.SKIP
                    }
                    if (input!!.LA(1) == IntStream.EOF) {
                        hitEOF = true
                    }
                    if (this.type == Token.INVALID_TYPE) this.type = ttype
                    if (this.type == io.github.kotlinmania.antlr4.Lexer.Companion.SKIP) {
                        continue@outer
                    }
                } while (this.type == io.github.kotlinmania.antlr4.Lexer.Companion.MORE)
                if (_token == null) emit()
                return _token!!
            }
        } finally {
            // make sure we release marker after match or
            // unbuffered char stream will keep buffering
            input!!.release(tokenStartMarker)
        }
    }

    /** Instruct the lexer to skip creating a token for current lexer rule
     * and look for another token.  nextToken() knows to keep looking when
     * a lexer rule finishes with token set to SKIP_TOKEN.  Recall that
     * if token==null at end of any token rule, it creates one for you
     * and emits it.
     */
    fun skip() {
        this.type = io.github.kotlinmania.antlr4.Lexer.Companion.SKIP
    }

    fun more() {
        this.type = io.github.kotlinmania.antlr4.Lexer.Companion.MORE
    }

    fun mode(m: Int) {
        mode = m
    }

    fun pushMode(m: Int) {
        if (LexerATNSimulator.debug) println("pushMode " + m)
        modeStack.push(mode)
        mode(m)
    }

    fun popMode(): Int {
        if (modeStack.isEmpty) throw NoSuchElementException("mode stack is empty")
        if (LexerATNSimulator.debug) println("popMode back to " + modeStack.peek())
        mode(modeStack.pop())
        return mode
    }

    override fun setTokenFactory(factory: TokenFactory<*>?) {
        this.factory = factory ?: CommonTokenFactory.DEFAULT
    }

    override val tokenFactory: TokenFactory<*>?
        get() = factory

    /** Set the char stream and reset the lexer  */
    override fun setInputStream(input: IntStream?) {
        this.input = null
        this.tokenFactorySourcePair = TokenSourceCharStream(this, this.input)
        reset()
        this.input = input as CharStream?
        this.tokenFactorySourcePair = TokenSourceCharStream(this, this.input)
    }

    override val sourceName: String
        get() = input!!.sourceName!!
    override val inputStream: CharStream?
        get() = input

    /** By default does not support multiple emits per nextToken invocation
     * for efficiency reasons.  Subclass and override this method, nextToken,
     * and getToken (to push tokens into a list and pull from that list
     * rather than a single variable as this implementation does).
     */
    fun emit(token: Token?) {
        // println("emit "+token);
        this._token = token
    }

    /** The standard method called to automatically emit a token at the
     * outermost lexical rule.  The token object should point into the
     * char buffer start..stop.  If there is a text override in 'text',
     * use that to set the token's text.  Override this method to emit
     * custom Token objects or provide a new factory.
     */
    fun emit(): Token? {
        val t: Token? =
            factory.create(
                tokenFactorySourcePair,
                this.type,
                _text,
                this.channel,
                tokenStartCharIndex,
                this.charIndex - 1,
                tokenStartLine,
                tokenStartCharPositionInLine,
            )
        emit(t)
        return t
    }

    fun emitEOF(): Token? {
        val cpos = this.charPositionInLine
        val line = this.line
        val eof: Token? =
            factory.create(
                tokenFactorySourcePair,
                Token.EOF,
                null,
                Token.DEFAULT_CHANNEL,
                input!!.index(),
                input!!.index() - 1,
                line,
                cpos,
            )
        emit(eof)
        return eof
    }

    override var line: Int
        get() = interpreter!!.line
        set(line) {
            interpreter!!.line = line
        }
    override var charPositionInLine: Int
        get() = interpreter!!.charPositionInLine
        set(charPositionInLine) {
            interpreter!!.charPositionInLine = charPositionInLine
        }

    val charIndex: Int
        /** What is the index of the current character of lookahead?  */
        get() = input!!.index()

    var text: String?
        /** Return the text matched so far for the current token or any
         * text override.
         */
        get() {
            val currentText = _text
            if (currentText != null) {
                return currentText
            }
            return interpreter!!.getText(input!!)
        }

        /** Set the complete text of this token; it wipes any previous
         * changes to the text.
         */
        set(text) {
            this._text = text
        }

    val token: Token?
        /** Override if emitting multiple tokens.  */
        get() = _token

    fun setToken(token: Token?) {
        this._token = token
    }

    val channelNames: Array<String>
        get() = emptyArray()

    val modeNames: Array<String>
        get() = emptyArray()

    override val tokenNames: Array<String>
        /** Used to print out token names like ID during debugging and
         * error reporting.  The generated parsers implement a method
         * that overrides this to point to their String[] tokenNames.
         */
        get() = emptyArray()

    val allTokens: List<Token>
        /** Return a list of all Token objects in input char stream.
         * Forces load of all tokens. Does not include EOF token.
         */
        get() {
            val tokens: MutableList<Token> = ArrayList()
            var t: Token = nextToken()
            while (t.type != Token.EOF) {
                tokens.add(t)
                t = nextToken()
            }
            return tokens
        }

    open fun recover(e: LexerNoViableAltException?) {
        if (input!!.LA(1) != IntStream.EOF) {
            // skip a char and try again
            interpreter!!.consume(input!!)
        }
    }

    fun notifyListeners(e: LexerNoViableAltException?) {
        val text: String = input!!.getText(Interval.of(tokenStartCharIndex, input!!.index()))!!
        val msg = "token recognition error at: '" + getErrorDisplay(text) + "'"

        val listener: ANTLRErrorListener = errorListenerDispatch
        listener.syntaxError(this, null, tokenStartLine, tokenStartCharPositionInLine, msg, e)
    }

    fun getErrorDisplay(s: String): String {
        val buf: StringBuilder = StringBuilder()
        for (c in s.toCharArray()) {
            buf.append(getErrorDisplay(c.code))
        }
        return buf.toString()
    }

    fun getErrorDisplay(c: Int): String? {
        var s: String? = c.toChar().toString()
        when (c) {
            Token.EOF -> s = "<EOF>"
            10 -> s = "\\n"
            9 -> s = "\\t"
            13 -> s = "\\r"
        }
        return s
    }

    fun getCharErrorDisplay(c: Int): String? {
        val s = getErrorDisplay(c)
        return "'" + s + "'"
    }

    /** Lexers can normally match any char in it's vocabulary after matching
     * a token, so do the easy thing and just kill a character and hope
     * it all works out.  You can instead use the rule invocation stack
     * to do sophisticated error recovery if you are in a fragment rule.
     */
    fun recover(re: RecognitionException?) {
        // println("consuming char "+(char)input.LA(1)+" during recovery");
        // re.printStackTrace();
        // TODO: Do we lose character or line position information?
        input!!.consume()
    }

    companion object {
        const val DEFAULT_MODE: Int = 0
        val MORE: Int = -2
        val SKIP: Int = -3

        val DEFAULT_TOKEN_CHANNEL: Int = Token.DEFAULT_CHANNEL
        val HIDDEN: Int = Token.HIDDEN_CHANNEL
        const val MIN_CHAR_VALUE: Int = 0x0000
        const val MAX_CHAR_VALUE: Int = 0x10FFFF
    }
}
