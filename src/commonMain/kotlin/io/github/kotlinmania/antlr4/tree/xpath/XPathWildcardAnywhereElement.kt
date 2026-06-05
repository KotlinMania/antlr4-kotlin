package io.github.kotlinmania.antlr4.tree.xpath

import io.github.kotlinmania.antlr4.tree.ParseTree
import io.github.kotlinmania.antlr4.tree.Trees

class XPathWildcardAnywhereElement : XPathElement(WILDCARD) {
    companion object {
        const val WILDCARD: String = "*"
    }

    override fun evaluate(t: ParseTree): Collection<ParseTree> {
        if (invert) return mutableListOf()
        return Trees.getDescendants(t)
    }
}
