package io.github.kotlinmania.antlr4.tree

import io.github.kotlinmania.antlr4.Token

class ErrorNodeImpl(
    symbol: Token?,
) : TerminalNodeImpl(symbol),
    ErrorNode {
    override fun <T> accept(visitor: ParseTreeVisitor<out T>?): T? = visitor?.visitErrorNode(this)
}
