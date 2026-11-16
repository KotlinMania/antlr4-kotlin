/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNDeserializer
import io.github.kotlinmania.antlr4.atn.ATNSerializer
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.atn.ATNType
import io.github.kotlinmania.antlr4.atn.LexerActionType
import io.github.kotlinmania.antlr4.atn.Transition
import io.github.kotlinmania.antlr4.tooling.automata.LexerATNFactory
import io.github.kotlinmania.antlr4.tooling.automata.ParserATNFactory
import io.github.kotlinmania.antlr4.tooling.semantics.SemanticPipeline
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Pure-Kotlin human-readable decoder of serialized ATN integer streams, ported from
 * upstream `org.antlr.v4.test.tool.ATNDescriber`.
 */
class ATNDescriber(
    val atn: ATN,
    private val tokenNames: List<String?>?,
) {
    fun decode(data: IntArray): String {
        val buf = StringBuilder()
        var p = 0
        val version = data[p++]
        if (version != ATNDeserializer.SERIALIZED_VERSION) {
            throw UnsupportedOperationException(
                "Could not deserialize ATN with version $version (expected ${ATNDeserializer.SERIALIZED_VERSION}).",
            )
        }

        p++ // skip grammarType
        val maxType = data[p++]
        buf.append("max type ").append(maxType).append('\n')
        val nstates = data[p++]
        for (i in 0 until nstates) {
            val stype = data[p++]
            if (stype == ATNState.INVALID_TYPE) continue

            var ruleIndex = data[p++]
            if (ruleIndex == Char.MAX_VALUE.code) {
                ruleIndex = -1
            }

            var arg = ""
            if (stype == ATNState.LOOP_END) {
                val loopBackStateNumber = data[p++]
                arg = " $loopBackStateNumber"
            } else if (
                stype == ATNState.PLUS_BLOCK_START ||
                stype == ATNState.STAR_BLOCK_START ||
                stype == ATNState.BLOCK_START
            ) {
                val endStateNumber = data[p++]
                arg = " $endStateNumber"
            }
            buf
                .append(i)
                .append(':')
                .append(ATNState.serializationNames[stype])
                .append(' ')
                .append(ruleIndex)
                .append(arg)
                .append('\n')
        }

        val numNonGreedyStates = data[p++]
        p += numNonGreedyStates
        val numPrecedenceStates = data[p++]
        p += numPrecedenceStates

        val nrules = data[p++]
        for (i in 0 until nrules) {
            val s = data[p++]
            if (atn.grammarType == ATNType.LEXER) {
                val arg1 = data[p++]
                buf
                    .append("rule ")
                    .append(i)
                    .append(':')
                    .append(s)
                    .append(' ')
                    .append(arg1)
                    .append('\n')
            } else {
                buf
                    .append("rule ")
                    .append(i)
                    .append(':')
                    .append(s)
                    .append('\n')
            }
        }

        val nmodes = data[p++]
        for (i in 0 until nmodes) {
            val s = data[p++]
            buf
                .append("mode ")
                .append(i)
                .append(':')
                .append(s)
                .append('\n')
        }

        val numSets = data[p++]
        p = appendSets(buf, data, p, numSets)

        val nedges = data[p++]
        repeat(nedges) {
            val src = data[p]
            val trg = data[p + 1]
            val ttype = data[p + 2]
            val arg1 = data[p + 3]
            val arg2 = data[p + 4]
            val arg3 = data[p + 5]
            buf
                .append(src)
                .append("->")
                .append(trg)
                .append(' ')
                .append(Transition.serializationNames[ttype])
                .append(' ')
                .append(arg1)
                .append(',')
                .append(arg2)
                .append(',')
                .append(arg3)
                .append('\n')
            p += 6
        }

        val ndecisions = data[p++]
        for (i in 0 until ndecisions) {
            val s = data[p++]
            buf
                .append(i)
                .append(':')
                .append(s)
                .append('\n')
        }

        if (atn.grammarType == ATNType.LEXER) {
            val lexerActionCount = data[p++]
            repeat(lexerActionCount) {
                LexerActionType.entries[data[p++]]
                p += 2
            }
        }
        return buf.toString()
    }

    private fun appendSets(
        buf: StringBuilder,
        data: IntArray,
        startPos: Int,
        nsets: Int,
    ): Int {
        var p = startPos
        for (i in 0 until nsets) {
            val nintervals = data[p++]
            buf.append(i).append(':')
            val containsEof = data[p++] != 0
            if (containsEof) {
                buf.append(getTokenName(Token.EOF))
            }
            for (j in 0 until nintervals) {
                if (containsEof || j > 0) {
                    buf.append(", ")
                }
                val a = data[p++]
                val b = data[p++]
                buf.append(getTokenName(a)).append("..").append(getTokenName(b))
            }
            buf.append('\n')
        }
        return p
    }

    fun getTokenName(t: Int): String {
        if (t == -1) return "EOF"
        if (atn.grammarType == ATNType.LEXER && t in Char.MIN_VALUE.code..Char.MAX_VALUE.code) {
            return when (t.toChar()) {
                '\n' -> "'\\n'"
                '\r' -> "'\\r'"
                '\t' -> "'\\t'"
                '\b' -> "'\\b'"
                '\u000C' -> "'\\f'"
                '\\' -> "'\\\\'"
                '\'' -> "'\\''"
                else -> {
                    if (t in 0x0020..0x007E) {
                        "'${t.toChar()}'"
                    } else {
                        val hex = (t or 0x10000).toString(16).uppercase().substring(1, 5)
                        "'\\u$hex'"
                    }
                }
            }
        }
        val names = tokenNames
        if (names != null && t >= 0 && t < names.size) {
            val found = names[t]
            if (found != null) return found
        }
        return t.toString()
    }
}

/**
 * Pure-Kotlin Multiplatform port of `TestATNSerialization` and `TestATNDeserialization`.
 */
class ATNSerializationAndDeserializationTest {
    private fun buildATN(
        g: Grammar,
        useSerializer: Boolean,
    ): ATN {
        if (g.atn == null) {
            SemanticPipeline(g).process()
            g.atn =
                if (g.isLexer) {
                    LexerATNFactory(g as LexerGrammar).createATN()
                } else {
                    ParserATNFactory(g).createATN()
                }
        }
        val raw = checkNotNull(g.atn)
        if (!useSerializer) {
            return raw
        }
        val serialized = ATNSerializer.getSerialized(raw)
        return ATNDeserializer().deserialize(serialized.toArray())
    }

    private fun checkSerializationResults(
        g: Grammar,
        expecting: String,
    ) {
        val atn = buildATN(g, useSerializer = true)
        val serialized = ATNSerializer.getSerialized(atn)
        val result = ATNDescriber(atn, g.tokenNames.toList()).decode(serialized.toArray())
        assertEquals(expecting, result)

        val serialized16 = ATNDeserializer.encodeIntsWith16BitWords(serialized)
        val ints16 = serialized16.toArray()
        val chars = CharArray(ints16.size) { i -> ints16[i].toChar() }
        val serialized32 = ATNDeserializer.decodeIntsEncodedAs16BitWords(chars, true)
        assertContentEquals(serialized.toArray(), serialized32)
    }

    private fun checkDeserializationIsStable(g: Grammar) {
        val atn = buildATN(g, useSerializer = false)
        val serialized = ATNSerializer.getSerialized(atn)
        val atnData = ATNDescriber(atn, g.tokenNames.toList()).decode(serialized.toArray())

        val serialized16 = ATNDeserializer.encodeIntsWith16BitWords(serialized)
        val ints16 = serialized16.toArray()
        val chars = CharArray(ints16.size) { i -> ints16[i].toChar() }
        val serialized32 = ATNDeserializer.decodeIntsEncodedAs16BitWords(chars, true)
        assertContentEquals(serialized.toArray(), serialized32)

        val atn2 = ATNDeserializer().deserialize(serialized.toArray())
        val serialized2 = ATNSerializer.getSerialized(atn2)
        val atn2Data = ATNDescriber(atn2, g.tokenNames.toList()).decode(serialized2.toArray())
        assertEquals(atnData, atn2Data)
    }

    @Test
    fun testSimpleNoBlock() {
        val g = Grammar("parser grammar T;\na : A B ;")
        val expecting =
            "max type 2\n" +
                "0:RULE_START 0\n" +
                "1:RULE_STOP 0\n" +
                "2:BASIC 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "5:BASIC 0\n" +
                "rule 0:0\n" +
                "0->2 EPSILON 0,0,0\n" +
                "2->3 ATOM 1,0,0\n" +
                "3->4 ATOM 2,0,0\n" +
                "4->1 EPSILON 0,0,0\n"
        checkSerializationResults(g, expecting)
    }

    @Test
    fun testEOF() {
        val g = Grammar("parser grammar T;\na : A EOF ;")
        val expecting =
            "max type 1\n" +
                "0:RULE_START 0\n" +
                "1:RULE_STOP 0\n" +
                "2:BASIC 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "5:BASIC 0\n" +
                "rule 0:0\n" +
                "0->2 EPSILON 0,0,0\n" +
                "2->3 ATOM 1,0,0\n" +
                "3->4 ATOM 0,0,1\n" +
                "4->1 EPSILON 0,0,0\n"
        checkSerializationResults(g, expecting)
    }

    @Test
    fun testEOFInSet() {
        val g = Grammar("parser grammar T;\na : (A|EOF) ;")
        val expecting =
            "max type 1\n" +
                "0:RULE_START 0\n" +
                "1:RULE_STOP 0\n" +
                "2:BASIC 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:0\n" +
                "0:EOF, A..A\n" +
                "0->2 EPSILON 0,0,0\n" +
                "2->3 SET 0,0,0\n" +
                "3->1 EPSILON 0,0,0\n"
        checkSerializationResults(g, expecting)
    }

    @Test
    fun testNot() {
        val g = Grammar("parser grammar T;\ntokens {A, B, C}\na : ~A ;")
        val expecting =
            "max type 3\n" +
                "0:RULE_START 0\n" +
                "1:RULE_STOP 0\n" +
                "2:BASIC 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:0\n" +
                "0:A..A\n" +
                "0->2 EPSILON 0,0,0\n" +
                "2->3 NOT_SET 0,0,0\n" +
                "3->1 EPSILON 0,0,0\n"
        checkSerializationResults(g, expecting)
    }

    @Test
    fun testWildcard() {
        val g = Grammar("parser grammar T;\ntokens {A, B, C}\na : . ;")
        val expecting =
            "max type 3\n" +
                "0:RULE_START 0\n" +
                "1:RULE_STOP 0\n" +
                "2:BASIC 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:0\n" +
                "0->2 EPSILON 0,0,0\n" +
                "2->3 WILDCARD 0,0,0\n" +
                "3->1 EPSILON 0,0,0\n"
        checkSerializationResults(g, expecting)
    }

    @Test
    fun testPEGAchillesHeel() {
        val g = Grammar("parser grammar T;\na : A | A B ;")
        val expecting =
            "max type 2\n" +
                "0:RULE_START 0\n" +
                "1:RULE_STOP 0\n" +
                "2:BASIC 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "5:BLOCK_START 0 6\n" +
                "6:BLOCK_END 0\n" +
                "7:BASIC 0\n" +
                "rule 0:0\n" +
                "0->5 EPSILON 0,0,0\n" +
                "2->6 ATOM 1,0,0\n" +
                "3->4 ATOM 1,0,0\n" +
                "4->6 ATOM 2,0,0\n" +
                "5->2 EPSILON 0,0,0\n" +
                "5->3 EPSILON 0,0,0\n" +
                "6->1 EPSILON 0,0,0\n" +
                "0:5\n"
        checkSerializationResults(g, expecting)
    }

    @Test
    fun test3Alts() {
        val g = Grammar("parser grammar T;\na : A | A B | A B C ;")
        val expecting =
            "max type 3\n" +
                "0:RULE_START 0\n" +
                "1:RULE_STOP 0\n" +
                "2:BASIC 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "5:BASIC 0\n" +
                "6:BASIC 0\n" +
                "7:BASIC 0\n" +
                "8:BLOCK_START 0 9\n" +
                "9:BLOCK_END 0\n" +
                "10:BASIC 0\n" +
                "rule 0:0\n" +
                "0->8 EPSILON 0,0,0\n" +
                "2->9 ATOM 1,0,0\n" +
                "3->4 ATOM 1,0,0\n" +
                "4->9 ATOM 2,0,0\n" +
                "5->6 ATOM 1,0,0\n" +
                "6->7 ATOM 2,0,0\n" +
                "7->9 ATOM 3,0,0\n" +
                "8->2 EPSILON 0,0,0\n" +
                "8->3 EPSILON 0,0,0\n" +
                "8->5 EPSILON 0,0,0\n" +
                "9->1 EPSILON 0,0,0\n" +
                "0:8\n"
        checkSerializationResults(g, expecting)
    }

    @Test
    fun testSimpleLoop() {
        val g = Grammar("parser grammar T;\na : A+ B ;")
        val expecting =
            "max type 2\n" +
                "0:RULE_START 0\n" +
                "1:RULE_STOP 0\n" +
                "2:BASIC 0\n" +
                "3:PLUS_BLOCK_START 0 4\n" +
                "4:BLOCK_END 0\n" +
                "5:PLUS_LOOP_BACK 0\n" +
                "6:LOOP_END 0 5\n" +
                "7:BASIC 0\n" +
                "8:BASIC 0\n" +
                "9:BASIC 0\n" +
                "rule 0:0\n" +
                "0->3 EPSILON 0,0,0\n" +
                "2->4 ATOM 1,0,0\n" +
                "3->2 EPSILON 0,0,0\n" +
                "4->5 EPSILON 0,0,0\n" +
                "5->3 EPSILON 0,0,0\n" +
                "5->6 EPSILON 0,0,0\n" +
                "6->7 EPSILON 0,0,0\n" +
                "7->8 ATOM 2,0,0\n" +
                "8->1 EPSILON 0,0,0\n" +
                "0:5\n"
        checkSerializationResults(g, expecting)
    }

    @Test
    fun testRuleRef() {
        val g = Grammar("parser grammar T;\na : e ;\ne : E ;\n")
        val expecting =
            "max type 1\n" +
                "0:RULE_START 0\n" +
                "1:RULE_STOP 0\n" +
                "2:RULE_START 1\n" +
                "3:RULE_STOP 1\n" +
                "4:BASIC 0\n" +
                "5:BASIC 0\n" +
                "6:BASIC 1\n" +
                "7:BASIC 1\n" +
                "8:BASIC 1\n" +
                "rule 0:0\n" +
                "rule 1:2\n" +
                "0->4 EPSILON 0,0,0\n" +
                "2->6 EPSILON 0,0,0\n" +
                "4->5 RULE 2,1,0\n" +
                "5->1 EPSILON 0,0,0\n" +
                "6->7 ATOM 1,0,0\n" +
                "7->3 EPSILON 0,0,0\n"
        checkSerializationResults(g, expecting)
    }

    @Test
    fun testLexerTwoRules() {
        val lg = LexerGrammar("lexer grammar L;\nA : 'a' ;\nB : 'b' ;\n")
        val expecting =
            "max type 2\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:RULE_START 1\n" +
                "4:RULE_STOP 1\n" +
                "5:BASIC 0\n" +
                "6:BASIC 0\n" +
                "7:BASIC 1\n" +
                "8:BASIC 1\n" +
                "rule 0:1 1\n" +
                "rule 1:3 2\n" +
                "mode 0:0\n" +
                "0->1 EPSILON 0,0,0\n" +
                "0->3 EPSILON 0,0,0\n" +
                "1->5 EPSILON 0,0,0\n" +
                "3->7 EPSILON 0,0,0\n" +
                "5->6 ATOM 97,0,0\n" +
                "6->2 EPSILON 0,0,0\n" +
                "7->8 ATOM 98,0,0\n" +
                "8->4 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeSMPLiteralSerializedToSet() {
        val lg = LexerGrammar("lexer grammar L;\nINT : '\\u{1F4A9}' ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 ATOM 128169,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeSMPRangeSerializedToSet() {
        val lg = LexerGrammar("lexer grammar L;\nINT : ('a'..'\\u{1F4A9}') ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 RANGE 97,128169,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeSMPAndBMPSetSerialized() {
        val lg = LexerGrammar("lexer grammar L;\nSMP : ('\\u{1F4A9}' | '\\u{1F4AF}') ;\nBMP : ('a' | 'x') ;")
        val expecting =
            "max type 2\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:RULE_START 1\n" +
                "4:RULE_STOP 1\n" +
                "5:BASIC 0\n" +
                "6:BASIC 0\n" +
                "7:BASIC 1\n" +
                "8:BASIC 1\n" +
                "rule 0:1 1\n" +
                "rule 1:3 2\n" +
                "mode 0:0\n" +
                "0:128169..128169, 128175..128175\n" +
                "1:'a'..'a', 'x'..'x'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "0->3 EPSILON 0,0,0\n" +
                "1->5 EPSILON 0,0,0\n" +
                "3->7 EPSILON 0,0,0\n" +
                "5->6 SET 0,0,0\n" +
                "6->2 EPSILON 0,0,0\n" +
                "7->8 SET 1,0,0\n" +
                "8->4 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerWith0xFFFCInSet() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ([A-Z_]|'Ā'..'\\uFFFC') ([A-Z_0-9]|'Ā'..'\\uFFFC')*;\n",
            )
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BLOCK_START 0 5\n" +
                "5:BLOCK_END 0\n" +
                "6:BASIC 0\n" +
                "7:STAR_BLOCK_START 0 8\n" +
                "8:BLOCK_END 0\n" +
                "9:STAR_LOOP_ENTRY 0\n" +
                "10:LOOP_END 0 11\n" +
                "11:STAR_LOOP_BACK 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'A'..'Z', '_'..'_', '\\u0100'..'\\uFFFC'\n" +
                "1:'0'..'9', 'A'..'Z', '_'..'_', '\\u0100'..'\\uFFFC'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->4 EPSILON 0,0,0\n" +
                "3->5 SET 0,0,0\n" +
                "4->3 EPSILON 0,0,0\n" +
                "5->9 EPSILON 0,0,0\n" +
                "6->8 SET 1,0,0\n" +
                "7->6 EPSILON 0,0,0\n" +
                "8->11 EPSILON 0,0,0\n" +
                "9->7 EPSILON 0,0,0\n" +
                "9->10 EPSILON 0,0,0\n" +
                "10->2 EPSILON 0,0,0\n" +
                "11->9 EPSILON 0,0,0\n" +
                "0:0\n" +
                "1:4\n" +
                "2:7\n" +
                "3:9\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerNotLiteral() {
        val lg = LexerGrammar("lexer grammar L;\nINT : ~'a' ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'a'..'a'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 NOT_SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerRange() {
        val lg = LexerGrammar("lexer grammar L;\nINT : '0'..'9' ;\n")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 RANGE 48,57,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerEOF() {
        val lg = LexerGrammar("lexer grammar L;\nINT : 'a' EOF ;\n")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "5:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 ATOM 97,0,0\n" +
                "4->5 ATOM 0,0,1\n" +
                "5->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerEOFInSet() {
        val lg = LexerGrammar("lexer grammar L;\nINT : 'a' (EOF|'\\n') ;\n")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "5:BLOCK_START 0 6\n" +
                "6:BLOCK_END 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:EOF, '\\n'..'\\n'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->5 ATOM 97,0,0\n" +
                "4->6 SET 0,0,0\n" +
                "5->4 EPSILON 0,0,0\n" +
                "6->2 EPSILON 0,0,0\n" +
                "0:0\n" +
                "1:5\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerLoops() {
        val lg = LexerGrammar("lexer grammar L;\nINT : '0'..'9'+ ;\n")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:PLUS_BLOCK_START 0 5\n" +
                "5:BLOCK_END 0\n" +
                "6:PLUS_LOOP_BACK 0\n" +
                "7:LOOP_END 0 6\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->4 EPSILON 0,0,0\n" +
                "3->5 RANGE 48,57,0\n" +
                "4->3 EPSILON 0,0,0\n" +
                "5->6 EPSILON 0,0,0\n" +
                "6->4 EPSILON 0,0,0\n" +
                "6->7 EPSILON 0,0,0\n" +
                "7->2 EPSILON 0,0,0\n" +
                "0:0\n" +
                "1:6\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerAction() {
        val lg = LexerGrammar("lexer grammar L;\nA : 'a' {a} ;\nB : 'b' ;\nC : 'c' {c} ;\n")
        val expecting =
            "max type 3\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:RULE_START 1\n" +
                "4:RULE_STOP 1\n" +
                "5:RULE_START 2\n" +
                "6:RULE_STOP 2\n" +
                "7:BASIC 0\n" +
                "8:BASIC 0\n" +
                "9:BASIC 0\n" +
                "10:BASIC 1\n" +
                "11:BASIC 1\n" +
                "12:BASIC 2\n" +
                "13:BASIC 2\n" +
                "14:BASIC 2\n" +
                "rule 0:1 1\n" +
                "rule 1:3 2\n" +
                "rule 2:5 3\n" +
                "mode 0:0\n" +
                "0->1 EPSILON 0,0,0\n" +
                "0->3 EPSILON 0,0,0\n" +
                "0->5 EPSILON 0,0,0\n" +
                "1->7 EPSILON 0,0,0\n" +
                "3->10 EPSILON 0,0,0\n" +
                "5->12 EPSILON 0,0,0\n" +
                "7->8 ATOM 97,0,0\n" +
                "8->9 ACTION 0,0,0\n" +
                "9->2 EPSILON 0,0,0\n" +
                "10->11 ATOM 98,0,0\n" +
                "11->4 EPSILON 0,0,0\n" +
                "12->13 ATOM 99,0,0\n" +
                "13->14 ACTION 2,1,0\n" +
                "14->6 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerNotSet() {
        val lg = LexerGrammar("lexer grammar L;\nID : ~('a'|'b')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'a'..'b'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 NOT_SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerSetWithRange() {
        val lg = LexerGrammar("lexer grammar L;\nID : ('a'|'b'|'e'|'p'..'t')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'a'..'b', 'e'..'e', 'p'..'t'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerNotSetWithRange() {
        val lg = LexerGrammar("lexer grammar L;\nID : ~('a'|'b'|'e'|'p'..'t')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'a'..'b', 'e'..'e', 'p'..'t'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 NOT_SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeUnescapedBMPNotSet() {
        val lg = LexerGrammar("lexer grammar L;\nID : ~('\u4E9C'|'\u4E9D')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'\\u4E9C'..'\\u4E9D'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 NOT_SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeUnescapedBMPSetWithRange() {
        val lg = LexerGrammar("lexer grammar L;\nID : ('\u4E9C'|'\u4E9D'|'\u6C5F'|'\u305F'..'\u307B')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'\\u305F'..'\\u307B', '\\u4E9C'..'\\u4E9D', '\\u6C5F'..'\\u6C5F'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeUnescapedBMPNotSetWithRange() {
        val lg = LexerGrammar("lexer grammar L;\nID : ~('\u4E9C'|'\u4E9D'|'\u6C5F'|'\u305F'..'\u307B')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'\\u305F'..'\\u307B', '\\u4E9C'..'\\u4E9D', '\\u6C5F'..'\\u6C5F'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 NOT_SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeEscapedBMPNotSet() {
        val lg = LexerGrammar("lexer grammar L;\nID : ~('\\u4E9C'|'\\u4E9D')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'\\u4E9C'..'\\u4E9D'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 NOT_SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeEscapedBMPSetWithRange() {
        val lg = LexerGrammar("lexer grammar L;\nID : ('\\u4E9C'|'\\u4E9D'|'\\u6C5F'|'\\u305F'..'\\u307B')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'\\u305F'..'\\u307B', '\\u4E9C'..'\\u4E9D', '\\u6C5F'..'\\u6C5F'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeEscapedBMPNotSetWithRange() {
        val lg = LexerGrammar("lexer grammar L;\nID : ~('\\u4E9C'|'\\u4E9D'|'\\u6C5F'|'\\u305F'..'\\u307B')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'\\u305F'..'\\u307B', '\\u4E9C'..'\\u4E9D', '\\u6C5F'..'\\u6C5F'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 NOT_SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeEscapedSMPNotSet() {
        val lg = LexerGrammar("lexer grammar L;\nID : ~('\\u{1F4A9}'|'\\u{1F4AA}')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:128169..128170\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 NOT_SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeEscapedSMPSetWithRange() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ('\\u{1F4A9}'|'\\u{1F4AA}'|'\\u{1F441}'|'\\u{1D40F}'..'\\u{1D413}')\n ;",
            )
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:119823..119827, 128065..128065, 128169..128170\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerUnicodeEscapedSMPNotSetWithRange() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : ~('\\u{1F4A9}'|'\\u{1F4AA}'|'\\u{1F441}'|'\\u{1D40F}'..'\\u{1D413}')\n ;",
            )
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:119823..119827, 128065..128065, 128169..128170\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 NOT_SET 0,0,0\n" +
                "4->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerWildcardWithMode() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "ID : 'a'..'z'+ ;\n" +
                    "mode CMT;" +
                    "COMMENT : '*/' {skip(); popMode();} ;\n" +
                    "JUNK : . {more();} ;\n",
            )
        val expecting =
            "max type 3\n" +
                "0:TOKEN_START -1\n" +
                "1:TOKEN_START -1\n" +
                "2:RULE_START 0\n" +
                "3:RULE_STOP 0\n" +
                "4:RULE_START 1\n" +
                "5:RULE_STOP 1\n" +
                "6:RULE_START 2\n" +
                "7:RULE_STOP 2\n" +
                "8:BASIC 0\n" +
                "9:PLUS_BLOCK_START 0 10\n" +
                "10:BLOCK_END 0\n" +
                "11:PLUS_LOOP_BACK 0\n" +
                "12:LOOP_END 0 11\n" +
                "13:BASIC 1\n" +
                "14:BASIC 1\n" +
                "15:BASIC 1\n" +
                "16:BASIC 1\n" +
                "17:BASIC 1\n" +
                "18:BASIC 2\n" +
                "19:BASIC 2\n" +
                "20:BASIC 2\n" +
                "rule 0:2 1\n" +
                "rule 1:4 2\n" +
                "rule 2:6 3\n" +
                "mode 0:0\n" +
                "mode 1:1\n" +
                "0->2 EPSILON 0,0,0\n" +
                "1->4 EPSILON 0,0,0\n" +
                "1->6 EPSILON 0,0,0\n" +
                "2->9 EPSILON 0,0,0\n" +
                "4->13 EPSILON 0,0,0\n" +
                "6->18 EPSILON 0,0,0\n" +
                "8->10 RANGE 97,122,0\n" +
                "9->8 EPSILON 0,0,0\n" +
                "10->11 EPSILON 0,0,0\n" +
                "11->9 EPSILON 0,0,0\n" +
                "11->12 EPSILON 0,0,0\n" +
                "12->3 EPSILON 0,0,0\n" +
                "13->14 ATOM 42,0,0\n" +
                "14->15 ATOM 47,0,0\n" +
                "15->16 EPSILON 0,0,0\n" +
                "16->17 ACTION 1,0,0\n" +
                "17->5 EPSILON 0,0,0\n" +
                "18->19 WILDCARD 0,0,0\n" +
                "19->20 ACTION 2,1,0\n" +
                "20->7 EPSILON 0,0,0\n" +
                "0:0\n" +
                "1:1\n" +
                "2:11\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testLexerNotSetWithRange2() {
        val lg = LexerGrammar("lexer grammar L;\nID : ~('a'|'b') ~('e'|'p'..'t')\n ;")
        val expecting =
            "max type 1\n" +
                "0:TOKEN_START -1\n" +
                "1:RULE_START 0\n" +
                "2:RULE_STOP 0\n" +
                "3:BASIC 0\n" +
                "4:BASIC 0\n" +
                "5:BASIC 0\n" +
                "rule 0:1 1\n" +
                "mode 0:0\n" +
                "0:'a'..'b'\n" +
                "1:'e'..'e', 'p'..'t'\n" +
                "0->1 EPSILON 0,0,0\n" +
                "1->3 EPSILON 0,0,0\n" +
                "3->4 NOT_SET 0,0,0\n" +
                "4->5 NOT_SET 1,0,0\n" +
                "5->2 EPSILON 0,0,0\n" +
                "0:0\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun testModeInLexer() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a'\n ;\n" +
                    "B : 'b';\n" +
                    "mode M;\n" +
                    "C : 'c';\n" +
                    "D : 'd';\n",
            )
        val expecting =
            "max type 4\n" +
                "0:TOKEN_START -1\n" +
                "1:TOKEN_START -1\n" +
                "2:RULE_START 0\n" +
                "3:RULE_STOP 0\n" +
                "4:RULE_START 1\n" +
                "5:RULE_STOP 1\n" +
                "6:RULE_START 2\n" +
                "7:RULE_STOP 2\n" +
                "8:RULE_START 3\n" +
                "9:RULE_STOP 3\n" +
                "10:BASIC 0\n" +
                "11:BASIC 0\n" +
                "12:BASIC 1\n" +
                "13:BASIC 1\n" +
                "14:BASIC 2\n" +
                "15:BASIC 2\n" +
                "16:BASIC 3\n" +
                "17:BASIC 3\n" +
                "rule 0:2 1\n" +
                "rule 1:4 2\n" +
                "rule 2:6 3\n" +
                "rule 3:8 4\n" +
                "mode 0:0\n" +
                "mode 1:1\n" +
                "0->2 EPSILON 0,0,0\n" +
                "0->4 EPSILON 0,0,0\n" +
                "1->6 EPSILON 0,0,0\n" +
                "1->8 EPSILON 0,0,0\n" +
                "2->10 EPSILON 0,0,0\n" +
                "4->12 EPSILON 0,0,0\n" +
                "6->14 EPSILON 0,0,0\n" +
                "8->16 EPSILON 0,0,0\n" +
                "10->11 ATOM 97,0,0\n" +
                "11->3 EPSILON 0,0,0\n" +
                "12->13 ATOM 98,0,0\n" +
                "13->5 EPSILON 0,0,0\n" +
                "14->15 ATOM 99,0,0\n" +
                "15->7 EPSILON 0,0,0\n" +
                "16->17 ATOM 100,0,0\n" +
                "17->9 EPSILON 0,0,0\n" +
                "0:0\n" +
                "1:1\n"
        checkSerializationResults(lg, expecting)
    }

    @Test
    fun test2ModesInLexer() {
        val lg =
            LexerGrammar(
                "lexer grammar L;\n" +
                    "A : 'a'\n ;\n" +
                    "mode M;\n" +
                    "B : 'b';\n" +
                    "mode M2;\n" +
                    "C : 'c';\n",
            )
        val expecting =
            "max type 3\n" +
                "0:TOKEN_START -1\n" +
                "1:TOKEN_START -1\n" +
                "2:TOKEN_START -1\n" +
                "3:RULE_START 0\n" +
                "4:RULE_STOP 0\n" +
                "5:RULE_START 1\n" +
                "6:RULE_STOP 1\n" +
                "7:RULE_START 2\n" +
                "8:RULE_STOP 2\n" +
                "9:BASIC 0\n" +
                "10:BASIC 0\n" +
                "11:BASIC 1\n" +
                "12:BASIC 1\n" +
                "13:BASIC 2\n" +
                "14:BASIC 2\n" +
                "rule 0:3 1\n" +
                "rule 1:5 2\n" +
                "rule 2:7 3\n" +
                "mode 0:0\n" +
                "mode 1:1\n" +
                "mode 2:2\n" +
                "0->3 EPSILON 0,0,0\n" +
                "1->5 EPSILON 0,0,0\n" +
                "2->7 EPSILON 0,0,0\n" +
                "3->9 EPSILON 0,0,0\n" +
                "5->11 EPSILON 0,0,0\n" +
                "7->13 EPSILON 0,0,0\n" +
                "9->10 ATOM 97,0,0\n" +
                "10->4 EPSILON 0,0,0\n" +
                "11->12 ATOM 98,0,0\n" +
                "12->6 EPSILON 0,0,0\n" +
                "13->14 ATOM 99,0,0\n" +
                "14->8 EPSILON 0,0,0\n" +
                "0:0\n" +
                "1:1\n" +
                "2:2\n"
        checkSerializationResults(lg, expecting)
    }

    // Deserialization stability suite (ported from TestATNDeserialization)

    @Test
    fun testDeserializationStabilitySuite() {
        checkDeserializationIsStable(Grammar("parser grammar T;\na : A B ;"))
        checkDeserializationIsStable(Grammar("parser grammar T;\na : EOF ;"))
        checkDeserializationIsStable(Grammar("parser grammar T;\na : (EOF|A) ;"))
        checkDeserializationIsStable(Grammar("parser grammar T;\ntokens {A, B, C}\na : ~A ;"))
        checkDeserializationIsStable(Grammar("parser grammar T;\ntokens {A, B, C}\na : . ;"))
        checkDeserializationIsStable(Grammar("parser grammar T;\na : A | A B ;"))
        checkDeserializationIsStable(Grammar("parser grammar T;\na : A | A B | A B C ;"))
        checkDeserializationIsStable(Grammar("parser grammar T;\na : A+ B ;"))
        checkDeserializationIsStable(Grammar("parser grammar T;\na : e ;\ne : E ;\n"))
        checkDeserializationIsStable(LexerGrammar("lexer grammar L;\nA : 'a' ;\nB : 'b' ;\n"))
        checkDeserializationIsStable(LexerGrammar("lexer grammar L;\nA : 'a' EOF ;\n"))
        checkDeserializationIsStable(LexerGrammar("lexer grammar L;\nA : 'a' (EOF|'\\n') ;\n"))
        checkDeserializationIsStable(LexerGrammar("lexer grammar L;\nINT : '0'..'9' ;\n"))
        checkDeserializationIsStable(LexerGrammar("lexer grammar L;\nINT : '0'..'9'+ ;\n"))
        checkDeserializationIsStable(LexerGrammar("lexer grammar L;\nID : ~('a'|'b')\n ;"))
        checkDeserializationIsStable(LexerGrammar("lexer grammar L;\nID : ~('a'|'b'|'e'|'p'..'t')\n ;"))
        checkDeserializationIsStable(LexerGrammar("lexer grammar L;\nID : ~('a'|'b') ~('e'|'p'..'t')\n ;"))
        checkDeserializationIsStable(
            LexerGrammar("lexer grammar L;\nA : 'a'\n ;\nmode M;\nB : 'b';\nmode M2;\nC : 'c';\n"),
        )
        checkDeserializationIsStable(
            LexerGrammar("lexer grammar L;\nID : 'Ā'..'\\uFFFC';\n"),
        )
    }
}
