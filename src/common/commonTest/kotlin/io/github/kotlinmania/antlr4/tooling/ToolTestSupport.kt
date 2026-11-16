/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.ANTLRInputStream
import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNDeserializer
import io.github.kotlinmania.antlr4.atn.ATNSerializer
import io.github.kotlinmania.antlr4.atn.LexerATNSimulator
import io.github.kotlinmania.antlr4.misc.IntegerList
import io.github.kotlinmania.antlr4.tooling.automata.ATNPrinter
import io.github.kotlinmania.antlr4.tooling.automata.LexerATNFactory
import io.github.kotlinmania.antlr4.tooling.automata.ParserATNFactory
import io.github.kotlinmania.antlr4.tooling.gui.TreeTextProvider
import io.github.kotlinmania.antlr4.tooling.semantics.SemanticPipeline
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import io.github.kotlinmania.antlr4.tree.ErrorNode
import io.github.kotlinmania.antlr4.tree.Tree
import io.github.kotlinmania.antlr4.tree.Trees
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Pure-Kotlin [TreeTextProvider] matching upstream `InterpreterTreeTextProvider`: formats
 * [ErrorNode] leaves as `<error ...>` and rule contexts via [Trees.getNodeText].
 */
class InterpreterTreeTextProvider(
    val ruleNames: List<String>,
) : TreeTextProvider {
    override fun getText(node: Tree?): String {
        if (node == null) return "null"
        val nodeText = Trees.getNodeText(node, ruleNames).orEmpty()
        if (node is ErrorNode) {
            return "<error $nodeText>"
        }
        return nodeText
    }
}

/**
 * Shared test utilities for ANTLR 4 tool semantic, ATN, left-recursion, and interpreter tests.
 */
object ToolTestSupport {
    fun getFilenameFromFirstLineOfGrammar(line: String): String {
        var fileName = "A" + Tool.GRAMMAR_EXTENSION
        val grIndex = line.lastIndexOf("grammar")
        val semi = line.lastIndexOf(';')
        if (grIndex >= 0 && semi >= 0) {
            val space = line.indexOf(' ', grIndex)
            if (space in 0..<semi) {
                fileName = line.substring(space + 1, semi) + Tool.GRAMMAR_EXTENSION
            }
        }
        if (fileName.length == Tool.GRAMMAR_EXTENSION.length) {
            fileName = "A" + Tool.GRAMMAR_EXTENSION
        }
        return fileName
    }

    fun antlrOnString(
        grammarStr: String,
        fileNameOverride: String? = null,
        defaultListener: Boolean = false,
        extraVirtualGrammars: Map<String, String> = emptyMap(),
        extraVirtualTokenVocabs: Map<String, String> = emptyMap(),
        vararg extraArgs: String,
    ): ToolErrorQueue {
        val firstLine = grammarStr.lineSequence().firstOrNull().orEmpty()
        val fileName = fileNameOverride ?: getFilenameFromFirstLineOfGrammar(firstLine)
        val args = extraArgs.toList() + fileName
        val tool = Tool(args)
        tool.writeFilesToDisk = false
        tool.registerVirtualGrammar(fileName, grammarStr)
        for ((name, content) in extraVirtualGrammars) {
            tool.registerVirtualGrammar(name, content)
        }
        for ((vocabName, content) in extraVirtualTokenVocabs) {
            tool.registerVirtualTokenVocab(vocabName, content)
        }

        val equeue = ToolErrorQueue(tool)
        if (!defaultListener) {
            tool.removeListeners()
        }
        tool.addListener(equeue)
        tool.processGrammarsOnCommandLine()
        return equeue
    }

    fun testErrors(
        pairs: Array<String>,
        printTree: Boolean = false,
    ) {
        var i = 0
        while (i < pairs.size) {
            val grammarStr = pairs[i]
            val expect = pairs[i + 1]
            val equeue = antlrOnString(grammarStr, defaultListener = false)
            val actual = equeue.toString(true)
            var msg = grammarStr
            msg = msg.replace("\n", "\\n")
            msg = msg.replace("\r", "\\r")
            msg = msg.replace("\t", "\\t")
            assertEquals(expect, actual, "error in: $msg")
            i += 2
        }
    }

    fun realElements(elements: List<String?>): List<String?> =
        if (elements.size <= Token.MIN_USER_TOKEN_TYPE) {
            emptyList()
        } else {
            elements.subList(Token.MIN_USER_TOKEN_TYPE, elements.size)
        }

    fun createATN(
        g: Grammar,
        useSerializer: Boolean,
    ): ATN {
        if (g.atn == null) {
            semanticProcess(g)
            assertEquals(0, g.tool.numErrors)
            val factory = if (g.isLexer) LexerATNFactory(g as LexerGrammar) else ParserATNFactory(g)
            g.atn = factory.createATN()
            assertEquals(0, g.tool.numErrors)
        }
        val builtAtn = assertNotNull(g.atn)
        if (useSerializer) {
            val serialized = ATNSerializer.getSerialized(builtAtn)
            return ATNDeserializer().deserialize(serialized.toArray())
        }
        return builtAtn
    }

    fun semanticProcess(g: Grammar) {
        val root = g.ast
        if (root != null && !root.hasErrors) {
            val antlr = Tool()
            antlr.writeFilesToDisk = false
            val sem = SemanticPipeline(g)
            sem.process()
            val imported = g.importedGrammars
            if (imported != null) {
                for (imp in imported) {
                    antlr.processNonCombinedGrammar(imp, false)
                }
            }
        }
    }

    fun checkRuleATN(
        g: Grammar,
        ruleName: String,
        expecting: String,
    ) {
        val r = assertNotNull(g.getRule(ruleName), "Missing rule $ruleName")
        val startState = g.getATN().ruleToStartState[r.index]
        val serializer = ATNPrinter(g, startState)
        val result = serializer.asString()
        assertEquals(expecting, result)
    }

    fun getTokenTypesViaATN(
        input: String,
        lexerATN: LexerATNSimulator,
    ): IntegerList {
        val stream = ANTLRInputStream(input)
        val tokenTypes = IntegerList()
        var ttype: Int
        do {
            ttype = lexerATN.match(stream, Lexer.DEFAULT_MODE)
            tokenTypes.add(ttype)
        } while (ttype != Token.EOF)
        return tokenTypes
    }
}
