/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Output model node representing a generated lexer recognizer class. */
class Lexer(
    factory: OutputModelFactory,
    val file: LexerFile,
) : Recognizer(factory) {
    val modes: Collection<String>

    internal val mutableActionFuncs: LinkedHashMap<Rule, RuleActionFunction> = LinkedHashMap()

    @ModelElement
    val actionFuncs: Map<Rule, RuleActionFunction>
        get() = mutableActionFuncs

    init {
        val g = factory.grammar as LexerGrammar
        modes = g.modes.keys.toList()
    }
}
