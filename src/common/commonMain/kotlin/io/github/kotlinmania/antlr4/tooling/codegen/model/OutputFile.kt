/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool

/** Base output model node representing a generated source file. */
abstract class OutputFile(
    factory: OutputModelFactory,
    val fileName: String,
) : OutputModelObject(factory) {
    fun interface NamedActionFilter {
        fun accept(ast: ActionAST): Boolean
    }

    val grammarFileName: String
    val ANTLRVersion: String = Tool.VERSION
    var TokenLabelType: String?
    var InputSymbolType: String?

    init {
        val g = factory.grammar
        val rawFile = g.fileName.orEmpty()
        grammarFileName = rawFile.substringAfterLast('/').substringAfterLast('\\')
        TokenLabelType = g.getOptionString("TokenLabelType")
        InputSymbolType = TokenLabelType
    }

    fun buildNamedActions(
        g: Grammar,
        filter: NamedActionFilter? = null,
    ): Map<String, Action> {
        val ownerFactory = requireNotNull(factory)
        val namedActions = LinkedHashMap<String, Action>()
        for ((name, ast) in g.namedActions) {
            if (filter == null || filter.accept(ast)) {
                namedActions[name] = Action(ownerFactory, ast)
            }
        }
        return namedActions
    }
}
