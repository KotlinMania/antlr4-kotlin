/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.CharStreams
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.tooling.ast.ActionAST
import io.github.kotlinmania.antlr4.tooling.ast.AltAST
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionChunk
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionText
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ArgRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.LabelRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ListLabelRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.LocalRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.NonLocalAttrRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.QRetValueRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.RetValueRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.RulePropertyRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.RulePropertyRefCtx
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.RulePropertyRefParser
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.RulePropertyRefStart
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.RulePropertyRefStop
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.RulePropertyRefText
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.SetAttr
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.SetNonLocalAttr
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ThisRulePropertyRefCtx
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ThisRulePropertyRefParser
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ThisRulePropertyRefStart
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ThisRulePropertyRefStop
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ThisRulePropertyRefText
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefChannel
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefIndex
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefInt
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefLine
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefPos
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefText
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefType
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.StructDecl
import io.github.kotlinmania.antlr4.tooling.parse.ANTLRParserTypes
import io.github.kotlinmania.antlr4.tooling.parse.ActionSplitter
import io.github.kotlinmania.antlr4.tooling.parse.ActionSplitterListener
import io.github.kotlinmania.antlr4.tooling.tool.AttributeDict
import io.github.kotlinmania.antlr4.tooling.tool.ErrorType

/**
 * Pure-Kotlin, reflection-free translator converting embedded target actions and predicates
 * into strongly-typed [ActionChunk] sequences via [ActionSplitter].
 */
class ActionTranslator(
    val factory: OutputModelFactory,
    val node: ActionAST,
) : ActionSplitterListener {
    internal val chunks: MutableList<ActionChunk> = ArrayList()
    var rf: RuleFunction? = null
    var nodeContext: StructDecl? = null

    private fun escape(name: String): String =
        factory.generator.target.escapeIfNeeded(name)

    override fun attr(
        expr: String,
        x: Token,
    ) {
        val xText = x.text.orEmpty()
        val escapedName = escape(xText)
        val resolver = node.resolver
        if (resolver != null) {
            val a = resolver.resolveToAttribute(xText, node)
            if (a != null) {
                when (a.dict?.type) {
                    AttributeDict.DictType.ARG -> {
                        chunks.add(ArgRef(nodeContext, xText, escapedName))
                        return
                    }

                    AttributeDict.DictType.RET -> {
                        chunks.add(RetValueRef(rf?.ruleCtx ?: nodeContext, xText, escapedName))
                        return
                    }

                    AttributeDict.DictType.LOCAL -> {
                        chunks.add(LocalRef(nodeContext, xText, escapedName))
                        return
                    }

                    AttributeDict.DictType.PREDEFINED_RULE -> {
                        val thisProp = createThisRulePropertyRef(nodeContext, getRuleLabel(xText), xText)
                        if (thisProp != null) {
                            chunks.add(thisProp)
                        }
                        return
                    }

                    else -> {}
                }
            }
            if (resolver.resolvesToToken(xText, node)) {
                val tokenLabel = getTokenLabel(xText)
                chunks.add(TokenRef(nodeContext, tokenLabel, escape(tokenLabel)))
                return
            }
            if (resolver.resolvesToLabel(xText, node)) {
                val tokenLabel = getTokenLabel(xText)
                chunks.add(LabelRef(nodeContext, tokenLabel, escape(tokenLabel)))
                return
            }
            if (resolver.resolvesToListLabel(xText, node)) {
                chunks.add(ListLabelRef(nodeContext, xText, escapedName))
                return
            }
        }

        val r = factory.grammar.getRule(xText)
        if (r != null) {
            val ruleLabel = getRuleLabel(xText)
            chunks.add(LabelRef(nodeContext, ruleLabel, escape(ruleLabel)))
        }
    }

    override fun qualifiedAttr(
        expr: String,
        x: Token,
        y: Token,
    ) {
        val xText = x.text.orEmpty()
        val yText = y.text.orEmpty()
        val resolver = node.resolver
        if (resolver == null) {
            chunks.add(ActionText(nodeContext, expr))
            return
        }
        if (resolver.resolveToAttribute(xText, node) != null || resolver.resolvesToListLabel(xText, node)) {
            attr(expr, x)
            chunks.add(ActionText(nodeContext, ".$yText"))
            return
        }
        val a = resolver.resolveToAttribute(xText, yText, node)
        if (a == null) {
            factory.grammar.tool.errMgr.grammarError(
                ErrorType.UNKNOWN_SIMPLE_ATTRIBUTE,
                factory.grammar.fileName,
                x,
                xText,
                "rule",
            )
            return
        }
        when (a.dict?.type) {
            AttributeDict.DictType.ARG -> {
                chunks.add(ArgRef(nodeContext, yText, escape(yText)))
            }

            AttributeDict.DictType.RET -> {
                chunks.add(QRetValueRef(nodeContext, getRuleLabel(xText), yText, escape(yText)))
            }

            AttributeDict.DictType.PREDEFINED_RULE -> {
                val prop = createRulePropertyRef(nodeContext, getRuleLabel(xText), yText)
                if (prop != null) {
                    chunks.add(prop)
                }
            }

            AttributeDict.DictType.TOKEN -> {
                val prop = createTokenPropertyRef(nodeContext, getTokenLabel(xText), yText)
                if (prop != null) {
                    chunks.add(prop)
                }
            }

            else -> {}
        }
    }

    override fun setAttr(
        expr: String,
        x: Token,
        rhs: Token,
    ) {
        val xText = x.text.orEmpty()
        val rhsChunks = translateActionChunk(factory, rf, rhs.text.orEmpty(), node)
        chunks.add(SetAttr(nodeContext, xText, escape(xText), rhsChunks))
    }

    override fun nonLocalAttr(
        expr: String,
        x: Token,
        y: Token,
    ) {
        val xText = x.text.orEmpty()
        val yText = y.text.orEmpty()
        val r = factory.grammar.getRule(xText) ?: return
        chunks.add(NonLocalAttrRef(nodeContext, xText, yText, escape(yText), r.index))
    }

    override fun setNonLocalAttr(
        expr: String,
        x: Token,
        y: Token,
        rhs: Token,
    ) {
        val xText = x.text.orEmpty()
        val yText = y.text.orEmpty()
        val r = factory.grammar.getRule(xText) ?: return
        val rhsChunks = translateActionChunk(factory, rf, rhs.text.orEmpty(), node)
        chunks.add(SetNonLocalAttr(nodeContext, xText, yText, escape(yText), r.index, rhsChunks))
    }

    override fun text(text: String) {
        chunks.add(ActionText(nodeContext, text))
    }

    fun getTokenLabel(x: String): String {
        if (node.resolver?.resolvesToLabel(x, node) == true) return x
        return factory.generator.target.getImplicitTokenLabel(x)
    }

    fun getRuleLabel(x: String): String {
        if (node.resolver?.resolvesToLabel(x, node) == true) return x
        return factory.generator.target.getImplicitRuleLabel(x)
    }

    companion object {
        fun createTokenPropertyRef(
            ctx: StructDecl?,
            label: String,
            prop: String,
        ): TokenPropertyRef? =
            when (prop) {
                "text" -> TokenPropertyRefText(ctx, label)
                "type" -> TokenPropertyRefType(ctx, label)
                "line" -> TokenPropertyRefLine(ctx, label)
                "pos" -> TokenPropertyRefPos(ctx, label)
                "channel" -> TokenPropertyRefChannel(ctx, label)
                "index" -> TokenPropertyRefIndex(ctx, label)
                "int" -> TokenPropertyRefInt(ctx, label)
                else -> null
            }

        fun createRulePropertyRef(
            ctx: StructDecl?,
            label: String,
            prop: String,
        ): RulePropertyRef? =
            when (prop) {
                "start" -> RulePropertyRefStart(ctx, label)
                "stop" -> RulePropertyRefStop(ctx, label)
                "text" -> RulePropertyRefText(ctx, label)
                "ctx" -> RulePropertyRefCtx(ctx, label)
                "parser" -> RulePropertyRefParser(ctx, label)
                else -> null
            }

        fun createThisRulePropertyRef(
            ctx: StructDecl?,
            label: String,
            prop: String,
        ): RulePropertyRef? =
            when (prop) {
                "start" -> ThisRulePropertyRefStart(ctx, label)
                "stop" -> ThisRulePropertyRefStop(ctx, label)
                "text" -> ThisRulePropertyRefText(ctx, label)
                "ctx" -> ThisRulePropertyRefCtx(ctx, label)
                "parser" -> ThisRulePropertyRefParser(ctx, label)
                else -> null
            }

        fun translateAction(
            factory: OutputModelFactory,
            rf: RuleFunction?,
            tokenWithinAction: Token?,
            node: ActionAST,
        ): List<ActionChunk> {
            var action = tokenWithinAction?.text ?: node.text
            if (action.isNotEmpty() && action[0] == '{' && action[action.length - 1] == '}') {
                action = action.substring(1, action.length - 1)
                if (node.type == ANTLRParserTypes.SEMPRED && action.endsWith("?")) {
                    action = action.substring(0, action.length - 1)
                }
            } else if (node.type == ANTLRParserTypes.SEMPRED && action.startsWith("{") && action.endsWith("}?")) {
                action = action.substring(1, action.length - 2)
            } else if (node.type == ANTLRParserTypes.ARG_ACTION && action.startsWith("[") && action.endsWith("]")) {
                action = action.substring(1, action.length - 1)
            }
            return translateActionChunk(factory, rf, action, node)
        }

        fun translateActionChunk(
            factory: OutputModelFactory,
            rf: RuleFunction?,
            action: String,
            node: ActionAST,
        ): List<ActionChunk> {
            val translator = ActionTranslator(factory, node)
            translator.rf = rf
            if (rf != null) {
                translator.nodeContext = rf.ruleCtx
                val altAST = node.getAncestor(ANTLRParserTypes.ALT) as? AltAST
                val altLabel = altAST?.altLabel?.text ?: altAST?.leftRecursiveAltInfo?.altLabel
                if (altLabel != null) {
                    val altCtx = rf.altLabelCtxs?.get(altLabel)
                    if (altCtx != null) {
                        translator.nodeContext = altCtx
                    }
                }
            }
            val inStream = CharStreams.fromString(action)
            val tok = node.token
            val splitter =
                ActionSplitter(
                    inStream,
                    translator,
                    tok?.line ?: 1,
                    tok?.charPositionInLine ?: 0,
                )
            splitter.getActionTokens()
            return translator.chunks
        }
    }
}
