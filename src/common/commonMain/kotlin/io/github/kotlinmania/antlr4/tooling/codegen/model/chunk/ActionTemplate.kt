/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen.model.chunk

import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.StructDecl

/** Identifies synthesized left-recursive rule actions injected by `OutputModelController`. */
enum class LeftRecursiveActionKind {
    REPLACE_CONTEXT,
    SET_STOP_TOKEN,
    SET_PREV_CTX,
    ALT_START,
    LABELED_ALT_START,
}

/** Strongly-typed descriptor for synthesized left-recursive context management actions. */
class LeftRecursiveActionSpec(
    val kind: LeftRecursiveActionKind,
    val ctxName: String? = null,
    val ruleName: String? = null,
    val currentAltLabel: String? = null,
    val label: String? = null,
    val isListLabel: Boolean = false,
)

/** Synthesized target-template action chunk for left-recursive rule lowering. */
class ActionTemplate(
    ctx: StructDecl?,
    val spec: LeftRecursiveActionSpec,
) : ActionChunk(ctx)
