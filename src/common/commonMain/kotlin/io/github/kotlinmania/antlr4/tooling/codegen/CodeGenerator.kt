/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject
import io.github.kotlinmania.antlr4.tooling.codegen.target.JavaTarget
import io.github.kotlinmania.antlr4.tooling.codegen.target.KotlinTarget
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool

/**
 * Pure-Kotlin ANTLR 4 code generator orchestrating [OutputModelController], [Target],
 * [OutputModelWalker], and `.tokens` vocabulary emission.
 */
class CodeGenerator(
    val tool: Tool,
    val grammar: Grammar,
    languageOverride: String?,
) {
    val language: String = languageOverride ?: grammar.language ?: DEFAULT_LANGUAGE

    var lineWidth: Int = 72

    private var cachedTarget: Target? = null

    val target: Target
        get() {
            var current = cachedTarget
            if (current == null) {
                current = instantiateTarget(language)
                cachedTarget = current
            }
            return current
        }

    constructor(g: Grammar) : this(g.tool, g, g.language)

    private fun instantiateTarget(lang: String): Target =
        when (lang) {
            "Kotlin" -> KotlinTarget(this)
            "Java" -> JavaTarget(this)
            else -> {
                if (!Tool.isSupportedTargetLanguage(lang)) {
                    tool.errMgr.toolError(ErrorType.CANNOT_CREATE_TARGET_GENERATOR, lang)
                }
                KotlinTarget(this)
            }
        }

    private fun createController(): OutputModelController {
        val factory: OutputModelFactory =
            if (grammar.isLexer) {
                LexerFactory(this)
            } else {
                ParserFactory(this)
            }
        val controller = OutputModelController(factory)
        factory.controller = controller
        return controller
    }

    fun generateLexer(header: Boolean = false): GeneratedOutput =
        walk(createController().buildLexerOutputModel(header), header)

    fun generateParser(header: Boolean = false): GeneratedOutput =
        walk(createController().buildParserOutputModel(header), header)

    fun generateListener(header: Boolean = false): GeneratedOutput =
        walk(createController().buildListenerOutputModel(header), header)

    fun generateBaseListener(header: Boolean = false): GeneratedOutput =
        walk(createController().buildBaseListenerOutputModel(header), header)

    fun generateVisitor(header: Boolean = false): GeneratedOutput =
        walk(createController().buildVisitorOutputModel(header), header)

    fun generateBaseVisitor(header: Boolean = false): GeneratedOutput =
        walk(createController().buildBaseVisitorOutputModel(header), header)

    fun walk(
        outputModel: OutputModelObject,
        header: Boolean = false,
    ): GeneratedOutput {
        val walker = OutputModelWalker(tool, target)
        return walker.walk(outputModel, header)
    }

    fun writeRecognizer(
        outputFileST: GeneratedOutput,
        header: Boolean = false,
    ) {
        write(outputFileST, getRecognizerFileName(header))
    }

    fun writeListener(
        outputFileST: GeneratedOutput,
        header: Boolean = false,
    ) {
        write(outputFileST, getListenerFileName(header))
    }

    fun writeBaseListener(
        outputFileST: GeneratedOutput,
        header: Boolean = false,
    ) {
        write(outputFileST, getBaseListenerFileName(header))
    }

    fun writeVisitor(
        outputFileST: GeneratedOutput,
        header: Boolean = false,
    ) {
        write(outputFileST, getVisitorFileName(header))
    }

    fun writeBaseVisitor(
        outputFileST: GeneratedOutput,
        header: Boolean = false,
    ) {
        write(outputFileST, getBaseVisitorFileName(header))
    }

    fun writeVocabFile() {
        val vocabContent = buildVocabFileContent()
        tool.writeOutputFile(grammar, vocabFileName, vocabContent)
    }

    fun buildVocabFileContent(): String {
        val sb = StringBuilder()
        for ((tokenName, tokenType) in grammar.tokenNameToTypeMap) {
            if (tokenType >= Token.MIN_USER_TOKEN_TYPE) {
                sb.append(tokenName).append('=').append(tokenType).append('\n')
            }
        }
        for ((lit, tokenType) in grammar.stringLiteralToTypeMap) {
            if (tokenType >= Token.MIN_USER_TOKEN_TYPE) {
                sb.append(lit).append('=').append(tokenType).append('\n')
            }
        }
        return sb.toString()
    }

    fun write(
        code: GeneratedOutput,
        fileName: String,
    ) {
        tool.writeOutputFile(grammar, fileName, code.render())
    }

    fun getRecognizerFileName(header: Boolean = false): String =
        target.getRecognizerFileName(header)

    fun getListenerFileName(header: Boolean = false): String =
        target.getListenerFileName(header)

    fun getVisitorFileName(header: Boolean = false): String =
        target.getVisitorFileName(header)

    fun getBaseListenerFileName(header: Boolean = false): String =
        target.getBaseListenerFileName(header)

    fun getBaseVisitorFileName(header: Boolean = false): String =
        target.getBaseVisitorFileName(header)

    val vocabFileName: String
        get() = "${grammar.name ?: "Grammar"}$VOCAB_FILE_EXTENSION"

    val headerFileName: String?
        get() = null

    companion object {
        const val VOCAB_FILE_EXTENSION: String = ".tokens"
        const val DEFAULT_LANGUAGE: String = "Kotlin"

        fun create(g: Grammar): CodeGenerator =
            create(g.tool, g, g.language)

        fun create(
            tool: Tool,
            g: Grammar,
            language: String?,
        ): CodeGenerator = CodeGenerator(tool, g, language)
    }
}
