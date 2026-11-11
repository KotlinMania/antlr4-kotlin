/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.tooling.tool.ANTLRMessage
import io.github.kotlinmania.antlr4.tooling.tool.ANTLRToolListener
import io.github.kotlinmania.antlr4.tooling.tool.Tool

/**
 * Pure-Kotlin [ANTLRToolListener] recording informational messages, warnings, and errors
 * emitted by [Tool] during `.g4` compilation and semantic analysis.
 */
class ToolErrorQueue(
    val tool: Tool? = null,
) : ANTLRToolListener {
    val infos: MutableList<String> = ArrayList()
    val errors: MutableList<ANTLRMessage> = ArrayList()
    val warnings: MutableList<ANTLRMessage> = ArrayList()
    val all: MutableList<Any> = ArrayList()

    override fun info(msg: String?) {
        if (msg != null) {
            infos.add(msg)
        }
    }

    override fun error(msg: ANTLRMessage) {
        errors.add(msg)
        all.add(msg)
    }

    override fun warning(msg: ANTLRMessage) {
        warnings.add(msg)
        all.add(msg)
    }

    fun error(msg: ErrorManagerFatalStub?) {
        if (msg != null) {
            all.add(msg)
        }
    }

    fun size(): Int = all.size + infos.size

    override fun toString(): String = toString(false)

    fun toString(rendered: Boolean): String {
        if (!rendered) {
            return all.joinToString("\n")
        }

        val activeTool = tool ?: throw IllegalStateException("No Tool instance is available.")
        val buf = StringBuilder()
        for (item in all) {
            if (item is ANTLRMessage) {
                buf.append(activeTool.errMgr.formatMessage(item))
            } else {
                buf.append(item.toString())
            }
            buf.append('\n')
        }
        return buf.toString()
    }

    class ErrorManagerFatalStub(
        val message: String,
    ) {
        override fun toString(): String = message
    }
}
