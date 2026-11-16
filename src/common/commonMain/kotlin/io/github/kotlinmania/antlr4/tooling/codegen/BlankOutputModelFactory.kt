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
import io.github.kotlinmania.antlr4.tooling.codegen.model.LabeledOp
import io.github.kotlinmania.antlr4.tooling.codegen.model.Lexer
import io.github.kotlinmania.antlr4.tooling.codegen.model.LexerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.Parser
import io.github.kotlinmania.antlr4.tooling.codegen.model.ParserFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp
import io.github.kotlinmania.antlr4.tooling.tool.Alternative
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Skeletal [OutputModelFactory] providing empty operation lists for subclasses to specialize. */
abstract class BlankOutputModelFactory : OutputModelFactory {
    override fun parserFile(fileName: String): ParserFile = ParserFile(this, fileName)

    override fun parser(file: ParserFile): Parser = Parser(this, file)

    override fun lexerFile(fileName: String): LexerFile = LexerFile(this, fileName)

    override fun lexer(file: LexerFile): Lexer = Lexer(this, file)

    override fun rule(r: Rule): RuleFunction? = null

    override fun rulePostamble(
        rf: RuleFunction,
        r: Rule,
    ): List<SrcOp> = emptyList()

    override fun alternative(
        alt: Alternative,
        outerMost: Boolean,
    ): CodeBlockForAlt = CodeBlockForAlt(this)

    override fun finishAlternative(
        blk: CodeBlockForAlt,
        ops: List<SrcOp>,
    ): CodeBlockForAlt = blk

    override fun epsilon(
        alt: Alternative,
        epsASTRoot: GrammarAST,
    ): List<SrcOp> = emptyList()

    override fun ruleRef(
        ID: GrammarAST,
        label: GrammarAST?,
        args: GrammarAST?,
    ): List<SrcOp> = emptyList()

    override fun tokenRef(
        ID: GrammarAST,
        labelAST: GrammarAST?,
        args: GrammarAST?,
    ): List<SrcOp> = emptyList()

    override fun stringRef(
        ID: GrammarAST,
        label: GrammarAST?,
    ): List<SrcOp> = tokenRef(ID, label, null)

    override fun set(
        setAST: GrammarAST,
        labelAST: GrammarAST?,
        invert: Boolean,
    ): List<SrcOp> = emptyList()

    override fun wildcard(
        ast: GrammarAST,
        labelAST: GrammarAST?,
    ): List<SrcOp> = emptyList()

    override fun action(ast: ActionAST): List<SrcOp> = emptyList()

    override fun sempred(ast: ActionAST): List<SrcOp> = emptyList()

    override fun getChoiceBlock(
        blkAST: BlockAST,
        alts: List<CodeBlockForAlt>,
        labelAST: GrammarAST?,
    ): Choice = throw UnsupportedOperationException("getChoiceBlock is only supported on ParserFactory.")

    override fun getEBNFBlock(
        ebnfRoot: GrammarAST?,
        alts: List<CodeBlockForAlt>,
    ): Choice = throw UnsupportedOperationException("getEBNFBlock is only supported on ParserFactory.")

    override fun getLL1ChoiceBlock(
        blkAST: BlockAST,
        alts: List<CodeBlockForAlt>,
    ): Choice = throw UnsupportedOperationException("getLL1ChoiceBlock is only supported on ParserFactory.")

    override fun getComplexChoiceBlock(
        blkAST: BlockAST,
        alts: List<CodeBlockForAlt>,
    ): Choice = throw UnsupportedOperationException("getComplexChoiceBlock is only supported on ParserFactory.")

    override fun getLL1EBNFBlock(
        ebnfRoot: GrammarAST,
        alts: List<CodeBlockForAlt>,
    ): Choice = throw UnsupportedOperationException("getLL1EBNFBlock is only supported on ParserFactory.")

    override fun getComplexEBNFBlock(
        ebnfRoot: GrammarAST,
        alts: List<CodeBlockForAlt>,
    ): Choice = throw UnsupportedOperationException("getComplexEBNFBlock is only supported on ParserFactory.")

    override fun getLL1Test(
        look: IntervalSet,
        blkAST: GrammarAST,
    ): List<SrcOp> = emptyList()

    override fun needsImplicitLabel(
        ID: GrammarAST,
        op: LabeledOp,
    ): Boolean = false
}
