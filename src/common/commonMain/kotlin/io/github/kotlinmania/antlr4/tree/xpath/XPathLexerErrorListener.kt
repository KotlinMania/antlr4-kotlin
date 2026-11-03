package io.github.kotlinmania.antlr4.tree.xpath

import io.github.kotlinmania.antlr4.BaseErrorListener
import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Recognizer

object XPathLexerErrorListener : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?,
    ) {
    }
}
