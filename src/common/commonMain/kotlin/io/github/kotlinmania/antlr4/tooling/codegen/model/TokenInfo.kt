/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

/** Token type and target label pair used in inline lookahead comparisons and switch cases. */
class TokenInfo(
    val type: Int,
    val name: String?,
) {
    override fun toString(): String = "TokenInfo{type=$type, name='$name'}"
}
