/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.atn.DecisionState
import io.github.kotlinmania.antlr4.atn.PlusBlockStartState
import io.github.kotlinmania.antlr4.atn.StarLoopEntryState
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.analysis.AnalysisPipeline
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.BlockAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.QuantifierAST
import io.github.kotlinmania.antlr4.tooling.codegen.model.Action
import io.github.kotlinmania.antlr4.tooling.codegen.model.AddToLabelList
import io.github.kotlinmania.antlr4.tooling.codegen.model.AltBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.CaptureNextToken
import io.github.kotlinmania.antlr4.tooling.codegen.model.Choice
import io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForOuterMostAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.InvokeRule
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1AltBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1OptionalBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1OptionalBlockSingleAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1PlusBlockSingleAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1StarBlockSingleAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.LabeledOp
import io.github.kotlinmania.antlr4.tooling.codegen.model.LeftRecursiveRuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.MatchNotSet
import io.github.kotlinmania.antlr4.tooling.codegen.model.MatchSet
import io.github.kotlinmania.antlr4.tooling.codegen.model.MatchToken
import io.github.kotlinmania.antlr4.tooling.codegen.model.OptionalBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.Parser
import io.github.kotlinmania.antlr4.tooling.codegen.model.ParserFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.PlusBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleSempredFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.SemPred
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp
import io.github.kotlinmania.antlr4.tooling.codegen.model.StarBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.TestSetInline
import io.github.kotlinmania.antlr4.tooling.codegen.model.ThrowEarlyExitException
import io.github.kotlinmania.antlr4.tooling.codegen.model.ThrowNoViableAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.Wildcard
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.Decl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenListDecl
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.tool.Alternative
import io.github.kotlinmania.antlr4.tooling.tool.LeftRecursiveRule
import io.github.kotlinmania.antlr4.tooling.tool.Rule

/** Concrete [OutputModelFactory] building [ParserFile], [Parser], [RuleFunction], and decision/matching ops. */
open class ParserFactory(
    gen: CodeGenerator,
) : DefaultOutputModelFactory(gen) {
    override fun parserFile(fileName: String): ParserFile = ParserFile(this, fileName)

    override fun parser(file: ParserFile): Parser = Parser(this, file)

    override fun rule(r: Rule): RuleFunction =
        if (r is LeftRecursiveRule) {
            LeftRecursiveRuleFunction(this, r)
        } else {
            RuleFunction(this, r)
        }

    override fun epsilon(
        alt: Alternative,
        epsASTRoot: GrammarAST,
    ): List<SrcOp> = emptyList()

    override fun alternative(
        alt: Alternative,
        outerMost: Boolean,
    ): CodeBlockForAlt =
        if (outerMost) {
            CodeBlockForOuterMostAlt(this, alt)
        } else {
            CodeBlockForAlt(this)
        }

    override fun finishAlternative(
        blk: CodeBlockForAlt,
        ops: List<SrcOp>,
    ): CodeBlockForAlt {
        blk.mutableOps.clear()
        blk.mutableOps.addAll(ops)
        return blk
    }

    override fun action(ast: ActionAST): List<SrcOp> = list(Action(this, ast))

    override fun sempred(ast: ActionAST): List<SrcOp> {
        val rf = currentRuleFunction
        if (rf != null) {
            val predIdx = grammar.sempreds[ast]
            val parserModel = controller.root as? ParserFile
            val parser = parserModel?.parser
            if (predIdx != null && parser != null) {
                val ctxType = generator.target.getRuleFunctionContextStructName(rf)
                val sempredFunc =
                    parser.mutableSempredFuncs.getOrPut(rf.rule) {
                        RuleSempredFunction(this, rf.rule, ctxType)
                    }
                sempredFunc.mutableActions[predIdx] = Action(this, ast)
            }
        }
        return list(SemPred(this, ast))
    }

    override fun ruleRef(
        ID: GrammarAST,
        label: GrammarAST?,
        args: GrammarAST?,
    ): List<SrcOp> {
        val invokeOp = InvokeRule(this, ID, label)
        val rf = currentRuleFunction
        if (rf != null && label != null && label.parent?.type == ANTLRParserTypes.PLUS_ASSIGN) {
            val listName = generator.target.getListLabel(label.text)
            val elemDecl = invokeOp.labels.firstOrNull()
            if (elemDecl != null) {
                val listOp = AddToLabelList(this, listName, elemDecl)
                return list(invokeOp, listOp)
            }
        }
        return list(invokeOp)
    }

    override fun tokenRef(
        ID: GrammarAST,
        labelAST: GrammarAST?,
        args: GrammarAST?,
    ): List<SrcOp> {
        val matchOp = MatchToken(this, ID)
        if (labelAST != null) {
            val label = labelAST.text
            val rf = currentRuleFunction
            if (rf != null) {
                if (labelAST.parent?.type == ANTLRParserTypes.PLUS_ASSIGN) {
                    val tempDecl = getImplicitTokenLabel(ID, matchOp)
                    val listDecl = TokenListDecl(this, label)
                    rf.addContextDecl(ID.enclosingAltLabel, listDecl)
                    val listName = generator.target.getListLabel(label)
                    return list(matchOp, AddToLabelList(this, listName, tempDecl))
                } else {
                    val d = getTokenLabelDecl(label)
                    matchOp.addLabel(d)
                    rf.addContextDecl(ID.enclosingAltLabel, d)
                }
            }
        }
        if (needsImplicitLabel(ID, matchOp)) {
            getImplicitTokenLabel(ID, matchOp)
        }
        return list(matchOp)
    }

    fun getTokenLabelDecl(label: String): Decl =
        TokenDecl(this, label).apply { isOuterMostAlt = treeLevel == 1 }

    fun getImplicitTokenLabel(
        ID: GrammarAST,
        matchOp: MatchToken,
    ): TokenDecl {
        val varName = generator.target.getImplicitTokenLabel(ID.text)
        val d = TokenDecl(this, varName).apply { isImplicit = true }
        matchOp.addLabel(d)
        currentRuleFunction?.addContextDecl(ID.enclosingAltLabel, d)
        return d
    }

    override fun set(
        setAST: GrammarAST,
        labelAST: GrammarAST?,
        invert: Boolean,
    ): List<SrcOp> {
        val matchOp: MatchSet =
            if (invert) {
                MatchNotSet(this, setAST)
            } else {
                MatchSet(this, setAST)
            }
        val rf = currentRuleFunction
        if (rf != null && labelAST != null) {
            val label = labelAST.text
            if (labelAST.parent?.type == ANTLRParserTypes.PLUS_ASSIGN) {
                val tempVar = generator.target.getImplicitSetLabel(setAST.token?.tokenIndex?.toString() ?: "0")
                val tempDecl = TokenDecl(this, tempVar).apply { isImplicit = true }
                rf.addContextDecl(setAST.enclosingAltLabel, tempDecl)
                val capture = CaptureNextToken(this, tempDecl.escapedName)
                val listDecl = TokenListDecl(this, label)
                rf.addContextDecl(setAST.enclosingAltLabel, listDecl)
                val listOp = AddToLabelList(this, generator.target.getListLabel(label), tempDecl)
                return list(capture, matchOp, listOp)
            } else {
                val d = getTokenLabelDecl(label)
                matchOp.addLabel(d)
                rf.addContextDecl(setAST.enclosingAltLabel, d)
                val capture = CaptureNextToken(this, d.escapedName)
                return list(capture, matchOp)
            }
        }
        return list(matchOp)
    }

    override fun wildcard(
        ast: GrammarAST,
        labelAST: GrammarAST?,
    ): List<SrcOp> {
        val wild = Wildcard(this, ast)
        val rf = currentRuleFunction
        if (rf != null && labelAST != null) {
            val label = labelAST.text
            if (labelAST.parent?.type == ANTLRParserTypes.PLUS_ASSIGN) {
                val tempVar = generator.target.getImplicitTokenLabel(ast.text)
                val tempDecl = TokenDecl(this, tempVar).apply { isImplicit = true }
                rf.addContextDecl(ast.enclosingAltLabel, tempDecl)
                wild.addLabel(tempDecl)
                val listDecl = TokenListDecl(this, label)
                rf.addContextDecl(ast.enclosingAltLabel, listDecl)
                val listOp = AddToLabelList(this, generator.target.getListLabel(label), tempDecl)
                return list(wild, listOp)
            } else {
                val d = getTokenLabelDecl(label)
                wild.addLabel(d)
                rf.addContextDecl(ast.enclosingAltLabel, d)
            }
        }
        return list(wild)
    }

    override fun getChoiceBlock(
        blkAST: BlockAST,
        alts: List<CodeBlockForAlt>,
        labelAST: GrammarAST?,
    ): Choice {
        val decision = (blkAST.atnState as? DecisionState)?.decision ?: -1
        val c: Choice =
            if (!grammar.tool.forceAtn && decision >= 0 && AnalysisPipeline.disjointNullable(grammar.decisionLOOK?.getOrNull(decision))) {
                getLL1ChoiceBlock(blkAST, alts)
            } else {
                getComplexChoiceBlock(blkAST, alts)
            }

        if (labelAST != null) {
            val rf = currentRuleFunction
            if (rf != null) {
                val label = labelAST.text
                if (labelAST.parent?.type == ANTLRParserTypes.PLUS_ASSIGN) {
                    val tempVar = generator.target.getImplicitSetLabel(blkAST.token?.tokenIndex?.toString() ?: "0")
                    val tempDecl = TokenDecl(this, tempVar).apply { isImplicit = true }
                    rf.addContextDecl(blkAST.enclosingAltLabel, tempDecl)
                    c.label = tempDecl
                    val listDecl = TokenListDecl(this, label)
                    rf.addContextDecl(blkAST.enclosingAltLabel, listDecl)
                    for (alt in alts) {
                        alt.addOp(AddToLabelList(this, generator.target.getListLabel(label), tempDecl))
                    }
                } else {
                    val d = getTokenLabelDecl(label)
                    c.label = d
                    rf.addContextDecl(blkAST.enclosingAltLabel, d)
                }
            }
        }
        return c
    }

    override fun getEBNFBlock(
        ebnfRoot: GrammarAST?,
        alts: List<CodeBlockForAlt>,
    ): Choice {
        if (ebnfRoot == null) {
            return AltBlock(this, BlockAST(ANTLRParserTypes.BLOCK), alts)
        }
        if (!grammar.tool.forceAtn) {
            val decision =
                when (ebnfRoot.type) {
                    ANTLRParserTypes.OPTIONAL -> (ebnfRoot.atnState as? DecisionState)?.decision ?: -1
                    ANTLRParserTypes.CLOSURE -> (ebnfRoot.atnState as? StarLoopEntryState)?.decision ?: -1
                    ANTLRParserTypes.POSITIVE_CLOSURE -> {
                        val blk = ebnfRoot.getChild(0) as? BlockAST
                        val plusStart = blk?.atnState as? PlusBlockStartState
                        plusStart?.loopBackState?.decision ?: -1
                    }
                    else -> -1
                }
            val greedy = (ebnfRoot as? QuantifierAST)?.isGreedy != false
            if (greedy && decision >= 0 && AnalysisPipeline.disjointNullable(grammar.decisionLOOK?.getOrNull(decision))) {
                return getLL1EBNFBlock(ebnfRoot, alts)
            }
        }
        return getComplexEBNFBlock(ebnfRoot, alts)
    }

    override fun getLL1ChoiceBlock(
        blkAST: BlockAST,
        alts: List<CodeBlockForAlt>,
    ): Choice = LL1AltBlock(this, blkAST, alts)

    override fun getComplexChoiceBlock(
        blkAST: BlockAST,
        alts: List<CodeBlockForAlt>,
    ): Choice {
        currentRuleFunction?.hasLookaheadBlock = true
        return AltBlock(this, blkAST, alts).apply {
            error = ThrowNoViableAlt(this@ParserFactory, blkAST, IntervalSet())
        }
    }

    override fun getLL1EBNFBlock(
        ebnfRoot: GrammarAST,
        alts: List<CodeBlockForAlt>,
    ): Choice =
        when (ebnfRoot.type) {
            ANTLRParserTypes.OPTIONAL -> {
                if (alts.size == 1) {
                    LL1OptionalBlockSingleAlt(this, ebnfRoot, alts)
                } else {
                    LL1OptionalBlock(this, ebnfRoot, alts)
                }
            }

            ANTLRParserTypes.CLOSURE -> {
                if (alts.size == 1) {
                    LL1StarBlockSingleAlt(this, ebnfRoot, alts)
                } else {
                    getComplexEBNFBlock(ebnfRoot, alts)
                }
            }

            ANTLRParserTypes.POSITIVE_CLOSURE -> {
                if (alts.size == 1) {
                    LL1PlusBlockSingleAlt(this, ebnfRoot, alts)
                } else {
                    getComplexEBNFBlock(ebnfRoot, alts)
                }
            }

            else -> getComplexEBNFBlock(ebnfRoot, alts)
        }

    override fun getComplexEBNFBlock(
        ebnfRoot: GrammarAST,
        alts: List<CodeBlockForAlt>,
    ): Choice {
        currentRuleFunction?.hasLookaheadBlock = true
        return when (ebnfRoot.type) {
            ANTLRParserTypes.OPTIONAL -> OptionalBlock(this, ebnfRoot, alts)
            ANTLRParserTypes.CLOSURE -> StarBlock(this, ebnfRoot, alts)
            ANTLRParserTypes.POSITIVE_CLOSURE -> {
                val error = ThrowEarlyExitException(this, ebnfRoot, IntervalSet())
                PlusBlock(this, ebnfRoot, alts, error)
            }
            else -> AltBlock(this, ebnfRoot, alts)
        }
    }

    override fun getLL1Test(
        look: IntervalSet,
        blkAST: GrammarAST,
    ): List<SrcOp> = list(TestSetInline(this, blkAST, look, generator.target.inlineTestSetWordSize))

    override fun needsImplicitLabel(
        ID: GrammarAST,
        op: LabeledOp,
    ): Boolean {
        val currentAlt = currentOuterMostAlternativeBlock?.alt ?: return false
        val actionRefsAsToken = currentAlt.tokenRefsInActions.containsKey(ID.text)
        val actionRefsAsRule = currentAlt.ruleRefsInActions.containsKey(ID.text)
        return op.labels.isEmpty() && (actionRefsAsToken || actionRefsAsRule)
    }
}

private val GrammarAST.enclosingAltLabel: String?
    get() {
        val alt = getAncestor(ANTLRParserTypes.ALT) as? io.github.kotlinmania.antlr4.tooling.ast.AltAST
        return alt?.altLabel?.text ?: alt?.leftRecursiveAltInfo?.altLabel
    }
