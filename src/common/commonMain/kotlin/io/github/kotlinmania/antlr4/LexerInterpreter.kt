/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNType
import io.github.kotlinmania.antlr4.atn.LexerATNSimulator
import io.github.kotlinmania.antlr4.atn.PredictionContextCache
import io.github.kotlinmania.antlr4.dfa.DFA

open class LexerInterpreter(
    grammarFileName: String?,
    vocabulary: Vocabulary,
    ruleNames: List<String>,
    channelNames: List<String>,
    modeNames: List<String>,
    atn: ATN,
    input: CharStream?,
) : Lexer(input) {
    init {
        require(atn.grammarType === ATNType.LEXER) { "The ATN must be a lexer ATN." }
    }

    override val grammarFileName: String = requireNotNull(grammarFileName) { "grammarFileName cannot be null" }
    override val atn: ATN = atn

    override val tokenNames: List<String> = List(atn.maxTokenType) { i -> vocabulary.getDisplayName(i).orEmpty() }
    override val ruleNames: List<String>? = ruleNames.toList()
    val interpreterChannelNames: List<String> = channelNames.toList()
    val interpreterModeNames: List<String> = modeNames.toList()

    override val channelNames: List<String>
        get() = interpreterChannelNames

    override val modeNames: List<String>
        get() = interpreterModeNames

    override val vocabulary: Vocabulary = vocabulary

    protected val decisionToDFA: MutableList<DFA> =
        MutableList(atn.numberOfDecisions) { i -> DFA(atn.getDecisionState(i), i) }
    protected val sharedContextCache: PredictionContextCache = PredictionContextCache()

    @Deprecated("")
    internal constructor(
        grammarFileName: String?,
        tokenNames: Collection<String>,
        ruleNames: Collection<String>,
        modeNames: Collection<String>,
        atn: ATN,
        input: CharStream?,
    ) : this(
        grammarFileName,
        VocabularyImpl.fromTokenNames(tokenNames.toList()),
        ruleNames.toList(),
        ArrayList<String>(),
        modeNames.toList(),
        atn,
        input,
    )

    @Deprecated("")
    internal constructor(
        grammarFileName: String?,
        vocabulary: Vocabulary,
        ruleNames: Collection<String>,
        modeNames: Collection<String>,
        atn: ATN,
        input: CharStream?,
    ) : this(grammarFileName, vocabulary, ruleNames.toList(), ArrayList<String>(), modeNames.toList(), atn, input)

    init {
        this.interpreter = LexerATNSimulator(this, atn, this.decisionToDFA, sharedContextCache)
    }
}
