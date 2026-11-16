/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

import io.github.kotlinmania.antlr4.Lexer
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.misc.EscapeSequenceParsing.EscapeParseResult
import io.github.kotlinmania.antlr4.tooling.misc.EscapeSequenceParsing.ResultKind
import kotlin.test.Test
import kotlin.test.assertEquals

class EscapeSequenceParsingTest {
    @Test
    fun testParseEmpty() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("", 0).type,
        )
    }

    @Test
    fun testParseJustBackslash() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\", 0).type,
        )
    }

    @Test
    fun testParseInvalidEscape() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\z", 0).type,
        )
    }

    @Test
    fun testParseNewline() {
        assertEquals(
            EscapeParseResult(ResultKind.CODE_POINT, '\n'.code, IntervalSet.EMPTY_SET, 0, 2),
            EscapeSequenceParsing.parseEscape("\\n", 0),
        )
    }

    @Test
    fun testParseTab() {
        assertEquals(
            EscapeParseResult(ResultKind.CODE_POINT, '\t'.code, IntervalSet.EMPTY_SET, 0, 2),
            EscapeSequenceParsing.parseEscape("\\t", 0),
        )
    }

    @Test
    fun testParseUnicodeTooShort() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\uABC", 0).type,
        )
    }

    @Test
    fun testParseUnicodeBMP() {
        assertEquals(
            EscapeParseResult(ResultKind.CODE_POINT, 0xABCD, IntervalSet.EMPTY_SET, 0, 6),
            EscapeSequenceParsing.parseEscape("\\uABCD", 0),
        )
    }

    @Test
    fun testParseUnicodeSMPTooShort() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\u{}", 0).type,
        )
    }

    @Test
    fun testParseUnicodeSMPMissingCloseBrace() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\u{12345", 0).type,
        )
    }

    @Test
    fun testParseUnicodeTooBig() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\u{110000}", 0).type,
        )
    }

    @Test
    fun testParseUnicodeSMP() {
        assertEquals(
            EscapeParseResult(ResultKind.CODE_POINT, 0x10ABCD, IntervalSet.EMPTY_SET, 0, 10),
            EscapeSequenceParsing.parseEscape("\\u{10ABCD}", 0),
        )
    }

    @Test
    fun testParseUnicodePropertyTooShort() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\p{}", 0).type,
        )
    }

    @Test
    fun testParseUnicodePropertyMissingCloseBrace() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\p{1234", 0).type,
        )
    }

    @Test
    fun testParseUnicodeProperty() {
        assertEquals(
            EscapeParseResult(ResultKind.PROPERTY, -1, IntervalSet.of(66560, 66639), 0, 11),
            EscapeSequenceParsing.parseEscape("\\p{Deseret}", 0),
        )
    }

    @Test
    fun testParseUnicodePropertyInvertedTooShort() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\P{}", 0).type,
        )
    }

    @Test
    fun testParseUnicodePropertyInvertedMissingCloseBrace() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\P{Deseret", 0).type,
        )
    }

    @Test
    fun testParseUnicodePropertyInverted() {
        val expected = IntervalSet.of(0, 66559)
        expected.add(66640, Lexer.MAX_CHAR_VALUE)
        assertEquals(
            EscapeParseResult(ResultKind.PROPERTY, -1, expected, 0, 11),
            EscapeSequenceParsing.parseEscape("\\P{Deseret}", 0),
        )
    }

    @Test
    fun testParseEmptyObsoleteUnicodePropertyRejected() {
        assertEquals(
            ResultKind.INVALID,
            EscapeSequenceParsing.parseEscape("\\p{Grapheme_Cluster_Break=E_Base}", 0).type,
        )
        assertEquals(
            EscapeParseResult(ResultKind.CODE_POINT, ']'.code, IntervalSet.EMPTY_SET, 0, 2),
            EscapeSequenceParsing.parseEscape("\\]", 0),
        )
        assertEquals(
            EscapeParseResult(ResultKind.CODE_POINT, '-'.code, IntervalSet.EMPTY_SET, 0, 2),
            EscapeSequenceParsing.parseEscape("\\-", 0),
        )
    }
}
