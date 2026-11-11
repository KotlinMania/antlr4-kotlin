/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.analysis

import io.github.kotlinmania.antlr4.tooling.ast.AltAST

/**
 * Metadata recorded for an alternative transformed from a left-recursive rule.
 */
class LeftRecursiveRuleAltInfo(
    var altNum: Int,
    var altText: String?,
    var leftRecursiveRuleRefLabel: String?,
    var altLabel: String?,
    val isListLabel: Boolean,
    var originalAltAST: AltAST?,
) {
    var altAST: AltAST? = null
    var nextPrec: Int = 0

    constructor(
        altNum: Int,
        altText: String?,
    ) : this(altNum, altText, null, null, false, null)
}
