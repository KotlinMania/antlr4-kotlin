/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionChunk
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionText

/** Output model node representing a generated parser source file. */
class ParserFile(
    factory: OutputModelFactory,
    fileName: String,
) : OutputFile(factory, fileName) {
    val genPackage: String?
    val exportMacro: String?
    val genListener: Boolean
    val genVisitor: Boolean

    @ModelElement
    var parser: Parser? = null

    @ModelElement
    val namedActions: Map<String, Action>

    @ModelElement
    var contextSuperClass: ActionChunk? = null

    val grammarName: String

    init {
        val g = factory.grammar
        namedActions = buildNamedActions(g)
        genPackage = g.tool.genPackage
        exportMacro = g.getOptionString("exportMacro")
        genListener = g.tool.genListener
        genVisitor = g.tool.genVisitor
        grammarName = g.name.orEmpty()
        val ctxSuper = g.getOptionString("contextSuperClass")
        if (ctxSuper != null) {
            contextSuperClass = ActionText(null, ctxSuper)
        }
    }
}
