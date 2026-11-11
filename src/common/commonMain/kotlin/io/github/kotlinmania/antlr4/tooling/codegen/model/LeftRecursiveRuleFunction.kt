/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.RuleContextDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.RuleContextListDecl
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.LeftRecursiveRule

/** Output model node representing a direct left-recursive parser rule lowered to precedence climbing. */
class LeftRecursiveRuleFunction(
    factory: OutputModelFactory,
    r: LeftRecursiveRule,
) : RuleFunction(factory, r) {
    init {
        val target = factory.generator.target
        for (refLabel in r.leftRecursiveRuleRefLabels) {
            val idAST = refLabel.labelAST
            val altLabel = refLabel.altLabel
            val labelName = idAST.text
            val ctxName = target.getRuleFunctionContextStructName(r)
            val d =
                if (idAST.parent?.type == ANTLRParserTypes.PLUS_ASSIGN) {
                    RuleContextListDecl(factory, labelName, ctxName)
                } else {
                    RuleContextDecl(factory, labelName, ctxName)
                }
            val struct =
                if (altLabel != null) {
                    mutableAltLabelCtxs?.get(altLabel) ?: ruleCtx
                } else {
                    ruleCtx
                }
            struct.addDecl(d)
        }
    }
}
