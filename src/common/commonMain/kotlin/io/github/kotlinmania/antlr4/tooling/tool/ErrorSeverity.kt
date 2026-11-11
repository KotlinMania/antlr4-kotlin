/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

/**
 * Defines message severity levels and their display text.
 */
enum class ErrorSeverity(val text: String) {
    INFO("info"),
    WARNING("warning"),
    WARNING_ONE_OFF("warning"),
    ERROR("error"),
    ERROR_ONE_OFF("error"),
    FATAL("fatal"),
}
