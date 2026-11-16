/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST

/**
 * Tracks the attributes within return values, argument lists, local scopes, and predefined token/rule scopes.
 */
class AttributeDict {
    var name: String? = null
    var ast: GrammarAST? = null
    var type: DictType? = null

    enum class DictType {
        ARG,
        RET,
        LOCAL,
        TOKEN,
        PREDEFINED_RULE,
        PREDEFINED_LEXER_RULE,
    }

    internal val attributeMap: LinkedHashMap<String, Attribute> = LinkedHashMap()

    /** Ordered view of [Attribute] entries keyed by attribute name. */
    val attributes: Map<String, Attribute>
        get() = attributeMap

    constructor()

    constructor(type: DictType?) {
        this.type = type
    }

    fun add(a: Attribute): Attribute? {
        a.dict = this
        val key = a.name ?: return null
        return attributeMap.put(key, a)
    }

    fun get(name: String?): Attribute? {
        if (name == null) return null
        return attributeMap[name]
    }

    fun size(): Int = attributeMap.size

    /**
     * Return the set of keys that collide from `this` and [other].
     */
    fun intersection(other: AttributeDict?): Set<String> {
        if (other == null || other.size() == 0 || size() == 0) {
            return emptySet()
        }
        val result = LinkedHashSet<String>(attributeMap.keys)
        result.retainAll(other.attributeMap.keys)
        return result
    }

    override fun toString(): String = "$name:$attributeMap"

    companion object {
        /**
         * All token scopes (token labels) share the same fixed scope of predefined attributes.
         */
        val predefinedTokenDict: AttributeDict =
            AttributeDict(DictType.TOKEN).apply {
                add(Attribute("text"))
                add(Attribute("type"))
                add(Attribute("line"))
                add(Attribute("index"))
                add(Attribute("pos"))
                add(Attribute("channel"))
                add(Attribute("int"))
            }
    }
}
