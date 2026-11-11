/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST

/**
 * Grammars, rules, and alternatives all have symbols visible to actions.
 * Actions resolve attribute expressions against their context-appropriate [AttributeResolver].
 */
interface AttributeResolver {
    fun resolvesToListLabel(
        x: String?,
        node: ActionAST?,
    ): Boolean

    fun resolvesToLabel(
        x: String?,
        node: ActionAST?,
    ): Boolean

    fun resolvesToAttributeDict(
        x: String?,
        node: ActionAST?,
    ): Boolean

    fun resolvesToToken(
        x: String?,
        node: ActionAST?,
    ): Boolean

    fun resolveToAttribute(
        x: String?,
        node: ActionAST?,
    ): Attribute?

    fun resolveToAttribute(
        x: String?,
        y: String?,
        node: ActionAST?,
    ): Attribute?
}
