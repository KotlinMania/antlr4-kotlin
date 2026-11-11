/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

/** Identifies which output file slice of a recognizer is being generated. */
enum class SourceType {
    HEADER,
    SOURCE,
    SOURCE_LEAN,
    SOURCE_DFA,
    SOURCE_CONTEXTS,
}
