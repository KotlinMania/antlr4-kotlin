package io.github.kotlinmania.antlr4.tree

import io.github.kotlinmania.antlr4.Parser
import io.github.kotlinmania.antlr4.RuleContext

interface ParseTree : SyntaxTree {
    override val parent: ParseTree?

    override fun getChild(i: Int): ParseTree?

    fun setParent(parent: RuleContext?)

    fun <T> accept(visitor: ParseTreeVisitor<out T>?): T?

    val text: String?

    fun toStringTree(parser: Parser?): String?
}
