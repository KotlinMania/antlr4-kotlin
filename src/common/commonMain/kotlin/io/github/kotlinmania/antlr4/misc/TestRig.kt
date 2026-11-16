/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.misc

import io.github.kotlinmania.antlr4.tooling.gui.TestRigRunResult
import io.github.kotlinmania.antlr4.tooling.gui.TestRig as ToolTestRig

/**
 * Runtime entry point delegating to [io.github.kotlinmania.antlr4.tooling.gui.TestRig].
 * Prefer [io.github.kotlinmania.antlr4.tooling.gui.TestRig] directly when configuring
 * grammar registrations or programmatic parse-tree / PostScript execution.
 */
object TestRig {
    fun run(args: List<String>): List<TestRigRunResult> {
        val rig = ToolTestRig(args)
        return if (args.size >= 2) rig.process() else emptyList()
    }

    fun main(args: List<String>) {
        ToolTestRig.main(args)
    }
}
