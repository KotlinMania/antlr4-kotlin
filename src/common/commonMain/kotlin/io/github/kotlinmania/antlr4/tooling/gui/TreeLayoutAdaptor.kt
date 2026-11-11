/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.gui

import io.github.kotlinmania.antlr4.tree.Tree
import kotlin.math.ceil
import kotlin.math.max

class NodeBounds(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    val centerX: Double
        get() = x + width / 2.0

    val maxY: Double
        get() = y + height
}

interface NodeExtentProvider {
    fun getWidth(tree: Tree?): Double

    fun getHeight(tree: Tree?): Double
}

/** Adaptor exposing ANTLR [Tree] hierarchies to [TreeLayout]. */
class TreeLayoutAdaptor(
    val root: Tree?,
) {
    private class AntlrTreeChildrenIterable(
        private val tree: Tree,
    ) : Iterable<Tree> {
        override fun iterator(): Iterator<Tree> =
            object : Iterator<Tree> {
                private var index = 0

                override fun hasNext(): Boolean = tree.childCount > index

                override fun next(): Tree {
                    if (!hasNext()) throw NoSuchElementException()
                    return tree.getChild(index++) ?: throw NoSuchElementException()
                }
            }
    }

    private class AntlrTreeChildrenReverseIterable(
        private val tree: Tree,
    ) : Iterable<Tree> {
        override fun iterator(): Iterator<Tree> =
            object : Iterator<Tree> {
                private var index = tree.childCount

                override fun hasNext(): Boolean = index > 0

                override fun next(): Tree {
                    if (!hasNext()) throw NoSuchElementException()
                    return tree.getChild(--index) ?: throw NoSuchElementException()
                }
            }
    }

    fun isLeaf(node: Tree?): Boolean = node == null || node.childCount == 0

    fun isChildOfParent(
        node: Tree,
        parentNode: Tree?,
    ): Boolean = node.parent === parentNode

    fun getLastChild(parentNode: Tree): Tree? =
        if (parentNode.childCount > 0) parentNode.getChild(parentNode.childCount - 1) else null

    fun getFirstChild(parentNode: Tree): Tree? =
        if (parentNode.childCount > 0) parentNode.getChild(0) else null

    fun getChildrenReverse(node: Tree?): Iterable<Tree> =
        if (node == null) emptyList() else AntlrTreeChildrenReverseIterable(node)

    fun getChildren(node: Tree?): Iterable<Tree> =
        if (node == null) emptyList() else AntlrTreeChildrenIterable(node)
}

/**
 * Pure-Kotlin tidy contour layout for parse trees, placing the root at the top
 * and children downward in PostScript bottom-left coordinates.
 */
class TreeLayout(
    val tree: TreeLayoutAdaptor,
    private val extentProvider: NodeExtentProvider,
    val gapBetweenLevels: Double = 17.0,
    val gapBetweenNodes: Double = 7.0,
) {
    private class LayoutEntry(
        val node: Tree,
        val depth: Int,
        val width: Double,
        val height: Double,
        val children: List<LayoutEntry>,
    ) {
        var x: Double = 0.0
        var y: Double = 0.0
        var leftContour: DoubleArray = DoubleArray(0)
        var rightContour: DoubleArray = DoubleArray(0)
    }

    private val orderedNodes: MutableList<Tree> = mutableListOf()
    private val identityBuckets: MutableMap<Int, MutableList<Pair<Tree, NodeBounds>>> = mutableMapOf()

    val boundsWidth: Int
    val boundsHeight: Int

    init {
        val rootNode = tree.root
        if (rootNode == null) {
            boundsWidth = 0
            boundsHeight = 0
        } else {
            val levelHeights = mutableListOf<Double>()
            val rootEntry = buildEntries(rootNode, 0, levelHeights)
            computeHorizontalSubtree(rootEntry)

            val levelCount = levelHeights.size
            var totalHeight = 0.0
            for (d in 0 until levelCount) {
                totalHeight += levelHeights[d]
                if (d > 0) {
                    totalHeight += gapBetweenLevels
                }
            }

            val levelTops = DoubleArray(levelCount)
            var cursorTop = totalHeight
            for (d in 0 until levelCount) {
                levelTops[d] = cursorTop
                cursorTop -= levelHeights[d] + gapBetweenLevels
            }

            var maxRight = 0.0
            assignAbsoluteBounds(rootEntry, 0.0, levelTops) { rightEdge ->
                if (rightEdge > maxRight) {
                    maxRight = rightEdge
                }
            }
            boundsWidth = ceil(maxRight).toInt()
            boundsHeight = ceil(totalHeight).toInt()
        }
    }

    val nodes: List<Tree>
        get() = orderedNodes

    fun getBoundsOfNode(node: Tree?): NodeBounds {
        if (node == null) return NodeBounds(0.0, 0.0, 0.0, 0.0)
        val bucket = identityBuckets[node.hashCode()]
        if (bucket != null) {
            for ((candidate, bounds) in bucket) {
                if (candidate === node) return bounds
            }
        }
        return NodeBounds(0.0, 0.0, 0.0, 0.0)
    }

    private fun buildEntries(
        node: Tree,
        depth: Int,
        levelHeights: MutableList<Double>,
    ): LayoutEntry {
        val w = extentProvider.getWidth(node)
        val h = extentProvider.getHeight(node)
        while (levelHeights.size <= depth) {
            levelHeights.add(0.0)
        }
        if (h > levelHeights[depth]) {
            levelHeights[depth] = h
        }
        val childEntries =
            if (tree.isLeaf(node)) {
                emptyList()
            } else {
                tree.getChildren(node).map { child -> buildEntries(child, depth + 1, levelHeights) }
            }
        return LayoutEntry(node, depth, w, h, childEntries)
    }

    private fun computeHorizontalSubtree(entry: LayoutEntry) {
        if (entry.children.isEmpty()) {
            entry.x = 0.0
            entry.leftContour = doubleArrayOf(0.0)
            entry.rightContour = doubleArrayOf(entry.width)
            return
        }

        for (child in entry.children) {
            computeHorizontalSubtree(child)
        }

        val childOffsets = DoubleArray(entry.children.size)
        var mergedLeft = entry.children[0].leftContour.copyOf()
        var mergedRight = entry.children[0].rightContour.copyOf()

        for (i in 1 until entry.children.size) {
            val child = entry.children[i]
            val sharedLevels = minOf(mergedRight.size, child.leftContour.size)
            var shift = 0.0
            for (lvl in 0 until sharedLevels) {
                val needed = mergedRight[lvl] + gapBetweenNodes - child.leftContour[lvl]
                if (needed > shift) {
                    shift = needed
                }
            }
            childOffsets[i] = shift

            val newMaxLevels = maxOf(mergedRight.size, child.rightContour.size)
            val nextLeft = DoubleArray(newMaxLevels)
            val nextRight = DoubleArray(newMaxLevels)
            for (lvl in 0 until newMaxLevels) {
                nextLeft[lvl] =
                    if (lvl < mergedLeft.size) {
                        mergedLeft[lvl]
                    } else {
                        child.leftContour[lvl] + shift
                    }
                nextRight[lvl] =
                    if (lvl < child.rightContour.size) {
                        max(
                            if (lvl < mergedRight.size) mergedRight[lvl] else Double.NEGATIVE_INFINITY,
                            child.rightContour[lvl] + shift,
                        )
                    } else {
                        mergedRight[lvl]
                    }
            }
            mergedLeft = nextLeft
            mergedRight = nextRight
        }

        val firstChild = entry.children.first()
        val lastChild = entry.children.last()
        val firstCenter = childOffsets.first() + firstChild.x + firstChild.width / 2.0
        val lastCenter = childOffsets.last() + lastChild.x + lastChild.width / 2.0
        val childrenMidpoint = (firstCenter + lastCenter) / 2.0

        var rootX = childrenMidpoint - entry.width / 2.0
        var childBaseShift = 0.0
        if (rootX < 0.0) {
            childBaseShift = -rootX
            rootX = 0.0
        }

        entry.x = rootX
        for (i in entry.children.indices) {
            entry.children[i].x += childOffsets[i] + childBaseShift
        }

        val totalLevels = mergedLeft.size + 1
        val fullLeft = DoubleArray(totalLevels)
        val fullRight = DoubleArray(totalLevels)
        fullLeft[0] = rootX
        fullRight[0] = rootX + entry.width
        for (lvl in mergedLeft.indices) {
            fullLeft[lvl + 1] = mergedLeft[lvl] + childBaseShift
            fullRight[lvl + 1] = mergedRight[lvl] + childBaseShift
        }
        entry.leftContour = fullLeft
        entry.rightContour = fullRight
    }

    private fun assignAbsoluteBounds(
        entry: LayoutEntry,
        parentOffsetX: Double,
        levelTops: DoubleArray,
        onRightEdge: (Double) -> Unit,
    ) {
        val absX = parentOffsetX + entry.x
        val absY = levelTops[entry.depth] - entry.height
        val bounds = NodeBounds(absX, absY, entry.width, entry.height)
        orderedNodes.add(entry.node)
        identityBuckets.getOrPut(entry.node.hashCode()) { mutableListOf() }.add(entry.node to bounds)
        onRightEdge(absX + entry.width)

        val childParentBaseX = absX - entry.x
        for (child in entry.children) {
            assignAbsoluteBounds(child, childParentBaseX, levelTops, onRightEdge)
        }
    }
}
