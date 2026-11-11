/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar

/**
 * Pipeline stage executing symbol-collision validation and emitting recognizer, listener,
 * visitor, and `.tokens` outputs for a processed [Grammar].
 */
class CodeGenPipeline(
    val g: Grammar,
) {
    fun process() {
        val gen = CodeGenerator.create(g)
        val target = gen.target

        val idTypes =
            IntervalSet().apply {
                add(ANTLRParserTypes.ID)
                add(ANTLRParserTypes.RULE_REF)
                add(ANTLRParserTypes.TOKEN_REF)
            }
        val rootAst = g.ast
        if (rootAst != null) {
            for (idNode in rootAst.getNodesWithType(idTypes)) {
                if (target.visibleGrammarSymbolCausesIssueInGeneratedCode(idNode)) {
                    g.tool.errMgr.grammarError(
                        ErrorType.USE_OF_BAD_WORD,
                        g.fileName,
                        idNode.token,
                        idNode.text,
                    )
                }
            }
        }

        val errorCountBefore = g.tool.errMgr.numErrors

        if (g.isLexer) {
            val lexerOut = gen.generateLexer(header = false)
            if (g.tool.errMgr.numErrors == errorCountBefore) {
                gen.writeRecognizer(lexerOut, header = false)
            }
        } else {
            val parserOut = gen.generateParser(header = false)
            if (g.tool.errMgr.numErrors == errorCountBefore) {
                gen.writeRecognizer(parserOut, header = false)
            }

            if (g.tool.genListener) {
                val listenerOut = gen.generateListener(header = false)
                if (g.tool.errMgr.numErrors == errorCountBefore) {
                    gen.writeListener(listenerOut, header = false)
                }
                if (target.wantsBaseListener()) {
                    val baseListenerOut = gen.generateBaseListener(header = false)
                    if (g.tool.errMgr.numErrors == errorCountBefore) {
                        gen.writeBaseListener(baseListenerOut, header = false)
                    }
                }
            }

            if (g.tool.genVisitor) {
                val visitorOut = gen.generateVisitor(header = false)
                if (g.tool.errMgr.numErrors == errorCountBefore) {
                    gen.writeVisitor(visitorOut, header = false)
                }
                if (target.wantsBaseVisitor()) {
                    val baseVisitorOut = gen.generateBaseVisitor(header = false)
                    if (g.tool.errMgr.numErrors == errorCountBefore) {
                        gen.writeBaseVisitor(baseVisitorOut, header = false)
                    }
                }
            }
        }

        gen.writeVocabFile()
    }
}
