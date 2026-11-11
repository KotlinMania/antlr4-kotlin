/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.misc.CharSupport
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType

/**
 * Base class for [GrammarAST] nodes capable of carrying grammar/rule/block/element options.
 */
abstract class GrammarASTWithOptions : GrammarAST {
    internal var optionMap: MutableMap<String, GrammarAST?>? = null

    constructor(node: GrammarASTWithOptions) : super(node) {
        val existing = node.optionMap
        if (existing != null) {
            this.optionMap = LinkedHashMap(existing)
        }
    }

    constructor(t: Token?) : super(t)

    constructor(type: Int) : super(type)

    constructor(
        type: Int,
        t: Token?,
    ) : super(type, t)

    constructor(
        type: Int,
        t: Token?,
        text: String?,
    ) : super(type, t, text)

    fun setOption(
        key: String,
        node: GrammarAST?,
    ) {
        var map = optionMap
        if (map == null) {
            map = LinkedHashMap()
            optionMap = map
        }
        map[key] = node
    }

    open fun getOptionString(key: String): String? {
        val value = getOptionAST(key) ?: return null
        if (value is ActionAST) {
            return value.text
        }
        var v: String? = value.text
        if (v != null && (v.startsWith("'") || v.startsWith("\""))) {
            v = CharSupport.getStringFromGrammarStringLiteral(v)
            if (v == null) {
                val owner = g
                owner?.errorManager?.grammarError(
                    ErrorType.INVALID_ESCAPE_SEQUENCE,
                    owner.fileName,
                    value.token,
                    value.text,
                )
                v = ""
            }
        }
        return v
    }

    /**
     * Gets AST node holding value for option [key]; ignores default options and command-line forced options.
     */
    fun getOptionAST(key: String): GrammarAST? = optionMap?.get(key)

    val numberOfOptions: Int
        get() = optionMap?.size ?: 0

    val options: Map<String, GrammarAST?>
        get() = optionMap ?: emptyMap()

    abstract override fun dupNode(): GrammarASTWithOptions
}
