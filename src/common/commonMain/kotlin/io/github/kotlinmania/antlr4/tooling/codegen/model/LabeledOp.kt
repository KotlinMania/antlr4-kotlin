/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model

import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.Decl

/** Rule element operation capable of capturing matched tokens or contexts into labels (`x=` / `xs+=`). */
interface LabeledOp {
    val labels: List<Decl>

    fun addLabel(decl: Decl)
}
