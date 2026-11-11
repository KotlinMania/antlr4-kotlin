/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.gui

import io.github.kotlinmania.antlr4.tree.Tree
import io.github.kotlinmania.antlr4.tree.Trees as RuntimeTrees

fun interface TreeTextProvider {
    fun getText(node: Tree?): String
}

class DefaultTreeTextProvider(
    private val ruleNames: List<String>?,
) : TreeTextProvider {
    override fun getText(node: Tree?): String {
        if (node == null) return "null"
        return RuntimeTrees.getNodeText(node, ruleNames).orEmpty()
    }
}
