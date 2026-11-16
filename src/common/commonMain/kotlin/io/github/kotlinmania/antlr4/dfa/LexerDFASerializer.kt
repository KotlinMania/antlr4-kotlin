/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.dfa

import io.github.kotlinmania.antlr4.VocabularyImpl

class LexerDFASerializer(
    dfa: DFA?,
) : DFASerializer(dfa!!, VocabularyImpl.EMPTY_VOCABULARY) {
    protected override fun getEdgeLabel(i: Int): String =
        StringBuilder("'")
            .append(i.toChar())
            .append("'")
            .toString()
}
