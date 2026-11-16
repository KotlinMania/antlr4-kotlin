/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Output model node for a per-rule `action` dispatch helper method (`<rule>Action`). */
open class RuleActionFunction(
    factory: OutputModelFactory,
    r: Rule,
    val ctxType: String,
) : OutputModelObject(factory) {
    val name: String = r.name
    val ruleIndex: Int = r.index
    val escapedName: String = factory.generator.target.escapeIfNeeded(r.name)

    internal val mutableActions: LinkedHashMap<Int, Action> = LinkedHashMap()

    @ModelElement
    val actions: Map<Int, Action>
        get() = mutableActions
}
