/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.Token

/**
 * Tracks the name, type, declaration text, and initializer of an attribute defined in
 * argument lists, return values, or scope blocks.
 */
class Attribute {
    /** The entire declaration such as `"String foo"` or `"x:int"`. */
    var decl: String? = null

    /** The type; might be empty such as for Python which has no static typing. */
    var type: String? = null

    /** The name of the attribute `"foo"`. */
    var name: String? = null

    /** A [Token] giving the position of the name of this attribute in the grammar. */
    var token: Token? = null

    /** The optional attribute initialization expression. */
    var initValue: String? = null

    /** Containing dictionary. */
    var dict: AttributeDict? = null

    constructor()

    constructor(name: String?) : this(name, null)

    constructor(
        name: String?,
        decl: String?,
    ) {
        this.name = name
        this.decl = decl
    }

    override fun toString(): String {
        val currentInit = initValue
        val currentType = type
        if (currentInit != null) {
            return "${name}:$currentType=$currentInit"
        }
        if (currentType != null) {
            return "${name}:$currentType"
        }
        return name ?: ""
    }
}
