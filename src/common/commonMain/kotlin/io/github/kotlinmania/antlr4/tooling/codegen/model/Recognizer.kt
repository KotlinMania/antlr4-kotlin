/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.codegen.CodeGenerator
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionChunk
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionText
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Shared base output model for generated [Parser] and [Lexer] classes. */
abstract class Recognizer(
    factory: OutputModelFactory,
) : OutputModelObject(factory) {
    val name: String
    val grammarName: String
    val grammarFileName: String
    val accessLevel: String?

    val tokens: Map<String, Int>
    val channels: Map<String, Int>
    val ruleNames: List<String>
    val rules: List<Rule>

    @ModelElement
    var superClass: ActionChunk? = null

    internal val rawLiteralNames: List<String?>
    internal val rawSymbolicNames: List<String?>
    internal val rawTokenNames: List<String?>

    val literalNames: List<String>
    val symbolicNames: List<String>
    val tokenNames: List<String>

    @ModelElement
    var atn: SerializedATN? = null

    internal val mutableSempredFuncs: LinkedHashMap<Rule, RuleSempredFunction> = LinkedHashMap()

    @ModelElement
    val sempredFuncs: Map<Rule, RuleSempredFunction>
        get() = mutableSempredFuncs

    init {
        val g = factory.grammar
        val gen = factory.generator
        val rawFile = g.fileName.orEmpty()
        grammarFileName = rawFile.substringAfterLast('/').substringAfterLast('\\')
        grammarName = g.name.orEmpty()
        name = g.recognizerName.orEmpty()
        accessLevel = g.getOptionString("accessLevel")

        val tokenMap = LinkedHashMap<String, Int>()
        for ((tokName, tokType) in g.tokenNameToTypeMap) {
            if (tokType >= Token.MIN_USER_TOKEN_TYPE) {
                tokenMap[tokName] = tokType
            }
        }
        tokens = tokenMap
        channels = LinkedHashMap(g.channelNameToValueMap)
        rules = g.rules.values.toList()
        ruleNames = g.rules.keys.toList()

        val superOpt = g.getOptionString("superClass")
        if (superOpt != null) {
            superClass = ActionText(null, superOpt)
        }

        rawTokenNames = translateTokenStringsToTarget(g.tokenDisplayNames, gen)
        rawLiteralNames = translateTokenStringsToTarget(g.tokenLiteralNames, gen)
        rawSymbolicNames = translateTokenStringsToTarget(g.tokenSymbolicNames, gen)

        tokenNames = rawTokenNames.map { it ?: "\"\"" }
        literalNames = rawLiteralNames.map { it ?: "\"\"" }
        symbolicNames = rawSymbolicNames.map { it ?: "\"\"" }

        val builtAtn = g.atn
        if (builtAtn != null) {
            atn =
                if (gen.target.wantsJavaSerializedATN()) {
                    SerializedJavaATN(factory, builtAtn)
                } else {
                    SerializedATN(factory, builtAtn)
                }
        }
    }

    companion object {
        internal fun translateTokenStringsToTarget(
            tokenStrings: List<String?>,
            gen: CodeGenerator,
        ): List<String?> {
            val result = ArrayList<String?>(tokenStrings.size)
            for (s in tokenStrings) {
                result.add(translateTokenStringToTarget(s, gen))
            }
            while (result.isNotEmpty() && result[result.size - 1] == null) {
                result.removeAt(result.size - 1)
            }
            return result
        }

        internal fun translateTokenStringToTarget(
            s: String?,
            gen: CodeGenerator,
        ): String? {
            if (s == null) return null
            return if (s.isNotEmpty() && s[0] == '\'') {
                val targetString =
                    gen.target.getTargetStringLiteralFromANTLRStringLiteral(
                        gen,
                        s,
                        addQuotes = false,
                        escapeSpecial = true,
                    )
                "\"'$targetString'\""
            } else {
                gen.target.getTargetStringLiteralFromString(s, quoted = true)
            }
        }
    }
}
