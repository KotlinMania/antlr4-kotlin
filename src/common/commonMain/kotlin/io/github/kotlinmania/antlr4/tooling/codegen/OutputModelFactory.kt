/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.model.Choice
import io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForOuterMostAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.Lexer
import io.github.kotlinmania.antlr4.tooling.codegen.model.LexerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.Parser
import io.github.kotlinmania.antlr4.tooling.codegen.model.ParserFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock
import io.github.kotlinmania.antlr4.tooling.tool.Alternative
import io.github.kotlinmania.antlr4.tooling.tool.Grammar
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Abstract factory building target-independent [io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject] nodes. */
interface OutputModelFactory {
    val grammar: Grammar
    val generator: CodeGenerator
    var controller: OutputModelController

    val root: io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject?
    val currentRuleFunction: RuleFunction?
    val currentOuterMostAlt: Int
    val currentBlock: CodeBlock?
    val currentOuterMostAlternativeBlock: CodeBlockForOuterMostAlt?
    val treeLevel: Int

    fun parserFile(fileName: String): ParserFile

    fun parser(file: ParserFile): Parser

    fun lexerFile(fileName: String): LexerFile

    fun lexer(file: LexerFile): Lexer

    fun rule(r: Rule): RuleFunction?

    fun rulePostamble(
        rf: RuleFunction,
        r: Rule,
    ): List<SrcOp>

    fun alternative(
        alt: Alternative,
        outerMost: Boolean,
    ): CodeBlockForAlt

    fun finishAlternative(
        blk: CodeBlockForAlt,
        ops: List<SrcOp>,
    ): CodeBlockForAlt

    fun epsilon(
        alt: Alternative,
        epsASTRoot: GrammarAST,
    ): List<SrcOp>

    fun ruleRef(
        ID: GrammarAST,
        label: GrammarAST?,
        args: GrammarAST?,
    ): List<SrcOp>

    fun tokenRef(
        ID: GrammarAST,
        labelAST: GrammarAST?,
        args: GrammarAST?,
    ): List<SrcOp>

    fun stringRef(
        ID: GrammarAST,
        label: GrammarAST?,
    ): List<SrcOp>

    fun set(
        setAST: GrammarAST,
        labelAST: GrammarAST?,
        invert: Boolean,
    ): List<SrcOp>

    fun wildcard(
        ast: GrammarAST,
        labelAST: GrammarAST?,
    ): List<SrcOp>

    fun action(ast: ActionAST): List<SrcOp>

    fun sempred(ast: ActionAST): List<SrcOp>

    fun getChoiceBlock(
        blkAST: BlockAST,
        alts: List<CodeBlockForAlt>,
        labelAST: GrammarAST?,
    ): Choice

    fun getEBNFBlock(
        ebnfRoot: GrammarAST?,
        alts: List<CodeBlockForAlt>,
    ): Choice

    fun getLL1ChoiceBlock(
        blkAST: BlockAST,
        alts: List<CodeBlockForAlt>,
    ): Choice

    fun getComplexChoiceBlock(
        blkAST: BlockAST,
        alts: List<CodeBlockForAlt>,
    ): Choice

    fun getLL1EBNFBlock(
        ebnfRoot: GrammarAST,
        alts: List<CodeBlockForAlt>,
    ): Choice

    fun getComplexEBNFBlock(
        ebnfRoot: GrammarAST,
        alts: List<CodeBlockForAlt>,
    ): Choice

    fun getLL1Test(
        look: IntervalSet,
        blkAST: GrammarAST,
    ): List<SrcOp>

    fun needsImplicitLabel(
        ID: GrammarAST,
        op: io.github.kotlinmania.antlr4.tooling.codegen.model.LabeledOp,
    ): Boolean
}
