/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import kotlin.test.Test
import kotlin.test.assertEquals

class UnicodeEscapesTest {
    @Test fun latinJavaEscape() = checkEscape("\\u0061", 0x0061, "Java")
    @Test fun latinPythonEscape() = checkEscape("\\u0061", 0x0061, "Python3")
    @Test fun latinSwiftEscape() = checkEscape("\\u{0061}", 0x0061, "Swift")
    @Test fun bmpJavaEscape() = checkEscape("\\uABCD", 0xABCD, "Java")
    @Test fun bmpPythonEscape() = checkEscape("\\uABCD", 0xABCD, "Python3")
    @Test fun bmpSwiftEscape() = checkEscape("\\u{ABCD}", 0xABCD, "Swift")
    @Test fun smpJavaEscape() = checkEscape("\\uD83D\\uDCA9", 0x1F4A9, "Java")
    @Test fun smpPythonEscape() = checkEscape("\\U0001F4A9", 0x1F4A9, "Python3")
    @Test fun smpSwiftEscape() = checkEscape("\\u{1F4A9}", 0x1F4A9, "Swift")

    @Test
    fun everyTargetUsesItsOriginalEscapeSyntax() {
        for (language in listOf("CSharp", "Cpp", "Go", "PHP")) {
            checkEscape("\\U0001F4A9", 0x1F4A9, language)
        }
        for (language in listOf("JavaScript", "TypeScript", "Dart", "Other")) {
            checkEscape("\\uD83D\\uDCA9", 0x1F4A9, language)
        }
    }

    @Test
    fun appendsWithoutReplacingExistingText() {
        val builder = StringBuilder("prefix:")
        UnicodeEscapes.appendEscapedCodePoint(builder, 0x10FFFF, "Cpp")
        assertEquals("prefix:\\U0010FFFF", builder.toString())
    }

    private fun checkEscape(expected: String, input: Int, language: String) {
        assertEquals(expected, UnicodeEscapes.escapeCodePoint(input, language))
    }
}
