/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.ANTLRInputStream
import io.github.kotlinmania.antlr4.CommonTokenStream
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.RuleAST
import io.github.kotlinmania.antlr4.tooling.ast.TerminalAST
import io.github.kotlinmania.antlr4.tooling.tool.ANTLRMessage
import io.github.kotlinmania.antlr4.tooling.tool.ErrorManager
import io.github.kotlinmania.antlr4.tooling.tool.GrammarASTOwner
import io.github.kotlinmania.antlr4.tooling.tool.ToolErrorContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ASTStructureTest {
    private fun execParser(
        ruleName: String,
        input: String,
        scriptLine: Int = 1,
    ): GrammarAST {
        val stream = ANTLRInputStream(input)
        val lexer = ANTLRLexer(stream)
        lexer.line = scriptLine
        val tokens = CommonTokenStream(lexer)
        val parser = ANTLRParser(tokens)
        parser.treeAdaptor = GrammarASTAdaptor()
        val tree: GrammarAST? =
            when (ruleName) {
                "grammarSpec" -> parser.grammarSpec().tree
                "delegateGrammars" -> parser.delegateGrammars().tree
                "rule" -> parser.rule().tree
                "ebnf" -> parser.ebnf().tree
                "element" -> parser.element().tree
                "alternativeEntry" -> parser.alternativeEntry().tree
                "elementEntry" -> parser.elementEntry().tree
                "ruleEntry" -> parser.ruleEntry().tree
                "blockEntry" -> parser.blockEntry().tree
                else -> error("Unsupported parser rule: $ruleName")
            }
        return checkNotNull(tree)
    }

    @Test
    fun testGrammarSpec1() {
        val actual = execParser("grammarSpec", "parser grammar P; a : A;", 15).toStringTree()
        assertEquals("(PARSER_GRAMMAR P (RULES (RULE a (BLOCK (ALT A)))))", actual)
    }

    @Test
    fun testGrammarSpec2() {
        val actual =
            execParser(
                "grammarSpec",
                "\n    parser grammar P;\n    tokens { A, B }\n    @header {foo}\n    a : A;\n    ",
                18,
            ).toStringTree()
        assertEquals(
            "(PARSER_GRAMMAR P (tokens { A B) (@ header {foo}) (RULES (RULE a (BLOCK (ALT A)))))",
            actual,
        )
    }

    @Test
    fun testGrammarSpec3() {
        val actual =
            execParser(
                "grammarSpec",
                "\n    parser grammar P;\n    @header {foo}\n    tokens { A,B }\n    a : A;\n    ",
                30,
            ).toStringTree()
        assertEquals(
            "(PARSER_GRAMMAR P (@ header {foo}) (tokens { A B) (RULES (RULE a (BLOCK (ALT A)))))",
            actual,
        )
    }

    @Test
    fun testGrammarSpec4() {
        val actual =
            execParser(
                "grammarSpec",
                "\n    parser grammar P;\n    import A=B, C;\n    a : A;\n    ",
                42,
            ).toStringTree()
        assertEquals(
            "(PARSER_GRAMMAR P (import (= A B) C) (RULES (RULE a (BLOCK (ALT A)))))",
            actual,
        )
    }

    @Test
    fun testDelegateGrammars1() {
        val actual = execParser("delegateGrammars", "import A;", 53).toStringTree()
        assertEquals("(import A)", actual)
    }

    @Test
    fun testRule1() {
        val actual = execParser("rule", "a : A<X,Y=a.b.c>;", 56).toStringTree()
        assertEquals("(RULE a (BLOCK (ALT (A (ELEMENT_OPTIONS X (= Y a.b.c))))))", actual)
    }

    @Test
    fun testRule2() {
        val actual = execParser("rule", "A : B+;", 58).toStringTree()
        assertEquals("(RULE A (BLOCK (ALT (+ (BLOCK (ALT B))))))", actual)
    }

    @Test
    fun testRule3() {
        val actual =
            execParser(
                "rule",
                "\n    a[int i] returns [int y]\n    @init {blort}\n      : ID ;\n    ",
                60,
            ).toStringTree()
        assertEquals("(RULE a int i (returns int y) (@ init {blort}) (BLOCK (ALT ID)))", actual)
    }

    @Test
    fun testRule4() {
        val actual =
            execParser(
                "rule",
                "\n    a[int i] returns [int y]\n    @init {blort}\n    options {backtrack=true;}\n      : ID;\n    ",
                75,
            ).toStringTree()
        assertEquals(
            "(RULE a int i (returns int y) (@ init {blort}) (OPTIONS (= backtrack true)) (BLOCK (ALT ID)))",
            actual,
        )
    }

    @Test
    fun testRule5() {
        val actual =
            execParser(
                "rule",
                "\n    a : ID ;\n      catch[A b] {foo}\n      finally {bar}\n    ",
                88,
            ).toStringTree()
        assertEquals("(RULE a (BLOCK (ALT ID)) (catch A b {foo}) (finally {bar}))", actual)
    }

    @Test
    fun testRule6() {
        val actual =
            execParser(
                "rule",
                "\n    a : ID ;\n      catch[A a] {foo}\n      catch[B b] {fu}\n      finally {bar}\n    ",
                97,
            ).toStringTree()
        assertEquals(
            "(RULE a (BLOCK (ALT ID)) (catch A a {foo}) (catch B b {fu}) (finally {bar}))",
            actual,
        )
    }

    @Test
    fun testRule7() {
        val actual =
            execParser("rule", "\n\ta[int i]\n\tlocals [int a, float b]\n\t\t:\tA\n\t\t;\n\t", 107)
                .toStringTree()
        assertEquals("(RULE a int i (locals int a, float b) (BLOCK (ALT A)))", actual)
    }

    @Test
    fun testRule8() {
        val actual =
            execParser("rule", "\n\ta[int i] throws a.b.c\n\t\t:\tA\n\t\t;\n\t", 115)
                .toStringTree()
        assertEquals("(RULE a int i (throws a.b.c) (BLOCK (ALT A)))", actual)
    }

    @Test
    fun testEbnf1() {
        assertEquals("(BLOCK (ALT A) (ALT B))", execParser("ebnf", "(A|B)", 123).toStringTree())
    }

    @Test
    fun testEbnf2() {
        assertEquals("(? (BLOCK (ALT A) (ALT B)))", execParser("ebnf", "(A|B)?", 124).toStringTree())
    }

    @Test
    fun testEbnf3() {
        assertEquals("(* (BLOCK (ALT A) (ALT B)))", execParser("ebnf", "(A|B)*", 125).toStringTree())
    }

    @Test
    fun testEbnf4() {
        assertEquals("(+ (BLOCK (ALT A) (ALT B)))", execParser("ebnf", "(A|B)+", 126).toStringTree())
    }

    @Test
    fun testElement1() {
        assertEquals("(~ (SET A))", execParser("element", "~A", 129).toStringTree())
    }

    @Test
    fun testElement2() {
        assertEquals("(+ (BLOCK (ALT b)))", execParser("element", "b+", 130).toStringTree())
    }

    @Test
    fun testElement3() {
        assertEquals("(+ (BLOCK (ALT b)))", execParser("element", "(b)+", 131).toStringTree())
    }

    @Test
    fun testElement4() {
        assertEquals("(? (BLOCK (ALT b)))", execParser("element", "b?", 132).toStringTree())
    }

    @Test
    fun testElement5() {
        assertEquals("(? (BLOCK (ALT b)))", execParser("element", "(b)?", 133).toStringTree())
    }

    @Test
    fun testElement6() {
        assertEquals("(* (BLOCK (ALT b)))", execParser("element", "(b)*", 134).toStringTree())
    }

    @Test
    fun testElement7() {
        assertEquals("(* (BLOCK (ALT b)))", execParser("element", "b*", 135).toStringTree())
    }

    @Test
    fun testElement8() {
        assertEquals("(* (BLOCK (ALT 'while')))", execParser("element", "'while'*", 136).toStringTree())
    }

    @Test
    fun testElement9() {
        assertEquals("(+ (BLOCK (ALT 'a')))", execParser("element", "'a'+", 137).toStringTree())
    }

    @Test
    fun testElement10() {
        assertEquals("(a 3)", execParser("element", "a[3]", 138).toStringTree())
    }

    @Test
    fun testElement11() {
        assertEquals("(+ (BLOCK (ALT (.. 'a' 'z'))))", execParser("element", "'a'..'z'+", 139).toStringTree())
    }

    @Test
    fun testElement12() {
        assertEquals("(= x ID)", execParser("element", "x=ID", 140).toStringTree())
    }

    @Test
    fun testElement13() {
        assertEquals("(? (BLOCK (ALT (= x ID))))", execParser("element", "x=ID?", 141).toStringTree())
    }

    @Test
    fun testElement14() {
        assertEquals("(* (BLOCK (ALT (= x ID))))", execParser("element", "x=ID*", 142).toStringTree())
    }

    @Test
    fun testElement15() {
        assertEquals("(= x b)", execParser("element", "x=b", 143).toStringTree())
    }

    @Test
    fun testElement16() {
        assertEquals("(= x (BLOCK (ALT A) (ALT B)))", execParser("element", "x=(A|B)", 144).toStringTree())
    }

    @Test
    fun testElement17() {
        assertEquals("(= x (~ (SET A B)))", execParser("element", "x=~(A|B)", 145).toStringTree())
    }

    @Test
    fun testElement18() {
        assertEquals("(+= x (~ (SET A B)))", execParser("element", "x+=~(A|B)", 146).toStringTree())
    }

    @Test
    fun testElement19() {
        assertEquals("(+ (BLOCK (ALT (+= x (~ (SET A B))))))", execParser("element", "x+=~(A|B)+", 147).toStringTree())
    }

    @Test
    fun testElement20() {
        assertEquals("(+ (BLOCK (ALT (= x b))))", execParser("element", "x=b+", 148).toStringTree())
    }

    @Test
    fun testElement21() {
        assertEquals("(* (BLOCK (ALT (+= x ID))))", execParser("element", "x+=ID*", 149).toStringTree())
    }

    @Test
    fun testElement22() {
        assertEquals("(* (BLOCK (ALT (+= x 'int'))))", execParser("element", "x+='int'*", 150).toStringTree())
    }

    @Test
    fun testElement23() {
        assertEquals("(+ (BLOCK (ALT (+= x b))))", execParser("element", "x+=b+", 151).toStringTree())
    }

    @Test
    fun testElement24() {
        assertEquals("(* (BLOCK (ALT {blort} 'x')))", execParser("element", "({blort} 'x')*", 152).toStringTree())
    }

    @Test
    fun testBlockSetTransformerRewritesRuleAndSubruleSets() {
        val owner =
            object : GrammarASTOwner {
                override val name: String = "T"
                override val fileName: String = "T.g4"
                override val isLexer: Boolean = false
                override val errorManager: ErrorManager = ErrorManager(null)
            }
        val grammar =
            execParser(
                "grammarSpec",
                """
                grammar T;
                p : (A | 'kw') (B | C)+ ;
                q : A | B | C ;
                LEX_SINGLE : 'a' | 'b' | '0'..'9' ;
                LEX_MULTI  : 'ab' | 'cd' ;
                """.trimIndent(),
            )
        val transformed = BlockSetTransformer(owner).downup(grammar)
        assertNotNull(transformed)
        assertEquals(
            "(COMBINED_GRAMMAR T (RULES " +
                "(RULE p (BLOCK (ALT (SET A 'kw') (+ (BLOCK (ALT (SET B C))))))) " +
                "(RULE q (BLOCK (ALT (SET A B C)))) " +
                "(RULE LEX_SINGLE (BLOCK (ALT (SET 'a' 'b' (.. '0' '9'))))) " +
                "(RULE LEX_MULTI (BLOCK (ALT 'ab') (ALT 'cd')))))",
            transformed.toStringTree(),
        )
    }

    @Test
    fun testLeftRecursiveRuleWalkerClassifiesOuterAlts() {
        val grammar =
            execParser(
                "grammarSpec",
                """
                grammar Expr;
                expr returns [int v]
                    : expr '*' expr          # Mul
                    | <assoc=right> expr '^' expr # Pow
                    | '-' expr               # Neg
                    | expr '++'              # Inc
                    | ID                     # Atom
                    ;
                nonRec : ID ;
                withArgs[int x] : withArgs '+' ID | ID ;
                """.trimIndent(),
            )
        val rulesNode = checkNotNull(grammar.getFirstChildWithType(ANTLRParserTypes.RULES))
        val exprRule = checkNotNull(rulesNode.getChild(0))
        val nonRecRule = checkNotNull(rulesNode.getChild(1))
        val withArgsRule = checkNotNull(rulesNode.getChild(2))

        val binaryAlts = ArrayList<Int>()
        val prefixAlts = ArrayList<Int>()
        val suffixAlts = ArrayList<Int>()
        val otherAlts = ArrayList<Int>()
        var returnValuesText: String? = null

        val walker =
            object : LeftRecursiveRuleWalker() {
                override fun setReturnValues(t: GrammarAST) {
                    returnValuesText = t.text
                }

                override fun binaryAlt(
                    altTree: AltAST,
                    alt: Int,
                ) {
                    binaryAlts.add(alt)
                }

                override fun prefixAlt(
                    altTree: AltAST,
                    alt: Int,
                ) {
                    prefixAlts.add(alt)
                }

                override fun suffixAlt(
                    altTree: AltAST,
                    alt: Int,
                ) {
                    suffixAlts.add(alt)
                }

                override fun otherAlt(
                    altTree: AltAST,
                    alt: Int,
                ) {
                    otherAlts.add(alt)
                }
            }

        assertTrue(walker.recRule(exprRule))
        assertEquals(5, walker.numAlts)
        assertEquals("int v", returnValuesText)
        assertEquals(listOf(1, 2), binaryAlts)
        assertEquals(listOf(3), prefixAlts)
        assertEquals(listOf(4), suffixAlts)
        assertEquals(listOf(5), otherAlts)

        assertFalse(walker.recRule(nonRecRule))
        assertFalse(walker.recRule(withArgsRule))
    }

    @Test
    fun testGrammarTreeVisitorWalksCombinedAndLexerConstructs() {
        val toolCtx =
            object : ToolErrorContext {
                override var longMessages: Boolean = false
                override val errMgr: ErrorManager = ErrorManager(this)

                override fun info(msg: String?) {}

                override fun error(msg: ANTLRMessage) {}

                override fun warning(msg: ANTLRMessage) {}
            }
        val grammar =
            execParser(
                "grammarSpec",
                """
                lexer grammar L;
                options { language=Kotlin; }
                channels { COMMENTS }
                ID : [a-zA-Z]+ -> pushMode(M) ;
                COMMENT : '//' ~[\r\n]* -> channel(COMMENTS) ;
                mode M;
                END : ';' -> popMode ;
                """.trimIndent(),
            )

        val discoveredRules = ArrayList<String>()
        val discoveredModes = ArrayList<String>()
        val discoveredChannels = ArrayList<String>()
        val discoveredCommands = ArrayList<String>()
        val grammarOptions = LinkedHashMap<String, String?>()

        val visitor =
            object : GrammarTreeVisitor() {
                override fun getErrorManager(): ErrorManager = toolCtx.errMgr

                override fun grammarOption(
                    id: GrammarAST,
                    valueAST: GrammarAST,
                ) {
                    grammarOptions[id.text] = valueAST.text
                }

                override fun defineChannel(id: GrammarAST) {
                    discoveredChannels.add(id.text)
                }

                override fun discoverLexerRule(
                    rule: RuleAST,
                    id: GrammarAST,
                    modifiers: List<GrammarAST>,
                    options: GrammarAST?,
                    block: GrammarAST,
                ) {
                    discoveredRules.add("${currentModeName}:${id.text}")
                }

                override fun modeDef(
                    m: GrammarAST,
                    id: GrammarAST,
                ) {
                    discoveredModes.add(id.text)
                }

                override fun lexerCallCommand(
                    outerAltNumber: Int,
                    id: GrammarAST,
                    arg: GrammarAST,
                ) {
                    discoveredCommands.add("${id.text}(${arg.text})")
                }

                override fun lexerCommand(
                    outerAltNumber: Int,
                    id: GrammarAST,
                ) {
                    discoveredCommands.add(id.text)
                }

                override fun tokenRef(ref: TerminalAST) {}

                override fun actionInAlt(action: ActionAST) {}
            }

        visitor.visitGrammar(grammar)
        assertEquals("L", visitor.grammarName)
        assertEquals<Map<String, String?>>(mapOf("language" to "Kotlin"), grammarOptions)
        assertEquals(listOf("COMMENTS"), discoveredChannels)
        assertEquals(listOf("M"), discoveredModes)
        assertEquals(listOf("DEFAULT_MODE:ID", "DEFAULT_MODE:COMMENT", "M:END"), discoveredRules)
        assertEquals(listOf("pushMode(M)", "channel(COMMENTS)", "popMode"), discoveredCommands)
    }
}
