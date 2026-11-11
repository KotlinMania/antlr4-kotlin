/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

/**
 * Default [ANTLRToolListener] formatting messages through [ErrorManager] and writing them out.
 */
class DefaultToolListener(val errMgr: ErrorManager) : ANTLRToolListener {
    override fun info(msg: String?) {
        if (msg == null) return
        val formatted =
            if (errMgr.formatWantsSingleLineMessage()) {
                msg.replace('\n', ' ')
            } else {
                msg
            }
        println(formatted)
    }

    override fun error(msg: ANTLRMessage) {
        var outputMsg = errMgr.formatMessage(msg)
        if (errMgr.formatWantsSingleLineMessage()) {
            outputMsg = outputMsg.replace('\n', ' ')
        }
        ErrorManager.rawError(outputMsg)
    }

    override fun warning(msg: ANTLRMessage) {
        var outputMsg = errMgr.formatMessage(msg)
        if (errMgr.formatWantsSingleLineMessage()) {
            outputMsg = outputMsg.replace('\n', ' ')
        }
        ErrorManager.rawError(outputMsg)
    }
}
