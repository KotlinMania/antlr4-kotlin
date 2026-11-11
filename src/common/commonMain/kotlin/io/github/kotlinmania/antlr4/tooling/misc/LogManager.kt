/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.misc

import io.github.kotlinmania.antlr4.internal.withHostFileSystemOrNull
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.writeString
import kotlin.time.TimeSource

/**
 * Pure-Kotlin structured diagnostic log manager for the ANTLR 4 tool.
 */
class LogManager {
    class Record(
        val sequenceNumber: Long,
        val elapsedMicros: Long,
        val component: String?,
        val msg: String,
    ) {
        override fun toString(): String =
            buildString {
                append(sequenceNumber)
                append(" (+")
                append(elapsedMicros)
                append("us) ")
                if (component != null) {
                    append(component)
                    append(": ")
                }
                append(msg)
            }
    }

    private val startMark = TimeSource.Monotonic.markNow()
    private var nextSequence: Long = 1L
    private val mutableRecords: MutableList<Record> = ArrayList()

    val records: List<Record>
        get() = mutableRecords.toList()

    fun log(
        component: String?,
        msg: String,
    ) {
        val record =
            Record(
                sequenceNumber = nextSequence++,
                elapsedMicros = startMark.elapsedNow().inWholeMicroseconds,
                component = component,
                msg = msg,
            )
        mutableRecords.add(record)
    }

    fun log(msg: String) {
        log(null, msg)
    }

    fun save(filename: String) {
        val text = toString()
        withHostFileSystemOrNull {
            val path = Path(filename)
            val parent = path.parent
            if (parent != null && !SystemFileSystem.exists(parent)) {
                SystemFileSystem.createDirectories(parent)
            }
            SystemFileSystem.sink(path).buffered().use { sink ->
                sink.writeString(text)
            }
        }
    }

    fun save(): String {
        val defaultFilename = "./antlr-${mutableRecords.size}.log"
        save(defaultFilename)
        return defaultFilename
    }

    override fun toString(): String {
        if (mutableRecords.isEmpty()) return ""
        return mutableRecords.joinToString("\n", postfix = "\n") { it.toString() }
    }
}
