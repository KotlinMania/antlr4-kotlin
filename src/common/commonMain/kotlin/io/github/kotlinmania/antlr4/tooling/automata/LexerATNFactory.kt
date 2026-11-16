/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.automata

import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.IntStream
import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.atn.ActionTransition
import io.github.kotlinmania.antlr4.atn.AtomTransition
import io.github.kotlinmania.antlr4.atn.CodePointTransitions
import io.github.kotlinmania.antlr4.atn.LexerAction
import io.github.kotlinmania.antlr4.atn.LexerChannelAction
import io.github.kotlinmania.antlr4.atn.LexerCustomAction
import io.github.kotlinmania.antlr4.atn.LexerModeAction
import io.github.kotlinmania.antlr4.atn.LexerMoreAction
import io.github.kotlinmania.antlr4.atn.LexerPopModeAction
import io.github.kotlinmania.antlr4.atn.LexerPushModeAction
import io.github.kotlinmania.antlr4.atn.LexerSkipAction
import io.github.kotlinmania.antlr4.atn.LexerTypeAction
import io.github.kotlinmania.antlr4.atn.NotSetTransition
import io.github.kotlinmania.antlr4.atn.SetTransition
import io.github.kotlinmania.antlr4.atn.TokensStartState
import io.github.kotlinmania.antlr4.atn.Transition
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.RangeAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.misc.CharSupport
import io.github.kotlinmania.antlr4.tooling.misc.EscapeSequenceParsing
import io.github.kotlinmania.antlr4.tooling.misc.charCountCompat
import io.github.kotlinmania.antlr4.tooling.misc.codePointAtCompat
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar

/**
 * Lexer-specific [ATNFactory] constructing mode entry states, character-range / Unicode-property transitions,
 * case-insensitive range expansions, and [LexerAction] tables.
 */
class LexerATNFactory(
    val lexerGrammar: LexerGrammar,
) : ParserATNFactory(lexerGrammar) {
    private val ruleCommands: MutableList<String> = ArrayList()
    private val indexToActionMap: MutableMap<Int, LexerAction> = HashMap()
    private val actionToIndexMap: MutableMap<LexerAction, Int> = HashMap()

    override fun createATN(): ATN {
        val modes = lexerGrammar.modes.keys
        for (modeName in modes) {
            val startState = registerNewState(TokensStartState())
            atn.mutableModeNameToStartState[modeName] = startState
            atn.mutableModeToStartState.add(startState)
            atn.defineDecisionState(startState)
        }

        atn.ruleToTokenType = IntArray(g.rules.size)
        for (r in g.rules.values) {
            atn.ruleToTokenType[r.index] = g.getTokenType(r.name)
        }

        buildRulesATN(g.rules.values)

        val actionList = MutableList<LexerAction?>(indexToActionMap.size) { null }
        for ((idx, lexerAct) in indexToActionMap) {
            actionList[idx] = lexerAct
        }
        atn.mutableLexerActions.clear()
        atn.mutableLexerActions.addAll(actionList)

        for (modeName in modes) {
            val rules = lexerGrammar.modes[modeName].orEmpty()
            val startState = atn.mutableModeNameToStartState[modeName]
            for (r in rules) {
                if (!r.isFragment) {
                    val s = atn.mutableRuleToStartState[r.index]
                    connectEpsilon(startState, s)
                }
            }
        }

        ATNOptimizer.optimize(g, atn)
        checkEpsilonClosure()
        return atn
    }

    override fun rule(
        ruleAST: GrammarAST,
        name: String,
        blk: ATNFactory.Handle,
    ): ATNFactory.Handle {
        ruleCommands.clear()
        return super.rule(ruleAST, name, blk)
    }

    override fun action(action: ActionAST): ATNFactory.Handle {
        val ruleIndex = currentRule?.index ?: -1
        val actionIndex = g.lexerActions[action] ?: -1
        val lexerAction = LexerCustomAction(ruleIndex, actionIndex)
        return lexerActionHandle(action, lexerAction)
    }

    private fun getLexerActionIndex(lexerAction: LexerAction): Int {
        val existing = actionToIndexMap[lexerAction]
        if (existing != null) {
            return existing
        }
        val newIndex = actionToIndexMap.size
        actionToIndexMap[lexerAction] = newIndex
        indexToActionMap[newIndex] = lexerAction
        return newIndex
    }

    override fun actionFromText(action: String): ATNFactory.Handle {
        if (action.trim { it <= ' ' }.isEmpty()) {
            val left = newStateForNode(null)
            val right = newStateForNode(null)
            connectEpsilon(left, right)
            return ATNFactory.Handle(left, right)
        }

        val ast = ActionAST(CommonToken(ANTLRParserTypes.ACTION, action))
        currentRule?.defineActionInAlt(currentOuterAlt, ast)
        return action(ast)
    }

    private fun lexerActionHandle(
        node: GrammarAST,
        lexerAction: LexerAction,
    ): ATNFactory.Handle {
        val left = newStateForNode(node)
        val right = newStateForNode(node)
        val isCtxDependent = false
        val lexerActionIndex = getLexerActionIndex(lexerAction)
        val ruleIndex = currentRule?.index ?: -1
        val a = ActionTransition(right, ruleIndex, lexerActionIndex, isCtxDependent)
        left.addTransition(a)
        node.atnState = left
        return ATNFactory.Handle(left, right)
    }

    override fun lexerAltCommands(
        alt: ATNFactory.Handle,
        cmds: ATNFactory.Handle,
    ): ATNFactory.Handle {
        val h = ATNFactory.Handle(alt.left, cmds.right)
        connectEpsilon(alt.right, cmds.left)
        return h
    }

    override fun lexerCallCommand(
        id: GrammarAST,
        arg: GrammarAST,
    ): ATNFactory.Handle? =
        lexerCallCommandOrCommand(id, arg)

    override fun lexerCommand(id: GrammarAST): ATNFactory.Handle? =
        lexerCallCommandOrCommand(id, null)

    private fun lexerCallCommandOrCommand(
        id: GrammarAST,
        arg: GrammarAST?,
    ): ATNFactory.Handle? {
        val lexerAction = createLexerAction(id, arg)
        if (lexerAction != null) {
            return lexerActionHandle(id, lexerAction)
        }

        val cmdName = id.text
        val expectsArg: Boolean =
            when (cmdName) {
                "skip", "more", "popMode" -> false
                "type", "channel", "mode", "pushMode" -> true
                else -> {
                    g.tool.errMgr.grammarError(ErrorType.INVALID_LEXER_COMMAND, g.fileName, id.token, cmdName)
                    return epsilon(id)
                }
            }

        val callCommand = arg != null
        if (callCommand != expectsArg) {
            val errorType =
                if (callCommand) {
                    ErrorType.UNWANTED_LEXER_COMMAND_ARGUMENT
                } else {
                    ErrorType.MISSING_LEXER_COMMAND_ARGUMENT
                }
            g.tool.errMgr.grammarError(errorType, g.fileName, id.token, cmdName)
            return epsilon(id)
        }

        val isKotlinTarget = g.language == "Kotlin"
        val argText = arg?.text.orEmpty()
        val rendered =
            when (cmdName) {
                "skip" -> if (isKotlinTarget) "skip()" else "skip();"
                "more" -> if (isKotlinTarget) "more()" else "more();"
                "popMode" -> if (isKotlinTarget) "popMode()" else "popMode();"
                "type" -> if (isKotlinTarget) "type = $argText" else "_type = $argText;"
                "channel" -> if (isKotlinTarget) "channel = $argText" else "_channel = $argText;"
                "mode" -> if (isKotlinTarget) "mode($argText)" else "_mode = $argText;"
                "pushMode" -> if (isKotlinTarget) "pushMode($argText)" else "pushMode($argText);"
                else -> ""
            }
        return actionFromText(rendered)
    }

    override fun range(
        a: GrammarAST,
        b: GrammarAST,
    ): ATNFactory.Handle {
        val left = newStateForNode(a)
        val right = newStateForNode(b)
        val t1 = CharSupport.getCharValueFromGrammarCharLiteral(a.text)
        val t2 = CharSupport.getCharValueFromGrammarCharLiteral(b.text)
        if (checkRange(a, b, t1, t2)) {
            left.addTransition(createTransition(right, t1, t2, a))
        }
        a.atnState = left
        b.atnState = left
        return ATNFactory.Handle(left, right)
    }

    override fun set(
        associatedAST: GrammarAST,
        alts: List<GrammarAST>,
        invert: Boolean,
    ): ATNFactory.Handle {
        val left = newStateForNode(associatedAST)
        val right = newStateForNode(associatedAST)
        val set = IntervalSet()
        val caseInsensitive = currentRule?.caseInsensitive ?: false
        for (t in alts) {
            when (t.type) {
                ANTLRParserTypes.RANGE -> {
                    val leftChild = t.getChild(0)
                    val rightChild = t.getChild(1)
                    if (leftChild != null && rightChild != null) {
                        val a = CharSupport.getCharValueFromGrammarCharLiteral(leftChild.text)
                        val b = CharSupport.getCharValueFromGrammarCharLiteral(rightChild.text)
                        if (checkRange(leftChild, rightChild, a, b)) {
                            checkRangeAndAddToSet(associatedAST, t, set, a, b, caseInsensitive, null)
                        }
                    }
                }

                ANTLRParserTypes.LEXER_CHAR_SET -> {
                    set.addAll(getSetFromCharSetLiteral(t))
                }

                ANTLRParserTypes.STRING_LITERAL -> {
                    val c = CharSupport.getCharValueFromGrammarCharLiteral(t.text)
                    if (c != -1) {
                        checkCharAndAddToSet(associatedAST, set, c)
                    } else {
                        g.tool.errMgr.grammarError(
                            ErrorType.INVALID_LITERAL_IN_LEXER_SET,
                            g.fileName,
                            t.token,
                            t.text,
                        )
                    }
                }

                ANTLRParserTypes.TOKEN_REF -> {
                    g.tool.errMgr.grammarError(
                        ErrorType.UNSUPPORTED_REFERENCE_IN_LEXER_SET,
                        g.fileName,
                        t.token,
                        t.text,
                    )
                }
            }
        }
        if (invert) {
            left.addTransition(NotSetTransition(right, set))
        } else {
            val intervals = set.getIntervals()
            val transition: Transition =
                if (intervals.size == 1) {
                    val interval = intervals[0]
                    CodePointTransitions.createWithCodePointRange(right, interval.a, interval.b)
                } else {
                    SetTransition(right, set)
                }
            left.addTransition(transition)
        }
        associatedAST.atnState = left
        return ATNFactory.Handle(left, right)
    }

    protected fun checkRange(
        leftNode: GrammarAST,
        rightNode: GrammarAST,
        leftValue: Int,
        rightValue: Int,
    ): Boolean {
        var result = true
        if (leftValue == -1) {
            result = false
            g.tool.errMgr.grammarError(
                ErrorType.INVALID_LITERAL_IN_LEXER_SET,
                g.fileName,
                leftNode.token,
                leftNode.text,
            )
        }
        if (rightValue == -1) {
            result = false
            g.tool.errMgr.grammarError(
                ErrorType.INVALID_LITERAL_IN_LEXER_SET,
                g.fileName,
                rightNode.token,
                rightNode.text,
            )
        }
        if (!result) return false

        if (rightValue < leftValue) {
            g.tool.errMgr.grammarError(
                ErrorType.EMPTY_STRINGS_AND_SETS_NOT_ALLOWED,
                g.fileName,
                leftNode.parent?.token ?: leftNode.token,
                "${leftNode.text}..${rightNode.text}",
            )
            return false
        }
        return true
    }

    override fun stringLiteral(stringLiteralAST: TerminalAST): ATNFactory.Handle {
        val chars = stringLiteralAST.text
        val left = newStateForNode(stringLiteralAST)
        val s = CharSupport.getStringFromGrammarStringLiteral(chars) ?: return ATNFactory.Handle(left, left)

        val n = s.length
        var prev: ATNState = left
        var right: ATNState = left
        var i = 0
        while (i < n) {
            right = newStateForNode(stringLiteralAST)
            val codePoint = s.codePointAtCompat(i)
            prev.addTransition(createTransition(right, codePoint, codePoint, stringLiteralAST))
            prev = right
            i += charCountCompat(codePoint)
        }
        stringLiteralAST.atnState = left
        return ATNFactory.Handle(left, right)
    }

    override fun charSetLiteral(charSetAST: GrammarAST): ATNFactory.Handle {
        val left = newStateForNode(charSetAST)
        val right = newStateForNode(charSetAST)
        val set = getSetFromCharSetLiteral(charSetAST)
        left.addTransition(SetTransition(right, set))
        charSetAST.atnState = left
        return ATNFactory.Handle(left, right)
    }

    private class CharSetParseState(
        val mode: Mode,
        val inRange: Boolean,
        val prevCodePoint: Int,
        val prevProperty: IntervalSet,
    ) {
        enum class Mode {
            NONE,
            ERROR,
            PREV_CODE_POINT,
            PREV_PROPERTY,
        }

        companion object {
            val NONE: CharSetParseState = CharSetParseState(Mode.NONE, false, -1, IntervalSet.EMPTY_SET)
            val ERROR: CharSetParseState = CharSetParseState(Mode.ERROR, false, -1, IntervalSet.EMPTY_SET)
        }
    }

    fun getSetFromCharSetLiteral(charSetAST: GrammarAST): IntervalSet {
        val rawText = charSetAST.text
        val chars =
            if (rawText.length >= 2) {
                rawText.substring(1, rawText.length - 1)
            } else {
                ""
            }
        val set = IntervalSet()
        var state = CharSetParseState.NONE

        val n = chars.length
        var i = 0
        while (i < n) {
            if (state.mode == CharSetParseState.Mode.ERROR) {
                return IntervalSet()
            }
            val c = chars.codePointAtCompat(i)
            var offset = charCountCompat(c)
            if (c == '\\'.code) {
                val escapeParseResult = EscapeSequenceParsing.parseEscape(chars, i)
                when (escapeParseResult.type) {
                    EscapeSequenceParsing.ResultKind.INVALID -> {
                        val endIdx = (escapeParseResult.startOffset + escapeParseResult.parseLength).coerceAtMost(chars.length)
                        val invalid = chars.substring(escapeParseResult.startOffset, endIdx)
                        g.tool.errMgr.grammarError(
                            ErrorType.INVALID_ESCAPE_SEQUENCE,
                            g.fileName,
                            charSetAST.token,
                            invalid,
                        )
                        state = CharSetParseState.ERROR
                    }

                    EscapeSequenceParsing.ResultKind.CODE_POINT -> {
                        state = applyPrevStateAndMoveToCodePoint(charSetAST, set, state, escapeParseResult.codePoint)
                    }

                    EscapeSequenceParsing.ResultKind.PROPERTY -> {
                        state = applyPrevStateAndMoveToProperty(charSetAST, set, state, escapeParseResult.propertyIntervalSet)
                    }
                }
                offset = escapeParseResult.parseLength
            } else if (
                c == '-'.code &&
                !state.inRange &&
                i != 0 &&
                i != n - 1 &&
                state.mode != CharSetParseState.Mode.NONE
            ) {
                if (state.mode == CharSetParseState.Mode.PREV_PROPERTY) {
                    g.tool.errMgr.grammarError(
                        ErrorType.UNICODE_PROPERTY_NOT_ALLOWED_IN_RANGE,
                        g.fileName,
                        charSetAST.token,
                        charSetAST.text,
                    )
                    state = CharSetParseState.ERROR
                } else {
                    state = CharSetParseState(state.mode, true, state.prevCodePoint, state.prevProperty)
                }
            } else {
                state = applyPrevStateAndMoveToCodePoint(charSetAST, set, state, c)
            }
            i += offset
        }
        if (state.mode == CharSetParseState.Mode.ERROR) {
            return IntervalSet()
        }
        applyPrevState(charSetAST, set, state)

        if (set.isNil) {
            g.tool.errMgr.grammarError(
                ErrorType.EMPTY_STRINGS_AND_SETS_NOT_ALLOWED,
                g.fileName,
                charSetAST.token,
                "[]",
            )
        }

        return set
    }

    private fun applyPrevStateAndMoveToCodePoint(
        charSetAST: GrammarAST,
        set: IntervalSet,
        state: CharSetParseState,
        codePoint: Int,
    ): CharSetParseState =
        if (state.inRange) {
            if (state.prevCodePoint > codePoint) {
                g.tool.errMgr.grammarError(
                    ErrorType.EMPTY_STRINGS_AND_SETS_NOT_ALLOWED,
                    g.fileName,
                    charSetAST.token,
                    CharSupport.getRangeEscapedString(state.prevCodePoint, codePoint),
                )
            }
            checkRangeAndAddToSet(charSetAST, set, state.prevCodePoint, codePoint)
            CharSetParseState.NONE
        } else {
            applyPrevState(charSetAST, set, state)
            CharSetParseState(
                CharSetParseState.Mode.PREV_CODE_POINT,
                false,
                codePoint,
                IntervalSet.EMPTY_SET,
            )
        }

    private fun applyPrevStateAndMoveToProperty(
        charSetAST: GrammarAST,
        set: IntervalSet,
        state: CharSetParseState,
        property: IntervalSet,
    ): CharSetParseState =
        if (state.inRange) {
            g.tool.errMgr.grammarError(
                ErrorType.UNICODE_PROPERTY_NOT_ALLOWED_IN_RANGE,
                g.fileName,
                charSetAST.token,
                charSetAST.text,
            )
            CharSetParseState.ERROR
        } else {
            applyPrevState(charSetAST, set, state)
            CharSetParseState(
                CharSetParseState.Mode.PREV_PROPERTY,
                false,
                -1,
                property,
            )
        }

    private fun applyPrevState(
        charSetAST: GrammarAST,
        set: IntervalSet,
        state: CharSetParseState,
    ) {
        when (state.mode) {
            CharSetParseState.Mode.NONE, CharSetParseState.Mode.ERROR -> {}
            CharSetParseState.Mode.PREV_CODE_POINT -> checkCharAndAddToSet(charSetAST, set, state.prevCodePoint)
            CharSetParseState.Mode.PREV_PROPERTY -> set.addAll(state.prevProperty)
        }
    }

    private fun checkCharAndAddToSet(
        ast: GrammarAST,
        set: IntervalSet,
        c: Int,
    ) {
        checkRangeAndAddToSet(ast, ast, set, c, c, currentRule?.caseInsensitive ?: false, null)
    }

    private fun checkRangeAndAddToSet(
        mainAst: GrammarAST,
        set: IntervalSet,
        a: Int,
        b: Int,
    ) {
        checkRangeAndAddToSet(mainAst, mainAst, set, a, b, currentRule?.caseInsensitive ?: false, null)
    }

    private fun checkRangeAndAddToSet(
        rootAst: GrammarAST,
        ast: GrammarAST,
        set: IntervalSet,
        a: Int,
        b: Int,
        caseInsensitive: Boolean,
        previousStatus: CharactersDataCheckStatus?,
    ): CharactersDataCheckStatus {
        val charactersData =
            RangeBorderCharactersData.getAndCheckCharactersData(
                a,
                b,
                g,
                ast,
                previousStatus == null || !previousStatus.notImpliedCharacters,
            )
        val status: CharactersDataCheckStatus
        if (caseInsensitive) {
            val initial = CharactersDataCheckStatus(false, charactersData.mixOfLowerAndUpperCharCase)
            status =
                if (charactersData.isSingleRange) {
                    checkRangeAndAddToSet(rootAst, ast, set, a, b, false, initial)
                } else {
                    val lowerStatus =
                        checkRangeAndAddToSet(
                            rootAst,
                            ast,
                            set,
                            charactersData.lowerFrom,
                            charactersData.lowerTo,
                            false,
                            initial,
                        )
                    checkRangeAndAddToSet(
                        rootAst,
                        ast,
                        set,
                        charactersData.upperFrom,
                        charactersData.upperTo,
                        false,
                        lowerStatus,
                    )
                }
        } else {
            var charactersCollision = previousStatus != null && previousStatus.collision
            if (!charactersCollision) {
                for (i in a..b) {
                    if (set.contains(i)) {
                        val rootChildren = rootAst.children
                        val setText =
                            if (rootChildren.isEmpty()) {
                                rootAst.text
                            } else {
                                val sb = StringBuilder()
                                for ((idx, child) in rootChildren.withIndex()) {
                                    if (idx > 0) {
                                        sb.append(" | ")
                                    }
                                    if (child is RangeAST) {
                                        sb.append(child.getChild(0)?.text.orEmpty())
                                        sb.append("..")
                                        sb.append(child.getChild(1)?.text.orEmpty())
                                    } else {
                                        sb.append(child.text)
                                    }
                                }
                                sb.toString()
                            }
                        val charsString =
                            if (a == b) {
                                a.toChar().toString()
                            } else {
                                "${a.toChar()}-${b.toChar()}"
                            }
                        g.tool.errMgr.grammarError(
                            ErrorType.CHARACTERS_COLLISION_IN_SET,
                            g.fileName,
                            ast.token,
                            charsString,
                            setText,
                        )
                        charactersCollision = true
                        break
                    }
                }
            }
            status = CharactersDataCheckStatus(charactersCollision, charactersData.mixOfLowerAndUpperCharCase)
            set.add(a, b)
        }
        return status
    }

    private fun createTransition(
        target: ATNState,
        from: Int,
        to: Int,
        tree: GrammarAST,
    ): Transition {
        val charactersData = RangeBorderCharactersData.getAndCheckCharactersData(from, to, g, tree, true)
        return if (currentRule?.caseInsensitive == true) {
            if (charactersData.isSingleRange) {
                CodePointTransitions.createWithCodePointRange(target, from, to)
            } else {
                val intervalSet = IntervalSet()
                intervalSet.add(charactersData.lowerFrom, charactersData.lowerTo)
                intervalSet.add(charactersData.upperFrom, charactersData.upperTo)
                SetTransition(target, intervalSet)
            }
        } else {
            CodePointTransitions.createWithCodePointRange(target, from, to)
        }
    }

    override fun tokenRef(node: TerminalAST): ATNFactory.Handle {
        if (node.text == "EOF") {
            val left = newStateForNode(node)
            val right = newStateForNode(node)
            left.addTransition(AtomTransition(right, IntStream.EOF))
            return ATNFactory.Handle(left, right)
        }
        return buildRuleRef(node)
    }

    private fun createLexerAction(
        id: GrammarAST,
        arg: GrammarAST?,
    ): LexerAction? {
        val command = id.text
        checkCommands(command, id.token)

        return when {
            command == "skip" && arg == null -> LexerSkipAction.INSTANCE
            command == "more" && arg == null -> LexerMoreAction.INSTANCE
            command == "popMode" && arg == null -> LexerPopModeAction.INSTANCE
            command == "mode" && arg != null -> {
                val mode = getModeConstantValue(arg.text, arg.token) ?: return null
                LexerModeAction(mode)
            }

            command == "pushMode" && arg != null -> {
                val mode = getModeConstantValue(arg.text, arg.token) ?: return null
                LexerPushModeAction(mode)
            }

            command == "type" && arg != null -> {
                val type = getTokenConstantValue(arg.text, arg.token) ?: return null
                LexerTypeAction(type)
            }

            command == "channel" && arg != null -> {
                val channel = getChannelConstantValue(arg.text, arg.token) ?: return null
                LexerChannelAction(channel)
            }

            else -> null
        }
    }

    private fun checkCommands(
        command: String,
        commandToken: Token?,
    ) {
        if (command != "pushMode" && command != "popMode") {
            if (ruleCommands.contains(command)) {
                g.tool.errMgr.grammarError(ErrorType.DUPLICATED_COMMAND, g.fileName, commandToken, command)
            }

            var firstCommand: String? = null
            when (command) {
                "skip" -> {
                    if (ruleCommands.contains("more")) {
                        firstCommand = "more"
                    } else if (ruleCommands.contains("type")) {
                        firstCommand = "type"
                    } else if (ruleCommands.contains("channel")) {
                        firstCommand = "channel"
                    }
                }

                "more" -> {
                    if (ruleCommands.contains("skip")) {
                        firstCommand = "skip"
                    } else if (ruleCommands.contains("type")) {
                        firstCommand = "type"
                    } else if (ruleCommands.contains("channel")) {
                        firstCommand = "channel"
                    }
                }

                "type", "channel" -> {
                    if (ruleCommands.contains("more")) {
                        firstCommand = "more"
                    } else if (ruleCommands.contains("skip")) {
                        firstCommand = "skip"
                    }
                }
            }

            if (firstCommand != null) {
                g.tool.errMgr.grammarError(
                    ErrorType.INCOMPATIBLE_COMMANDS,
                    g.fileName,
                    commandToken,
                    firstCommand,
                    command,
                )
            }
        }

        ruleCommands.add(command)
    }

    private fun getModeConstantValue(
        modeName: String?,
        token: Token?,
    ): Int? {
        if (modeName == null) {
            return null
        }
        if (modeName == "DEFAULT_MODE") {
            return Lexer.DEFAULT_MODE
        }
        if (COMMON_CONSTANTS.containsKey(modeName)) {
            g.tool.errMgr.grammarError(
                ErrorType.MODE_CONFLICTS_WITH_COMMON_CONSTANTS,
                g.fileName,
                token,
                token?.text ?: modeName,
            )
            return null
        }

        val modeNames = ArrayList(lexerGrammar.modes.keys)
        val mode = modeNames.indexOf(modeName)
        if (mode >= 0) {
            return mode
        }

        val numeric = modeName.toIntOrNull()
        if (numeric != null) {
            return numeric
        }
        g.tool.errMgr.grammarError(
            ErrorType.CONSTANT_VALUE_IS_NOT_A_RECOGNIZED_MODE_NAME,
            g.fileName,
            token,
            token?.text ?: modeName,
        )
        return null
    }

    private fun getTokenConstantValue(
        tokenName: String?,
        token: Token?,
    ): Int? {
        if (tokenName == null) {
            return null
        }
        if (tokenName == "EOF") {
            return Lexer.EOF
        }
        if (COMMON_CONSTANTS.containsKey(tokenName)) {
            g.tool.errMgr.grammarError(
                ErrorType.TOKEN_CONFLICTS_WITH_COMMON_CONSTANTS,
                g.fileName,
                token,
                token?.text ?: tokenName,
            )
            return null
        }

        val tokenType = g.getTokenType(tokenName)
        if (tokenType != Token.INVALID_TYPE) {
            return tokenType
        }

        val numeric = tokenName.toIntOrNull()
        if (numeric != null) {
            return numeric
        }
        g.tool.errMgr.grammarError(
            ErrorType.CONSTANT_VALUE_IS_NOT_A_RECOGNIZED_TOKEN_NAME,
            g.fileName,
            token,
            token?.text ?: tokenName,
        )
        return null
    }

    private fun getChannelConstantValue(
        channelName: String?,
        token: Token?,
    ): Int? {
        if (channelName == null) {
            return null
        }
        if (channelName == "HIDDEN") {
            return Lexer.HIDDEN
        }
        if (channelName == "DEFAULT_TOKEN_CHANNEL") {
            return Lexer.DEFAULT_TOKEN_CHANNEL
        }
        if (COMMON_CONSTANTS.containsKey(channelName)) {
            g.tool.errMgr.grammarError(
                ErrorType.CHANNEL_CONFLICTS_WITH_COMMON_CONSTANTS,
                g.fileName,
                token,
                token?.text ?: channelName,
            )
            return null
        }

        val channelValue = g.getChannelValue(channelName)
        if (channelValue >= Token.MIN_USER_CHANNEL_VALUE) {
            return channelValue
        }

        val numeric = channelName.toIntOrNull()
        if (numeric != null) {
            return numeric
        }
        g.tool.errMgr.grammarError(
            ErrorType.CONSTANT_VALUE_IS_NOT_A_RECOGNIZED_CHANNEL_NAME,
            g.fileName,
            token,
            token?.text ?: channelName,
        )
        return null
    }

    companion object {
        val COMMON_CONSTANTS: Map<String, Int> =
            mapOf(
                "HIDDEN" to Lexer.HIDDEN,
                "DEFAULT_TOKEN_CHANNEL" to Lexer.DEFAULT_TOKEN_CHANNEL,
                "DEFAULT_MODE" to Lexer.DEFAULT_MODE,
                "SKIP" to Lexer.SKIP,
                "MORE" to Lexer.MORE,
                "EOF" to Lexer.EOF,
                "MAX_CHAR_VALUE" to Lexer.MAX_CHAR_VALUE,
                "MIN_CHAR_VALUE" to Lexer.MIN_CHAR_VALUE,
            )

        val commonConstants: Set<String>
            get() = COMMON_CONSTANTS.keys
    }
}
