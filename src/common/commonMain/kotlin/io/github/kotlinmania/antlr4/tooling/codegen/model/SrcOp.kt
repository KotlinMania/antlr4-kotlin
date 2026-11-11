/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock

/** Base executable/declarative node inside a generated rule function or context structure. */
abstract class SrcOp(
    factory: OutputModelFactory? = null,
    ast: GrammarAST? = null,
) : OutputModelObject(factory, ast) {
    val uniqueID: Int = nextUniqueId++

    var enclosingBlock: CodeBlock? = null
    var enclosingRuleRunction: RuleFunction? = null

    val contextName: String?
        get() {
            val block = findOuterMostAltBlock()
            val alt = block?.altLabel
            if (alt != null) {
                return alt.name
            }
            return enclosingRuleRunction?.ruleCtx?.name
        }

    fun findOuterMostAltBlock(): CodeBlockForOuterMostAlt? {
        if (this is CodeBlockForOuterMostAlt) {
            return this
        }
        var p: CodeBlock? = enclosingBlock
        while (p != null) {
            if (p is CodeBlockForOuterMostAlt) {
                return p
            }
            p = p.enclosingBlock
        }
        return null
    }

    companion object {
        private var nextUniqueId: Int = 1
    }
}
