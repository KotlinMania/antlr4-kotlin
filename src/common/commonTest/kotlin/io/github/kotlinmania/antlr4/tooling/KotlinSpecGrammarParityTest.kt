/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling

import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.internal.withHostFileSystemOrNull
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.LexerGrammar
import io.github.kotlinmania.antlr4.tooling.tool.Tool
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.readString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.github.kotlinmania.antlr4.tooling.gui.Trees as GuiTrees

/**
 * Verifies end-to-end compilation, Kotlin code generation, lexical mode transitions, and parser
 * interpreter execution for the Kotlin specification split grammar family
 * (`UnicodeClasses.g4`, `KotlinLexer.g4`, and `KotlinParser.g4`).
 */
class KotlinSpecGrammarParityTest {
    @Test
    fun kotlinSpecSplitGrammarsCompileEmitCodeAndParseKotlinConstructs() {
        val tool = Tool(listOf("KotlinLexer.g4", "KotlinParser.g4"))
        tool.writeFilesToDisk = false
        val equeue = ToolErrorQueue(tool)
        tool.removeListeners()
        tool.addListener(equeue)

        tool.registerVirtualGrammar("UnicodeClasses.g4", UNICODE_CLASSES_SPEC_SLICE)
        tool.registerVirtualGrammar("KotlinLexer.g4", KOTLIN_LEXER_SPEC_CODEGEN_SLICE)
        tool.registerVirtualGrammar("KotlinParser.g4", KOTLIN_PARSER_SPEC_SLICE)

        tool.processGrammarsOnCommandLine()
        assertEquals(0, equeue.errors.size, "Unexpected grammar errors: $equeue")

        val generatedTokens = assertNotNull(tool.virtualOutputs["KotlinLexer.tokens"], "Expected KotlinLexer.tokens")
        assertContains(generatedTokens, "PACKAGE=")
        assertContains(generatedTokens, "SUSPEND=")
        assertContains(generatedTokens, "SEALED=")

        val generatedLexerKt = assertNotNull(tool.virtualOutputs["KotlinLexer.kt"], "Expected KotlinLexer.kt")
        assertContains(generatedLexerKt, "class KotlinLexer")
        assertContains(generatedLexerKt, "if (!modeStack.isEmpty) { popMode() }")

        val generatedParserKt = assertNotNull(tool.virtualOutputs["KotlinParser.kt"], "Expected KotlinParser.kt")
        assertContains(generatedParserKt, "class KotlinParser")
        assertContains(generatedParserKt, "fun kotlinFile(")
        assertContains(generatedParserKt, "fun classDeclaration(")
        assertContains(generatedParserKt, "fun functionDeclaration(")

        // Build live LexerGrammar + Parser Grammar interpreters and parse representative Kotlin source
        val interpTool = Tool()
        interpTool.writeFilesToDisk = false
        val interpErrors = ToolErrorQueue(interpTool)
        interpTool.removeListeners()
        interpTool.addListener(interpErrors)
        interpTool.registerVirtualGrammar("UnicodeClasses.g4", UNICODE_CLASSES_SPEC_SLICE)
        interpTool.registerVirtualGrammar("KotlinLexer.g4", KOTLIN_LEXER_SPEC_INTERP_SLICE)
        interpTool.registerVirtualGrammar("KotlinParser.g4", KOTLIN_PARSER_SPEC_SLICE)

        val lexerAst = assertNotNull(interpTool.parseGrammar("KotlinLexer.g4"))
        val lexerGrammar = interpTool.createGrammar(lexerAst) as LexerGrammar
        lexerGrammar.fileName = "KotlinLexer.g4"
        interpTool.process(lexerGrammar, false)
        assertEquals(0, interpErrors.errors.size, "Unexpected lexer interpreter errors: $interpErrors")

        val parserGrammar = Grammar(KOTLIN_PARSER_SPEC_SLICE, lexerGrammar)
        assertEquals(0, parserGrammar.tool.numErrors)

        val sampleKotlin =
            """
            package io.github.kotlinmania.sample

            import kotlin.coroutines.Continuation

            sealed class Outcome<out T> {
                data class Ok<out T>(val value: T) : Outcome<T>()
            }

            suspend fun renderGreeting(name: String, count: Int): String {
                val tag = "item#${'$'}{count + 1}"
                return when (count) {
                    0 -> "empty:${'$'}name"
                    else -> tag
                }
            }
            """.trimIndent() + "\n"

        val lexerInterp = lexerGrammar.createLexerInterpreter(CharStreams.fromString(sampleKotlin))
        val tokenStream = CommonTokenStream(lexerInterp)
        tokenStream.fill()
        val defaultChannelTokens = tokenStream.tokens.filter { it.channel == Token.DEFAULT_CHANNEL && it.type != Token.EOF }
        assertTrue(defaultChannelTokens.isNotEmpty(), "Expected default-channel Kotlin tokens")

        val parserInterp = parserGrammar.createGrammarParserInterpreter(CommonTokenStream(lexerGrammar.createLexerInterpreter(CharStreams.fromString(sampleKotlin))))
        val startRule = assertNotNull(parserGrammar.getRule("kotlinFile"))
        val parseTree = parserInterp.parse(startRule.index)
        assertEquals(0, parserInterp.numberOfSyntaxErrors, "Expected zero syntax errors parsing sample Kotlin file")

        val renderedTree = GuiTrees.toStringTree(parseTree, InterpreterTreeTextProvider(parserGrammar.ruleNames))
        assertContains(renderedTree, "packageHeader")
        assertContains(renderedTree, "importHeader")
        assertContains(renderedTree, "classDeclaration")
        assertContains(renderedTree, "functionDeclaration")
        assertContains(renderedTree, "lineStringExpression")
        assertContains(renderedTree, "whenExpression")
    }

    @Test
    fun fullUpstreamKotlinSpecFilesCompileWhenPresentOnHost() {
        val specFiles =
            withHostFileSystemOrNull {
                val candidates =
                    listOf(
                        Path("../toport/kotlin-spec"),
                        Path("/Volumes/stuff/Projects/kotlinmania/toport/kotlin-spec"),
                    )
                val specDir = candidates.firstOrNull { dir -> SystemFileSystem.exists(Path(dir, "KotlinParser.g4")) } ?: return@withHostFileSystemOrNull null
                fun readText(name: String): String =
                    SystemFileSystem.source(Path(specDir, name)).buffered().use { it.readString() }
                Triple(
                    readText("UnicodeClasses.g4"),
                    readText("KotlinLexer.g4"),
                    readText("KotlinParser.g4"),
                )
            } ?: return

        val (unicodeG4, rawLexerG4, parserG4) = specFiles
        val kotlinAdaptedLexerG4 =
            rawLexerG4.replace(
                "if (!_modeStack.isEmpty()) { popMode(); }",
                "if (!modeStack.isEmpty) { popMode() }",
            )

        val tool = Tool(listOf("KotlinLexer.g4", "KotlinParser.g4"))
        tool.writeFilesToDisk = false
        val equeue = ToolErrorQueue(tool)
        tool.removeListeners()
        tool.addListener(equeue)
        tool.registerVirtualGrammar("UnicodeClasses.g4", unicodeG4)
        tool.registerVirtualGrammar("KotlinLexer.g4", kotlinAdaptedLexerG4)
        tool.registerVirtualGrammar("KotlinParser.g4", parserG4)

        tool.processGrammarsOnCommandLine()
        assertEquals(0, equeue.errors.size, "Expected zero errors on full Kotlin spec grammars: $equeue")
        assertTrue(tool.virtualOutputs.containsKey("KotlinLexer.kt"))
        assertTrue(tool.virtualOutputs.containsKey("KotlinParser.kt"))
        assertTrue(tool.virtualOutputs.containsKey("KotlinParser.tokens"))
    }

    companion object {
        private val UNICODE_CLASSES_SPEC_SLICE: String =
            """
            lexer grammar UnicodeClasses;

            UNICODE_CLASS_LL: '\u0061'..'\u007A' | '\u03B1'..'\u03C9';
            UNICODE_CLASS_LM: '\u02B0'..'\u02C1';
            UNICODE_CLASS_LO: '\u3041'..'\u3096' | '\u4E00'..'\u4E20';
            UNICODE_CLASS_LT: '\u01C5' | '\u01C8';
            UNICODE_CLASS_LU: '\u0041'..'\u005A' | '\u0391'..'\u03A9';
            UNICODE_CLASS_ND: '\u0030'..'\u0039';
            """.trimIndent()

        private fun buildKotlinLexerSlice(rcurlAction: String): String =
            """
            lexer grammar KotlinLexer;

            import UnicodeClasses;

            DelimitedComment: '/*' ( DelimitedComment | . )*? '*/' -> channel(HIDDEN);
            LineComment: '//' ~[\r\n]* -> channel(HIDDEN);
            WS: [\u0020\u0009\u000C] -> channel(HIDDEN);
            NL: '\n' | '\r' '\n'?;
            fragment Hidden: DelimitedComment | LineComment | WS;

            DOT: '.';
            COMMA: ',';
            LPAREN: '(' -> pushMode(Inside);
            RPAREN: ')';
            LSQUARE: '[' -> pushMode(Inside);
            RSQUARE: ']';
            LCURL: '{' -> pushMode(DEFAULT_MODE);
            RCURL: '}' $rcurlAction;
            ADD: '+';
            SUB: '-';
            MULT: '*';
            COLON: ':';
            SEMICOLON: ';';
            ASSIGNMENT: '=';
            ARROW: '->';
            LANGLE: '<';
            RANGLE: '>';
            QUEST_NO_WS: '?';

            PACKAGE: 'package';
            IMPORT: 'import';
            CLASS: 'class';
            INTERFACE: 'interface';
            FUN: 'fun';
            VAL: 'val';
            VAR: 'var';
            WHEN: 'when';
            ELSE: 'else';
            RETURN: 'return';
            OUT: 'out';
            IN: 'in';
            SEALED: 'sealed';
            DATA: 'data';
            SUSPEND: 'suspend';
            PUBLIC: 'public';
            PRIVATE: 'private';

            fragment DecDigit: '0'..'9';
            IntegerLiteral: DecDigit+;
            BooleanLiteral: 'true' | 'false';

            fragment UnicodeDigit: UNICODE_CLASS_ND;
            fragment Letter
                : UNICODE_CLASS_LU
                | UNICODE_CLASS_LL
                | UNICODE_CLASS_LT
                | UNICODE_CLASS_LM
                | UNICODE_CLASS_LO
                ;

            Identifier
                : (Letter | '_') (Letter | '_' | UnicodeDigit)*
                | '`' ~([\r\n] | '`')+ '`'
                ;

            FieldIdentifier: '${'$'}' (Identifier | OUT | IN | DATA | SEALED | SUSPEND);

            QUOTE_OPEN: '"' -> pushMode(LineString);

            mode LineString;
            QUOTE_CLOSE: '"' -> popMode;
            LineStrRef: FieldIdentifier;
            LineStrText: ~('\\' | '"' | '${'$'}')+ | '${'$'}';
            LineStrEscapedChar: '\\' ('t' | 'b' | 'r' | 'n' | '\'' | '"' | '\\' | '${'$'}');
            LineStrExprStart: '${'$'}{' -> pushMode(DEFAULT_MODE);

            mode Inside;
            Inside_RPAREN: RPAREN -> popMode, type(RPAREN);
            Inside_RSQUARE: RSQUARE -> popMode, type(RSQUARE);
            Inside_LPAREN: LPAREN -> pushMode(Inside), type(LPAREN);
            Inside_LSQUARE: LSQUARE -> pushMode(Inside), type(LSQUARE);
            Inside_LCURL: LCURL -> pushMode(DEFAULT_MODE), type(LCURL);
            Inside_RCURL: RCURL -> popMode, type(RCURL);
            Inside_DOT: DOT -> type(DOT);
            Inside_COMMA: COMMA -> type(COMMA);
            Inside_ADD: ADD -> type(ADD);
            Inside_SUB: SUB -> type(SUB);
            Inside_MULT: MULT -> type(MULT);
            Inside_COLON: COLON -> type(COLON);
            Inside_ASSIGNMENT: ASSIGNMENT -> type(ASSIGNMENT);
            Inside_ARROW: ARROW -> type(ARROW);
            Inside_LANGLE: LANGLE -> type(LANGLE);
            Inside_RANGLE: RANGLE -> type(RANGLE);
            Inside_QUEST: QUEST_NO_WS -> type(QUEST_NO_WS);
            Inside_VAL: VAL -> type(VAL);
            Inside_VAR: VAR -> type(VAR);
            Inside_OUT: OUT -> type(OUT);
            Inside_IN: IN -> type(IN);
            Inside_IntegerLiteral: IntegerLiteral -> type(IntegerLiteral);
            Inside_BooleanLiteral: BooleanLiteral -> type(BooleanLiteral);
            Inside_Identifier: Identifier -> type(Identifier);
            Inside_QUOTE_OPEN: QUOTE_OPEN -> pushMode(LineString), type(QUOTE_OPEN);
            Inside_Comment: (LineComment | DelimitedComment) -> channel(HIDDEN);
            Inside_WS: WS -> channel(HIDDEN);
            Inside_NL: NL -> channel(HIDDEN);
            """.trimIndent()

        private val KOTLIN_LEXER_SPEC_CODEGEN_SLICE: String =
            buildKotlinLexerSlice("{ if (!modeStack.isEmpty) { popMode() } }")

        private val KOTLIN_LEXER_SPEC_INTERP_SLICE: String =
            buildKotlinLexerSlice("-> popMode")

        private val KOTLIN_PARSER_SPEC_SLICE: String =
            """
            parser grammar KotlinParser;

            options { tokenVocab = KotlinLexer; }

            kotlinFile
                : NL* packageHeader importList topLevelObject* EOF
                ;

            packageHeader
                : (PACKAGE identifier semi?)?
                ;

            importList
                : importHeader*
                ;

            importHeader
                : IMPORT identifier semi?
                ;

            topLevelObject
                : declaration semis?
                ;

            declaration
                : classDeclaration
                | functionDeclaration
                | propertyDeclaration
                ;

            classDeclaration
                : modifiers? (CLASS | INTERFACE) NL* simpleIdentifier
                  (NL* typeParameters)? (NL* primaryConstructor)?
                  (NL* COLON NL* delegationSpecifiers)?
                  (NL* classBody)?
                ;

            primaryConstructor
                : classParameters
                ;

            classParameters
                : LPAREN (classParameter (COMMA classParameter)* COMMA?)? RPAREN
                ;

            classParameter
                : modifiers? (VAL | VAR)? simpleIdentifier COLON type
                ;

            delegationSpecifiers
                : delegationSpecifier (COMMA delegationSpecifier)*
                ;

            delegationSpecifier
                : userType valueArguments?
                ;

            classBody
                : LCURL NL* (declaration semis?)* RCURL
                ;

            typeParameters
                : LANGLE typeParameter (COMMA typeParameter)* RANGLE
                ;

            typeParameter
                : (OUT | IN)? simpleIdentifier
                ;

            typeArguments
                : LANGLE type (COMMA type)* RANGLE
                ;

            type
                : userType QUEST_NO_WS?
                ;

            userType
                : simpleIdentifier typeArguments? (DOT simpleIdentifier typeArguments?)*
                ;

            functionDeclaration
                : modifiers? FUN (NL* typeParameters)? NL* simpleIdentifier
                  NL* functionValueParameters (NL* COLON NL* type)? (NL* functionBody)?
                ;

            functionValueParameters
                : LPAREN (parameter (COMMA parameter)* COMMA?)? RPAREN
                ;

            parameter
                : simpleIdentifier COLON type
                ;

            functionBody
                : block
                | ASSIGNMENT NL* expression
                ;

            propertyDeclaration
                : modifiers? (VAL | VAR) NL* simpleIdentifier (COLON type)? (NL* ASSIGNMENT NL* expression)?
                ;

            block
                : LCURL NL* statements NL* RCURL
                ;

            statements
                : (statement (semis statement)*)? semis?
                ;

            statement
                : declaration
                | RETURN expression?
                | expression
                ;

            expression
                : additiveExpression
                ;

            additiveExpression
                : primaryExpression ((ADD | SUB) NL* primaryExpression)*
                ;

            primaryExpression
                : whenExpression
                | lineStringLiteral
                | IntegerLiteral
                | BooleanLiteral
                | simpleIdentifier valueArguments?
                | LPAREN expression RPAREN
                ;

            valueArguments
                : LPAREN (expression (COMMA expression)*)? RPAREN
                ;

            lineStringLiteral
                : QUOTE_OPEN (lineStringContent | lineStringExpression)* QUOTE_CLOSE
                ;

            lineStringContent
                : LineStrText
                | LineStrEscapedChar
                | LineStrRef
                ;

            lineStringExpression
                : LineStrExprStart NL* expression NL* RCURL
                ;

            whenExpression
                : WHEN NL* LPAREN expression RPAREN NL* LCURL NL* (whenEntry NL*)* RCURL
                ;

            whenEntry
                : expression NL* ARROW NL* expression semi?
                | ELSE NL* ARROW NL* expression semi?
                ;

            modifiers
                : modifier+
                ;

            modifier
                : (PUBLIC | PRIVATE | SEALED | DATA | SUSPEND) NL*
                ;

            semi
                : (SEMICOLON | NL) NL*
                ;

            semis
                : (SEMICOLON | NL)+
                ;

            simpleIdentifier
                : Identifier
                | OUT
                | IN
                | DATA
                | SEALED
                | SUSPEND
                ;

            identifier
                : simpleIdentifier (DOT simpleIdentifier)*
                ;
            """.trimIndent()
    }
}
