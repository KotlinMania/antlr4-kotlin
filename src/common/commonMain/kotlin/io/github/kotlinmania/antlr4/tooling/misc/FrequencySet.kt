/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

/** Count how many of each key we have; not thread safe. */
class FrequencySet<T : Any> {
    private val backing: LinkedHashMap<T, MutableInt> = LinkedHashMap()

    val size: Int
        get() = backing.size

    fun isEmpty(): Boolean = backing.isEmpty()

    fun containsKey(key: T): Boolean = backing.containsKey(key)

    operator fun get(key: T): MutableInt? = backing[key]

    fun put(
        key: T,
        value: MutableInt,
    ): MutableInt? = backing.put(key, value)

    operator fun set(
        key: T,
        value: MutableInt,
    ) {
        backing[key] = value
    }

    fun remove(key: T): MutableInt? = backing.remove(key)

    fun clear() {
        backing.clear()
    }

    fun count(key: T): Int = backing[key]?.v ?: 0

    fun add(key: T) {
        val existing = backing[key]
        if (existing == null) {
            backing[key] = MutableInt(1)
        } else {
            existing.v++
        }
    }

    val keys: List<T>
        get() = backing.keys.toList()

    fun asMap(): Map<T, MutableInt> = backing

    override fun equals(other: Any?): Boolean =
        other is FrequencySet<*> && backing == other.backing

    override fun hashCode(): Int = backing.hashCode()

    override fun toString(): String = backing.toString()
}
