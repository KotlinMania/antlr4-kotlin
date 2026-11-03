/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.misc

import io.github.kotlinmania.antlr4.Vocabulary
import io.github.kotlinmania.antlr4.VocabularyImpl
import io.github.kotlinmania.antlr4.atn.ATN
import io.github.kotlinmania.antlr4.atn.ATNDeserializer

object InterpreterDataReader {
    fun parse(data: String): InterpreterData = parse(StringLineReader(data))

    fun parse(reader: LineReader): InterpreterData {
        val result = InterpreterData()
        val ruleNames = mutableListOf<String>()
        result.ruleNames = ruleNames

        val literalNames = mutableListOf<String>()
        val symbolicNames = mutableListOf<String>()

        reader.expect("token literal names:")
        while (true) {
            val line = reader.readLineOrNull()
            if (line.isNullOrEmpty()) break
            literalNames.add(if (line == "null") "" else line)
        }

        reader.expect("token symbolic names:")
        while (true) {
            val line = reader.readLineOrNull()
            if (line.isNullOrEmpty()) break
            symbolicNames.add(if (line == "null") "" else line)
        }

        result.vocabulary =
            VocabularyImpl(
                literalNames.toTypedArray(),
                symbolicNames.toTypedArray(),
            )

        reader.expect("rule names:")
        while (true) {
            val line = reader.readLineOrNull()
            if (line.isNullOrEmpty()) break
            ruleNames.add(line)
        }

        var line = reader.readLineOrNull() ?: throw RuntimeException("Unexpected data entry")
        if (line == "channel names:") {
            val channels = mutableListOf<String>()
            result.channels = channels
            while (true) {
                val channel = reader.readLineOrNull()
                if (channel.isNullOrEmpty()) break
                channels.add(channel)
            }

            reader.expect("mode names:")
            val modes = mutableListOf<String>()
            result.modes = modes
            while (true) {
                val mode = reader.readLineOrNull()
                if (mode.isNullOrEmpty()) break
                modes.add(mode)
            }
            line = reader.readLineOrNull() ?: throw RuntimeException("Unexpected data entry")
        }

        if (line != "atn:") throw RuntimeException("Unexpected data entry")
        line = reader.readLineOrNull() ?: throw RuntimeException("Unexpected data entry")
        val elements = line.substring(1, line.length - 1).split(",")
        val serializedATN = IntArray(elements.size)

        for (i in elements.indices) {
            serializedATN[i] = elements[i].trim().toInt()
        }

        result.atn = ATNDeserializer().deserialize(serializedATN)
        return result
    }

    private fun LineReader.expect(expected: String) {
        val line = readLineOrNull() ?: throw RuntimeException("Unexpected data entry")
        if (line != expected) throw RuntimeException("Unexpected data entry")
    }

    class InterpreterData {
        var atn: ATN? = null
        var vocabulary: Vocabulary? = null
        var ruleNames: List<String>? = null
            internal set
        var channels: List<String>? = null
            internal set
        var modes: List<String>? = null
            internal set
    }
}
