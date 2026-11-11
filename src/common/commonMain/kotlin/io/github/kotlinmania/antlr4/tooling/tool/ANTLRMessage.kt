/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.Token

/**
 * Base diagnostic message emitted by the ANTLR 4 tool.
 */
open class ANTLRMessage internal constructor(
    val errorType: ErrorType,
    internal open val cause: Throwable?,
    var offendingToken: Token?,
    internal val argsList: List<Any?>,
) {
    var fileName: String? = null
    var line: Int = -1
    var charPosition: Int = -1

    constructor(errorType: ErrorType) : this(
        errorType = errorType,
        cause = null,
        offendingToken = null,
        argsList = emptyList(),
    )

    constructor(
        errorType: ErrorType,
        offendingToken: Token?,
    ) : this(
        errorType = errorType,
        cause = null,
        offendingToken = offendingToken,
        argsList = emptyList(),
    )

    internal constructor(
        errorType: ErrorType,
        offendingToken: Token?,
        argsList: List<Any?>,
    ) : this(
        errorType = errorType,
        cause = null,
        offendingToken = offendingToken,
        argsList = argsList,
    )

    internal fun getArgs(): List<Any?> = argsList

    fun renderMessageText(verbose: Boolean): String {
        val bindings = LinkedHashMap<String, Any?>()
        bindings["verbose"] = verbose
        for (i in argsList.indices) {
            val attr = if (i == 0) "arg" else "arg${i + 1}"
            bindings[attr] = argsList[i]
        }
        if (argsList.size < 2) {
            bindings["arg2"] = null
        }
        val errCause = cause
        if (errCause != null) {
            bindings["exception"] = errCause.message ?: errCause.toString()
            bindings["stackTrace"] = errCause.stackTraceToString().split('\n')
        } else {
            bindings["exception"] = null
            bindings["stackTrace"] = null
        }
        return MessageRenderer.renderTemplate(errorType.msg, bindings)
    }

    override fun toString(): String =
        "Message{" +
            "errorType=$errorType" +
            ", args=$argsList" +
            ", e=$cause" +
            ", fileName='$fileName'" +
            ", line=$line" +
            ", charPosition=$charPosition" +
            "}"
}
