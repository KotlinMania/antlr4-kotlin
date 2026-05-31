package io.github.kotlinmania.antlr4.tree.xpath

import io.github.kotlinmania.antlr4.ParserRuleContext
import io.github.kotlinmania.antlr4.tree.ParseTree
import io.github.kotlinmania.antlr4.tree.Trees

class XPathRuleElement(
    ruleName: String,
    val ruleIndex: Int,
) : XPathElement(ruleName) {
    override fun evaluate(t: ParseTree): Collection<ParseTree?> {
        val nodes = mutableListOf<ParseTree>()
        for (c in Trees.getChildren(t)) {
            if (c is ParserRuleContext) {
                val ctx = c
                if ((ctx.ruleIndex == ruleIndex && !invert) ||
                    (ctx.ruleIndex != ruleIndex && invert)
                ) {
                    nodes.add(ctx)
                }
            }
        }
        return nodes
    }
}
