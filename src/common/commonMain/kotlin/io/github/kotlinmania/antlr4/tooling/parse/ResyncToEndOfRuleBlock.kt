/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

/**
 * Internal control-flow signal used by [ANTLRParser] to unwind from a nested
 * rule element directly back to the enclosing `ruleBlock` or `lexerRuleBlock`
 * after detecting a missing semicolon.
 */
internal class ResyncToEndOfRuleBlock : RuntimeException()
