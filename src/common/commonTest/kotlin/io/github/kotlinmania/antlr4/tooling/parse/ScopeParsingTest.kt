/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.ANTLRInputStream
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.GrammarASTOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ScopeParsingTest {
    private val dummyOwner: GrammarASTOwner =
        object : GrammarASTOwner {
            override val name: String = "T"
            override val fileName: String = "T.g4"
            override val isLexer: Boolean = false
            override val errorManager: ErrorManager = ErrorManager(null)
        }

    @Test
    fun testAllArgListPairs() {
        var i = 0
        while (i < ARG_PAIRS.size) {
            val input = ARG_PAIRS[i]
            val expected = ARG_PAIRS[i + 1]
            val dict = ScopeParser.parseTypedArgList(null, input, dummyOwner)
            val actual = dict.attributes.values.joinToString(", ") { it.toString() }
            assertEquals(expected, actual, "Failed on scope input: <$input>")
            i += 2
        }
    }

    @Test
    fun testActionASTTokenLineAndColumnLocations() {
        val grammarText =
            "grammar T;\n" +
                "a[\n" +
                "  int firstArg, // inline comment\n" +
                "  String secondArg = \"ok\"\n" +
                "] : 'x' ;\n"
        val stream = ANTLRInputStream(grammarText)
        stream.name = "T.g4"
        val lexer = ANTLRLexer(stream)
        val tokens = CommonTokenStream(lexer)
        val parser = ANTLRParser(tokens)
        val root = checkNotNull(parser.grammarSpec().tree)
        val rulesNode = checkNotNull(root.getFirstChildWithType(ANTLRParserTypes.RULES))
        val ruleA = checkNotNull(rulesNode.getChild(0))
        val argAction = ruleA.getFirstChildWithType(ANTLRParserTypes.ARG_ACTION) as? ActionAST
        assertNotNull(argAction)

        val dict = ScopeParser.parseTypedArgList(argAction, argAction.text, dummyOwner)
        val first = dict.get("firstArg")
        val second = dict.get("secondArg")
        assertNotNull(first)
        assertNotNull(second)
        assertEquals("int", first.type)
        assertEquals("String", second.type)
        assertEquals("\"ok\"", second.initValue)

        val firstTok = first.token
        val secondTok = second.token
        assertNotNull(firstTok)
        assertNotNull(secondTok)
        assertEquals(3, firstTok.line)
        assertEquals(6, firstTok.charPositionInLine)
        assertEquals(4, secondTok.line)
        assertEquals(9, secondTok.charPositionInLine)
    }

    companion object {
        private val ARG_PAIRS: List<String> =
            listOf(
                "",
                "",
                " ",
                "",
                "int i",
                "i:int",
                "int[] i, int j[]",
                "i:int[], j:int []",
                "Map<A,B>[] i, int j[]",
                "i:Map<A,B>[], j:int []",
                "Map<A,List<B>>[] i",
                "i:Map<A,List<B>>[]",
                "int i = 34+a[3], int j[] = new int[34]",
                "i:int=34+a[3], j:int []=new int[34]",
                "char *[3] foo = {1,2,3}",
                "foo:char *[3]={1,2,3}",
                "String[] headers",
                "headers:String[]",
                "std::vector<std::string> x",
                "x:std::vector<std::string>",
                "i",
                "i",
                "i,j",
                "i, j",
                "i\t,j, k",
                "i, j, k",
                "x: int",
                "x:int",
                "x :int",
                "x:int",
                "x:int",
                "x:int",
                "x:int=3",
                "x:int=3",
                "r:Rectangle=Rectangle(fromLength: 6, fromBreadth: 12)",
                "r:Rectangle=Rectangle(fromLength: 6, fromBreadth: 12)",
                "p:pointer to int",
                "p:pointer to int",
                "a: array[3] of int",
                "a:array[3] of int",
                "a \t:\tfunc(array[3] of int)",
                "a:func(array[3] of int)",
                "x:int, y:float",
                "x:int, y:float",
                "x:T?, f:func(array[3] of int), y:int",
                "x:T?, f:func(array[3] of int), y:int",
                "float64 x = 3",
                "x:float64=3",
                "map[string]int x",
                "x:map[string]int",
            )
    }
}
