/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.misc

import kotlin.time.Clock

class LogManager {
    class Record(
        val sequence: Int,
        val timestamp: String,
        val location: String,
        val component: String?,
        val msg: String?,
    ) {
        override fun toString(): String = "$timestamp $component $location $msg"
    }

    private val mutableRecords: MutableList<Record> = mutableListOf()

    val records: List<Record>
        get() = mutableRecords

    fun log(
        component: String?,
        msg: String?,
    ) {
        val timestamp = Clock.System.now().toString()
        val callerLine =
            Throwable()
                .stackTraceToString()
                .lineSequence()
                .drop(1)
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.contains("LogManager") }
                ?: "LogManager:0"
        mutableRecords.add(Record(mutableRecords.size, timestamp, callerLine, component, msg))
    }

    fun log(msg: String?) {
        log(null, msg)
    }

    fun save(filename: String) {
        Utils.writeFile(filename, toString())
    }

    fun save(): String {
        val compactTimestamp =
            Clock.System
                .now()
                .toString()
                .substringBefore('.')
                .replace('T', '-')
                .replace(':', '.')
                .removeSuffix("Z")
        val defaultFilename = "./antlr-$compactTimestamp.log"
        save(defaultFilename)
        return defaultFilename
    }

    override fun toString(): String {
        if (mutableRecords.isEmpty()) return ""
        val buf = StringBuilder()
        for (record in mutableRecords) {
            buf.append(record)
            buf.append('\n')
        }
        return buf.toString()
    }

    companion object {
        fun main(args: List<String>) {
            val mgr = LogManager()
            mgr.log("atn", "test msg")
            mgr.log("dfa", "test msg 2")
            println(mgr)
            mgr.save()
        }
    }
}
