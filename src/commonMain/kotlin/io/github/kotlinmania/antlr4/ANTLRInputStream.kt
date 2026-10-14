@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package io.github.kotlinmania.antlr4

import io.github.kotlinmania.antlr4.misc.Interval

// Upstream deprecates this in favor of the CharStreams factory. This
// char-array-backed stream remains available for generated code and tests that
// expect the older UTF-16 code-unit behavior.
open class ANTLRInputStream : CharStream {
    protected var data: CharArray
    protected var n: Int = 0
    protected var p: Int = 0
    var name: String? = null

    constructor() {
        this.data = CharArray(0)
    }

    constructor(input: String) {
        this.data = input.toCharArray()
        this.n = input.length
    }

    constructor(data: CharArray, numberOfActualCharsInArray: Int) {
        this.data = data
        this.n = numberOfActualCharsInArray
    }

    fun reset() {
        p = 0
    }

    override fun consume() {
        if (p >= n) {
            assert(LA(1) == IntStream.EOF)
            throw IllegalStateException("cannot consume EOF")
        }
        if (p < n) {
            p++
        }
    }

    override fun LA(i: Int): Int {
        var offset = i
        if (offset == 0) return 0
        if (offset < 0) {
            offset++
            if ((p + offset - 1) < 0) return IntStream.EOF
        }
        if ((p + offset - 1) >= n) return IntStream.EOF
        return data[p + offset - 1].code
    }

    fun LT(i: Int): Int = LA(i)

    override fun index(): Int = p

    override fun size(): Int = n

    override fun mark(): Int = -1

    override fun release(marker: Int) {}

    override fun seek(index: Int) {
        var targetIndex = index
        if (targetIndex <= p) {
            p = targetIndex
            return
        }
        targetIndex = minOf(targetIndex, n)
        while (p < targetIndex) {
            consume()
        }
    }

    override fun getText(interval: Interval?): String? {
        val start = interval?.a ?: 0
        var stop = interval?.b ?: 0
        if (stop >= n) stop = n - 1
        val count = stop - start + 1
        if (start >= n) return ""
        return data.concatToString(start, start + count)
    }

    override val sourceName: String?
        get() {
            val n = name
            if (n.isNullOrEmpty()) {
                return IntStream.UNKNOWN_SOURCE_NAME
            }
            return name
        }

    override fun toString(): String = data.concatToString()

    companion object {
        const val READ_BUFFER_SIZE: Int = 1024
        const val INITIAL_BUFFER_SIZE: Int = 1024
    }
}
