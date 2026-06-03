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

    protected var records: MutableList<Record>? = null

    fun log(
        component: String?,
        msg: String?,
    ) {
        if (records == null) {
            records = mutableListOf()
        }
        records!!.add(Record(records!!.size, component, msg))
    }

    fun log(msg: String?) {
        log(null, msg)
    }

    override fun toString(): String {
        val currentRecords = records ?: return ""
        return currentRecords.joinToString("\n")
    }
}
