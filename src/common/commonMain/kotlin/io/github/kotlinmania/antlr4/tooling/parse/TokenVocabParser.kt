/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.internal.withHostFileSystemOrNull
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.GrammarASTOwner
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.readString

/**
 * Loads imported `.tokens` vocabulary files into a ordered token-name/literal to token-type map.
 */
class TokenVocabParser(
    val g: GrammarASTOwner,
) {
    fun load(): Map<String, Int> {
        val tokens = LinkedHashMap<String, Int>()
        val vocabName = g.getOptionString("tokenVocab") ?: return tokens
        val registeredContent = g.getRegisteredTokenVocabContent(vocabName)
        if (registeredContent != null) {
            parseTokensContent(vocabName, registeredContent, tokens)
            return tokens
        }
        withHostFileSystemOrNull {
            val fullPath = resolveImportedVocabPath(vocabName)
            if (!SystemFileSystem.exists(fullPath)) {
                val inTree = g.ast?.getOptionAST("tokenVocab")
                val inTreeValue = inTree?.token?.text
                val unquotedInTree = inTreeValue?.trim('\'', '"')
                if (inTree != null && (vocabName == inTreeValue || vocabName == unquotedInTree)) {
                    g.errorManager?.grammarError(
                        ErrorType.CANNOT_FIND_TOKENS_FILE_REFD_IN_GRAMMAR,
                        g.fileName,
                        inTree.token,
                        fullPath.toString(),
                    )
                } else {
                    g.errorManager?.toolError(
                        ErrorType.CANNOT_FIND_TOKENS_FILE_GIVEN_ON_CMDLINE,
                        fullPath.toString(),
                        g.name,
                    )
                }
                return@withHostFileSystemOrNull
            }

            val content =
                SystemFileSystem.source(fullPath).buffered().use { source ->
                    source.readString()
                }
            parseTokensContent(vocabName, content, tokens)
        }
        return tokens
    }

    private fun parseTokensContent(
        vocabName: String,
        content: String,
        tokens: MutableMap<String, Int>,
    ) {
        var maxTokenType = -1
        var lineNum = 1
        for (rawLine in content.lines()) {
            val tokenDef = rawLine.trimEnd('\r')
            if (tokenDef.isEmpty()) {
                continue
            }
            val match = TOKEN_DEF_PATTERN.matchEntire(tokenDef)
            if (match != null) {
                val tokenId = match.groupValues[1]
                val tokenTypeStr = match.groupValues[2]
                val parsedType = tokenTypeStr.toIntOrNull()
                val tokenType =
                    if (parsedType == null) {
                        g.errorManager?.toolError(
                            ErrorType.TOKENS_FILE_SYNTAX_ERROR,
                            "$vocabName$VOCAB_FILE_EXTENSION",
                            " bad token type: $tokenTypeStr",
                            lineNum,
                        )
                        -1
                    } else {
                        parsedType
                    }
                g.log("grammar", "import $tokenId=$tokenType")
                tokens[tokenId] = tokenType
                if (tokenType > maxTokenType) {
                    maxTokenType = tokenType
                }
                lineNum++
            } else {
                g.errorManager?.toolError(
                    ErrorType.TOKENS_FILE_SYNTAX_ERROR,
                    "$vocabName$VOCAB_FILE_EXTENSION",
                    " bad token def: $tokenDef",
                    lineNum,
                )
            }
        }
    }

    fun getImportedVocabFileName(): String? {
        val vocabName = g.getOptionString("tokenVocab") ?: return null
        return withHostFileSystemOrNull {
            resolveImportedVocabPath(vocabName).toString()
        }
    }

    internal fun resolveImportedVocabPath(vocabName: String): Path {
        val libDir = g.libDirectory
        if (libDir != null) {
            val candidate = Path(libDir, "$vocabName$VOCAB_FILE_EXTENSION")
            if (SystemFileSystem.exists(candidate)) {
                return candidate
            }
        }

        val outDir = g.outputDirectory
        if (outDir != null) {
            val candidate = Path(outDir, "$vocabName$VOCAB_FILE_EXTENSION")
            if (SystemFileSystem.exists(candidate)) {
                return candidate
            }
        }

        val grammarFile = g.fileName.orEmpty()
        val lastSep = maxOf(grammarFile.lastIndexOf('/'), grammarFile.lastIndexOf('\\'))
        val fileDirectory = if (lastSep == -1) "." else grammarFile.substring(0, lastSep)
        return Path(fileDirectory, "$vocabName$VOCAB_FILE_EXTENSION")
    }

    companion object {
        const val VOCAB_FILE_EXTENSION: String = ".tokens"
        private val TOKEN_DEF_PATTERN: Regex = Regex("^(.+?)[ \\t]*=[ \\t]*([0-9]+)$")
    }
}
