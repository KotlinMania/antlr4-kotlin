/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNDeserializer
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** 16-bit UTF-16 string-segmented ATN serialization model for targets with string-constant length limits. */
class SerializedJavaATN(
    factory: OutputModelFactory,
    atn: ATN,
) : SerializedATN(factory, atn) {
    val segments: List<List<String>>
    val serializedAsString: List<String>

    init {
        val data16 = ATNDeserializer.encodeIntsWith16BitWords(rawData)
        val size = data16.size()
        val target = factory.generator.target
        val segmentLimit = (65535 - 1) / MAX_UTF8_BYTES_PER_CHAR

        val builtSegments = ArrayList<List<String>>()
        var i = 0
        while (i < size) {
            val currentSegment = ArrayList<String>()
            val end = minOf(size, i + segmentLimit)
            while (i < end) {
                val word = data16.get(i)
                if (end < size && i + 1 == end && (word and 0x8000) != 0) {
                    // Never split a 2-word encoded 31-bit value across segment boundaries.
                    break
                }
                currentSegment.add(target.encodeInt16AsCharEscape(word))
                i++
            }
            builtSegments.add(currentSegment)
        }
        segments = builtSegments
        serializedAsString = if (builtSegments.isNotEmpty()) builtSegments[0] else emptyList()
    }

    companion object {
        private const val MAX_UTF8_BYTES_PER_CHAR: Int = 3
    }
}
