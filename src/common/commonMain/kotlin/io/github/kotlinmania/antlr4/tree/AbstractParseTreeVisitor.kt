package io.github.kotlinmania.antlr4.tree

open class AbstractParseTreeVisitor<T> : ParseTreeVisitor<T> {
    override fun visit(tree: ParseTree?): T? = tree?.accept(this)

    override fun visitChildren(node: RuleNode?): T? {
        var result = defaultResult()
        if (node == null) return result
        val n: Int = node.childCount
        for (i in 0..<n) {
            if (!shouldVisitNextChild(node, result)) {
                break
            }
            val c: ParseTree? = node.getChild(i)
            if (c == null) continue
            val childResult: T? = c.accept(this)
            result = aggregateResult(result, childResult)
        }
        return result
    }

    override fun visitTerminal(node: TerminalNode?): T? = defaultResult()

    override fun visitErrorNode(node: ErrorNode?): T? = defaultResult()

    protected open fun defaultResult(): T? = null

    protected open fun aggregateResult(
        aggregate: T?,
        nextResult: T?,
    ): T? = nextResult

    protected open fun shouldVisitNextChild(
        node: RuleNode?,
        currentResult: T?,
    ): Boolean = true
}
