/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.decl

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp

/** Base declaration node representing a field, parameter, local variable, or context accessor. */
open class Decl(
    factory: OutputModelFactory?,
    val name: String,
    val decl: String? = null,
) : SrcOp(factory) {
    val escapedName: String =
        factory
            ?.generator
            ?.target
            ?.escapeIfNeeded(name)
            .orEmpty()
            .ifEmpty { name }

    var isImplicit: Boolean = false
    var ctx: StructDecl? = null

    override fun hashCode(): Int = name.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Decl) return false
        if (other is ContextGetterDecl) return false
        return name == other.name
    }
}
