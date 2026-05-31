package io.github.kotlinmania.antlr4.tree.xpath

import io.github.kotlinmania.antlr4.tree.ParseTree
import io.github.kotlinmania.antlr4.tree.TerminalNode
import io.github.kotlinmania.antlr4.tree.Trees

class XPathTokenElement(
    tokenName: String,
    val tokenType: Int,
) : XPathElement(tokenName) {
    override fun evaluate(t: ParseTree): Collection<ParseTree?> {
        val nodes = mutableListOf<ParseTree>()
        for (c in Trees.getChildren(t)) {
            if (c is TerminalNode) {
                val tnode: TerminalNode = c
                val tokenTypeMatch = tnode.symbol?.type == tokenType
                if ((tokenTypeMatch && !invert) ||
                    (!tokenTypeMatch && invert)
                ) {
                    nodes.add(tnode)
                }
            }
        }
        return nodes
    }
}
