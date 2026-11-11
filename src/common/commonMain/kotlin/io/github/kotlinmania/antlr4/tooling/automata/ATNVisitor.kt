/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.automata

import io.github.kotlinmania.antlr4.atn.ATNState

/**
 * Simple cycle-safe ATN graph visitor starting from a root [ATNState].
 */
open class ATNVisitor {
    fun visit(s: ATNState) {
        visitRecursive(s, HashSet())
    }

    internal fun visitRecursive(
        s: ATNState,
        visited: MutableSet<Int>,
    ) {
        if (!visited.add(s.stateNumber)) return
        visitState(s)
        val n = s.numberOfTransitions
        for (i in 0 until n) {
            val t = s.transition(i)
            visitRecursive(t.target, visited)
        }
    }

    open fun visitState(s: ATNState) {
    }
}
