/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.gui

import io.github.kotlinmania.antlr4.misc.Utils
import io.github.kotlinmania.antlr4.tree.ErrorNode
import io.github.kotlinmania.antlr4.tree.Tree

class TreePostScriptGenerator(
    ruleNames: List<String>?,
    val root: Tree?,
    fontName: String? = PostScriptDocument.DEFAULT_FONT,
    fontSize: Int = 11,
) {
    private class VariableExtentProvider(
        private val generator: TreePostScriptGenerator,
    ) : NodeExtentProvider {
        override fun getWidth(tree: Tree?): Double {
            val s = generator.getText(tree)
            return generator.doc.getWidth(s) + generator.nodeWidthPadding * 2
        }

        override fun getHeight(tree: Tree?): Double {
            val s = generator.getText(tree)
            val lineHeight =
                generator.doc.lineHeight + generator.nodeHeightPaddingAbove + generator.nodeHeightPaddingBelow
            val lines = s.split('\n')
            return lineHeight * lines.size
        }
    }

    var gapBetweenLevels: Double = 17.0
    var gapBetweenNodes: Double = 7.0
    var nodeWidthPadding: Int = 1
    var nodeHeightPaddingAbove: Int = 0
    var nodeHeightPaddingBelow: Int = 5

    var treeTextProvider: TreeTextProvider = DefaultTreeTextProvider(ruleNames)
    val doc: PostScriptDocument = PostScriptDocument(fontName, fontSize)
    val treeLayout: TreeLayout =
        TreeLayout(
            getTreeLayoutAdaptor(root),
            VariableExtentProvider(this),
            gapBetweenLevels,
            gapBetweenNodes,
        )

    constructor(
        ruleNames: List<String>?,
        root: Tree?,
    ) : this(ruleNames, root, PostScriptDocument.DEFAULT_FONT, 11)

    fun getTreeLayoutAdaptor(root: Tree?): TreeLayoutAdaptor = TreeLayoutAdaptor(root)

    val ps: String
        get() {
            generateEdges(treeLayout.tree.root)
            for (node in treeLayout.nodes) {
                generateNode(node)
            }
            doc.boundingBox(treeLayout.boundsWidth, treeLayout.boundsHeight)
            doc.close()
            return doc.ps
        }

    fun getPS(): String = ps

    private fun generateEdges(parent: Tree?) {
        if (!treeLayout.tree.isLeaf(parent)) {
            val parentBounds = treeLayout.getBoundsOfNode(parent)
            val x1 = parentBounds.centerX
            val y1 = parentBounds.y
            for (child in treeLayout.tree.getChildren(parent)) {
                val childBounds = treeLayout.getBoundsOfNode(child)
                val x2 = childBounds.centerX
                val y2 = childBounds.maxY
                doc.line(x1, y1, x2, y2)
                generateEdges(child)
            }
        }
    }

    private fun generateNode(t: Tree?) {
        val lines = getText(t).split('\n')
        val box = treeLayout.getBoundsOfNode(t)
        if (t is ErrorNode) {
            doc.highlight(box.x, box.y, box.width, box.height)
        }
        val x = box.x + nodeWidthPadding
        var y = box.y + nodeHeightPaddingBelow
        for (line in lines) {
            doc.text(line, x, y)
            y += doc.lineHeight
        }
    }

    internal fun getText(tree: Tree?): String =
        Utils.escapeWhitespace(treeTextProvider.getText(tree), false)
}
