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

class LexerInterpreter internal constructor(
    grammarFileName: String?,
    vocabulary: Vocabulary,
    ruleNames: Collection<String?>,
    channelNames: Collection<String?>,
    modeNames: Collection<String?>,
    atn: ATN,
    input: CharStream?,
) : Lexer(input) {
    override val grammarFileName: String
    override val atn: ATN

    override val tokenNames: Array<String>
    override val ruleNames: Array<String>?
    val interpreterChannelNames: Array<String>
    val interpreterModeNames: Array<String>

    override var vocabulary: Vocabulary = VocabularyImpl.fromTokenNames(emptyArray())

    protected val _decisionToDFA: Array<DFA?>
    protected val _sharedContextCache: PredictionContextCache = PredictionContextCache()

    @Deprecated("")
    internal constructor(
        grammarFileName: String?,
        tokenNames: Collection<String?>,
        ruleNames: Collection<String?>,
        modeNames: Collection<String?>,
        atn: ATN,
        input: CharStream?,
    ) : this(
        grammarFileName,
        VocabularyImpl.fromTokenNames(tokenNames.map { it ?: "" }.toTypedArray()),
        ruleNames,
        ArrayList<String?>(),
        modeNames,
        atn,
        input,
    )

    @Deprecated("")
    internal constructor(
        grammarFileName: String?,
        vocabulary: Vocabulary,
        ruleNames: Collection<String?>,
        modeNames: Collection<String?>,
        atn: ATN,
        input: CharStream?,
    ) : this(grammarFileName, vocabulary, ruleNames, ArrayList<String?>(), modeNames, atn, input)

    init {
        require(atn.grammarType === ATNType.LEXER) { "The ATN must be a lexer ATN." }

        this.grammarFileName = grammarFileName!!
        this.atn = atn
        val tokenNamesArr = Array(atn.maxTokenType) { "" }
        for (i in tokenNamesArr.indices) {
            tokenNamesArr[i] = vocabulary.getDisplayName(i) ?: ""
        }
        this.tokenNames = tokenNamesArr

        this.ruleNames = ruleNames.map { it ?: "" }.toTypedArray()
        this.interpreterChannelNames = channelNames.map { it ?: "" }.toTypedArray()
        this.interpreterModeNames = modeNames.map { it ?: "" }.toTypedArray()
        this.vocabulary = vocabulary

        val decisionToDFA = Array(atn.numberOfDecisions) { i -> DFA(atn.getDecisionState(i), i) }
        this._decisionToDFA = arrayOfNulls(atn.numberOfDecisions)
        for (i in decisionToDFA.indices) {
            this._decisionToDFA[i] = decisionToDFA[i]
        }
        this.interpreter = LexerATNSimulator(this, atn, decisionToDFA, _sharedContextCache)
    }
}
