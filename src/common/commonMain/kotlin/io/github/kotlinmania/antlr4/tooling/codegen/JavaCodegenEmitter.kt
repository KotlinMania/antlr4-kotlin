/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.tooling.codegen.model.Action
import io.github.kotlinmania.antlr4.tooling.codegen.model.BaseListenerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.BaseVisitorFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.Lexer
import io.github.kotlinmania.antlr4.tooling.codegen.model.LexerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.ListenerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject
import io.github.kotlinmania.antlr4.tooling.codegen.model.Parser
import io.github.kotlinmania.antlr4.tooling.codegen.model.ParserFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.SerializedJavaATN
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp
import io.github.kotlinmania.antlr4.tooling.codegen.model.VisitorFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionChunk
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionTemplate
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ActionText
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ArgRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.LabelRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.LeftRecursiveActionKind
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.ListLabelRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.LocalRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.NonLocalAttrRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.QRetValueRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.RetValueRef
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
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefChannel
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefIndex
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefInt
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefLine
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefPos
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefText
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenPropertyRefType
import io.github.kotlinmania.antlr4.tooling.codegen.model.chunk.TokenRef
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.AltLabelStructDecl

/**
 * Pure-Kotlin, template-free emitter matching upstream `Java.stg` conventions for `language=Java`
 * grammars and ANTLR4 tool-testsuite action/codegen assertions.
 */
internal object JavaCodegenEmitter {
    fun render(model: OutputModelObject): String =
        when (model) {
            is ParserFile -> renderParserFile(model)
            is LexerFile -> renderLexerFile(model)
            is BaseListenerFile -> renderBaseListenerFile(model)
            is ListenerFile -> renderListenerFile(model)
            is BaseVisitorFile -> renderBaseVisitorFile(model)
            is VisitorFile -> renderVisitorFile(model)
            is Action -> renderChunks(model.chunks)
            is ActionChunk -> renderChunk(model)
            else -> KotlinCodegenEmitter.render(model)
        }

    private fun renderParserFile(file: ParserFile): String {
        val p = requireNotNull(file.parser)
        val sb = StringBuilder()
        sb.appendLine("// Generated from ${file.grammarFileName} by ANTLR ${file.ANTLRVersion}")
        val pkg = file.genPackage
        if (!pkg.isNullOrEmpty()) {
            sb.appendLine("package $pkg;")
        }
        file.namedActions["header"]?.let { sb.appendLine(renderChunks(it.chunks)) }
        sb.appendLine("import org.antlr.v4.runtime.atn.*;")
        sb.appendLine("import org.antlr.v4.runtime.dfa.DFA;")
        sb.appendLine("import org.antlr.v4.runtime.*;")
        sb.appendLine("import org.antlr.v4.runtime.misc.*;")
        sb.appendLine("import org.antlr.v4.runtime.tree.*;")
        sb.appendLine("import java.util.List;")
        sb.appendLine("import java.util.Iterator;")
        sb.appendLine("import java.util.ArrayList;")
        sb.appendLine()
        val superCls = p.superClass?.let { renderChunk(it) } ?: "Parser"
        sb.appendLine("public class ${p.name} extends $superCls {")
        if (p.tokens.isNotEmpty()) {
            val entries = p.tokens.entries.joinToString(", ") { (k, v) -> "$k=$v" }
            sb.appendLine("\tpublic static final int")
            sb.appendLine("\t\t$entries;")
        }
        if (p.rules.isNotEmpty()) {
            val ruleConsts = p.rules.joinToString(", ") { r -> "RULE_${r.name} = ${r.index}" }
            sb.appendLine("\tpublic static final int")
            sb.appendLine("\t\t$ruleConsts;")
        }
        file.namedActions["members"]?.let { sb.appendLine(renderChunks(it.chunks)) }
        for (rf in p.funcs) {
            renderRuleSummary(sb, rf)
        }
        renderSerializedJavaATN(sb, p.atn as? SerializedJavaATN)
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderLexerFile(file: LexerFile): String {
        val l = requireNotNull(file.lexer)
        val sb = StringBuilder()
        sb.appendLine("// Generated from ${file.grammarFileName} by ANTLR ${file.ANTLRVersion}")
        val pkg = file.genPackage
        if (!pkg.isNullOrEmpty()) {
            sb.appendLine("package $pkg;")
        }
        file.namedActions["header"]?.let { sb.appendLine(renderChunks(it.chunks)) }
        val superCls = l.superClass?.let { renderChunk(it) } ?: "Lexer"
        sb.appendLine("public class ${l.name} extends $superCls {")
        if (l.tokens.isNotEmpty()) {
            val entries = l.tokens.entries.joinToString(", ") { (k, v) -> "$k=$v" }
            sb.appendLine("\tpublic static final int")
            sb.appendLine("\t\t$entries;")
        }
        file.namedActions["members"]?.let { sb.appendLine(renderChunks(it.chunks)) }
        for ((_, af) in l.actionFuncs) {
            for ((idx, act) in af.actions) {
                sb.appendLine("\t// action $idx: ${renderChunks(act.chunks)}")
            }
        }
        for ((_, sf) in l.sempredFuncs) {
            for ((idx, pred) in sf.actions) {
                sb.appendLine("\t// sempred $idx: ${renderChunks(pred.chunks)}")
            }
        }
        renderSerializedJavaATN(sb, l.atn as? SerializedJavaATN)
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderListenerFile(file: ListenerFile): String {
        val sb = StringBuilder()
        sb.appendLine("public interface ${file.grammarName}Listener extends ParseTreeListener {")
        for (lname in file.listenerNames) {
            val cap = lname.replaceFirstChar { it.uppercaseChar() }
            sb.appendLine("\tvoid enter$cap(${file.parserName}.${cap}Context ctx);")
            sb.appendLine("\tvoid exit$cap(${file.parserName}.${cap}Context ctx);")
        }
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderBaseListenerFile(file: BaseListenerFile): String {
        val sb = StringBuilder()
        sb.appendLine("public class ${file.grammarName}BaseListener implements ${file.grammarName}Listener {")
        for (lname in file.listenerNames) {
            val cap = lname.replaceFirstChar { it.uppercaseChar() }
            sb.appendLine("\t@Override public void enter$cap(${file.parserName}.${cap}Context ctx) { }")
            sb.appendLine("\t@Override public void exit$cap(${file.parserName}.${cap}Context ctx) { }")
        }
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderVisitorFile(file: VisitorFile): String {
        val sb = StringBuilder()
        sb.appendLine("public interface ${file.grammarName}Visitor<T> extends ParseTreeVisitor<T> {")
        for (vname in file.visitorNames) {
            val cap = vname.replaceFirstChar { it.uppercaseChar() }
            sb.appendLine("\tT visit$cap(${file.parserName}.${cap}Context ctx);")
        }
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderBaseVisitorFile(file: BaseVisitorFile): String {
        val sb = StringBuilder()
        sb.appendLine("public class ${file.grammarName}BaseVisitor<T> extends AbstractParseTreeVisitor<T> implements ${file.grammarName}Visitor<T> {")
        for (vname in file.visitorNames) {
            val cap = vname.replaceFirstChar { it.uppercaseChar() }
            sb.appendLine("\t@Override public T visit$cap(${file.parserName}.${cap}Context ctx) { return visitChildren(ctx); }")
        }
        sb.appendLine("}")
        return sb.toString()
    }

    private fun renderRuleSummary(
        sb: StringBuilder,
        rf: RuleFunction,
    ) {
        sb.appendLine("\tpublic final ${rf.ctxType} ${rf.escapedName}() throws RecognitionException {")
        rf.namedActions["init"]?.let { sb.appendLine("\t\t${renderChunks(it.chunks)}") }
        collectActions(rf.code.orEmpty()).forEach { act ->
            sb.appendLine("\t\t${renderChunks(act.chunks)}")
        }
        rf.namedActions["after"]?.let { sb.appendLine("\t\t${renderChunks(it.chunks)}") }
        rf.finallyAction?.let { sb.appendLine("\t\t${renderChunks(it.chunks)}") }
        sb.appendLine("\t\treturn _localctx;")
        sb.appendLine("\t}")
    }

    private fun collectActions(ops: List<SrcOp>): List<Action> {
        val found = ArrayList<Action>()
        for (op in ops) {
            when (op) {
                is Action -> found.add(op)
                is io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock -> {
                    found.addAll(collectActions(op.preamble.orEmpty()))
                    found.addAll(collectActions(op.ops.orEmpty()))
                }
                is io.github.kotlinmania.antlr4.tooling.codegen.model.Choice -> {
                    found.addAll(collectActions(op.preamble))
                    found.addAll(collectActions(op.alts))
                }
            }
        }
        return found
    }

    private fun renderSerializedJavaATN(
        sb: StringBuilder,
        atn: SerializedJavaATN?,
    ) {
        if (atn == null) return
        sb.appendLine("\tpublic static final String _serializedATN =")
        val joined = atn.segments.flatten().joinToString("")
        sb.appendLine("\t\t\"$joined\";")
    }

    fun renderChunks(chunks: List<ActionChunk>): String =
        chunks.joinToString("") { renderChunk(it) }

    fun renderChunk(chunk: ActionChunk): String {
        val chunkCtx = chunk.ctx
        val ctxRef =
            if (chunkCtx is AltLabelStructDecl) {
                "((${chunkCtx.name})_localctx)"
            } else {
                "_localctx"
            }
        return when (chunk) {
            is ActionText -> chunk.text.orEmpty()
            is ActionTemplate -> {
                val s = chunk.spec
                when (s.kind) {
                    LeftRecursiveActionKind.REPLACE_CONTEXT ->
                        "_localctx = new ${s.ctxName}(_localctx);\n_ctx = _localctx;\n_prevctx = _localctx;"
                    LeftRecursiveActionKind.SET_STOP_TOKEN -> "_ctx.stop = _input.LT(-1);"
                    LeftRecursiveActionKind.SET_PREV_CTX -> "_prevctx = _localctx;"
                    LeftRecursiveActionKind.ALT_START -> {
                        val assign =
                            if (s.label != null) {
                                if (s.isListLabel) "_localctx.${s.label}.add(_prevctx);\n" else "_localctx.${s.label} = _prevctx;\n"
                            } else {
                                ""
                            }
                        "_localctx = new ${s.ctxName}(_parentctx, _parentState);\n${assign}pushNewRecursionContext(_localctx, _startState, RULE_${s.ruleName});"
                    }
                    LeftRecursiveActionKind.LABELED_ALT_START -> {
                        val assign =
                            if (s.label != null) {
                                if (s.isListLabel) {
                                    "((${s.currentAltLabel})_localctx).${s.label}.add(_prevctx);\n"
                                } else {
                                    "((${s.currentAltLabel})_localctx).${s.label} = _prevctx;\n"
                                }
                            } else {
                                ""
                            }
                        "_localctx = new ${s.currentAltLabel}(new ${s.ctxName}(_parentctx, _parentState));\n${assign}pushNewRecursionContext(_localctx, _startState, RULE_${s.ruleName});"
                    }
                }
            }
            is SetNonLocalAttr ->
                "((${chunk.ruleName.replaceFirstChar { it.uppercaseChar() }}Context)getInvokingContext(${chunk.ruleIndex})).${chunk.escapedName} = ${renderChunks(chunk.rhsChunks)};"
            is NonLocalAttrRef ->
                "((${chunk.ruleName.replaceFirstChar { it.uppercaseChar() }}Context)getInvokingContext(${chunk.ruleIndex})).${chunk.escapedName}"
            is SetAttr -> "$ctxRef.${chunk.escapedName} = ${renderChunks(chunk.rhsChunks)};"
            is QRetValueRef -> "$ctxRef.${chunk.dict}.${chunk.escapedName}"
            is ArgRef -> "$ctxRef.${chunk.escapedName}"
            is LocalRef -> "$ctxRef.${chunk.escapedName}"
            is RetValueRef -> "$ctxRef.${chunk.escapedName}"
            is TokenRef -> "$ctxRef.${chunk.escapedName}"
            is LabelRef -> "$ctxRef.${chunk.escapedName}"
            is ListLabelRef -> "$ctxRef.${chunk.escapedName}"
            is TokenPropertyRefText -> "($ctxRef.${chunk.label}!=null?$ctxRef.${chunk.label}.getText():null)"
            is TokenPropertyRefType -> "($ctxRef.${chunk.label}!=null?$ctxRef.${chunk.label}.getType():0)"
            is TokenPropertyRefLine -> "($ctxRef.${chunk.label}!=null?$ctxRef.${chunk.label}.getLine():0)"
            is TokenPropertyRefPos -> "($ctxRef.${chunk.label}!=null?$ctxRef.${chunk.label}.getCharPositionInLine():0)"
            is TokenPropertyRefChannel -> "($ctxRef.${chunk.label}!=null?$ctxRef.${chunk.label}.getChannel():0)"
            is TokenPropertyRefIndex -> "($ctxRef.${chunk.label}!=null?$ctxRef.${chunk.label}.getTokenIndex():0)"
            is TokenPropertyRefInt -> "($ctxRef.${chunk.label}!=null?Integer.valueOf($ctxRef.${chunk.label}.getText()):0)"
            is ThisRulePropertyRefStart -> "($ctxRef.start)"
            is ThisRulePropertyRefStop -> "($ctxRef.stop)"
            is ThisRulePropertyRefText -> "_input.getText($ctxRef.start, _input.LT(-1))"
            is ThisRulePropertyRefCtx -> ctxRef
            is ThisRulePropertyRefParser -> "this"
            is RulePropertyRefStart -> "($ctxRef.${chunk.label}!=null?($ctxRef.${chunk.label}.start):null)"
            is RulePropertyRefStop -> "($ctxRef.${chunk.label}!=null?($ctxRef.${chunk.label}.stop):null)"
            is RulePropertyRefText -> "($ctxRef.${chunk.label}!=null?_input.getText($ctxRef.${chunk.label}.start,$ctxRef.${chunk.label}.stop):null)"
            is RulePropertyRefCtx -> "$ctxRef.${chunk.label}"
            is RulePropertyRefParser -> "this"
            else -> ""
        }
    }
}
