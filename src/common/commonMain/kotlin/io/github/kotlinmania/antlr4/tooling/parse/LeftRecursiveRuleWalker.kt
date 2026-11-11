/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.parse

import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.ast.GrammarAST

/**
 * Pure-Kotlin tree walker implementing `LeftRecursiveRuleWalker.g` to classify outer
 * alternatives of a direct left-recursive parser rule into binary, prefix, suffix, and
 * non-left-recursive forms.
 */
open class LeftRecursiveRuleWalker {
    private var ruleName: String? = null
    var currentOuterAltNumber: Int = 0
    var numAlts: Int = 0

    open fun setAltAssoc(
        altTree: AltAST,
        alt: Int,
    ) {
    }

    open fun binaryAlt(
        altTree: AltAST,
        alt: Int,
    ) {
    }

    open fun prefixAlt(
        altTree: AltAST,
        alt: Int,
    ) {
    }

    open fun suffixAlt(
        altTree: AltAST,
        alt: Int,
    ) {
    }

    open fun otherAlt(
        altTree: AltAST,
        alt: Int,
    ) {
    }

    open fun setReturnValues(t: GrammarAST) {
    }

    /**
     * Matches `rec_rule` on [ruleNode] and returns `true` iff the rule matches the permitted
     * left-recursive preamble structure and contains at least one left-recursive (`binary` or `suffix`)
     * outer alternative.
     */
    open fun recRule(ruleNode: GrammarAST): Boolean {
        if (ruleNode.type != ANTLRParserTypes.RULE || ruleNode.childCount == 0) {
            return false
        }
        currentOuterAltNumber = 1

        var idx = 0
        val idNode = ruleNode.getChild(idx) ?: return false
        if (idNode.type != ANTLRParserTypes.RULE_REF) {
            return false
        }
        ruleName = idNode.text
        idx++

        // Optional ruleModifier or ^(RULEMODIFIERS ruleModifier+)
        val maybeMod = ruleNode.getChild(idx)
        if (maybeMod != null) {
            if (isRuleModifierType(maybeMod.type)) {
                idx++
            } else if (maybeMod.type == ANTLRParserTypes.RULEMODIFIERS) {
                for (mIdx in 0 until maybeMod.childCount) {
                    val m = maybeMod.getChild(mIdx) ?: return false
                    if (!isRuleModifierType(m.type)) {
                        return false
                    }
                }
                idx++
            }
        }

        // ARG_ACTION on rule definition is forbidden in LeftRecursiveRuleWalker.g (`// no arg_action!`)
        if (ruleNode.getChild(idx)?.type == ANTLRParserTypes.ARG_ACTION) {
            return false
        }

        // ruleReturns? : ^(RETURNS a=ARG_ACTION)
        val maybeReturns = ruleNode.getChild(idx)
        if (maybeReturns != null && maybeReturns.type == ANTLRParserTypes.RETURNS) {
            val retAction = maybeReturns.getChild(0) ?: return false
            if (retAction.type != ANTLRParserTypes.ARG_ACTION) {
                return false
            }
            setReturnValues(retAction)
            idx++
        }

        // throwsSpec is forbidden in LeftRecursiveRuleWalker.g (`// no throwsSpec!`)
        if (ruleNode.getChild(idx)?.type == ANTLRParserTypes.THROWS) {
            return false
        }

        // locals? : ^(LOCALS ARG_ACTION)
        val maybeLocals = ruleNode.getChild(idx)
        if (maybeLocals != null && maybeLocals.type == ANTLRParserTypes.LOCALS) {
            if (maybeLocals.getChild(0)?.type != ANTLRParserTypes.ARG_ACTION) {
                return false
            }
            idx++
        }

        // ( ^(OPTIONS .*) | ^(AT ID ACTION) )*
        while (idx < ruleNode.childCount) {
            val preq = ruleNode.getChild(idx) ?: break
            if (preq.type == ANTLRParserTypes.OPTIONS) {
                idx++
            } else if (
                preq.type == ANTLRParserTypes.AT &&
                preq.childCount == 2 &&
                preq.getChild(0)?.type == ANTLRParserTypes.ID &&
                preq.getChild(1)?.type == ANTLRParserTypes.ACTION
            ) {
                idx++
            } else {
                break
            }
        }

        val blockNode = ruleNode.getChild(idx) ?: return false
        if (blockNode.type != ANTLRParserTypes.BLOCK) {
            return false
        }
        val isLeftRec = walkRuleBlock(blockNode) ?: return false
        idx++

        // exceptionGroup : exceptionHandler* finallyClause?
        while (idx < ruleNode.childCount && ruleNode.getChild(idx)?.type == ANTLRParserTypes.CATCH) {
            val catchNode = ruleNode.getChild(idx) ?: return false
            if (
                catchNode.childCount != 2 ||
                catchNode.getChild(0)?.type != ANTLRParserTypes.ARG_ACTION ||
                catchNode.getChild(1)?.type != ANTLRParserTypes.ACTION
            ) {
                return false
            }
            idx++
        }
        if (idx < ruleNode.childCount && ruleNode.getChild(idx)?.type == ANTLRParserTypes.FINALLY) {
            val finNode = ruleNode.getChild(idx) ?: return false
            if (finNode.childCount != 1 || finNode.getChild(0)?.type != ANTLRParserTypes.ACTION) {
                return false
            }
            idx++
        }

        if (idx != ruleNode.childCount) {
            return false
        }
        return isLeftRec
    }

    private fun walkRuleBlock(blockNode: GrammarAST): Boolean? {
        if (blockNode.childCount == 0) {
            return null
        }
        numAlts = blockNode.childCount
        var isLeftRec = false
        for (i in 0 until blockNode.childCount) {
            val child = blockNode.getChild(i)
            val altTree = child as? AltAST ?: return null
            if (altTree.type != ANTLRParserTypes.ALT) {
                return null
            }
            val matchedLeftRec = walkOuterAlternative(altTree) ?: return null
            if (matchedLeftRec) {
                isLeftRec = true
            }
            currentOuterAltNumber++
        }
        return isLeftRec
    }

    /**
     * Classifies [altTree] according to the four predicated branches of `outerAlternative`:
     * 1. `(binary)=> binary` -> `isLeftRec = true`
     * 2. `(prefix)=> prefix` -> `isLeftRec = false`
     * 3. `(suffix)=> suffix` -> `isLeftRec = true`
     * 4. `nonLeftRecur`      -> `isLeftRec = false`
     */
    private fun walkOuterAlternative(altTree: AltAST): Boolean? {
        var start = 0
        if (altTree.childCount > 0 && altTree.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
            if (!isElementOptions(altTree.getChild(0))) {
                return null
            }
            start = 1
        }
        val end = altTree.childCount
        if (start >= end) {
            return null
        }

        // 1. binary : ^(ALT elementOptions? recurse element* recurse epsilonElement*)
        if (matchesBinary(altTree, start, end)) {
            setAltAssoc(altTree, currentOuterAltNumber)
            binaryAlt(altTree, currentOuterAltNumber)
            return true
        }

        // 2. prefix : ^(ALT elementOptions? ({!text.equals(ruleName)}? element)+ recurse epsilonElement*)
        if (matchesPrefix(altTree, start, end)) {
            setAltAssoc(altTree, currentOuterAltNumber)
            prefixAlt(altTree, currentOuterAltNumber)
            return false
        }

        // 3. suffix : ^(ALT elementOptions? recurse element+)
        if (matchesSuffix(altTree, start, end)) {
            setAltAssoc(altTree, currentOuterAltNumber)
            suffixAlt(altTree, currentOuterAltNumber)
            return true
        }

        // 4. nonLeftRecur : ^(ALT elementOptions? element+)
        if (matchesNonLeftRecur(altTree, start, end)) {
            otherAlt(altTree, currentOuterAltNumber)
            return false
        }

        return null
    }

    private fun matchesBinary(
        altTree: AltAST,
        start: Int,
        end: Int,
    ): Boolean {
        val first = altTree.getChild(start) ?: return false
        if (!isRecurse(first)) {
            return false
        }
        // Find a second `recurse` at index `r2 > start` where `(start + 1 until r2)` are `element`
        // and `(r2 + 1 until end)` are `epsilonElement`. Try from left to right per ANTLR3 DFA order.
        for (r2 in (start + 1) until end) {
            val candidate = altTree.getChild(r2) ?: continue
            if (!isRecurse(candidate)) {
                continue
            }
            var validMiddle = true
            for (m in (start + 1) until r2) {
                val mid = altTree.getChild(m)
                if (mid == null || !isElement(mid)) {
                    validMiddle = false
                    break
                }
            }
            if (!validMiddle) {
                continue
            }
            var validTail = true
            for (t in (r2 + 1) until end) {
                val tail = altTree.getChild(t)
                if (tail == null || !isEpsilonElement(tail)) {
                    validTail = false
                    break
                }
            }
            if (validTail) {
                return true
            }
        }
        return false
    }

    private fun matchesPrefix(
        altTree: AltAST,
        start: Int,
        end: Int,
    ): Boolean {
        val first = altTree.getChild(start) ?: return false
        if (first.text == ruleName) {
            return false
        }
        // At least 1 leading element (`!text.equals(ruleName)`), then `recurse` at `r`, then `epsilonElement*`.
        for (r in (start + 1) until end) {
            val candidate = altTree.getChild(r) ?: continue
            if (!isRecurse(candidate)) {
                continue
            }
            var validPrefix = true
            for (p in start until r) {
                val pre = altTree.getChild(p)
                if (pre == null || pre.text == ruleName || !isElement(pre)) {
                    validPrefix = false
                    break
                }
            }
            if (!validPrefix) {
                continue
            }
            var validTail = true
            for (t in (r + 1) until end) {
                val tail = altTree.getChild(t)
                if (tail == null || !isEpsilonElement(tail)) {
                    validTail = false
                    break
                }
            }
            if (validTail) {
                return true
            }
        }
        return false
    }

    private fun matchesSuffix(
        altTree: AltAST,
        start: Int,
        end: Int,
    ): Boolean {
        if (end - start < 2) {
            return false
        }
        val first = altTree.getChild(start) ?: return false
        if (!isRecurse(first)) {
            return false
        }
        for (i in (start + 1) until end) {
            val elem = altTree.getChild(i) ?: return false
            if (!isElement(elem)) {
                return false
            }
        }
        return true
    }

    private fun matchesNonLeftRecur(
        altTree: AltAST,
        start: Int,
        end: Int,
    ): Boolean {
        if (start >= end) {
            return false
        }
        for (i in start until end) {
            val elem = altTree.getChild(i) ?: return false
            if (!isElement(elem)) {
                return false
            }
        }
        return true
    }

    /**
     * `recurse : ^(ASSIGN ID recurseNoLabel) | ^(PLUS_ASSIGN ID recurseNoLabel) | recurseNoLabel ;`
     * `recurseNoLabel : {text.equals(ruleName)}? RULE_REF ;`
     *
     * Note: in tree grammar, `RULE_REF` without `^(...)` matches only a leaf `RULE_REF` (`childCount == 0`),
     * so parameterized calls like `a[3]` (`^(RULE_REF ARG_ACTION)`) are NOT treated as direct left-recursion!
     */
    private fun isRecurse(node: GrammarAST): Boolean {
        if (isRecurseNoLabel(node)) {
            return true
        }
        if (
            (node.type == ANTLRParserTypes.ASSIGN || node.type == ANTLRParserTypes.PLUS_ASSIGN) &&
            node.childCount == 2 &&
            node.getChild(0)?.type == ANTLRParserTypes.ID
        ) {
            val rhs = node.getChild(1) ?: return false
            return isRecurseNoLabel(rhs)
        }
        return false
    }

    private fun isRecurseNoLabel(node: GrammarAST): Boolean =
        node.type == ANTLRParserTypes.RULE_REF && node.childCount == 0 && node.text == ruleName

    private fun isEpsilonElement(node: GrammarAST): Boolean =
        when (node.type) {
            ANTLRParserTypes.ACTION, ANTLRParserTypes.SEMPRED -> {
                node.childCount == 0 || (node.childCount == 1 && isElementOptions(node.getChild(0)))
            }
            ANTLRParserTypes.EPSILON -> node.childCount == 0
            else -> false
        }

    private fun isElement(node: GrammarAST): Boolean {
        if (isAtom(node) || isEpsilonElement(node)) {
            return true
        }
        return when (node.type) {
            ANTLRParserTypes.ASSIGN, ANTLRParserTypes.PLUS_ASSIGN -> {
                node.childCount == 2 &&
                    node.getChild(0)?.type == ANTLRParserTypes.ID &&
                    node.getChild(1)?.let { isElement(it) } == true
            }
            ANTLRParserTypes.BLOCK -> isBlock(node)
            ANTLRParserTypes.OPTIONAL, ANTLRParserTypes.CLOSURE, ANTLRParserTypes.POSITIVE_CLOSURE -> {
                node.childCount == 1 && node.getChild(0)?.let { isBlock(it) } == true
            }
            ANTLRParserTypes.NOT -> {
                node.childCount == 1 && node.getChild(0)?.let { isSet(it) } == true
            }
            else -> false
        }
    }

    private fun isBlock(node: GrammarAST): Boolean {
        if (node.type != ANTLRParserTypes.BLOCK || node.childCount == 0) {
            return false
        }
        var idx = 0
        while (idx < node.childCount) {
            val c = node.getChild(idx) ?: return false
            if (c.type == ANTLRParserTypes.OPTIONS || c.type == ANTLRParserTypes.AT || c.type == ANTLRParserTypes.ACTION) {
                idx++
            } else {
                break
            }
        }
        if (idx >= node.childCount) {
            return false
        }
        while (idx < node.childCount) {
            val alt = node.getChild(idx) ?: return false
            if (alt.type != ANTLRParserTypes.ALT) {
                return false
            }
            var aIdx = 0
            if (alt.childCount > 0 && alt.getChild(0)?.type == ANTLRParserTypes.ELEMENT_OPTIONS) {
                if (!isElementOptions(alt.getChild(0))) return false
                aIdx = 1
            }
            if (aIdx >= alt.childCount) return false
            while (aIdx < alt.childCount) {
                val el = alt.getChild(aIdx) ?: return false
                if (!isElement(el)) return false
                aIdx++
            }
            idx++
        }
        return true
    }

    private fun isAtom(node: GrammarAST): Boolean =
        when (node.type) {
            ANTLRParserTypes.RULE_REF -> {
                when (node.childCount) {
                    0 -> true
                    1 -> {
                        val c0 = node.getChild(0)
                        c0 != null && (c0.type == ANTLRParserTypes.ARG_ACTION || isElementOptions(c0))
                    }
                    2 -> {
                        node.getChild(0)?.type == ANTLRParserTypes.ARG_ACTION && isElementOptions(node.getChild(1))
                    }
                    else -> false
                }
            }
            ANTLRParserTypes.STRING_LITERAL, ANTLRParserTypes.TOKEN_REF, ANTLRParserTypes.WILDCARD -> {
                node.childCount == 0 || (node.childCount == 1 && isElementOptions(node.getChild(0)))
            }
            ANTLRParserTypes.SET -> isSet(node)
            ANTLRParserTypes.DOT -> {
                if (node.childCount == 2 && node.getChild(0)?.type == ANTLRParserTypes.ID) {
                    val target = node.getChild(1)
                    target != null && isAtom(target)
                } else {
                    false
                }
            }
            else -> false
        }

    private fun isSet(node: GrammarAST): Boolean {
        if (node.type != ANTLRParserTypes.SET || node.childCount == 0) {
            return false
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: return false
            when (c.type) {
                ANTLRParserTypes.STRING_LITERAL, ANTLRParserTypes.TOKEN_REF -> {
                    if (!(c.childCount == 0 || (c.childCount == 1 && isElementOptions(c.getChild(0))))) {
                        return false
                    }
                }
                ANTLRParserTypes.LEXER_CHAR_SET -> {
                    if (c.childCount != 0) return false
                }
                ANTLRParserTypes.RANGE -> {
                    if (
                        c.childCount != 2 ||
                        c.getChild(0)?.type != ANTLRParserTypes.STRING_LITERAL ||
                        c.getChild(1)?.type != ANTLRParserTypes.STRING_LITERAL
                    ) {
                        return false
                    }
                }
                else -> return false
            }
        }
        return true
    }

    private fun isElementOptions(node: GrammarAST?): Boolean {
        if (node == null || node.type != ANTLRParserTypes.ELEMENT_OPTIONS) {
            return false
        }
        for (i in 0 until node.childCount) {
            val opt = node.getChild(i) ?: return false
            if (opt.type == ANTLRParserTypes.ID) {
                continue
            }
            if (opt.type == ANTLRParserTypes.ASSIGN && opt.childCount == 2 && opt.getChild(0)?.type == ANTLRParserTypes.ID) {
                val valType = opt.getChild(1)?.type
                if (
                    valType == ANTLRParserTypes.ID ||
                    valType == ANTLRParserTypes.STRING_LITERAL ||
                    valType == ANTLRParserTypes.ACTION ||
                    valType == ANTLRParserTypes.INT
                ) {
                    continue
                }
            }
            return false
        }
        return true
    }

    private fun isRuleModifierType(type: Int): Boolean =
        type == ANTLRParserTypes.PUBLIC ||
            type == ANTLRParserTypes.PRIVATE ||
            type == ANTLRParserTypes.PROTECTED
}
