/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Output model node representing a generated parser recognizer class. */
class Parser(
    factory: OutputModelFactory,
    val file: ParserFile,
) : Recognizer(factory) {
    internal val mutableFuncs: MutableList<RuleFunction> = ArrayList()

    @ModelElement
    val funcs: List<RuleFunction>
        get() = mutableFuncs

    fun addRuleFunction(func: RuleFunction) {
        mutableFuncs.add(func)
    }
}
