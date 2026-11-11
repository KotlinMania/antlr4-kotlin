/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.unicode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnicodeDataTest {
    private fun requireSet(name: String) =
        assertNotNull(UnicodeData.getPropertyCodePoints(name), "Expected property '$name' to exist")

    @Test
    fun testUnicodeGeneralCategoriesLatin() {
        assertTrue(requireSet("Lu").contains('X'.code))
        assertFalse(requireSet("Lu").contains('x'.code))
        assertTrue(requireSet("Ll").contains('x'.code))
        assertFalse(requireSet("Ll").contains('X'.code))
        assertTrue(requireSet("L").contains('X'.code))
        assertTrue(requireSet("L").contains('x'.code))
        assertTrue(requireSet("N").contains('0'.code))
        assertTrue(requireSet("Z").contains(' '.code))
    }

    @Test
    fun testUnicodeGeneralCategoriesBMP() {
        assertTrue(requireSet("Lu").contains('\u1E3A'.code))
        assertFalse(requireSet("Lu").contains('\u1E3B'.code))
        assertTrue(requireSet("Ll").contains('\u1E3B'.code))
        assertFalse(requireSet("Ll").contains('\u1E3A'.code))
        assertTrue(requireSet("L").contains('\u1E3A'.code))
        assertTrue(requireSet("L").contains('\u1E3B'.code))
        assertTrue(requireSet("N").contains('\u1BB0'.code))
        assertFalse(requireSet("N").contains('\u1E3A'.code))
        assertTrue(requireSet("Z").contains('\u2028'.code))
        assertFalse(requireSet("Z").contains('\u1E3A'.code))
    }

    @Test
    fun testUnicodeGeneralCategoriesSMP() {
        assertTrue(requireSet("Lu").contains(0x1D5D4))
        assertFalse(requireSet("Lu").contains(0x1D770))
        assertTrue(requireSet("Ll").contains(0x1D770))
        assertFalse(requireSet("Ll").contains(0x1D5D4))
        assertTrue(requireSet("L").contains(0x1D5D4))
        assertTrue(requireSet("L").contains(0x1D770))
        assertTrue(requireSet("N").contains(0x11C50))
        assertFalse(requireSet("N").contains(0x1D5D4))
    }

    @Test
    fun testUnicodeCategoryAliases() {
        assertTrue(requireSet("Lowercase_Letter").contains('x'.code))
        assertFalse(requireSet("Lowercase_Letter").contains('X'.code))
        assertTrue(requireSet("Letter").contains('x'.code))
        assertFalse(requireSet("Letter").contains('0'.code))
        assertTrue(requireSet("Enclosing_Mark").contains(0x20E2))
        assertFalse(requireSet("Enclosing_Mark").contains('x'.code))
        assertTrue(requireSet("Cs").contains(0xD800))
        assertTrue(requireSet("C").contains(0xD800))
        assertTrue(requireSet("Control").contains(0xD800))
        assertTrue(requireSet("Space").contains(' '.code))
    }

    @Test
    fun testUnicodeBinaryProperties() {
        assertTrue(requireSet("Emoji").contains(0x1F4A9))
        assertFalse(requireSet("Emoji").contains('X'.code))
        assertTrue(requireSet("alnum").contains('9'.code))
        assertFalse(requireSet("alnum").contains(0x1F4A9))
        assertTrue(requireSet("Dash").contains('-'.code))
        assertTrue(requireSet("Hex").contains('D'.code))
        assertFalse(requireSet("Hex").contains('Q'.code))
    }

    @Test
    fun testUnicodeBinaryPropertyAliases() {
        assertTrue(requireSet("Ideo").contains('\u611B'.code))
        assertFalse(requireSet("Ideo").contains('X'.code))
        assertTrue(requireSet("Soft_Dotted").contains('\u0456'.code))
        assertFalse(requireSet("Soft_Dotted").contains('X'.code))
        assertTrue(requireSet("Noncharacter_Code_Point").contains('\uFFFF'.code))
        assertFalse(requireSet("Noncharacter_Code_Point").contains('X'.code))
    }

    @Test
    fun testUnicodeScripts() {
        assertTrue(requireSet("Zyyy").contains('0'.code))
        assertTrue(requireSet("Latn").contains('X'.code))
        assertTrue(requireSet("Hani").contains(0x4E04))
        assertTrue(requireSet("Cyrl").contains(0x0404))
    }

    @Test
    fun testUnicodeScriptEquals() {
        assertTrue(requireSet("Script=Zyyy").contains('0'.code))
        assertTrue(requireSet("Script=Latn").contains('X'.code))
        assertTrue(requireSet("Script=Hani").contains(0x4E04))
        assertTrue(requireSet("Script=Cyrl").contains(0x0404))
    }

    @Test
    fun testUnicodeScriptAliases() {
        assertTrue(requireSet("Common").contains('0'.code))
        assertTrue(requireSet("Latin").contains('X'.code))
        assertTrue(requireSet("Han").contains(0x4E04))
        assertTrue(requireSet("Cyrillic").contains(0x0404))
    }

    @Test
    fun testUnicodeBlocks() {
        assertTrue(requireSet("InASCII").contains('0'.code))
        assertTrue(requireSet("InCJK").contains(0x4E04))
        assertTrue(requireSet("InCyrillic").contains(0x0404))
        assertTrue(requireSet("InMisc_Pictographs").contains(0x1F4A9))
    }

    @Test
    fun testUnicodeBlockEquals() {
        assertTrue(requireSet("Block=ASCII").contains('0'.code))
        assertTrue(requireSet("Block=CJK").contains(0x4E04))
        assertTrue(requireSet("Block=Cyrillic").contains(0x0404))
        assertTrue(requireSet("Block=Misc_Pictographs").contains(0x1F4A9))
    }

    @Test
    fun testUnicodeBlockAliases() {
        assertTrue(requireSet("InBasic_Latin").contains('0'.code))
        assertTrue(requireSet("InMiscellaneous_Mathematical_Symbols_B").contains(0x29BE))
    }

    @Test
    fun testEnumeratedPropertyEquals() {
        assertFalse(
            requireSet("Grapheme_Cluster_Break=E_Base").contains(0x1F47E),
            "U+1F47E ALIEN MONSTER is not an emoji modifier",
        )
        assertFalse(
            requireSet("Grapheme_Cluster_Break=E_Base").contains(0x1038),
            "U+1038 MYANMAR SIGN VISARGA is not a spacing mark",
        )
        assertTrue(
            requireSet("East_Asian_Width=Ambiguous").contains(0x00A1),
            "U+00A1 INVERTED EXCLAMATION MARK has ambiguous East Asian Width",
        )
        assertFalse(
            requireSet("East_Asian_Width=Ambiguous").contains(0x00A2),
            "U+00A2 CENT SIGN does not have ambiguous East Asian Width",
        )
    }

    @Test
    fun extendedPictographic() {
        assertTrue(
            requireSet("Extended_Pictographic").contains(0x1F588),
            "U+1F588 BLACK PUSHPIN is in Extended Pictographic",
        )
        assertFalse(
            requireSet("Extended_Pictographic").contains('0'.code),
            "0 is not in Extended Pictographic",
        )
        assertTrue(requireSet("EP").contains(0x1F588))
    }

    @Test
    fun emojiPresentation() {
        assertTrue(
            requireSet("EmojiPresentation=EmojiDefault").contains(0x1F4A9),
            "U+1F4A9 PILE OF POO is in EmojiPresentation=EmojiDefault",
        )
        assertFalse(
            requireSet("EmojiPresentation=EmojiDefault").contains('0'.code),
            "0 is not in EmojiPresentation=EmojiDefault",
        )
        assertFalse(
            requireSet("EmojiPresentation=EmojiDefault").contains('A'.code),
            "A is not in EmojiPresentation=EmojiDefault",
        )
        assertFalse(
            requireSet("EmojiPresentation=TextDefault").contains(0x1F4A9),
            "U+1F4A9 PILE OF POO is not in EmojiPresentation=TextDefault",
        )
        assertTrue(
            requireSet("EmojiPresentation=TextDefault").contains('0'.code),
            "0 is in EmojiPresentation=TextDefault",
        )
        assertFalse(
            requireSet("EmojiPresentation=TextDefault").contains('A'.code),
            "A is not in EmojiPresentation=TextDefault",
        )
        assertTrue(requireSet("EmojiPresentation=Text").contains('A'.code))
        assertTrue(requireSet("EmojiRK").contains('#'.code))
        assertTrue(requireSet("EmojiNRK").contains(0x1F4A9))
        assertFalse(requireSet("EmojiNRK").contains('#'.code))
    }

    @Test
    fun testPropertyCaseInsensitivity() {
        assertTrue(requireSet("l").contains('x'.code))
        assertFalse(requireSet("l").contains('0'.code))
        assertTrue(requireSet("common").contains('0'.code))
        assertTrue(requireSet("Alnum").contains('0'.code))
    }

    @Test
    fun testPropertyDashSameAsUnderscore() {
        assertTrue(requireSet("InLatin-1").contains('\u00F0'.code))
        assertNull(UnicodeData.getPropertyCodePoints("NotAProperty"))
    }

    @Test
    fun modifyingUnicodeDataShouldThrow() {
        val exception =
            assertFailsWith<IllegalStateException> {
                requireSet("L").add(0x12345)
            }
        assertEquals("can't alter readonly IntervalSet", exception.message)
    }
}
