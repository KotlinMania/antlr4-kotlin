/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

/**
 * Generic tool-level diagnostic message without a specific token location.
 */
class ToolMessage internal constructor(
    errorType: ErrorType,
    cause: Throwable?,
    argsList: List<Any?>,
) : ANTLRMessage(
        errorType = errorType,
        cause = cause,
        offendingToken = null,
        argsList = argsList,
    ) {
    constructor(errorType: ErrorType) : this(
        errorType = errorType,
        cause = null,
        argsList = emptyList(),
    )

    internal constructor(
        errorType: ErrorType,
        argsList: List<Any?>,
    ) : this(
        errorType = errorType,
        cause = null,
        argsList = argsList,
    )
}
