/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.atn.ATN

/** A handy class for use with
 *
 * options {contextSuperClass=io.github.kotlinmania.antlr4.RuleContextWithAltNum;}
 *
 * that provides a backing field / impl for the outer alternative number
 * matched for an internal parse tree node.
 *
 * I'm only putting into Java runtime as I'm certain I'm the only one that
 * will really every use this.
 */
class RuleContextWithAltNum : ParserRuleContext {
    override var altNumber: Int = 0

    constructor() {
        this.altNumber = ATN.INVALID_ALT_NUMBER
    }

    constructor(parent: ParserRuleContext?, invokingStateNumber: Int) : super(parent, invokingStateNumber)
}
