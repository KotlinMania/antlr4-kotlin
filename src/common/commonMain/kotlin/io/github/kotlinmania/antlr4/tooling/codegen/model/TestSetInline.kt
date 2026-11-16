/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Inline bitset membership test expression over lookahead token type. */
class TestSetInline(
    factory: OutputModelFactory,
    ast: GrammarAST?,
    set: IntervalSet,
    val bitsetWordSize: Int,
) : SrcOp(factory, ast) {
    class Bitset(
        val shift: Int,
    ) {
        private val mutableTokens: MutableList<TokenInfo> = ArrayList()
        val tokens: List<TokenInfo>
            get() = mutableTokens

        var calculated: Long = 0L
            private set

        fun addToken(
            type: Int,
            name: String,
        ) {
            mutableTokens.add(TokenInfo(type, name))
            calculated = calculated or (1L shl (type - shift))
        }
    }

    val varName: String = factory.generator.target.lookaheadTempVarName
    val bitsets: List<Bitset> = createBitsets(factory, set, bitsetWordSize, useZeroOffset = true)

    companion object {
        private fun createBitsets(
            factory: OutputModelFactory,
            set: IntervalSet,
            wordSize: Int,
            useZeroOffset: Boolean,
        ): List<Bitset> {
            val bitsetList = ArrayList<Bitset>()
            var current: Bitset? = null
            for (ttype in set.toList()) {
                if (current == null || ttype > current.shift + wordSize - 1) {
                    val shift =
                        if (useZeroOffset && ttype in 0 until wordSize - 1) {
                            0
                        } else {
                            ttype
                        }
                    current = Bitset(shift)
                    bitsetList.add(current)
                }
                val label = factory.generator.target.getTokenTypeAsTargetLabel(factory.grammar, ttype)
                current.addToken(ttype, label)
            }
            return bitsetList
        }
    }
}
