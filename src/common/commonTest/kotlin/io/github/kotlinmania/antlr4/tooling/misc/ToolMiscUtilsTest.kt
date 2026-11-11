/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolMiscUtilsTest {
    @Test
    fun testStripFileExtension() {
        assertNull(Utils.stripFileExtension(null))
        assertEquals("foo", Utils.stripFileExtension("foo"))
        assertEquals("foo", Utils.stripFileExtension("foo.txt"))
    }

    @Test
    fun testJoin() {
        assertEquals("foobbar", Utils.join(listOf("foo", "bar"), "b"))
        assertEquals("foo,bar", Utils.join(listOf("foo", "bar"), ","))
    }

    @Test
    fun testSortLinesInString() {
        assertEquals("bar\nbaz\nfoo\n", Utils.sortLinesInString("foo\nbar\nbaz"))
    }

    @Test
    fun testCapitalizeAndDecapitalize() {
        assertEquals("Foo", Utils.capitalize("foo"))
        assertEquals("fOO", Utils.decapitalize("FOO"))
    }

    @Test
    fun testSelect() {
        val strings = listOf("foo", "bar")
        val func1 = Utils.Func1<String, String> { "baz" }
        assertEquals(listOf("baz", "baz"), Utils.select(strings, func1))
        assertNull(Utils.select<String, String>(null, null))
    }

    @Test
    fun testFind() {
        val items: List<Any> = listOf(42, "foo", "bar")
        assertEquals("foo", Utils.find(items, String::class))
        assertNull(Utils.find(emptyList<Any>(), String::class))
    }

    @Test
    fun testIndexOfAndLastIndexOf() {
        val strings = listOf("foo", "bar")
        val filter = Utils.Filter<String> { true }
        assertEquals(0, Utils.indexOf(strings, filter))
        assertEquals(-1, Utils.indexOf(emptyList<String>(), null))
        assertEquals(1, Utils.lastIndexOf(strings, filter))
        assertEquals(-1, Utils.lastIndexOf(emptyList<String>(), null))
    }

    @Test
    fun testSetSize() {
        val strings = mutableListOf<String?>("foo", "bar", "baz")
        assertEquals(3, strings.size)
        Utils.setSize(strings, 2)
        assertEquals(2, strings.size)
        Utils.setSize(strings, 4)
        assertEquals(4, strings.size)
        assertNull(strings[2])
    }

    @Test
    fun testMutableIntAndFrequencySetAndOrderedHashMap() {
        val m1 = MutableInt(3)
        val m2 = MutableInt(5)
        assertTrue(m1 < m2)
        assertEquals(3L, m1.longValue())

        val freq = FrequencySet<String>()
        assertEquals(0, freq.count("x"))
        freq.add("x")
        freq.add("x")
        freq.add("y")
        assertEquals(2, freq.count("x"))
        assertEquals(1, freq.count("y"))

        val map = OrderedHashMap<String, Int>()
        map.put("alpha", 10)
        map.put("beta", 20)
        map.put("alpha", 15)
        assertEquals(2, map.size)
        assertEquals("alpha", map.getKey(0))
        assertEquals(15, map.getElement(0))
        assertEquals("beta", map.getKey(1))
        assertEquals(20, map.getElement(1))
    }
}
