/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.Token

/**
 * Canonical token and AST node type constants for ANTLR4 `.g4` lexing, parsing, and AST walking.
 */
object ANTLRParserTypes {
    const val EOF: Int = -1
    const val INVALID_TYPE: Int = Token.INVALID_TYPE
    const val EOR_TOKEN_TYPE: Int = 1
    const val DOWN: Int = 2
    const val UP: Int = 3

    // Lexer & shared tokens
    const val ACTION: Int = 4
    const val ACTION_CHAR_LITERAL: Int = 5
    const val ACTION_ESC: Int = 6
    const val ACTION_STRING_LITERAL: Int = 7
    const val ARG_ACTION: Int = 8
    const val ARG_OR_CHARSET: Int = 9
    const val ASSIGN: Int = 10
    const val AT: Int = 11
    const val CATCH: Int = 12
    const val CHANNELS: Int = 13
    const val COLON: Int = 14
    const val COLONCOLON: Int = 15
    const val COMMA: Int = 16
    const val COMMENT: Int = 17
    const val DOC_COMMENT: Int = 18
    const val DOLLAR: Int = 19
    const val DOT: Int = 20
    const val ERRCHAR: Int = 21
    const val ESC_SEQ: Int = 22
    const val FINALLY: Int = 23
    const val FRAGMENT: Int = 24
    const val GRAMMAR: Int = 25
    const val GT: Int = 26
    const val HEX_DIGIT: Int = 27
    const val ID: Int = 28
    const val IMPORT: Int = 29
    const val INT: Int = 30
    const val LEXER: Int = 31
    const val LEXER_CHAR_SET: Int = 32
    const val LOCALS: Int = 33
    const val LPAREN: Int = 34
    const val LT: Int = 35
    const val MODE: Int = 36
    const val NESTED_ACTION: Int = 37
    const val NLCHARS: Int = 38
    const val NOT: Int = 39
    const val OPTIONS: Int = 40
    const val OR: Int = 41
    const val PARSER: Int = 42
    const val PLUS: Int = 43
    const val PLUS_ASSIGN: Int = 44
    const val POUND: Int = 45
    const val QUESTION: Int = 46
    const val RANGE: Int = 47
    const val RARROW: Int = 48
    const val RBRACE: Int = 49
    const val RETURNS: Int = 50
    const val RPAREN: Int = 51
    const val RULE_REF: Int = 52
    const val SEMI: Int = 53
    const val SEMPRED: Int = 54
    const val SRC: Int = 55
    const val STAR: Int = 56
    const val STRING_LITERAL: Int = 57
    const val THROWS: Int = 58
    const val TOKENS_SPEC: Int = 59
    const val TOKEN_REF: Int = 60
    const val UNICODE_BOM: Int = 61
    const val UNICODE_ESC: Int = 62
    const val UNICODE_EXTENDED_ESC: Int = 63
    const val WS: Int = 64
    const val WSCHARS: Int = 65
    const val WSNLCHARS: Int = 66

    // Imaginary AST node types from ANTLRParser.g
    const val ALT: Int = 67
    const val BLOCK: Int = 68
    const val CLOSURE: Int = 69
    const val COMBINED: Int = 70
    const val ELEMENT_OPTIONS: Int = 71
    const val EPSILON: Int = 72
    const val LEXER_ACTION_CALL: Int = 73
    const val LEXER_ALT_ACTION: Int = 74
    const val OPTIONAL: Int = 75
    const val POSITIVE_CLOSURE: Int = 76
    const val RULE: Int = 77
    const val RULEMODIFIERS: Int = 78
    const val RULES: Int = 79
    const val SET: Int = 80
    const val WILDCARD: Int = 81

    const val COMMENTS_CHANNEL: Int = 2

    val tokenNames: List<String> =
        listOf(
            "<invalid>",
            "<EOR>",
            "<DOWN>",
            "<UP>",
            "ACTION",
            "ACTION_CHAR_LITERAL",
            "ACTION_ESC",
            "ACTION_STRING_LITERAL",
            "ARG_ACTION",
            "ARG_OR_CHARSET",
            "ASSIGN",
            "AT",
            "CATCH",
            "CHANNELS",
            "COLON",
            "COLONCOLON",
            "COMMA",
            "COMMENT",
            "DOC_COMMENT",
            "DOLLAR",
            "DOT",
            "ERRCHAR",
            "ESC_SEQ",
            "FINALLY",
            "FRAGMENT",
            "GRAMMAR",
            "GT",
            "HEX_DIGIT",
            "ID",
            "IMPORT",
            "INT",
            "LEXER",
            "LEXER_CHAR_SET",
            "LOCALS",
            "LPAREN",
            "LT",
            "MODE",
            "NESTED_ACTION",
            "NLCHARS",
            "NOT",
            "OPTIONS",
            "OR",
            "PARSER",
            "PLUS",
            "PLUS_ASSIGN",
            "POUND",
            "QUESTION",
            "RANGE",
            "RARROW",
            "RBRACE",
            "RETURNS",
            "RPAREN",
            "RULE_REF",
            "SEMI",
            "SEMPRED",
            "SRC",
            "STAR",
            "STRING_LITERAL",
            "THROWS",
            "TOKENS_SPEC",
            "TOKEN_REF",
            "UNICODE_BOM",
            "UNICODE_ESC",
            "UNICODE_EXTENDED_ESC",
            "WS",
            "WSCHARS",
            "WSNLCHARS",
            "ALT",
            "BLOCK",
            "CLOSURE",
            "COMBINED",
            "ELEMENT_OPTIONS",
            "EPSILON",
            "LEXER_ACTION_CALL",
            "LEXER_ALT_ACTION",
            "OPTIONAL",
            "POSITIVE_CLOSURE",
            "RULE",
            "RULEMODIFIERS",
            "RULES",
            "SET",
            "WILDCARD",
        )

    fun tokenName(type: Int): String =
        if (type == EOF) {
            "EOF"
        } else if (type in tokenNames.indices) {
            tokenNames[type]
        } else {
            type.toString()
        }
}
