/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.ANTLRInputStream
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.semantics.BlankActionSplitterListener
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionSplitterTest {
    @Test
    fun testExprs() {
        var i = 0
        while (i < EXPRS.size) {
            val input = EXPRS[i]
            val expect = EXPRS[i + 1]
            val chunks = getActionChunks(input)
            assertEquals(expect, chunks.toString(), "input: $input")
            i += 2
        }
    }

    @Test
    fun testSetAttrCommentsAndListenerCallbacks() {
        val events = ArrayList<String>()
        val listener =
            object : ActionSplitterListener {
                override fun qualifiedAttr(
                    expr: String,
                    x: Token,
                    y: Token,
                ) {
                    events.add("QUAL(${x.text}.${y.text})")
                }

                override fun setAttr(
                    expr: String,
                    x: Token,
                    rhs: Token,
                ) {
                    events.add("SET(${x.text}=${rhs.text})")
                }

                override fun attr(
                    expr: String,
                    x: Token,
                ) {
                    events.add("ATTR(${x.text})")
                }

                override fun setNonLocalAttr(
                    expr: String,
                    x: Token,
                    y: Token,
                    rhs: Token,
                ) {
                    events.add("SET_NONLOCAL(${x.text}::${y.text}=${rhs.text})")
                }

                override fun nonLocalAttr(
                    expr: String,
                    x: Token,
                    y: Token,
                ) {
                    events.add("NONLOCAL(${x.text}::${y.text})")
                }

                override fun text(text: String) {
                    events.add("TEXT($text)")
                }
            }

        val actionText = "{ /* \$ignoredBlock */ // \$ignoredLine\n \$res = 42; \\\$escaped \$s::count = \$tok.line; }"
        val splitter = ActionSplitter(ANTLRInputStream(actionText), listener)
        val tokens = splitter.getActionTokens()
        assertEquals(9, tokens.size)
        assertEquals(ActionSplitter.COMMENT, tokens[1].type)
        assertEquals(ActionSplitter.LINE_COMMENT, tokens[3].type)
        assertEquals(ActionSplitter.SET_ATTR, tokens[5].type)
        assertEquals(ActionSplitter.SET_NONLOCAL_ATTR, tokens[7].type)
        assertEquals(
            listOf(
                "TEXT({ )",
                "TEXT(/* \$ignoredBlock */)",
                "TEXT( )",
                "TEXT(// \$ignoredLine\n)",
                "TEXT( )",
                "SET(res= 42)",
                "TEXT( \$escaped )",
                "SET_NONLOCAL(s::count= \$tok.line)",
                "TEXT( })",
            ),
            events,
        )
    }

    companion object {
        private val EXPRS: List<String> =
            listOf(
                "foo",
                "['foo'<${ActionSplitter.TEXT}>]",
                "\$x",
                "['\$x'<${ActionSplitter.ATTR}>]",
                "\\\$x",
                "['\\\$x'<${ActionSplitter.TEXT}>]",
                "\$x.y",
                "['\$x.y'<${ActionSplitter.QUALIFIED_ATTR}>]",
                "\$ID.text",
                "['\$ID.text'<${ActionSplitter.QUALIFIED_ATTR}>]",
                "\$ID",
                "['\$ID'<${ActionSplitter.ATTR}>]",
                "\$ID.getText()",
                "['\$ID'<${ActionSplitter.ATTR}>, '.getText()'<${ActionSplitter.TEXT}>]",
                "\$ID.text = \"test\";",
                "['\$ID.text'<${ActionSplitter.QUALIFIED_ATTR}>, ' = \"test\";'<${ActionSplitter.TEXT}>]",
                "\$a.line == \$b.line",
                "['\$a.line'<${ActionSplitter.QUALIFIED_ATTR}>, ' == '<${ActionSplitter.TEXT}>, '\$b.line'<${ActionSplitter.QUALIFIED_ATTR}>]",
                "\$r.tree",
                "['\$r.tree'<${ActionSplitter.QUALIFIED_ATTR}>]",
                "foo \$a::n bar",
                "['foo '<${ActionSplitter.TEXT}>, '\$a::n'<${ActionSplitter.NONLOCAL_ATTR}>, ' bar'<${ActionSplitter.TEXT}>]",
                "\$rule::x;",
                "['\$rule::x'<${ActionSplitter.NONLOCAL_ATTR}>, ';'<${ActionSplitter.TEXT}>]",
                "\$field::x = \$field.st;",
                "['\$field::x = \$field.st;'<${ActionSplitter.SET_NONLOCAL_ATTR}>]",
                "\$foo.get(\"ick\");",
                "['\$foo'<${ActionSplitter.ATTR}>, '.get(\"ick\");'<${ActionSplitter.TEXT}>]",
            )

        private fun getActionChunks(a: String): List<String> {
            val chunks = ArrayList<String>()
            val splitter = ActionSplitter(ANTLRInputStream(a), BlankActionSplitterListener())
            var t = splitter.nextToken()
            while (t.type != Token.EOF) {
                chunks.add("'${t.text}'<${t.type}>")
                t = splitter.nextToken()
            }
            return chunks
        }
    }
}
