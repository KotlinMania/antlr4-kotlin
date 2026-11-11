/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.misc.Utils

/**
 * This is an [ANTLRInputStream] that is loaded from a file all at once
 * when you construct the object. Prefer [CharStreams] in new code.
 */
class ANTLRFileStream(
    override var sourceName: String?,
    val encoding: String? = null,
) : ANTLRInputStream() {
    constructor(input: String?) : this(input, null)

    init {
        load(sourceName, encoding)
    }

    fun load(
        fileName: String?,
        encoding: String? = null,
    ) {
        if (fileName == null) {
            this.data = CharArray(0)
            this.n = 0
            this.p = 0
            return
        }
        this.sourceName = fileName
        this.name = fileName
        this.data = Utils.readFile(fileName, encoding)
        this.n = this.data.size
        this.p = 0
    }
}
