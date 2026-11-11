/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Reports and recovers from a `NoViableAltException` at an alternative block. */
class ThrowNoViableAlt(
    factory: OutputModelFactory,
    ast: GrammarAST,
    expectSet: IntervalSet,
) : ThrowRecognitionException(factory, ast, expectSet)
