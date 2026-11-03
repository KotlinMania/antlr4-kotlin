package io.github.kotlinmania.antlr4.tree.xpath

import io.github.kotlinmania.antlr4.tree.ParseTree
import io.github.kotlinmania.antlr4.tree.Trees

class XPathWildcardElement : XPathElement(WILDCARD) {
    companion object {
        const val WILDCARD: String = "*"
    }

    override fun evaluate(t: ParseTree): Collection<ParseTree> {
        if (invert) return mutableListOf()
        val kids = mutableListOf<ParseTree>()
        for (c in Trees.getChildren(t)) {
            if (c is ParseTree) {
                kids.add(c)
            }
        }
        return kids
    }
}
