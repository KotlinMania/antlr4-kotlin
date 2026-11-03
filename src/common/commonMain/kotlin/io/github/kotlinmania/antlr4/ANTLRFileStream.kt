/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

/**
 * This is an [ANTLRInputStream] that is loaded from a file all at once
 * when you construct the object.
 *
 */

@Deprecated("as of 4.7 Please use {@link CharStreams} interface.")
class ANTLRFileStream(
    override var sourceName: String?,
    val encoding: String? = null,
) : ANTLRInputStream() {
    constructor(input: String?) : this(input, null)

    init {
        this.n = 0
    }
}
