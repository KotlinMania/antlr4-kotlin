/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes

/**
 * Pure-Kotlin `make`-compatible build dependency generator implementing `depend.stg`.
 */
class BuildDependencyGenerator(
    val tool: Tool,
    val g: Grammar,
) {
    val generatedFileList: List<String>?
        get() {
            val files = ArrayList<String>()
            val lang = g.language ?: "Kotlin"
            val ext = codeFileExtension(lang)
            val headerExt = headerFileExtension(lang)
            val supportsSplit = g.tool.genSplitParser && supportsSplitParser(lang)

            val grammarName = g.name ?: ""
            val recName = g.recognizerName ?: grammarName

            if (headerExt != null) {
                files.add(getOutputFile("$recName$headerExt"))
            }
            if (supportsSplit) {
                files.add(getOutputFile("$recName$ext"))
                files.add(getOutputFile("${recName}Contexts$ext"))
                files.add(getOutputFile("${recName}DFA$ext"))
            } else {
                files.add(getOutputFile("$recName$ext"))
            }

            val vocabFileName = "$grammarName$VOCAB_FILE_EXTENSION"
            files.add(getOutputFile(vocabFileName))

            if (g.isCombined) {
                val lexerSuffix = Grammar.getGrammarTypeToFileNameSuffix(ANTLRParserTypes.LEXER)
                files.add(getOutputFile("$grammarName$lexerSuffix$ext"))
                files.add(getOutputFile("$grammarName$lexerSuffix$VOCAB_FILE_EXTENSION"))
                if (headerExt != null) {
                    files.add(getOutputFile("$grammarName$lexerSuffix$headerExt"))
                }
            }

            if (!g.isLexer && g.tool.genListener) {
                if (headerExt != null) {
                    files.add(getOutputFile("${grammarName}Listener$headerExt"))
                }
                files.add(getOutputFile("${grammarName}Listener$ext"))
                if (!isSwiftOrGoWithoutBase(lang)) {
                    if (headerExt != null) {
                        files.add(getOutputFile("${grammarName}BaseListener$headerExt"))
                    }
                    files.add(getOutputFile("${grammarName}BaseListener$ext"))
                }
            }

            if (!g.isLexer && g.tool.genVisitor) {
                if (headerExt != null) {
                    files.add(getOutputFile("${grammarName}Visitor$headerExt"))
                }
                files.add(getOutputFile("${grammarName}Visitor$ext"))
                if (!isSwiftOrGoWithoutBase(lang)) {
                    if (headerExt != null) {
                        files.add(getOutputFile("${grammarName}BaseVisitor$headerExt"))
                    }
                    files.add(getOutputFile("${grammarName}BaseVisitor$ext"))
                }
            }

            val imports = g.allImportedGrammars
            if (imports != null) {
                for (imp in imports) {
                    val impFile = imp.fileName
                    if (impFile != null) {
                        files.add(getOutputFile(impFile))
                    }
                }
            }

            return if (files.isEmpty()) null else files
        }

    fun getOutputFile(fileName: String): String {
        var outputDir = tool.getOutputDirectory(g.fileName)
        if (outputDir == ".") {
            outputDir = tool.getOutputDirectory(fileName)
        }
        if (outputDir == ".") {
            return fileName
        }
        val baseName = outputDir.substringAfterLast('/')
        if (baseName == ".") {
            val dot = outputDir.lastIndexOf('.')
            if (dot >= 0) {
                outputDir = outputDir.substring(0, dot)
            }
        }
        if (outputDir.substringAfterLast('/').contains(' ')) {
            outputDir = outputDir.replace(" ", "\\ ")
        }
        return joinPath(outputDir, fileName)
    }

    val dependenciesFileList: List<String>?
        get() {
            val files = ArrayList(nonImportDependenciesFileList)
            val imports = g.allImportedGrammars
            if (imports != null) {
                val libDir = tool.libDirectory ?: "."
                for (imp in imports) {
                    val impFile = imp.fileName
                    if (impFile != null) {
                        files.add(groomQualifiedFileName(libDir, impFile))
                    }
                }
            }
            return if (files.isEmpty()) null else files
        }

    val nonImportDependenciesFileList: List<String>
        get() {
            val files = ArrayList<String>()
            val tokenVocab = g.getOptionString("tokenVocab")
            if (tokenVocab != null) {
                val fileName = "$tokenVocab$VOCAB_FILE_EXTENSION"
                val libDir = tool.libDirectory ?: "."
                val vocabPath = if (libDir == ".") fileName else joinPath(libDir, fileName)
                files.add(vocabPath)
            }
            return files
        }

    fun renderDependencies(): String {
        val inputs = dependenciesFileList
        val outputs = generatedFileList
        val grammarFile = g.fileName ?: ""
        return buildString {
            if (!inputs.isNullOrEmpty()) {
                append(grammarFile).append(": ").append(inputs.joinToString(", ")).append("\n")
            }
            if (!outputs.isNullOrEmpty()) {
                append(outputs.joinToString("\n") { f -> "$f : $grammarFile" })
            }
        }
    }

    fun groomQualifiedFileName(
        outputDir: String,
        fileName: String,
    ): String =
        when {
            outputDir == "." -> fileName
            outputDir.contains(' ') -> joinPath(outputDir.replace(" ", "\\ "), fileName)
            else -> joinPath(outputDir, fileName)
        }

    private fun joinPath(
        dir: String,
        name: String,
    ): String =
        if (dir.endsWith("/") || dir.endsWith("\\")) "$dir$name" else "$dir/$name"

    companion object {
        const val VOCAB_FILE_EXTENSION: String = ".tokens"

        internal fun codeFileExtension(language: String): String =
            when (language) {
                "Kotlin" -> ".kt"
                "Java" -> ".java"
                "CSharp" -> ".cs"
                "Python2", "Python3" -> ".py"
                "JavaScript" -> ".js"
                "TypeScript" -> ".ts"
                "Cpp" -> ".cpp"
                "Go" -> ".go"
                "Swift" -> ".swift"
                "PHP" -> ".php"
                "Dart" -> ".dart"
                else -> ".kt"
            }

        internal fun headerFileExtension(language: String): String? =
            when (language) {
                "Cpp" -> ".h"
                else -> null
            }

        internal fun supportsSplitParser(language: String): Boolean =
            language == "Go" || language == "Kotlin"

        private fun isSwiftOrGoWithoutBase(language: String): Boolean = false
    }
}
