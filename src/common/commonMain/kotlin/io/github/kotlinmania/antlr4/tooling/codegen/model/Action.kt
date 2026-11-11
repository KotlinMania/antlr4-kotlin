/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.codegen.ActionTranslator
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionChunk
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionTemplate
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionText
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.LeftRecursiveActionSpec
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.StructDecl

/** Output model node representing an embedded target action decomposed into [ActionChunk]s. */
open class Action : RuleElement {
    @ModelElement
    val chunks: List<ActionChunk>

    constructor(
        factory: OutputModelFactory,
        ast: ActionAST?,
    ) : super(factory, ast) {
        val rf = factory.currentRuleFunction
        chunks =
            if (ast != null) {
                ActionTranslator.translateAction(factory, rf, ast.token, ast)
            } else {
                emptyList()
            }
    }

    constructor(
        factory: OutputModelFactory,
        ctx: StructDecl?,
        action: String,
    ) : super(factory, null) {
        chunks = listOf(ActionText(ctx, action))
    }

    constructor(
        factory: OutputModelFactory,
        ctx: StructDecl?,
        spec: LeftRecursiveActionSpec,
    ) : super(factory, null) {
        chunks = listOf(ActionTemplate(ctx, spec))
    }
}
