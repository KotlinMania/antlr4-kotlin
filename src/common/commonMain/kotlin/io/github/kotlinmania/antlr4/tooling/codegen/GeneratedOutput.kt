/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

/** Pure-Kotlin rendered source output produced by [CodeGenerator] and [OutputModelWalker]. */
class GeneratedOutput(
    val text: String,
) {
    fun render(): String = text

    override fun toString(): String = text

    override fun equals(other: Any?): Boolean =
        other is GeneratedOutput && text == other.text

    override fun hashCode(): Int = text.hashCode()
}
