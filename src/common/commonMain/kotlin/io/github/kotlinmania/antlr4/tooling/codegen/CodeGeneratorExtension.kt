/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.codegen.model.Choice
import io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.Lexer
import io.github.kotlinmania.antlr4.tooling.codegen.model.LexerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.Parser
import io.github.kotlinmania.antlr4.tooling.codegen.model.ParserFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionChunk
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.StructDecl
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Filter/transform extension hook invoked by [OutputModelController] at every output-model stage. */
open class CodeGeneratorExtension(
    val factory: OutputModelFactory,
) {
    open fun parserFile(f: ParserFile): ParserFile = f

    open fun parser(p: Parser): Parser = p

    open fun lexerFile(f: LexerFile): LexerFile = f

    open fun lexer(l: Lexer): Lexer = l

    open fun rule(rf: RuleFunction): RuleFunction = rf

    open fun rulePostamble(
        ops: List<SrcOp>,
        r: Rule,
    ): List<SrcOp> = ops

    open fun alternative(
        blk: CodeBlockForAlt,
        outerMost: Boolean,
    ): CodeBlockForAlt = blk

    open fun finishAlternative(
        blk: CodeBlockForAlt,
        ops: List<SrcOp>,
    ): CodeBlockForAlt = blk

    open fun epsilon(ops: List<SrcOp>): List<SrcOp> = ops

    open fun ruleRef(ops: List<SrcOp>): List<SrcOp> = ops

    open fun tokenRef(ops: List<SrcOp>): List<SrcOp> = ops

    open fun set(ops: List<SrcOp>): List<SrcOp> = ops

    open fun stringRef(ops: List<SrcOp>): List<SrcOp> = ops

    open fun wildcard(ops: List<SrcOp>): List<SrcOp> = ops

    open fun action(ops: List<SrcOp>): List<SrcOp> = ops

    open fun sempred(ops: List<SrcOp>): List<SrcOp> = ops

    open fun getChoiceBlock(c: Choice): Choice = c

    open fun getEBNFBlock(c: Choice): Choice = c

    open fun needsImplicitLabel(
        ID: GrammarAST,
        op: io.github.kotlinmania.antlr4.tooling.codegen.model.LabeledOp,
    ): Boolean = false

    open fun actionInAlt(chunks: List<ActionChunk>): List<ActionChunk> = chunks

    open fun ruleContext(
        ctx: StructDecl,
        ast: BlockAST,
    ): StructDecl = ctx

    open fun altContext(
        ctx: StructDecl,
        ast: AltAST,
    ): StructDecl = ctx

    open fun currentBlock(blk: CodeBlock): CodeBlock = blk

    open fun argAction(
        ast: ActionAST,
        chunks: List<ActionChunk>,
    ): List<ActionChunk> = chunks
}
