/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.InterpreterRuleContext
import io.github.kotlinmania.antlr4.ParserRuleContext

/**
 * An [InterpreterRuleContext] that records which outermost alternative of a rule was matched,
 * including original pre-transformation alternative numbers for left-recursive rules.
 */
class GrammarInterpreterRuleContext(
    parent: ParserRuleContext?,
    invokingStateNumber: Int,
    ruleIndex: Int,
) : InterpreterRuleContext(parent, invokingStateNumber, ruleIndex) {
    var outerAltNum: Int = 1

    override var altNumber: Int
        get() = outerAltNum
        set(value) {
            outerAltNum = value
        }
}
