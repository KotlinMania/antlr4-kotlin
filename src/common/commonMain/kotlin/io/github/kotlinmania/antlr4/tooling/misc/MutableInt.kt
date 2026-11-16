/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

class MutableInt(
    var v: Int,
) : Comparable<MutableInt> {
    override fun equals(other: Any?): Boolean =
        when (other) {
            is MutableInt -> v == other.v
            is Number -> v == other.toInt()
            else -> false
        }

    override fun hashCode(): Int = v

    override fun compareTo(other: MutableInt): Int = v.compareTo(other.v)

    fun intValue(): Int = v

    fun longValue(): Long = v.toLong()

    fun floatValue(): Float = v.toFloat()

    fun doubleValue(): Double = v.toDouble()

    fun toByte(): Byte = v.toByte()

    fun toShort(): Short = v.toShort()

    fun toInt(): Int = v

    fun toLong(): Long = v.toLong()

    fun toFloat(): Float = v.toFloat()

    fun toDouble(): Double = v.toDouble()

    override fun toString(): String = v.toString()
}
