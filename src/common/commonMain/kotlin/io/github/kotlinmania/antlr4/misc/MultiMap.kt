package io.github.kotlinmania.antlr4.misc

internal class MultiMap<K, V> {
    val data: MutableMap<K, MutableList<V>> = LinkedHashMap()

    val size: Int
        get() = data.size

    val keys: MutableSet<K>
        get() = data.keys

    val values: MutableCollection<MutableList<V>>
        get() = data.values

    val entries: MutableSet<MutableMap.MutableEntry<K, MutableList<V>>>
        get() = data.entries

    fun isEmpty(): Boolean = data.isEmpty()

    fun isNotEmpty(): Boolean = data.isNotEmpty()

    fun containsKey(key: K): Boolean = data.containsKey(key)

    fun map(
        key: K,
        value: V,
    ) {
        data.getOrPut(key) { ArrayList() }.add(value)
    }

    fun put(
        key: K,
        value: MutableList<V>,
    ): MutableList<V>? = data.put(key, value)

    fun remove(key: K): MutableList<V>? = data.remove(key)

    fun clear() {
        data.clear()
    }

    fun toReadOnlyMap(): Map<K, List<V>> = data

    val pairs: List<Pair<K, V>>
        get() = data.flatMap { (key, values) -> values.map { value -> Pair(key, value) } }

    operator fun get(key: K): MutableList<V>? = data[key]
}
