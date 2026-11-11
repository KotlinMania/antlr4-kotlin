/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.BailErrorStrategy
import io.github.kotlinmania.antlr4.DefaultErrorStrategy
import io.github.kotlinmania.antlr4.InputMismatchException
import io.github.kotlinmania.antlr4.InterpreterRuleContext
import io.github.kotlinmania.antlr4.Parser
import io.github.kotlinmania.antlr4.ParserInterpreter
import io.github.kotlinmania.antlr4.ParserRuleContext
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.Vocabulary
import io.github.kotlinmania.antlr4.asControlException
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNDeserializer
import io.github.kotlinmania.antlr4.atn.ATNSerializer
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.atn.DecisionState
import io.github.kotlinmania.antlr4.atn.PredictionMode
import io.github.kotlinmania.antlr4.atn.StarLoopEntryState
import io.github.kotlinmania.antlr4.misc.BitSet
import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.antlr4.tree.Trees

/**
 * Grammar-aware [ParserInterpreter] that records original outermost alternative numbers on
 * [GrammarInterpreterRuleContext] nodes and computes ambiguous / lookahead parse trees.
 */
class GrammarParserInterpreter : ParserInterpreter {
    val g: Grammar

    private var decisionStatesThatSetOuterAltNumInContext: BitSet
    private var stateToAltsMap: Array<IntArray?>

    constructor(
        g: Grammar,
        grammarFileName: String,
        vocabulary: Vocabulary,
        ruleNames: List<String>,
        atn: ATN,
        input: TokenStream?,
    ) : super(grammarFileName, vocabulary, ruleNames, atn, input) {
        this.g = g
        this.decisionStatesThatSetOuterAltNumInContext = findOuterMostDecisionStates()
        val stateCount = maxOf(atn.states.size, g.atn?.states?.size ?: 0)
        this.stateToAltsMap = arrayOfNulls(stateCount)
    }

    constructor(
        g: Grammar,
        atn: ATN,
        input: TokenStream?,
    ) : super(
            g.fileName ?: Grammar.GRAMMAR_FROM_STRING_NAME,
            g.getVocabulary(),
            g.ruleNames,
            atn,
            input,
        ) {
        this.g = g
        this.decisionStatesThatSetOuterAltNumInContext = findOuterMostDecisionStates()
        val stateCount = maxOf(atn.states.size, g.atn?.states?.size ?: 0)
        this.stateToAltsMap = arrayOfNulls(stateCount)
    }

    override fun createInterpreterRuleContext(
        parent: ParserRuleContext?,
        invokingStateNumber: Int,
        ruleIndex: Int,
    ): InterpreterRuleContext =
        GrammarInterpreterRuleContext(parent, invokingStateNumber, ruleIndex)

    override fun createTempInterpreter(tokens: TokenStream?): ParserInterpreter =
        GrammarParserInterpreter(g, atn, tokens)

    override fun reset() {
        super.reset()
        overrideDecisionRoot = null
    }

    fun findOuterMostDecisionStates(): BitSet {
        val track = BitSet()
        val numberOfDecisions = atn.numberOfDecisions
        for (i in 0 until numberOfDecisions) {
            val decisionState = atn.getDecisionState(i) ?: continue
            val startState = atn.ruleToStartState.getOrNull(decisionState.ruleIndex) ?: continue
            if (decisionState is StarLoopEntryState) {
                if (decisionState.isPrecedenceDecision && decisionState.numberOfTransitions > 0) {
                    val blockStart = decisionState.transition(0).target
                    track.set(blockStart.stateNumber)
                }
            } else if (startState.numberOfTransitions > 0 && startState.transition(0).target === decisionState) {
                track.set(decisionState.stateNumber)
            }
        }
        return track
    }

    override fun visitDecisionState(p: DecisionState): Int {
        val predictedAlt = super.visitDecisionState(p)
        if (p.numberOfTransitions > 1) {
            if (p.decision == overrideDecision && inputField?.index() == overrideDecisionInputIndex) {
                overrideDecisionRoot = getContext() as? GrammarInterpreterRuleContext
            }
        }

        val ruleCtx = ctx as? GrammarInterpreterRuleContext
        if (ruleCtx != null && decisionStatesThatSetOuterAltNumInContext.get(p.stateNumber)) {
            ruleCtx.outerAltNum = predictedAlt
            val r = g.getRule(p.ruleIndex)
            val startState = if (r != null) atn.ruleToStartState.getOrNull(r.index) else null
            if (r != null && startState != null && startState.isLeftRecursiveRule) {
                var alts = stateToAltsMap.getOrNull(p.stateNumber)
                val lr = r as? LeftRecursiveRule
                if (lr != null) {
                    if (p.stateType == ATNState.BLOCK_START) {
                        if (alts == null) {
                            alts = lr.primaryAlts
                            if (p.stateNumber in stateToAltsMap.indices) {
                                stateToAltsMap[p.stateNumber] = alts
                            }
                        }
                    } else if (p.stateType == ATNState.STAR_BLOCK_START) {
                        if (alts == null) {
                            alts = lr.recursiveOpAlts
                            if (p.stateNumber in stateToAltsMap.indices) {
                                stateToAltsMap[p.stateNumber] = alts
                            }
                        }
                    }
                }
                if (alts != null && predictedAlt in alts.indices) {
                    ruleCtx.outerAltNum = alts[predictedAlt]
                }
            }
        }

        return predictedAlt
    }

    /**
     * Error recovery strategy used during lookahead parse-tree exploration: latches the first error
     * token index and consumes one erroneous token so error nodes appear in the constructed tree.
     */
    class BailButConsumeErrorStrategy : DefaultErrorStrategy() {
        var firstErrorTokenIndex: Int = -1

        override fun recover(
            recognizer: Parser?,
            e: RecognitionException?,
        ) {
            val activeRecognizer = recognizer ?: return
            val input = activeRecognizer.tokenStream
            val errIndex = input.index()
            if (firstErrorTokenIndex == -1) {
                firstErrorTokenIndex = errIndex
            }
            if (input.index() < input.size() - 1) {
                activeRecognizer.consume()
            }
        }

        override fun recoverInline(recognizer: Parser?): Token? {
            val activeRecognizer = recognizer ?: return null
            val errIndex = activeRecognizer.tokenStream.index()
            if (firstErrorTokenIndex == -1) {
                firstErrorTokenIndex = errIndex
            }
            throw InputMismatchException(activeRecognizer).asControlException()
        }

        override fun sync(recognizer: Parser?) {
            // Do not consume tokens proactively during lookahead tree probing.
        }
    }

    companion object {
        fun getAllPossibleParseTrees(
            g: Grammar,
            originalParser: Parser,
            tokens: TokenStream,
            decision: Int,
            alts: BitSet,
            startIndex: Int,
            stopIndex: Int,
            startRuleIndex: Int,
        ): List<ParserRuleContext> {
            var effectiveStopIndex = stopIndex
            val trees = ArrayList<ParserRuleContext>()
            val parser = deriveTempParserInterpreter(g, originalParser, tokens)

            if (effectiveStopIndex >= tokens.size() - 1) {
                effectiveStopIndex = tokens.size() - 2
            }

            var alt = alts.nextSetBit(0)
            while (alt >= 0) {
                parser.reset()
                parser.addDecisionOverride(decision, startIndex, alt)
                val t = parser.parse(startRuleIndex)
                var ambigSubTree: ParserRuleContext? =
                    if (t != null) Trees.getRootOfSubtreeEnclosingRegion(t, startIndex, effectiveStopIndex) else null
                val overrideRoot = parser.decisionOverrideRoot
                if (overrideRoot != null && Trees.isAncestorOf(overrideRoot, ambigSubTree)) {
                    ambigSubTree = overrideRoot
                }
                if (ambigSubTree != null) {
                    trees.add(ambigSubTree)
                }
                alt = alts.nextSetBit(alt + 1)
            }

            return trees
        }

        fun getLookaheadParseTrees(
            g: Grammar,
            originalParser: ParserInterpreter,
            tokens: TokenStream,
            startRuleIndex: Int,
            decision: Int,
            startIndex: Int,
            stopIndex: Int,
        ): List<ParserRuleContext> {
            val trees = ArrayList<ParserRuleContext>()
            val parser = deriveTempParserInterpreter(g, originalParser, tokens)
            val decisionState = originalParser.atn.decisionToState.getOrNull(decision) ?: return trees

            for (alt in 1..decisionState.transitions.size) {
                val errorStrategy = BailButConsumeErrorStrategy()
                parser.errorHandler = errorStrategy
                parser.reset()
                parser.addDecisionOverride(decision, startIndex, alt)
                val tt = parser.parse(startRuleIndex) ?: continue
                var stopTreeAt = stopIndex
                if (errorStrategy.firstErrorTokenIndex >= 0) {
                    stopTreeAt = errorStrategy.firstErrorTokenIndex
                }
                val overallRange: Interval = tt.sourceInterval
                if (stopTreeAt > overallRange.b) {
                    stopTreeAt = overallRange.b
                }
                var subtree = Trees.getRootOfSubtreeEnclosingRegion(tt, startIndex, stopTreeAt)
                val overrideRoot = parser.decisionOverrideRoot
                if (overrideRoot != null && Trees.isAncestorOf(overrideRoot, subtree)) {
                    subtree = overrideRoot
                }
                Trees.stripChildrenOutOfRange(subtree, overrideRoot, startIndex, stopTreeAt)
                if (subtree != null) {
                    trees.add(subtree)
                }
            }

            return trees
        }

        fun deriveTempParserInterpreter(
            g: Grammar,
            originalParser: Parser,
            tokens: TokenStream?,
        ): ParserInterpreter {
            val parser: ParserInterpreter =
                when (originalParser) {
                    is GrammarParserInterpreter -> GrammarParserInterpreter(g, originalParser.atn, tokens)
                    is ParserInterpreter -> originalParser.createTempInterpreter(tokens)
                    else -> {
                        val serialized = ATNSerializer.getSerialized(g.getATN())
                        val deserialized = ATNDeserializer().deserialize(serialized.toArray())
                        GrammarParserInterpreter(g, deserialized, tokens)
                    }
                }
            parser.setInputStream(tokens)
            parser.errorHandler = BailErrorStrategy()
            parser.removeErrorListeners()
            parser.removeParseListeners()
            parser.interpreter?.setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION)
            return parser
        }
    }
}
