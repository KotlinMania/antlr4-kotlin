/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Output model node representing a generated `ParseTreeVisitor` source file. */
open class VisitorFile(
    factory: OutputModelFactory,
    fileName: String,
) : OutputFile(factory, fileName) {
    val genPackage: String?
    val accessLevel: String?
    val exportMacro: String?
    val grammarName: String
    val parserName: String
    val visitorNames: Set<String>
    val visitorLabelRuleNames: Map<String, String>

    @ModelElement
    var header: Action? = null

    @ModelElement
    val namedActions: Map<String, Action>

    init {
        val g = factory.grammar
        parserName = g.recognizerName.orEmpty()
        grammarName = g.name.orEmpty()
        namedActions = buildNamedActions(g) { ast -> ast.scope == null }

        val names = LinkedHashSet<String>()
        val labelRules = LinkedHashMap<String, String>()
        for (r in g.rules.values) {
            val labels = r.altLabels
            if (labels != null) {
                for (key in labels.keys) {
                    names.add(key)
                    labelRules[key] = r.name
                }
            } else {
                names.add(r.name)
            }
        }
        visitorNames = names
        visitorLabelRuleNames = labelRules

        val headerAst = g.namedActions["header"]
        if (headerAst != null && headerAst.scope == null) {
            header = Action(factory, headerAst)
        }
        genPackage = g.tool.genPackage
        accessLevel = g.getOptionString("accessLevel")
        exportMacro = g.getOptionString("exportMacro")
    }
}
