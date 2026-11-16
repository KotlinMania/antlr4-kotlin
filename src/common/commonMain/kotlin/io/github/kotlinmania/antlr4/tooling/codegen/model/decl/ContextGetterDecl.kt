/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.decl

import io.github.kotlinmania.antlr4.misc.MurmurHash
import io.github.kotlinmania.antlr4.tooling.codegen.OutputModelFactory

/** Base declaration node for a `ParserRuleContext` token or subrule getter method. */
abstract class ContextGetterDecl(
    factory: OutputModelFactory,
    name: String,
) : Decl(factory, name) {
    open fun getArgType(): String = ""

    override fun hashCode(): Int {
        var hash = MurmurHash.initialize()
        hash = MurmurHash.update(hash, name)
        hash = MurmurHash.update(hash, getArgType())
        return MurmurHash.finish(hash, 2)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ContextGetterDecl
        return name == other.name && getArgType() == other.getArgType()
    }
}
