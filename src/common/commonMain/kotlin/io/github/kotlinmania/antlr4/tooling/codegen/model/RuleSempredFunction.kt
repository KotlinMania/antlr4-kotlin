/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Output model node for a per-rule semantic-predicate dispatch helper method (`<rule>Sempred`). */
class RuleSempredFunction(
    factory: OutputModelFactory,
    r: Rule,
    ctxType: String,
) : RuleActionFunction(factory, r, ctxType)
