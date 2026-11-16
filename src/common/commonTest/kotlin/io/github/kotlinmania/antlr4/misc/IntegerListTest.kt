/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.misc

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class IntegerListTest {
    @Test
    fun emptyListToEmptyCharArray() {
        val list = IntegerList()
        assertContentEquals(CharArray(0), list.toCharArray())
    }

    @Test
    fun negativeIntegerToCharArrayThrows() {
        val list = IntegerList()
        list.add(-42)
        assertFailsWith<IllegalArgumentException> {
            list.toCharArray()
        }
    }

    @Test
    fun surrogateRangeIntegerToCharArray() {
        val list = IntegerList()
        list.add(0xDC00)
        assertContentEquals(charArrayOf(0xDC00.toChar()), list.toCharArray())
    }

    @Test
    fun tooLargeIntegerToCharArrayThrows() {
        val list = IntegerList()
        list.add(0x110000)
        assertFailsWith<IllegalArgumentException> {
            list.toCharArray()
        }
    }

    @Test
    fun unicodeBmpIntegerListToCharArray() {
        val list = IntegerList()
        list.add(0x35)
        list.add(0x4E94)
        list.add(0xFF15)

        val expected = charArrayOf(0x35.toChar(), 0x4E94.toChar(), 0xFF15.toChar())
        assertContentEquals(expected, list.toCharArray())
    }

    @Test
    fun unicodeSmpIntegerListToCharArray() {
        val list = IntegerList()
        list.add(0x104A5)
        list.add(0x116C5)
        list.add(0x1D7FB)

        val expected =
            charArrayOf(
                0xD801.toChar(),
                0xDCA5.toChar(),
                0xD805.toChar(),
                0xDEC5.toChar(),
                0xD835.toChar(),
                0xDFFB.toChar(),
            )
        assertContentEquals(expected, list.toCharArray())
    }
}
