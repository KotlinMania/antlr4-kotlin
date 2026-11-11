/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.ast

import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.WritableToken
import io.github.kotlinmania.antlr4.atn.ATNState
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.GrammarASTOwner

/**
 * Pure-Kotlin ANTLR4 grammar AST node backed directly by [Token] / [CommonToken].
 */
open class GrammarAST {
    var token: Token? = null
    var parent: GrammarAST? = null
    var childIndex: Int = -1

    internal var startIndex: Int = -1
    internal var stopIndex: Int = -1
    internal val childNodes: MutableList<GrammarAST> = ArrayList()

    /** Owning grammar context for diagnostics and lexer/parser disambiguation. */
    var g: GrammarASTOwner? = null

    /** Left edge of the corresponding ATN construct when built. */
    var atnState: ATNState? = null

    var textOverride: String? = null

    constructor()

    constructor(t: Token?) {
        this.token = t
    }

    constructor(node: GrammarAST) {
        this.token = node.token
        this.startIndex = node.tokenStartIndex
        this.stopIndex = node.tokenStopIndex
        this.g = node.g
        this.atnState = node.atnState
        this.textOverride = node.textOverride
    }

    constructor(type: Int) : this(CommonToken(type, ANTLRParserTypes.tokenName(type)))

    constructor(
        type: Int,
        t: Token?,
    ) : this(copyWithType(t, type))

    constructor(
        type: Int,
        t: Token?,
        text: String?,
    ) : this(copyWithTypeAndText(t, type, text))

    open val isNil: Boolean
        get() = token == null

    open var type: Int
        get() = token?.type ?: Token.INVALID_TYPE
        set(value) {
            ensureWritableToken().type = value
        }

    open var text: String
        get() {
            val overrideVal = textOverride
            if (overrideVal != null) return overrideVal
            return token?.text.orEmpty()
        }
        set(value) {
            ensureWritableToken().text = value
        }

    open val line: Int
        get() {
            val tokLine = token?.line ?: 0
            if (tokLine == 0) {
                return if (childNodes.isNotEmpty()) childNodes[0].line else 0
            }
            return tokLine
        }

    open val charPositionInLine: Int
        get() {
            val tok = token
            if (tok == null || tok.charPositionInLine == -1) {
                return if (childNodes.isNotEmpty()) childNodes[0].charPositionInLine else 0
            }
            return tok.charPositionInLine
        }

    var tokenStartIndex: Int
        get() {
            val tok = token
            if (startIndex == -1 && tok != null) {
                return tok.tokenIndex
            }
            return startIndex
        }
        set(value) {
            startIndex = value
        }

    var tokenStopIndex: Int
        get() {
            val tok = token
            if (stopIndex == -1 && tok != null) {
                return tok.tokenIndex
            }
            return stopIndex
        }
        set(value) {
            stopIndex = value
        }

    val children: List<GrammarAST>
        get() = childNodes

    val childCount: Int
        get() = childNodes.size

    fun getChild(i: Int): GrammarAST? =
        if (i in childNodes.indices) childNodes[i] else null

    fun addChild(t: GrammarAST?) {
        if (t == null) return
        require(t !== this) { "Cannot add child to itself" }
        if (t.isNil) {
            require(t.childNodes !== this.childNodes) { "attempt to add child list to itself" }
            for (c in t.childNodes) {
                val idx = childNodes.size
                childNodes.add(c)
                c.parent = this
                c.childIndex = idx
            }
        } else {
            val idx = childNodes.size
            childNodes.add(t)
            t.parent = this
            t.childIndex = idx
        }
    }

    fun addChildren(kids: List<GrammarAST>) {
        for (kid in kids) {
            addChild(kid)
        }
    }

    fun insertChild(
        i: Int,
        t: GrammarAST?,
    ) {
        if (i < 0 || i > childNodes.size) {
            throw IndexOutOfBoundsException("Index: $i, Size: ${childNodes.size}")
        }
        if (t == null) return
        if (t.isNil) {
            require(t.childNodes !== this.childNodes) { "attempt to insert child list into itself" }
            var insertAt = i
            for (c in t.childNodes) {
                childNodes.add(insertAt, c)
                c.parent = this
                insertAt++
            }
        } else {
            childNodes.add(i, t)
            t.parent = this
        }
        freshenParentAndChildIndexes(i)
    }

    fun setChild(
        i: Int,
        t: GrammarAST?,
    ) {
        if (t == null) return
        require(!t.isNil) { "Can't set single child to a list" }
        childNodes[i] = t
        t.parent = this
        t.childIndex = i
    }

    fun deleteChild(i: Int): GrammarAST? {
        if (i !in childNodes.indices) return null
        val killed = childNodes.removeAt(i)
        freshenParentAndChildIndexes(i)
        return killed
    }

    fun deleteChild(t: GrammarAST): Boolean {
        for (i in childNodes.indices) {
            if (childNodes[i] === t) {
                deleteChild(i)
                return true
            }
        }
        return false
    }

    fun replaceChildren(
        startChildIndex: Int,
        stopChildIndex: Int,
        t: GrammarAST?,
    ) {
        require(startChildIndex >= 0 && stopChildIndex < childNodes.size && startChildIndex <= stopChildIndex) {
            "replaceChildren invalid range $startChildIndex..$stopChildIndex (size=${childNodes.size})"
        }
        val newChildren: List<GrammarAST> =
            when {
                t == null -> emptyList()
                t.isNil -> t.childNodes.toList()
                else -> listOf(t)
            }
        val numToDelete = stopChildIndex - startChildIndex + 1
        repeat(numToDelete) {
            childNodes.removeAt(startChildIndex)
        }
        for (idx in newChildren.indices) {
            val child = newChildren[idx]
            childNodes.add(startChildIndex + idx, child)
            child.parent = this
        }
        freshenParentAndChildIndexes(startChildIndex)
    }

    fun freshenParentAndChildIndexes(offset: Int = 0) {
        for (i in offset until childNodes.size) {
            val c = childNodes[i]
            c.childIndex = i
            c.parent = this
        }
    }

    fun sanityCheckParentAndChildIndexes(
        expectedParent: GrammarAST? = null,
        expectedIndex: Int = -1,
    ) {
        require(expectedParent === this.parent) {
            "parents don't match; expected $expectedParent found ${this.parent}"
        }
        require(expectedIndex == this.childIndex) {
            "child indexes don't match; expected $expectedIndex found ${this.childIndex}"
        }
        for (i in childNodes.indices) {
            childNodes[i].sanityCheckParentAndChildIndexes(this, i)
        }
    }

    fun setUnknownTokenBoundaries() {
        if (childNodes.isEmpty()) {
            if (startIndex < 0 || stopIndex < 0) {
                val idx = token?.tokenIndex ?: -1
                startIndex = idx
                stopIndex = idx
            }
            return
        }
        for (child in childNodes) {
            child.setUnknownTokenBoundaries()
        }
        if (startIndex < 0 && stopIndex < 0) {
            startIndex = childNodes.first().tokenStartIndex
            stopIndex = childNodes.last().tokenStopIndex
        }
    }

    fun getFirstChildWithType(targetType: Int): GrammarAST? {
        for (child in childNodes) {
            if (child.type == targetType) return child
        }
        return null
    }

    fun getAllChildrenWithType(targetType: Int): List<GrammarAST> {
        val result = ArrayList<GrammarAST>()
        for (child in childNodes) {
            if (child.type == targetType) {
                result.add(child)
            }
        }
        return result
    }

    fun getNodesWithType(ttype: Int): List<GrammarAST> =
        getNodesWithType(IntervalSet.of(ttype))

    fun getNodesWithType(types: IntervalSet?): List<GrammarAST> {
        val nodes = ArrayList<GrammarAST>()
        val work = ArrayDeque<GrammarAST>()
        work.addLast(this)
        while (work.isNotEmpty()) {
            val current = work.removeFirst()
            if (types == null || types.contains(current.type)) {
                nodes.add(current)
            }
            for (child in current.childNodes) {
                work.addLast(child)
            }
        }
        return nodes
    }

    fun getNodesWithTypePreorderDFS(types: IntervalSet): List<GrammarAST> {
        val nodes = ArrayList<GrammarAST>()
        collectPreorderDFS(nodes, types)
        return nodes
    }

    private fun collectPreorderDFS(
        nodes: MutableList<GrammarAST>,
        types: IntervalSet,
    ) {
        if (types.contains(type)) {
            nodes.add(this)
        }
        for (child in childNodes) {
            child.collectPreorderDFS(nodes, types)
        }
    }

    fun getNodeWithTokenIndex(index: Int): GrammarAST? {
        val tok = token
        if (tok != null && tok.tokenIndex == index) {
            return this
        }
        for (child in childNodes) {
            val found = child.getNodeWithTokenIndex(index)
            if (found != null) return found
        }
        return null
    }

    fun getFirstDescendantWithType(targetType: Int): GrammarAST? {
        if (type == targetType) return this
        for (child in childNodes) {
            if (child.type == targetType) return child
            val d = child.getFirstDescendantWithType(targetType)
            if (d != null) return d
        }
        return null
    }

    fun getFirstDescendantWithType(types: IntervalSet): GrammarAST? {
        if (types.contains(type)) return this
        for (child in childNodes) {
            if (types.contains(child.type)) return child
            val d = child.getFirstDescendantWithType(types)
            if (d != null) return d
        }
        return null
    }

    val ancestors: List<GrammarAST>?
        get() {
            val immediateParent = parent ?: return null
            val list = ArrayList<GrammarAST>()
            var p: GrammarAST? = immediateParent
            while (p != null) {
                list.add(0, p)
                p = p.parent
            }
            return list
        }

    fun hasAncestor(ttype: Int): Boolean = getAncestor(ttype) != null

    fun getAncestor(ttype: Int): GrammarAST? {
        var p = parent
        while (p != null) {
            if (p.type == ttype) return p
            p = p.parent
        }
        return null
    }

    val outermostAltNode: AltAST?
        get() {
            val p = parent
            if (this is AltAST && p?.parent is RuleAST) {
                return this
            }
            return p?.outermostAltNode
        }

    fun getAltLabel(): String? {
        val anc = ancestors ?: return null
        for (i in anc.size - 1 downTo 0) {
            val p = anc[i]
            if (p is AltAST) {
                val explicitLabel = p.altLabel
                if (explicitLabel != null) return explicitLabel.text
                val lrInfo = p.leftRecursiveAltInfo
                if (lrInfo != null) return lrInfo.altLabel
            }
        }
        return null
    }

    open fun dupNode(): GrammarAST = GrammarAST(this)

    fun dupTree(): GrammarAST = dupTreeRecursive(null)

    private fun dupTreeRecursive(parentNode: GrammarAST?): GrammarAST {
        val copy = dupNode()
        copy.childIndex = childIndex
        copy.parent = parentNode
        for (child in childNodes) {
            copy.addChild(child.dupTreeRecursive(copy))
        }
        return copy
    }

    fun toTokenString(): String {
        val buf = StringBuilder()
        appendTokenString(buf)
        return buf.toString()
    }

    private fun appendTokenString(buf: StringBuilder) {
        if (isNil) {
            for (child in childNodes) {
                child.appendTokenString(buf)
            }
            return
        }
        buf.append(' ').append(text)
        if (childNodes.isNotEmpty()) {
            buf.append(" DOWN")
            for (child in childNodes) {
                child.appendTokenString(buf)
            }
            buf.append(" UP")
        }
    }

    open fun toStringTree(): String {
        if (childNodes.isEmpty()) {
            return toString()
        }
        val buf = StringBuilder()
        if (!isNil) {
            buf.append('(')
            buf.append(toString())
            buf.append(' ')
        }
        for (i in childNodes.indices) {
            if (i > 0) buf.append(' ')
            buf.append(childNodes[i].toStringTree())
        }
        if (!isNil) {
            buf.append(')')
        }
        return buf.toString()
    }

    override fun toString(): String {
        if (isNil) return "nil"
        if (type == Token.INVALID_TYPE) return "<errornode>"
        val txt = text
        if (txt.isNotEmpty()) return txt
        return ANTLRParserTypes.tokenName(type)
    }

    open fun visit(v: GrammarASTVisitor): Any? = v.visit(this)

    private fun ensureWritableToken(): WritableToken {
        val current = token
        if (current is WritableToken) {
            return current
        }
        val created = if (current != null) CommonToken(current) else CommonToken(Token.INVALID_TYPE)
        token = created
        return created
    }

    companion object {
        internal fun copyWithType(
            t: Token?,
            type: Int,
        ): CommonToken {
            val copied = if (t != null) CommonToken(t) else CommonToken(type, ANTLRParserTypes.tokenName(type))
            copied.type = type
            return copied
        }

        internal fun copyWithTypeAndText(
            t: Token?,
            type: Int,
            text: String?,
        ): CommonToken {
            val copied = if (t != null) CommonToken(t) else CommonToken(type, text)
            copied.type = type
            copied.text = text
            return copied
        }
    }
}
