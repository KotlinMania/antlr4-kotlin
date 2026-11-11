/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNSerializer
import io.github.kotlinmania.antlr4.misc.IntegerList
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Target-neutral integer-array representation of a serialized [ATN]. */
open class SerializedATN(
    factory: OutputModelFactory,
    atn: ATN,
) : OutputModelObject(factory) {
    internal val rawData: IntegerList = ATNSerializer.getSerialized(atn)

    val serializedInts: IntArray = rawData.toArray()

    val serialized: List<String> = List(serializedInts.size) { idx -> serializedInts[idx].toString() }
}
