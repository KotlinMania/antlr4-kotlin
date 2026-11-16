/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

import kotlin.test.Test
import kotlin.test.assertEquals

/** Test topological sorting of grammar dependencies. */
class TopologicalSortTest {
    @Test
    fun fairlyLargeGraph() {
        val graph = Graph<String>()
        graph.addEdge("C", "F")
        graph.addEdge("C", "G")
        graph.addEdge("C", "A")
        graph.addEdge("C", "B")
        graph.addEdge("A", "D")
        graph.addEdge("A", "E")
        graph.addEdge("B", "E")
        graph.addEdge("D", "E")
        graph.addEdge("D", "F")
        graph.addEdge("F", "H")
        graph.addEdge("E", "F")
        assertEquals(listOf("H", "F", "G", "E", "D", "A", "B", "C"), graph.sort())
    }

    @Test
    fun cyclicGraph() {
        val graph = Graph<String>()
        graph.addEdge("A", "B")
        graph.addEdge("B", "C")
        graph.addEdge("C", "A")
        graph.addEdge("C", "D")
        assertEquals(listOf("D", "C", "B", "A"), graph.sort())
    }

    @Test
    fun repeatedEdges() {
        val graph = Graph<String>()
        graph.addEdge("A", "B")
        graph.addEdge("B", "C")
        graph.addEdge("A", "B")
        graph.addEdge("C", "D")
        assertEquals(listOf("D", "C", "B", "A"), graph.sort())
    }

    @Test
    fun simpleTokenDependence() {
        val graph = Graph<String>()
        graph.addEdge("Java.g4", "MyJava.tokens")
        graph.addEdge("Java.tokens", "Java.g4")
        graph.addEdge("Def.g4", "Java.tokens")
        graph.addEdge("Ref.g4", "Java.tokens")
        assertEquals(listOf("MyJava.tokens", "Java.g4", "Java.tokens", "Def.g4", "Ref.g4"), graph.sort())
    }

    @Test
    fun parserLexerCombo() {
        val graph = Graph<String>()
        graph.addEdge("JavaLexer.tokens", "JavaLexer.g4")
        graph.addEdge("JavaParser.g4", "JavaLexer.tokens")
        graph.addEdge("Def.g4", "JavaLexer.tokens")
        graph.addEdge("Ref.g4", "JavaLexer.tokens")
        assertEquals(listOf("JavaLexer.g4", "JavaLexer.tokens", "JavaParser.g4", "Def.g4", "Ref.g4"), graph.sort())
    }

    @Test
    fun emptyGraph() {
        assertEquals(emptyList(), Graph<String>().sort())
    }

    @Test
    fun nullablePayloadAndIsolatedNode() {
        val graph = Graph<String?>()
        graph.addEdge("dependent", null)
        graph.getNode("isolated")
        assertEquals(listOf(null, "dependent", "isolated"), graph.sort())
    }
}
