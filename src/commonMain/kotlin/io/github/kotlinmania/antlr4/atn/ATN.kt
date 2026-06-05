/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.atn

import io.github.kotlinmania.antlr4.RuleContext
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.misc.IntervalSet

class ATN(
    val grammarType: ATNType,
    val maxTokenType: Int,
) {
    internal val mutableStates: MutableList<ATNState> = ArrayList()
    val states: List<ATNState>
        get() = mutableStates

    internal val mutableDecisionToState: MutableList<DecisionState> = ArrayList()
    val decisionToState: List<DecisionState>
        get() = mutableDecisionToState

    var ruleToStartState: Array<RuleStartState?> = arrayOf()
        internal set
    var ruleToStopState: Array<RuleStopState?> = arrayOf()
        internal set

    internal val mutableModeNameToStartState: MutableMap<String, TokensStartState> = LinkedHashMap()
    val modeNameToStartState: Map<String, TokensStartState>
        get() = mutableModeNameToStartState

    var ruleToTokenType: IntArray = IntArray(0)
        internal set
    var lexerActions: Array<LexerAction?> = arrayOf()
        internal set

    internal val mutableModeToStartState: MutableList<TokensStartState> = ArrayList()
    val modeToStartState: List<TokensStartState>
        get() = mutableModeToStartState

    fun nextTokens(
        s: ATNState,
        ctx: RuleContext?,
    ): IntervalSet = LL1Analyzer(this).LOOK(s, ctx)

    fun nextTokens(s: ATNState): IntervalSet {
        if (s.nextTokenWithinRule != null) return s.nextTokenWithinRule!!
        s.nextTokenWithinRule = nextTokens(s, null)
        s.nextTokenWithinRule!!.makeReadonly()
        return s.nextTokenWithinRule!!
    }

    fun addState(state: ATNState?) {
        if (state != null) {
            state.atn = this
            state.stateNumber = mutableStates.size
        }
        mutableStates.add(
            state ?: object : ATNState() {
                override val stateType: Int = INVALID_TYPE
            },
        )
    }

    fun removeState(state: ATNState) {
        // Replace with invalid-state sentinel instead of null
        mutableStates[state.stateNumber] =
            object : ATNState() {
                override val stateType: Int = INVALID_TYPE
            }
    }

    fun defineDecisionState(s: DecisionState): Int {
        mutableDecisionToState.add(s)
        s.decision = mutableDecisionToState.size - 1
        return s.decision
    }

    fun getDecisionState(decision: Int): DecisionState? {
        if (mutableDecisionToState.isNotEmpty() && decision in mutableDecisionToState.indices) {
            return mutableDecisionToState[decision]
        }
        return null
    }

    val numberOfDecisions: Int
        get() = mutableDecisionToState.size

    fun getExpectedTokens(
        stateNumber: Int,
        context: RuleContext?,
    ): IntervalSet {
        require(stateNumber in 0 until mutableStates.size) { "Invalid state number." }

        var ctx = context
        val s = mutableStates[stateNumber]
        var following = nextTokens(s)
        if (!following.contains(Token.EPSILON)) return following

        val expected = IntervalSet()
        expected.addAll(following)
        expected.remove(Token.EPSILON)
        while (ctx != null && ctx.invokingState >= 0 && following.contains(Token.EPSILON)) {
            val invokingState = mutableStates[ctx.invokingState]
            val rt = invokingState.transition(0) as RuleTransition
            following = nextTokens(rt.followState)
            expected.addAll(following)
            expected.remove(Token.EPSILON)
            ctx = ctx.parent as? RuleContext
        }
        if (following.contains(Token.EPSILON)) {
            expected.add(Token.EOF)
        }
        return expected
    }

    companion object {
        const val INVALID_ALT_NUMBER: Int = 0
    }
}
