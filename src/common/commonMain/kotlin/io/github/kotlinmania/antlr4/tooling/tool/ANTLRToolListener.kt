/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

/**
 * Listener receiving info, warning, and error notifications from the ANTLR 4 tool.
 */
interface ANTLRToolListener {
    fun info(msg: String?)

    fun error(msg: ANTLRMessage)

    fun warning(msg: ANTLRMessage)
}

/**
 * Context contract implemented by the ANTLR 4 tool for coordinating diagnostic dispatch.
 */
interface ToolErrorContext : ANTLRToolListener {
    val longMessages: Boolean
    val errMgr: ErrorManager
}
