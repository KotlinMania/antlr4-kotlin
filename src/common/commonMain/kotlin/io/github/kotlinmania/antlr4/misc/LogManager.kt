/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.misc

class LogManager {
    protected class Record(
        val sequence: Int,
        val component: String?,
        val msg: String?,
    ) {
        override fun toString(): String = "$sequence $component $msg"
    }

    protected val records: MutableList<Record> = mutableListOf()

    fun log(
        component: String?,
        msg: String?,
    ) {
        records.add(Record(records.size, component, msg))
    }

    fun log(msg: String?) {
        log(null, msg)
    }

    override fun toString(): String {
        if (records.isEmpty()) return ""
        return records.joinToString("\n")
    }
}
