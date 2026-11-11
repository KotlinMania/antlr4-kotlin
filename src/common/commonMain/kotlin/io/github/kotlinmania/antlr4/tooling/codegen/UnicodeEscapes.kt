/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

/** Escapes Unicode code points using the selected target language's syntax. */
object UnicodeEscapes {
    fun escapeCodePoint(codePoint: Int, language: String): String =
        buildString { appendEscapedCodePoint(this, codePoint, language) }

    fun appendEscapedCodePoint(builder: StringBuilder, codePoint: Int, language: String) {
        val supplementary = codePoint in 0x10000..0x10FFFF
        when (language) {
            "CSharp", "Python3", "Cpp", "Go", "PHP" -> {
                if (supplementary) {
                    builder.append("\\U").append(hex(codePoint, 8))
                } else {
                    builder.append("\\u").append(hex(codePoint, 4))
                }
            }
            "Swift" -> builder.append("\\u{").append(hex(codePoint, 4)).append('}')
            else -> {
                if (supplementary) {
                    val value = codePoint - 0x10000
                    builder.append("\\u").append(hex((value ushr 10) + 0xD800, 4))
                    builder.append("\\u").append(hex((value and 0x3FF) + 0xDC00, 4))
                } else {
                    builder.append("\\u").append(hex(codePoint, 4))
                }
            }
        }
    }

    private fun hex(value: Int, width: Int): String =
        value.toUInt().toString(16).uppercase().padStart(width, '0')
}
