/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

import io.github.kotlinmania.antlr4.misc.IntegerList
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.writeString
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

object Utils {
    const val INTEGER_POOL_MAX_VALUE: Int = 1000

    fun interface Filter<T> {
        fun select(t: T): Boolean
    }

    fun interface Func0<TResult> {
        fun exec(): TResult
    }

    fun interface Func1<T1, TResult> {
        fun exec(arg1: T1): TResult
    }

    fun stripFileExtension(name: String?): String? {
        if (name == null) return null
        val lastDot = name.lastIndexOf('.')
        if (lastDot < 0) return name
        return name.substring(0, lastDot)
    }

    fun <T> join(
        values: List<T>,
        separator: String,
    ): String {
        val buf = StringBuilder()
        for (i in values.indices) {
            buf.append(values[i].toString())
            if (i + 1 < values.size) {
                buf.append(separator)
            }
        }
        return buf.toString()
    }

    fun sortLinesInString(s: String): String {
        val lines = s.split("\n").sorted()
        val buf = StringBuilder()
        for (line in lines) {
            buf.append(line)
            buf.append('\n')
        }
        return buf.toString()
    }

    fun writeSerializedATNIntegerHistogram(
        filename: String,
        serializedATN: IntegerList,
    ) {
        val histo = HashMap<Int, Int>()
        for (value in serializedATN.toArray()) {
            histo[value] = (histo[value] ?: 0) + 1
        }
        val sortedKeys = histo.keys.sorted()
        val buf = StringBuilder("value,count\n")
        for (key in sortedKeys) {
            buf.append(key).append(',').append(histo[key]).append('\n')
        }
        val path = Path(filename)
        val parent = path.parent
        if (parent != null) {
            SystemFileSystem.createDirectories(parent)
        }
        SystemFileSystem.sink(path).buffered().use { sink ->
            sink.writeString(buf.toString())
        }
    }

    fun capitalize(s: String): String {
        if (s.isEmpty()) return s
        return s[0].uppercaseChar() + s.substring(1)
    }

    fun decapitalize(s: String): String {
        if (s.isEmpty()) return s
        return s[0].lowercaseChar() + s.substring(1)
    }

    fun <From, To> select(
        list: List<From>?,
        selector: Func1<From, To>?,
    ): List<To>? {
        if (list == null || selector == null) return null
        val result = ArrayList<To>(list.size)
        for (item in list) {
            result.add(selector.exec(item))
        }
        return result
    }

    fun <T : Any> find(
        ops: List<*>,
        cl: KClass<T>,
    ): T? {
        for (item in ops) {
            val matched = cl.safeCast(item)
            if (matched != null) {
                return matched
            }
        }
        return null
    }

    fun <T> indexOf(
        elems: List<T>,
        filter: Filter<T>?,
    ): Int {
        if (filter == null) return -1
        for (i in elems.indices) {
            if (filter.select(elems[i])) return i
        }
        return -1
    }

    fun <T> lastIndexOf(
        elems: List<T>,
        filter: Filter<T>?,
    ): Int {
        if (filter == null) return -1
        for (i in elems.size - 1 downTo 0) {
            if (filter.select(elems[i])) return i
        }
        return -1
    }

    internal fun <T> setSize(
        list: MutableList<T?>,
        size: Int,
    ) {
        if (size < list.size) {
            list.subList(size, list.size).clear()
        } else {
            while (size > list.size) {
                list.add(null)
            }
        }
    }
}
