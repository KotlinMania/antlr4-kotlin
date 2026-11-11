package io.github.kotlinmania.antlr4.tree.pattern

import io.github.kotlinmania.antlr4.tree.ParseTree
import io.github.kotlinmania.antlr4.tree.xpath.XPath

class ParseTreePattern(
    matcher: ParseTreePatternMatcher,
    pattern: String?,
    patternRuleIndex: Int,
    patternTree: ParseTree?,
) {
    val patternRuleIndex: Int
    val pattern: String?
    private val patternTree: ParseTree?
    private val matcher: ParseTreePatternMatcher

    init {
        this.matcher = matcher
        this.patternRuleIndex = patternRuleIndex
        this.pattern = pattern
        this.patternTree = patternTree
    }

    fun match(tree: ParseTree?): ParseTreeMatch =
        matcher.match(requireNotNull(tree) { "tree cannot be null" }, this)

    fun matches(tree: ParseTree?): Boolean =
        matcher.matches(requireNotNull(tree) { "tree cannot be null" }, this)

    fun findAll(
        tree: ParseTree?,
        xpath: String?,
    ): List<ParseTreeMatch> {
        val activeParser = requireNotNull(matcher.getParser()) { "parser cannot be null" }
        val subtrees = XPath.findAll(tree, xpath.orEmpty(), activeParser)
        val matches = mutableListOf<ParseTreeMatch>()
        for (t in subtrees) {
            val match = match(t)
            if (match.succeeded()) {
                matches.add(match)
            }
        }
        return matches
    }

    fun getMatcher(): ParseTreePatternMatcher = matcher

    fun getPatternTree(): ParseTree? = patternTree
}
