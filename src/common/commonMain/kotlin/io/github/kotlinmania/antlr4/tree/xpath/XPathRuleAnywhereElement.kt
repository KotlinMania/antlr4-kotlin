package io.github.kotlinmania.antlr4.tree.xpath

import io.github.kotlinmania.antlr4.tree.ParseTree
import io.github.kotlinmania.antlr4.tree.Trees

class XPathRuleAnywhereElement(
    ruleName: String,
    val ruleIndex: Int,
) : XPathElement(ruleName) {
    override fun evaluate(t: ParseTree): List<ParseTree> = Trees.findAllRuleNodes(t, ruleIndex)
}
