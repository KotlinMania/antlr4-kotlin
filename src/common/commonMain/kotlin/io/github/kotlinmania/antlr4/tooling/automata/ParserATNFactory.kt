/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.automata

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.atn.ATNType
import io.github.kotlinmania.antlr4.atn.AbstractPredicateTransition
import io.github.kotlinmania.antlr4.atn.ActionTransition
import io.github.kotlinmania.antlr4.atn.AtomTransition
import io.github.kotlinmania.antlr4.atn.BasicBlockStartState
import io.github.kotlinmania.antlr4.atn.BasicState
import io.github.kotlinmania.antlr4.atn.BlockEndState
import io.github.kotlinmania.antlr4.atn.BlockStartState
import io.github.kotlinmania.antlr4.atn.EpsilonTransition
import io.github.kotlinmania.antlr4.atn.LL1Analyzer
import io.github.kotlinmania.antlr4.atn.LoopEndState
import io.github.kotlinmania.antlr4.atn.NotSetTransition
import io.github.kotlinmania.antlr4.atn.PlusBlockStartState
import io.github.kotlinmania.antlr4.atn.PlusLoopbackState
import io.github.kotlinmania.antlr4.atn.PrecedencePredicateTransition
import io.github.kotlinmania.antlr4.atn.PredicateTransition
import io.github.kotlinmania.antlr4.atn.RuleStartState
import io.github.kotlinmania.antlr4.atn.RuleStopState
import io.github.kotlinmania.antlr4.atn.RuleTransition
import io.github.kotlinmania.antlr4.atn.SetTransition
import io.github.kotlinmania.antlr4.atn.StarBlockStartState
import io.github.kotlinmania.antlr4.atn.StarLoopEntryState
import io.github.kotlinmania.antlr4.atn.StarLoopbackState
import io.github.kotlinmania.antlr4.atn.Transition
import io.github.kotlinmania.antlr4.atn.WildcardTransition
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.analysis.LeftRecursiveRuleTransformer
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarASTWithOptions
import io.github.kotlinmania.antlr4.tooling.ast.PredAST
import io.github.kotlinmania.antlr4.tooling.ast.QuantifierAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.misc.CharSupport
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.parse.ATNBuilder
import io.github.kotlinmania.antlr4.tooling.semantics.UseDefAnalyzer
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LeftRecursiveRule
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/**
 * ATN construction routines triggered by [ATNBuilder] for parser grammars (and inherited by [LexerATNFactory]).
 */
open class ParserATNFactory(
    val g: Grammar,
) : ATNFactory {
    internal class EpsilonBlockRecord(
        val rule: Rule,
        val startState: ATNState,
        val stopState: ATNState,
    )

    val atn: ATN =
        ATN(
            grammarType = if (g is LexerGrammar) ATNType.LEXER else ATNType.PARSER,
            maxTokenType = g.maxTokenType,
        )

    var currentRule: Rule? = null
    private var currentOuterAltNumber: Int = 0
    val currentOuterAlt: Int
        get() = currentOuterAltNumber

    internal val preventEpsilonClosureBlocks: MutableList<EpsilonBlockRecord> = ArrayList()
    internal val preventEpsilonOptionalBlocks: MutableList<EpsilonBlockRecord> = ArrayList()

    override fun createATN(): ATN {
        buildRulesATN(g.rules.values)
        addRuleFollowLinks()
        addEOFTransitionToStartRules()
        ATNOptimizer.optimize(g, atn)
        checkEpsilonClosure()

        optionalCheck@ for (record in preventEpsilonOptionalBlocks) {
            var bypassCount = 0
            for (i in 0 until record.startState.numberOfTransitions) {
                val startState = record.startState.transition(i).target
                if (startState === record.stopState) {
                    bypassCount++
                    continue
                }

                val analyzer = LL1Analyzer(atn)
                if (analyzer.LOOK(startState, record.stopState, null).contains(Token.EPSILON)) {
                    g.tool.errMgr.grammarError(
                        ErrorType.EPSILON_OPTIONAL,
                        g.fileName,
                        record.rule.ast?.getChild(0)?.token,
                        record.rule.name,
                    )
                    continue@optionalCheck
                }
            }

            if (bypassCount != 1) {
                throw UnsupportedOperationException("Expected optional block with exactly 1 bypass alternative.")
            }
        }

        return atn
    }

    protected fun checkEpsilonClosure() {
        for (record in preventEpsilonClosureBlocks) {
            val analyzer = LL1Analyzer(atn)
            val lookahead = analyzer.LOOK(record.startState, record.stopState, null)
            if (lookahead.contains(Token.EPSILON)) {
                val errorType =
                    if (record.rule is LeftRecursiveRule) {
                        ErrorType.EPSILON_LR_FOLLOW
                    } else {
                        ErrorType.EPSILON_CLOSURE
                    }
                g.tool.errMgr.grammarError(
                    errorType,
                    g.fileName,
                    record.rule.ast?.getChild(0)?.token,
                    record.rule.name,
                )
            }
            if (lookahead.contains(Token.EOF)) {
                g.tool.errMgr.grammarError(
                    ErrorType.EOF_CLOSURE,
                    g.fileName,
                    record.rule.ast?.getChild(0)?.token,
                    record.rule.name,
                )
            }
        }
    }

    protected fun buildRulesATN(rules: Collection<Rule>) {
        createRuleStartAndStopATNStates()

        for (r in rules) {
            val ruleAst = r.ast ?: continue
            val blk = ruleAst.getFirstChildWithType(ANTLRParserTypes.BLOCK) as? BlockAST ?: continue
            val builder = ATNBuilder(this)
            setCurrentRuleName(r.name)
            val h = builder.ruleBlock(blk, null)
            rule(ruleAst, r.name, h)
        }
    }

    override fun setCurrentRuleName(name: String) {
        this.currentRule = g.getRule(name)
    }

    override fun setCurrentOuterAlt(alt: Int) {
        currentOuterAltNumber = alt
    }

    override fun rule(
        ruleAST: GrammarAST,
        name: String,
        blk: ATNFactory.Handle,
    ): ATNFactory.Handle {
        val r = requireNotNull(g.getRule(name)) { "Undefined rule '$name'" }
        val start = requireNotNull(atn.mutableRuleToStartState[r.index]) { "Missing RuleStartState for '$name'" }
        connectEpsilon(start, blk.left)
        val stop = requireNotNull(atn.mutableRuleToStopState[r.index]) { "Missing RuleStopState for '$name'" }
        connectEpsilon(blk.right, stop)
        val h = ATNFactory.Handle(start, stop)
        ruleAST.atnState = start
        return h
    }

    override fun tokenRef(node: TerminalAST): ATNFactory.Handle {
        val left = newStateForNode(node)
        val right = newStateForNode(node)
        val ttype = g.getTokenType(node.text)
        left.addTransition(AtomTransition(right, ttype))
        node.atnState = left
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
        for (t in alts) {
            val ttype = g.getTokenType(t.text)
            set.add(ttype)
        }
        if (invert) {
            left.addTransition(NotSetTransition(right, set))
        } else {
            left.addTransition(SetTransition(right, set))
        }
        associatedAST.atnState = left
        return ATNFactory.Handle(left, right)
    }

    override fun range(
        a: GrammarAST,
        b: GrammarAST,
    ): ATNFactory.Handle {
        g.tool.errMgr.grammarError(
            ErrorType.TOKEN_RANGE_IN_PARSER,
            g.fileName,
            a.token,
            a.token?.text ?: a.text,
            b.token?.text ?: b.text,
        )
        return if (a is TerminalAST) {
            tokenRef(a)
        } else {
            epsilon(a)
        }
    }

    protected fun resolveAtomTokenType(atom: GrammarAST): Int =
        if (g.isLexer) {
            CharSupport.getCharValueFromGrammarCharLiteral(atom.text)
        } else {
            g.getTokenType(atom.text)
        }

    override fun stringLiteral(stringLiteralAST: TerminalAST): ATNFactory.Handle =
        tokenRef(stringLiteralAST)

    override fun charSetLiteral(charSetAST: GrammarAST): ATNFactory.Handle =
        epsilon(charSetAST)

    override fun ruleRef(node: GrammarAST): ATNFactory.Handle =
        buildRuleRef(node)

    internal fun buildRuleRef(node: GrammarAST): ATNFactory.Handle {
        val r = g.getRule(node.text)
        if (r == null) {
            g.tool.errMgr.grammarError(
                ErrorType.INTERNAL_ERROR,
                g.fileName,
                node.token,
                "Rule ${node.text} undefined",
            )
            return epsilon(node)
        }
        val start = requireNotNull(atn.mutableRuleToStartState[r.index]) { "Missing RuleStartState for '${r.name}'" }
        val left = newStateForNode(node)
        val right = newStateForNode(node)
        var precedence = 0
        val precText = (node as? GrammarASTWithOptions)?.getOptionString(LeftRecursiveRuleTransformer.PRECEDENCE_OPTION_NAME)
        if (precText != null) {
            precedence = precText.toInt()
        }
        val call = RuleTransition(start, r.index, precedence, right)
        left.addTransition(call)

        node.atnState = left
        return ATNFactory.Handle(left, right)
    }

    fun addFollowLink(
        ruleIndex: Int,
        right: ATNState,
    ) {
        val stop = atn.mutableRuleToStopState[ruleIndex]
        connectEpsilon(stop, right)
    }

    override fun epsilon(node: GrammarAST?): ATNFactory.Handle {
        val left = newStateForNode(node)
        val right = newStateForNode(node)
        connectEpsilon(left, right)
        if (node != null) {
            node.atnState = left
        }
        return ATNFactory.Handle(left, right)
    }

    override fun sempred(pred: PredAST): ATNFactory.Handle {
        val left = newStateForNode(pred)
        val right = newStateForNode(pred)

        val p: AbstractPredicateTransition
        val precOption = pred.getOptionString(LeftRecursiveRuleTransformer.PRECEDENCE_OPTION_NAME)
        if (precOption != null) {
            val precedence = precOption.toInt()
            p = PrecedencePredicateTransition(right, precedence)
        } else {
            val isCtxDependent = UseDefAnalyzer.actionIsContextDependent(pred)
            val ruleIndex = currentRule?.index ?: -1
            val predIndex = g.sempreds[pred] ?: -1
            p = PredicateTransition(right, ruleIndex, predIndex, isCtxDependent)
        }

        left.addTransition(p)
        pred.atnState = left
        return ATNFactory.Handle(left, right)
    }

    override fun action(action: ActionAST): ATNFactory.Handle {
        val left = newStateForNode(action)
        val right = newStateForNode(action)
        val ruleIndex = currentRule?.index ?: -1
        val a = ActionTransition(right, ruleIndex)
        left.addTransition(a)
        action.atnState = left
        return ATNFactory.Handle(left, right)
    }

    override fun actionFromText(action: String): ATNFactory.Handle {
        throw UnsupportedOperationException("This element is not valid in parsers.")
    }

    override fun block(
        blkAST: BlockAST,
        ebnfRoot: GrammarAST?,
        alts: List<ATNFactory.Handle>,
    ): ATNFactory.Handle {
        if (ebnfRoot == null) {
            if (alts.size == 1) {
                val h = alts[0]
                blkAST.atnState = h.left
                return h
            }
            val start = registerNewState(BasicBlockStartState())
            if (alts.size > 1) {
                atn.defineDecisionState(start)
            }
            return makeBlock(start, blkAST, alts)
        }
        return when (ebnfRoot.type) {
            ANTLRParserTypes.OPTIONAL -> {
                val start = registerNewState(BasicBlockStartState())
                atn.defineDecisionState(start)
                val h = makeBlock(start, blkAST, alts)
                optional(ebnfRoot, h)
            }

            ANTLRParserTypes.CLOSURE -> {
                val starState = registerNewState(StarBlockStartState())
                if (alts.size > 1) {
                    atn.defineDecisionState(starState)
                }
                val h = makeBlock(starState, blkAST, alts)
                star(ebnfRoot, h)
            }

            ANTLRParserTypes.POSITIVE_CLOSURE -> {
                val plusState = registerNewState(PlusBlockStartState())
                if (alts.size > 1) {
                    atn.defineDecisionState(plusState)
                }
                val h = makeBlock(plusState, blkAST, alts)
                plus(ebnfRoot, h)
            }

            else -> throw UnsupportedOperationException("Unsupported EBNF root type: ${ebnfRoot.type}")
        }
    }

    protected fun makeBlock(
        start: BlockStartState,
        blkAST: BlockAST,
        alts: List<ATNFactory.Handle>,
    ): ATNFactory.Handle {
        val end = registerNewState(BlockEndState())
        start.endState = end
        for (alt in alts) {
            connectEpsilon(start, alt.left)
            connectEpsilon(alt.right, end)
            val opt = TailEpsilonRemover(atn)
            opt.visit(alt.left)
        }
        val h = ATNFactory.Handle(start, end)
        blkAST.atnState = start
        return h
    }

    override fun alt(els: List<ATNFactory.Handle>): ATNFactory.Handle =
        elemList(els)

    fun elemList(els: List<ATNFactory.Handle>): ATNFactory.Handle {
        val n = els.size
        for (i in 0 until n - 1) {
            val el = els[i]
            var tr: Transition? = null
            if (el.left.numberOfTransitions == 1) {
                tr = el.left.transition(0)
            }
            if (
                el.left.stateType == ATNState.BASIC &&
                el.right.stateType == ATNState.BASIC &&
                tr != null &&
                ((tr is RuleTransition && tr.followState === el.right) || tr.target === el.right)
            ) {
                val nextHandle = els[i + 1]
                if (tr is RuleTransition) {
                    tr.followState = nextHandle.left
                } else {
                    tr.target = nextHandle.left
                }
                atn.removeState(el.right)
            } else {
                connectEpsilon(el.right, els[i + 1].left)
            }
        }
        val first = els[0]
        val last = els[n - 1]
        return ATNFactory.Handle(first.left, last.right)
    }

    override fun optional(
        optAST: GrammarAST,
        blk: ATNFactory.Handle,
    ): ATNFactory.Handle {
        val blkStart = blk.left as BlockStartState
        val blkEnd = blk.right
        val activeRule = currentRule
        if (activeRule != null) {
            preventEpsilonOptionalBlocks.add(EpsilonBlockRecord(activeRule, blkStart, blkEnd))
        }

        val greedy = (optAST as? QuantifierAST)?.isGreedy ?: true
        blkStart.nonGreedy = !greedy
        connectEpsilon(blkStart, blk.right, !greedy)

        optAST.atnState = blk.left
        return blk
    }

    override fun plus(
        plusAST: GrammarAST,
        blk: ATNFactory.Handle,
    ): ATNFactory.Handle {
        val blkStart = blk.left as PlusBlockStartState
        val blkEnd = blk.right as BlockEndState
        val activeRule = currentRule
        if (activeRule != null) {
            preventEpsilonClosureBlocks.add(EpsilonBlockRecord(activeRule, blkStart, blkEnd))
        }

        val loop = registerNewState(PlusLoopbackState())
        val isGreedy = (plusAST as? QuantifierAST)?.isGreedy ?: true
        loop.nonGreedy = !isGreedy
        atn.defineDecisionState(loop)
        val end = registerNewState(LoopEndState())
        blkStart.loopBackState = loop
        end.loopBackState = loop

        plusAST.atnState = loop
        connectEpsilon(blkEnd, loop)

        val blkAST = plusAST.getChild(0) as? BlockAST
        if (isGreedy) {
            if (blkAST != null && expectNonGreedy(blkAST)) {
                g.tool.errMgr.grammarError(
                    ErrorType.EXPECTED_NON_GREEDY_WILDCARD_BLOCK,
                    g.fileName,
                    plusAST.token,
                    plusAST.token?.text ?: plusAST.text,
                )
            }
            connectEpsilon(loop, blkStart)
            connectEpsilon(loop, end)
        } else {
            connectEpsilon(loop, end)
            connectEpsilon(loop, blkStart)
        }

        return ATNFactory.Handle(blkStart, end)
    }

    override fun star(
        starAST: GrammarAST,
        blk: ATNFactory.Handle,
    ): ATNFactory.Handle {
        val blkStart = blk.left as StarBlockStartState
        val blkEnd = blk.right as BlockEndState
        val activeRule = currentRule
        if (activeRule != null) {
            preventEpsilonClosureBlocks.add(EpsilonBlockRecord(activeRule, blkStart, blkEnd))
        }

        val entry = registerNewState(StarLoopEntryState())
        val isGreedy = (starAST as? QuantifierAST)?.isGreedy ?: true
        entry.nonGreedy = !isGreedy
        atn.defineDecisionState(entry)
        val end = registerNewState(LoopEndState())
        val loop = registerNewState(StarLoopbackState())
        entry.loopBackState = loop
        end.loopBackState = loop

        val blkAST = starAST.getChild(0) as? BlockAST
        if (isGreedy) {
            if (blkAST != null && expectNonGreedy(blkAST)) {
                g.tool.errMgr.grammarError(
                    ErrorType.EXPECTED_NON_GREEDY_WILDCARD_BLOCK,
                    g.fileName,
                    starAST.token,
                    starAST.token?.text ?: starAST.text,
                )
            }
            connectEpsilon(entry, blkStart)
            connectEpsilon(entry, end)
        } else {
            connectEpsilon(entry, end)
            connectEpsilon(entry, blkStart)
        }
        connectEpsilon(blkEnd, loop)
        connectEpsilon(loop, entry)

        starAST.atnState = entry
        return ATNFactory.Handle(entry, end)
    }

    override fun wildcard(ast: GrammarAST): ATNFactory.Handle {
        val left = newStateForNode(ast)
        val right = newStateForNode(ast)
        left.addTransition(WildcardTransition(right))
        ast.atnState = left
        return ATNFactory.Handle(left, right)
    }

    protected fun connectEpsilon(
        a: ATNState?,
        b: ATNState?,
        prepend: Boolean = false,
    ) {
        if (a != null && b != null) {
            val index = if (prepend) 0 else a.numberOfTransitions
            a.addTransition(index, EpsilonTransition(b))
        }
    }

    fun createRuleStartAndStopATNStates() {
        val count = g.rules.size
        atn.mutableRuleToStartState = MutableList(count) { null }
        atn.mutableRuleToStopState = MutableList(count) { null }
        for (r in g.rules.values) {
            val start = registerNewState(RuleStartState())
            val stop = registerNewState(RuleStopState())
            start.stopState = stop
            start.isLeftRecursiveRule = r is LeftRecursiveRule
            start.ruleIndex = r.index
            stop.ruleIndex = r.index
            atn.mutableRuleToStartState[r.index] = start
            atn.mutableRuleToStopState[r.index] = stop
        }
    }

    fun addRuleFollowLinks() {
        for (p in atn.states) {
            if (
                p.stateType == ATNState.BASIC &&
                p.numberOfTransitions == 1 &&
                p.transition(0) is RuleTransition
            ) {
                val rt = p.transition(0) as RuleTransition
                addFollowLink(rt.ruleIndex, rt.followState)
            }
        }
    }

    fun addEOFTransitionToStartRules(): Int {
        var n = 0
        val eofTarget = newStateForNode(null)
        for (r in g.rules.values) {
            val stop = atn.mutableRuleToStopState[r.index] ?: continue
            if (stop.numberOfTransitions > 0) continue
            n++
            stop.addTransition(AtomTransition(eofTarget, Token.EOF))
        }
        return n
    }

    override fun label(t: ATNFactory.Handle): ATNFactory.Handle = t

    override fun listLabel(t: ATNFactory.Handle): ATNFactory.Handle = t

    internal fun <S : ATNState> registerNewState(state: S): S {
        val activeRule = currentRule
        state.ruleIndex = activeRule?.index ?: -1
        atn.addState(state)
        return state
    }

    internal fun newStateForNode(node: GrammarAST?): ATNState {
        val n = BasicState()
        n.ruleIndex = currentRule?.index ?: -1
        atn.addState(n)
        return n
    }

    override fun newState(): ATNState = newStateForNode(null)

    fun expectNonGreedy(blkAST: BlockAST): Boolean =
        blockHasWildcardAlt(blkAST)

    override fun lexerAltCommands(
        alt: ATNFactory.Handle,
        cmds: ATNFactory.Handle,
    ): ATNFactory.Handle {
        throw UnsupportedOperationException("This element is not allowed in parsers.")
    }

    override fun lexerCallCommand(
        id: GrammarAST,
        arg: GrammarAST,
    ): ATNFactory.Handle? {
        throw UnsupportedOperationException("This element is not allowed in parsers.")
    }

    override fun lexerCommand(id: GrammarAST): ATNFactory.Handle? {
        throw UnsupportedOperationException("This element is not allowed in parsers.")
    }

    companion object {
        /**
         * `(BLOCK (ALT .))` or `(BLOCK (ALT 'a') (ALT .))`.
         */
        fun blockHasWildcardAlt(block: GrammarAST): Boolean {
            for (alt in block.children) {
                val altAST = alt as? AltAST ?: continue
                if (
                    altAST.childCount == 1 ||
                    (altAST.childCount == 2 && altAST.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS)
                ) {
                    val e = altAST.getChild(altAST.childCount - 1)
                    if (e != null && e.type == ANTLRParserTypes.WILDCARD) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
