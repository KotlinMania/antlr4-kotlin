/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package io.github.kotlinmania.antlr4.misc

/**
 *
 * @author Sam Harwell
 */
open class IntegerList {
    private var data: IntArray

    private var count = 0

    constructor() {
        data = io.github.kotlinmania.antlr4.misc.IntegerList.Companion.EMPTY_DATA
    }

    constructor(capacity: Int) {
        require(capacity >= 0)

        if (capacity == 0) {
            data = io.github.kotlinmania.antlr4.misc.IntegerList.Companion.EMPTY_DATA
        } else {
            data = IntArray(capacity)
        }
    }

    constructor(list: IntegerList) {
        data = list.data.copyOf()
        count = list.count
    }

    internal constructor(list: Collection<Int?>) : this(list.size) {
        for (value in list) {
            add(value!!)
        }
    }

    fun add(value: Int) {
        if (data.size == count) {
            ensureCapacity(count + 1)
        }

        data[count] = value
        count++
    }

    fun addAll(array: IntArray) {
        ensureCapacity(count + array.size)
        array.copyInto(data, count, 0, array.size)
        count += array.size
    }

    fun addAll(list: IntegerList) {
        ensureCapacity(count + list.count)
        list.data.copyInto(data, count, 0, list.count)
        count += list.count
    }

    fun addAll(list: Collection<Int?>) {
        ensureCapacity(count + list.size)
        var current = 0
        for (x in list) {
            data[count + current] = x!!
            current++
        }
        count += list.size
    }

    fun get(index: Int): Int {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("")
        }

        return data[index]
    }

    fun contains(value: Int): Boolean {
        for (i in 0..<count) {
            if (data[i] == value) {
                return true
            }
        }

        return false
    }

    fun set(
        index: Int,
        value: Int,
    ): Int {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("")
        }

        val previous = data[index]
        data[index] = value
        return previous
    }

    fun removeAt(index: Int): Int {
        val value = get(index)
        data.copyInto(data, index, index + 1, count)
        data[count - 1] = 0
        count--
        return value
    }

    fun removeRange(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex > count || toIndex > count) {
            throw IndexOutOfBoundsException("")
        }
        require(fromIndex <= toIndex)

        data.copyInto(data, fromIndex, toIndex, count)
        data.fill(0, count - (toIndex - fromIndex), count)
        count -= (toIndex - fromIndex)
    }

    val isEmpty: Boolean
        get() = count == 0

    fun size(): Int = count

    fun trimToSize() {
        if (data.size == count) {
            return
        }

        data = data.copyOf(count)
    }

    fun clear() {
        data.fill(0, 0, count)
        count = 0
    }

    fun toArray(): IntArray {
        if (count == 0) {
            return io.github.kotlinmania.antlr4.misc.IntegerList.Companion.EMPTY_DATA
        }

        return data.copyOf(count)
    }

    fun sort() {
        data.sort(0, count)
    }

    /**
     * Compares the specified object with this list for equality.  Returns
     * `true` if and only if the specified object is also an [IntegerList],
     * both lists have the same size, and all corresponding pairs of elements in
     * the two lists are equal.  In other words, two lists are defined to be
     * equal if they contain the same elements in the same order.
     *
     *
     * This implementation first checks if the specified object is this
     * list. If so, it returns `true`; if not, it checks if the
     * specified object is an [IntegerList]. If not, it returns `false`;
     * if so, it checks the size of both lists. If the lists are not the same size,
     * it returns `false`; otherwise it iterates over both lists, comparing
     * corresponding pairs of elements.  If any comparison returns `false`,
     * this method returns `false`.
     *
     * @param o the object to be compared for equality with this list
     * @return `true` if the specified object is equal to this list
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other !is IntegerList) {
            return false
        }

        if (count != other.count) {
            return false
        }

        for (i in 0..<count) {
            if (data[i] != other.data[i]) {
                return false
            }
        }

        return true
    }

    /**
     * Returns the hash code value for this list.
     *
     *
     * This implementation uses exactly the code that is used to define the
     * list hash function in the documentation for the [List.hashCode]
     * method.
     *
     * @return the hash code value for this list
     */
    override fun hashCode(): Int {
        var hashCode = 1
        for (i in 0..<count) {
            hashCode = 31 * hashCode + data[i]
        }

        return hashCode
    }

    /**
     * Returns a string representation of this list.
     */
    override fun toString(): String = toArray().contentToString()

    fun binarySearch(key: Int): Int = binarySearch(0, count, key)

    fun binarySearch(
        fromIndex: Int,
        toIndex: Int,
        key: Int,
    ): Int {
        if (fromIndex < 0 || toIndex < 0 || fromIndex > count || toIndex > count) {
            throw IndexOutOfBoundsException("")
        }
        require(fromIndex <= toIndex)

        var low = fromIndex
        var high = toIndex - 1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midVal = data[mid]
            when {
                midVal < key -> low = mid + 1
                midVal > key -> high = mid - 1
                else -> return mid
            }
        }
        return -(low + 1)
    }

    private fun ensureCapacity(capacity: Int) {
        if (capacity < 0 || capacity > io.github.kotlinmania.antlr4.misc.IntegerList.Companion.MAX_ARRAY_SIZE) {
            throw IllegalStateException("Requested array size exceeds limit")
        }

        var newLength: Int
        if (data.size == 0) {
            newLength = io.github.kotlinmania.antlr4.misc.IntegerList.Companion.INITIAL_SIZE
        } else {
            newLength = data.size
        }

        while (newLength < capacity) {
            newLength = newLength * 2
            if (newLength < 0 || newLength > io.github.kotlinmania.antlr4.misc.IntegerList.Companion.MAX_ARRAY_SIZE) {
                newLength = io.github.kotlinmania.antlr4.misc.IntegerList.Companion.MAX_ARRAY_SIZE
            }
        }

        data = data.copyOf(newLength)
    }

    /**
     * Converts this list of Unicode code points to UTF-16 code units.
     */
    fun toCharArray(): CharArray? {
        // Optimize for the common case (all data values are
        // < 0xFFFF) to avoid an extra scan
        var resultArray: CharArray? = CharArray(count)
        var resultIdx = 0
        var calculatedPreciseResultSize = false
        for (i in 0..<count) {
            val codePoint = data[i]
            // Calculate the precise result size if we encounter
            // a code point > 0xFFFF
            if (!calculatedPreciseResultSize &&
                codePoint >= 0x10000
            ) {
                resultArray = resultArray!!.copyOf(charArraySize())
                calculatedPreciseResultSize = true
            }
            val chars = intToChars(codePoint)
            chars.copyInto(resultArray!!, resultIdx)
            resultIdx += chars.size
        }
        return resultArray
    }

    private fun charArraySize(): Int {
        var result = 0
        for (i in 0..<count) {
            result += charCount(data[i])
        }
        return result
    }

    companion object {
        private fun intToChars(codePoint: Int): CharArray {
            if (codePoint < 0 || codePoint > 0x10FFFF) {
                throw IllegalArgumentException("Invalid code point: $codePoint")
            }
            if (codePoint <= Char.MAX_VALUE.code) {
                return charArrayOf(codePoint.toChar())
            }

            val offset = codePoint - 0x10000
            val high = (0xD800 + (offset ushr 10)).toChar()
            val low = (0xDC00 + (offset and 0x3FF)).toChar()
            return charArrayOf(high, low)
        }

        private fun charCount(codePoint: Int): Int =
            if (codePoint < 0) {
                0
            } else if (codePoint <= Char.MAX_VALUE.code) {
                1
            } else {
                2
            }

        private val EMPTY_DATA = IntArray(0)

        private const val INITIAL_SIZE = 4
        private val MAX_ARRAY_SIZE: Int = Int.MAX_VALUE - 8
    }
}
