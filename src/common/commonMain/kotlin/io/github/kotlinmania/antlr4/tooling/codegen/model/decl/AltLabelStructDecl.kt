/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.decl

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Declaration model for a `#altLabel` context subclass extending the parent rule's [StructDecl]. */
class AltLabelStructDecl(
    factory: OutputModelFactory,
    r: Rule,
    val altNum: Int,
    altLabel: String,
) : StructDecl(
        factory = factory,
        r = r,
        resolvedName = "${altLabel.replaceFirstChar { it.uppercaseChar() }}Context",
        derivedFromName = altLabel,
        includeRuleDispatchMethods = false,
    ) {
    val parentRule: String = r.name

    init {
        provideCopyFrom = false
        populateDefaultDispatchMethods(factory)
    }
}
