/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.analysis

import io.github.kotlinmania.antlr4.CommonToken
import io.github.kotlinmania.antlr4.TokenStream
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarASTWithOptions
import io.github.kotlinmania.antlr4.tooling.ast.RuleRefAST
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.parse.LeftRecursiveRuleWalker
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType
import io.github.kotlinmania.antlr4.tooling.tool.LeftRecursiveRuleRefLabel
import io.github.kotlinmania.antlr4.tooling.tool.Tool

/**
 * Classifies outer alternatives of a direct left-recursive parser rule into binary, prefix,
 * suffix, and primary forms and synthesizes the precedence-climbing rule text.
 */
class LeftRecursiveRuleAnalyzer(
    val ruleAST: GrammarAST,
    val tool: Tool,
    val ruleName: String,
    val language: String?,
) : LeftRecursiveRuleWalker() {
    enum class ASSOC {
        LEFT,
        RIGHT,
    }

    internal val mutableBinaryAlts: LinkedHashMap<Int, LeftRecursiveRuleAltInfo> = LinkedHashMap()
    val binaryAlts: Map<Int, LeftRecursiveRuleAltInfo>
        get() = mutableBinaryAlts

    internal val mutableTernaryAlts: LinkedHashMap<Int, LeftRecursiveRuleAltInfo> = LinkedHashMap()
    val ternaryAlts: Map<Int, LeftRecursiveRuleAltInfo>
        get() = mutableTernaryAlts

    internal val mutableSuffixAlts: LinkedHashMap<Int, LeftRecursiveRuleAltInfo> = LinkedHashMap()
    val suffixAlts: Map<Int, LeftRecursiveRuleAltInfo>
        get() = mutableSuffixAlts

    internal val mutablePrefixAndOtherAlts: MutableList<LeftRecursiveRuleAltInfo> = ArrayList()
    val prefixAndOtherAlts: List<LeftRecursiveRuleAltInfo>
        get() = mutablePrefixAndOtherAlts

    internal val mutableLeftRecursiveRuleRefLabels: MutableList<LeftRecursiveRuleRefLabel> = ArrayList()
    val leftRecursiveRuleRefLabels: List<LeftRecursiveRuleRefLabel>
        get() = mutableLeftRecursiveRuleRefLabels

    val tokenStream: TokenStream =
        requireNotNull(ruleAST.g?.originalTokenStream) { "grammar must have a token stream" }

    var retvals: GrammarAST? = null

    internal val mutableAltAssociativity: MutableMap<Int, ASSOC> = HashMap()
    val altAssociativity: Map<Int, ASSOC>
        get() = mutableAltAssociativity

    override fun setReturnValues(t: GrammarAST) {
        retvals = t
    }

    override fun setAltAssoc(
        altTree: AltAST,
        alt: Int,
    ) {
        var assoc = ASSOC.LEFT
        if (altTree.options.isNotEmpty()) {
            val a = altTree.getOptionString("assoc")
            if (a != null) {
                when (a) {
                    "right" -> assoc = ASSOC.RIGHT
                    "left" -> assoc = ASSOC.LEFT
                    else -> {
                        tool.errMgr.grammarError(
                            ErrorType.ILLEGAL_OPTION_VALUE,
                            altTree.g?.fileName,
                            altTree.getOptionAST("assoc")?.token,
                            "assoc",
                            assoc.name.lowercase(),
                        )
                    }
                }
            }
        }

        val existing = mutableAltAssociativity[alt]
        if (existing != null && existing != assoc) {
            tool.errMgr.toolError(
                ErrorType.INTERNAL_ERROR,
                "all operators of alt $alt of left-recursive rule must have same associativity",
            )
        }
        mutableAltAssociativity[alt] = assoc
    }

    override fun binaryAlt(
        altTree: AltAST,
        alt: Int,
    ) {
        var dupAltTree = altTree.dupTree() as AltAST
        val altLabel = dupAltTree.altLabel?.text

        var label: String? = null
        var isListLabel = false
        val lrlabel = stripLeftRecursion(dupAltTree)
        if (lrlabel != null) {
            label = lrlabel.text
            isListLabel = lrlabel.parent?.type == ANTLRParserTypes.PLUS_ASSIGN
            mutableLeftRecursiveRuleRefLabels.add(LeftRecursiveRuleRefLabel(lrlabel, altLabel))
        }

        stripAltLabel(dupAltTree)

        val nextPrec = nextPrecedence(alt)
        dupAltTree = addPrecedenceArgToRules(dupAltTree, nextPrec)

        stripAltLabel(dupAltTree)
        val altText = text(dupAltTree).trim()
        val info = LeftRecursiveRuleAltInfo(alt, altText, label, altLabel, isListLabel, altTree)
        info.nextPrec = nextPrec
        mutableBinaryAlts[alt] = info
    }

    override fun prefixAlt(
        altTree: AltAST,
        alt: Int,
    ) {
        var dupAltTree = altTree.dupTree() as AltAST
        stripAltLabel(dupAltTree)

        val nextPrec = precedence(alt)
        dupAltTree = addPrecedenceArgToRules(dupAltTree, nextPrec)
        val altText = text(dupAltTree).trim()
        val altLabel = dupAltTree.altLabel?.text
        val info = LeftRecursiveRuleAltInfo(alt, altText, null, altLabel, false, altTree)
        info.nextPrec = nextPrec
        mutablePrefixAndOtherAlts.add(info)
    }

    override fun suffixAlt(
        altTree: AltAST,
        alt: Int,
    ) {
        val dupAltTree = altTree.dupTree() as AltAST
        val altLabel = dupAltTree.altLabel?.text

        var label: String? = null
        var isListLabel = false
        val lrlabel = stripLeftRecursion(dupAltTree)
        if (lrlabel != null) {
            label = lrlabel.text
            isListLabel = lrlabel.parent?.type == ANTLRParserTypes.PLUS_ASSIGN
            mutableLeftRecursiveRuleRefLabels.add(LeftRecursiveRuleRefLabel(lrlabel, altLabel))
        }
        stripAltLabel(dupAltTree)
        val altText = text(dupAltTree).trim()
        val info = LeftRecursiveRuleAltInfo(alt, altText, label, altLabel, isListLabel, altTree)
        mutableSuffixAlts[alt] = info
    }

    override fun otherAlt(
        altTree: AltAST,
        alt: Int,
    ) {
        val dupAltTree = altTree.dupTree() as AltAST
        stripAltLabel(dupAltTree)
        val altText = text(dupAltTree)
        val altLabel = dupAltTree.altLabel?.text
        val info = LeftRecursiveRuleAltInfo(alt, altText, null, altLabel, false, altTree)
        mutablePrefixAndOtherAlts.add(info)
    }

    val artificialOpPrecRule: String
        get() {
            val opPrecRuleAlts = LinkedHashMap<Int, LeftRecursiveRuleAltInfo>()
            opPrecRuleAlts.putAll(mutableBinaryAlts)
            opPrecRuleAlts.putAll(mutableTernaryAlts)
            opPrecRuleAlts.putAll(mutableSuffixAlts)

            val opAltStrings = ArrayList<String>(opPrecRuleAlts.size)
            for ((alt, altInfo) in opPrecRuleAlts) {
                val prec = precedence(alt)
                val pred = renderRecRuleAltPredicate(language, prec)
                val precOpt = LeftRecursiveRuleTransformer.PRECEDENCE_OPTION_NAME
                opAltStrings.add("{$pred}?<$precOpt=$prec> ${altInfo.altText.orEmpty()}")
            }

            val retClause = retvals?.let { " returns [${it.text}]" } ?: ""
            val primariesJoined =
                mutablePrefixAndOtherAlts.joinToString("\n        | ") { "${it.altText.orEmpty()} " }
            val opsJoined = opAltStrings.joinToString("\n        | ")

            val rendered =
                buildString {
                    append(ruleName).append(retClause).append('\n')
                    append("    :   ( {} ").append(primariesJoined).append('\n')
                    append("        )\n")
                    append("        (\n")
                    append("          ").append(opsJoined).append('\n')
                    append("        )*\n")
                    append("    ;")
                }

            tool.log("left-recursion", rendered)
            return rendered
        }

    fun addPrecedenceArgToRules(
        t: AltAST,
        prec: Int,
    ): AltAST {
        val outerAltRuleRefs = t.getNodesWithTypePreorderDFS(IntervalSet.of(ANTLRParserTypes.RULE_REF))
        val lastRef = outerAltRuleRefs.lastOrNull()
        for (x in outerAltRuleRefs) {
            val rref = x as? RuleRefAST ?: continue
            val recursive = rref.text == ruleName
            val rightmost = rref === lastRef
            if (recursive && rightmost) {
                val dummyValueNode = GrammarAST(CommonToken(ANTLRParserTypes.INT, prec.toString()))
                rref.setOption(LeftRecursiveRuleTransformer.PRECEDENCE_OPTION_NAME, dummyValueNode)
            }
        }
        return t
    }

    fun stripLeftRecursion(altAST: GrammarAST): GrammarAST? {
        var lrlabel: GrammarAST? = null
        var first = altAST.getChild(0) ?: return null
        var leftRecurRuleIndex = 0
        if (first.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
            first = altAST.getChild(1) ?: return null
            leftRecurRuleIndex = 1
        }
        val rref = first.getChild(1)
        if ((first.type == ANTLRParserTypes.RULE_REF && first.text == ruleName) ||
            (rref != null && rref.type == ANTLRParserTypes.RULE_REF && rref.text == ruleName)
        ) {
            if (first.type == ANTLRParserTypes.ASSIGN || first.type == ANTLRParserTypes.PLUS_ASSIGN) {
                lrlabel = first.getChild(0)
            }
            altAST.deleteChild(leftRecurRuleIndex)
            val newFirstChild = altAST.getChild(leftRecurRuleIndex)
            if (newFirstChild != null) {
                altAST.tokenStartIndex = newFirstChild.tokenStartIndex
            }
        }
        return lrlabel
    }

    fun stripAltLabel(altAST: GrammarAST) {
        val start = altAST.tokenStartIndex
        val stop = altAST.tokenStopIndex
        if (start < 0 || stop < 0) return
        for (i in stop downTo start) {
            if (i in 0 until tokenStream.size() && tokenStream.get(i)?.type == ANTLRParserTypes.POUND) {
                altAST.tokenStopIndex = i - 1
                return
            }
        }
    }

    fun text(t: GrammarAST?): String {
        if (t == null) return ""

        val tokenStartIndex = t.tokenStartIndex
        val tokenStopIndex = t.tokenStopIndex
        if (tokenStartIndex < 0 || tokenStopIndex < tokenStartIndex) return ""

        val ignore = IntervalSet()
        val optionsSubTrees = t.getNodesWithType(ANTLRParserTypes.ELEMENT_OPTIONS)
        for (sub in optionsSubTrees) {
            ignore.add(sub.tokenStartIndex, sub.tokenStopIndex)
        }

        val labelOpTypes =
            IntervalSet().apply {
                add(ANTLRParserTypes.ASSIGN)
                add(ANTLRParserTypes.PLUS_ASSIGN)
            }
        val noOptions = IntervalSet()
        val labeledSubTrees = t.getNodesWithType(labelOpTypes)
        for (sub in labeledSubTrees) {
            val labelChild = sub.getChild(0) ?: continue
            noOptions.add(labelChild.tokenStartIndex)
        }

        val buf = StringBuilder()
        var i = tokenStartIndex
        while (i <= tokenStopIndex && i < tokenStream.size()) {
            if (ignore.contains(i)) {
                i++
                continue
            }

            val tok = tokenStream.get(i)
            if (tok == null) {
                i++
                continue
            }

            val elementOptions = StringBuilder()
            if (!noOptions.contains(i)) {
                val node = t.getNodeWithTokenIndex(tok.tokenIndex)
                if (node != null &&
                    (
                        tok.type == ANTLRParserTypes.TOKEN_REF ||
                            tok.type == ANTLRParserTypes.STRING_LITERAL ||
                            tok.type == ANTLRParserTypes.RULE_REF
                    )
                ) {
                    elementOptions.append("tokenIndex=").append(tok.tokenIndex)
                }

                if (node is GrammarASTWithOptions) {
                    for ((key, value) in node.options) {
                        if (value == null) continue
                        if (elementOptions.isNotEmpty()) {
                            elementOptions.append(',')
                        }
                        elementOptions.append(key)
                        elementOptions.append('=')
                        elementOptions.append(value.text)
                    }
                }
            }

            buf.append(tok.text.orEmpty())
            i++

            if (tok.type == ANTLRParserTypes.RULE_REF &&
                i <= tokenStopIndex &&
                i < tokenStream.size() &&
                tokenStream.get(i)?.type == ANTLRParserTypes.ARG_ACTION
            ) {
                buf.append('[').append(tokenStream.get(i)?.text.orEmpty()).append(']')
                i++
            }

            if (elementOptions.isNotEmpty()) {
                buf.append('<').append(elementOptions).append('>')
            }
        }
        return buf.toString()
    }

    fun precedence(alt: Int): Int = numAlts - alt + 1

    fun nextPrecedence(alt: Int): Int {
        val p = precedence(alt)
        if (mutableAltAssociativity[alt] == ASSOC.RIGHT) return p
        return p + 1
    }

    override fun toString(): String =
        "PrecRuleOperatorCollector{" +
            "binaryAlts=$mutableBinaryAlts" +
            ", ternaryAlts=$mutableTernaryAlts" +
            ", suffixAlts=$mutableSuffixAlts" +
            ", prefixAndOtherAlts=$mutablePrefixAndOtherAlts" +
            "}"

    companion object {
        internal fun renderRecRuleAltPredicate(
            language: String?,
            prec: Int,
        ): String =
            when (language) {
                "Python2", "Python3" -> "self.precpred(self._ctx, $prec)"
                "CSharp" -> "Precpred(Context, $prec)"
                "Dart" -> "precpred(context, $prec)"
                "JavaScript", "TypeScript" -> "this.precpred(this._ctx, $prec)"
                "Go" -> "p.Precpred(p.GetParserRuleContext(), $prec)"
                "PHP" -> "\$this->precpred(\$this->ctx, $prec)"
                else -> "precpred(_ctx, $prec)"
            }

        fun hasImmediateRecursiveRuleRefs(
            t: GrammarAST?,
            ruleName: String?,
        ): Boolean {
            if (t == null || ruleName == null) return false
            val blk = t.getFirstChildWithType(ANTLRParserTypes.BLOCK) ?: return false
            for (alt in blk.children) {
                var first = alt.getChild(0) ?: continue
                if (first.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                    first = alt.getChild(1) ?: continue
                }
                if (first.type == ANTLRParserTypes.RULE_REF && first.text == ruleName) return true
                val rref = first.getChild(1)
                if (rref != null && rref.type == ANTLRParserTypes.RULE_REF && rref.text == ruleName) return true
            }
            return false
        }
    }
}
