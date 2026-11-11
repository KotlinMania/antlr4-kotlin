/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

/** Insertion-ordered hash map supporting positional index lookup via [getKey] and [getElement]. */
class OrderedHashMap<K : Any, V : Any> {
    private val backing: LinkedHashMap<K, V> = LinkedHashMap()
    private val elementKeys: ArrayList<K> = ArrayList()

    val size: Int
        get() = backing.size

    fun isEmpty(): Boolean = backing.isEmpty()

    fun containsKey(key: K): Boolean = backing.containsKey(key)

    fun containsValue(value: V): Boolean = backing.containsValue(value)

    operator fun get(key: K): V? = backing[key]

    fun getKey(i: Int): K = elementKeys[i]

    fun getElement(i: Int): V? = backing[elementKeys[i]]

    fun put(
        key: K,
        value: V,
    ): V? {
        if (!backing.containsKey(key)) {
            elementKeys.add(key)
        }
        return backing.put(key, value)
    }

    operator fun set(
        key: K,
        value: V,
    ) {
        put(key, value)
    }

    fun putAll(from: Map<K, V>) {
        for ((key, value) in from) {
            put(key, value)
        }
    }

    fun putAll(other: OrderedHashMap<K, V>) {
        for ((key, value) in other.backing) {
            put(key, value)
        }
    }

    fun remove(key: K): V? {
        elementKeys.remove(key)
        return backing.remove(key)
    }

    fun clear() {
        elementKeys.clear()
        backing.clear()
    }

    val keys: List<K>
        get() = ArrayList(elementKeys)

    val values: List<V>
        get() = backing.values.toList()

    fun asMap(): Map<K, V> = backing

    override fun equals(other: Any?): Boolean =
        other is OrderedHashMap<*, *> && backing == other.backing

    override fun hashCode(): Int = backing.hashCode()

    override fun toString(): String = backing.toString()
}
