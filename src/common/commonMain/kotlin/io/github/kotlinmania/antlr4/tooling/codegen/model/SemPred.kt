/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.AbstractPredicateTransition
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.codegen.ActionTranslator
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionChunk

/** Output model node representing a semantic predicate (`{...}?`) and optional `<fail=...>` message. */
class SemPred(
    factory: OutputModelFactory,
    ast: ActionAST,
) : Action(factory, ast) {
    val msg: String?
    val predicate: String

    @ModelElement
    var failChunks: List<ActionChunk>? = null

    init {
        val atnState = ast.atnState
        check(atnState != null && atnState.numberOfTransitions == 1 && atnState.transition(0) is AbstractPredicateTransition) {
            "Expected semantic predicate ATN state with single AbstractPredicateTransition."
        }
        val failNode = ast.getOptionAST("fail")
        var rawPredicate = ast.text
        if (rawPredicate.startsWith("{") && rawPredicate.endsWith("}?")) {
            rawPredicate = rawPredicate.substring(1, rawPredicate.length - 2)
        }
        predicate = factory.generator.target.getTargetStringLiteralFromString(rawPredicate)

        if (failNode == null) {
            msg = null
        } else if (failNode is ActionAST) {
            msg = null
            failChunks = ActionTranslator.translateAction(factory, factory.currentRuleFunction, failNode.token, failNode)
        } else {
            msg =
                factory.generator.target.getTargetStringLiteralFromANTLRStringLiteral(
                    factory.generator,
                    failNode.text,
                    addQuotes = true,
                    escapeSpecial = true,
                )
        }
    }
}
