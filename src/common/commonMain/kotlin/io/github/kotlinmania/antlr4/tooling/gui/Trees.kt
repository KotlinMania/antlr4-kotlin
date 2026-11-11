/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.gui

import io.github.kotlinmania.antlr4.Parser
import io.github.kotlinmania.antlr4.misc.Utils
import io.github.kotlinmania.antlr4.tree.Tree

object Trees {
    /** Save this parse tree as an EPS PostScript file. */
    fun save(
        t: Tree?,
        parser: Parser?,
        fileName: String,
    ) {
        save(t, parser?.ruleNames, fileName)
    }

    /** Save this parse tree as an EPS PostScript file using a specific font name and size. */
    fun save(
        t: Tree?,
        parser: Parser?,
        fileName: String,
        fontName: String,
        fontSize: Int,
    ) {
        save(t, parser?.ruleNames, fileName, fontName, fontSize)
    }

    /** Save this parse tree as an EPS PostScript file. */
    fun save(
        t: Tree?,
        ruleNames: List<String>?,
        fileName: String,
    ) {
        writePS(t, ruleNames, fileName)
    }

    /** Save this parse tree as an EPS PostScript file using a specific font name and size. */
    fun save(
        t: Tree?,
        ruleNames: List<String>?,
        fileName: String,
        fontName: String,
        fontSize: Int,
    ) {
        writePS(t, ruleNames, fileName, fontName, fontSize)
    }

    fun getPS(
        t: Tree?,
        ruleNames: List<String>?,
        fontName: String,
        fontSize: Int,
    ): String = TreePostScriptGenerator(ruleNames, t, fontName, fontSize).ps

    fun getPS(
        t: Tree?,
        ruleNames: List<String>?,
    ): String = getPS(t, ruleNames, "Helvetica", 11)

    fun writePS(
        t: Tree?,
        ruleNames: List<String>?,
        fileName: String,
        fontName: String,
        fontSize: Int,
    ) {
        val ps = getPS(t, ruleNames, fontName, fontSize)
        Utils.writeFile(fileName, ps)
    }

    fun writePS(
        t: Tree?,
        ruleNames: List<String>?,
        fileName: String,
    ) {
        writePS(t, ruleNames, fileName, "Helvetica", 11)
    }

    /** Print a whole tree in LISP form using [nodeTextProvider] for node labels. */
    fun toStringTree(
        t: Tree?,
        nodeTextProvider: TreeTextProvider,
    ): String {
        if (t == null) return "null"
        var s = Utils.escapeWhitespace(nodeTextProvider.getText(t), false)
        if (t.childCount == 0) return s
        val buf = StringBuilder()
        buf.append('(')
        s = Utils.escapeWhitespace(nodeTextProvider.getText(t), false)
        buf.append(s)
        buf.append(' ')
        for (i in 0 until t.childCount) {
            if (i > 0) buf.append(' ')
            buf.append(toStringTree(t.getChild(i), nodeTextProvider))
        }
        buf.append(')')
        return buf.toString()
    }
}
