/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject
import io.github.kotlinmania.antlr4.tooling.tool.Tool

/**
 * Pure-Kotlin, reflection-free walker converting an [OutputModelObject] hierarchy into
 * [GeneratedOutput] for the selected [Target].
 */
class OutputModelWalker(
    val tool: Tool,
    val target: Target,
) {
    fun walk(
        omo: OutputModelObject,
        header: Boolean = false,
    ): GeneratedOutput {
        val text =
            when (target.language) {
                "Java" -> JavaCodegenEmitter.render(omo)
                else -> KotlinCodegenEmitter.render(omo)
            }
        return GeneratedOutput(text)
    }
}
