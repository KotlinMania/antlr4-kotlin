/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.semantics

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.parse.ActionSplitterListener

/**
 * Default no-op [ActionSplitterListener] base class extended by semantic checkers and codegen translators.
 */
open class BlankActionSplitterListener : ActionSplitterListener {
    override fun qualifiedAttr(
        expr: String,
        x: Token,
        y: Token,
    ) {
    }

    override fun setAttr(
        expr: String,
        x: Token,
        rhs: Token,
    ) {
    }

    override fun attr(
        expr: String,
        x: Token,
    ) {
    }

    override fun setNonLocalAttr(
        expr: String,
        x: Token,
        y: Token,
        rhs: Token,
    ) {
    }

    override fun nonLocalAttr(
        expr: String,
        x: Token,
        y: Token,
    ) {
    }

    override fun text(text: String) {
    }

    open fun templateInstance(expr: String?) {
    }

    open fun indirectTemplateInstance(expr: String?) {
    }

    open fun setExprAttribute(expr: String?) {
    }

    open fun setSTAttribute(expr: String?) {
    }

    open fun templateExpr(expr: String?) {
    }
}
