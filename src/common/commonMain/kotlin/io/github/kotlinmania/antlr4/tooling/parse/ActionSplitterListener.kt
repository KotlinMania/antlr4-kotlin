/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.Token

/**
 * Callback interface invoked by [ActionSplitter] as `$x`, `$x.y`, `$x::y`, assignments,
 * comments, and verbatim target-language text are scanned in an embedded grammar action.
 */
interface ActionSplitterListener {
    fun qualifiedAttr(
        expr: String,
        x: Token,
        y: Token,
    )

    fun setAttr(
        expr: String,
        x: Token,
        rhs: Token,
    )

    fun attr(
        expr: String,
        x: Token,
    )

    fun setNonLocalAttr(
        expr: String,
        x: Token,
        y: Token,
        rhs: Token,
    )

    fun nonLocalAttr(
        expr: String,
        x: Token,
        y: Token,
    )

    fun text(text: String)
}
