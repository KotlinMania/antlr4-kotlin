package io.github.kotlinmania.antlr4.tree.xpath

import io.github.kotlinmania.antlr4.tree.ParseTree
import io.github.kotlinmania.antlr4.tree.Trees

class XPathTokenAnywhereElement(
    tokenName: String,
    val tokenType: Int,
) : XPathElement(tokenName) {
    override fun evaluate(t: ParseTree): List<ParseTree> = Trees.findAllTokenNodes(t, tokenType)
}
