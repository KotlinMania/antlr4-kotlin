/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

/** A generic graph used to topologically sort grammar dependencies. */
class Graph<T> {
    class Node<T>(var payload: T) {
        private val mutableEdges: MutableList<Node<T>> = mutableListOf()

        val edges: List<Node<T>>
            get() = mutableEdges

        fun addEdge(node: Node<T>) {
            if (node !in mutableEdges) {
                mutableEdges.add(node)
            }
        }

        override fun toString(): String = payload.toString()
    }

    private val nodes: MutableMap<T, Node<T>> = linkedMapOf()

    fun addEdge(from: T, to: T) {
        val fromNode = getNode(from)
        val toNode = getNode(to)
        fromNode.addEdge(toNode)
    }

    fun getNode(payload: T): Node<T> = nodes.getOrPut(payload) { Node(payload) }

    /**
     * Dependency-first postorder traversal. An edge from one node to another
     * means that the second node must occur before the first.
     */
    fun sort(): List<T> {
        val visited = linkedSetOf<Node<T>>()
        val sorted = mutableListOf<T>()
        while (visited.size < nodes.size) {
            val node = nodes.values.firstOrNull { it !in visited }
            if (node != null) {
                dfs(node, visited, sorted)
            }
        }
        return sorted
    }

    internal fun dfs(
        node: Node<T>,
        visited: MutableSet<Node<T>>,
        sorted: MutableList<T>,
    ) {
        if (node in visited) return
        visited.add(node)
        for (target in node.edges) {
            dfs(target, visited, sorted)
        }
        sorted.add(node.payload)
    }
}
