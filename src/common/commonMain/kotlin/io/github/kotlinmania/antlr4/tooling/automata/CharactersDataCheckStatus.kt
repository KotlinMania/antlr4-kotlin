/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.automata

/**
 * Diagnostic result of checking character set/range collisions and mixed-case ASCII ranges.
 */
class CharactersDataCheckStatus(
    val collision: Boolean,
    val notImpliedCharacters: Boolean,
)
