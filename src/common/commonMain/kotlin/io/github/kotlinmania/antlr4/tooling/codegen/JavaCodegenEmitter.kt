/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.tooling.codegen.model.Action
import io.github.kotlinmania.antlr4.tooling.codegen.model.BaseListenerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.BaseVisitorFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.LexerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.ListenerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject
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
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.AttributeDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextRuleGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextRuleListGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextRuleListIndexedGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextTokenGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextTokenListGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextTokenListIndexedGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.RuleContextDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.RuleContextListDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.StructDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenListDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenTypeDecl

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
        if (file.genContexts) {
            sb.appendLine("import org.antlr.v4.runtime.*;")
            sb.appendLine("import org.antlr.v4.runtime.tree.*;")
            sb.appendLine("import java.util.List;")
            sb.appendLine("import java.util.ArrayList;")
            sb.appendLine()
            sb.appendLine("public class ${p.name}Contexts {")
            val ctxSuper = file.contextSuperClass?.let { renderChunk(it) } ?: "ParserRuleContext"
            for (rf in p.funcs) {
                renderStructDecl(sb, p.name, rf.ruleCtx, ctxSuper, inContextsFile = true)
                rf.altLabelCtxs?.values?.forEach { altStruct ->
                    renderAltLabelStructDecl(sb, p.name, altStruct, rf.ruleCtx.name)
                }
            }
            sb.appendLine("}")
            return sb.toString()
        }
        if (file.genDFA) {
            sb.appendLine("import org.antlr.v4.runtime.atn.*;")
            sb.appendLine("import org.antlr.v4.runtime.dfa.DFA;")
            sb.appendLine()
            sb.appendLine("public class ${p.name}DFA {")
            renderSerializedJavaATN(sb, p.atn as? SerializedJavaATN)
            sb.appendLine("}")
            return sb.toString()
        }
        if (file.genLean) {
            sb.appendLine("import org.antlr.v4.runtime.atn.*;")
            sb.appendLine("import org.antlr.v4.runtime.dfa.DFA;")
            sb.appendLine("import org.antlr.v4.runtime.*;")
        } else {
            sb.appendLine("import org.antlr.v4.runtime.atn.*;")
            sb.appendLine("import org.antlr.v4.runtime.dfa.DFA;")
            sb.appendLine("import org.antlr.v4.runtime.*;")
            sb.appendLine("import org.antlr.v4.runtime.misc.*;")
            sb.appendLine("import org.antlr.v4.runtime.tree.*;")
            sb.appendLine("import java.util.List;")
            sb.appendLine("import java.util.Iterator;")
            sb.appendLine("import java.util.ArrayList;")
        }
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
        if (!file.genLean) {
            val ctxSuper = file.contextSuperClass?.let { renderChunk(it) } ?: "ParserRuleContext"
            for (rf in p.funcs) {
                renderStructDecl(sb, p.name, rf.ruleCtx, ctxSuper, inContextsFile = false)
                rf.altLabelCtxs?.values?.forEach { altStruct ->
                    renderAltLabelStructDecl(sb, p.name, altStruct, rf.ruleCtx.name)
                }
            }
        }
        for (rf in p.funcs) {
            renderRuleSummary(sb, rf, if (file.genLean) "${p.name}Contexts." else "")
        }
        if (!file.genLean) {
            renderSerializedJavaATN(sb, p.atn as? SerializedJavaATN)
        }
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

    private fun renderStructDecl(
        sb: StringBuilder,
        parserName: String,
        struct: StructDecl,
        ctxSuper: String,
        inContextsFile: Boolean,
    ) {
        sb.appendLine("\tpublic static class ${struct.escapedName} extends $ctxSuper {")
        renderJavaStructAttrsAndGetters(sb, parserName, struct)
        if (struct.ctorAttrs.isNotEmpty()) {
            sb.appendLine("\t\tpublic ${struct.escapedName}(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }")
            val params =
                struct.ctorAttrs.joinToString(", ") { a ->
                    val ad = a as? AttributeDecl
                    "${ad?.type ?: "Object"} ${a.escapedName}"
                }
            sb.appendLine("\t\tpublic ${struct.escapedName}(ParserRuleContext parent, int invokingState, $params) {")
            sb.appendLine("\t\t\tsuper(parent, invokingState);")
            for (a in struct.ctorAttrs) {
                sb.appendLine("\t\t\tthis.${a.escapedName} = ${a.escapedName};")
            }
            sb.appendLine("\t\t}")
        } else {
            sb.appendLine("\t\tpublic ${struct.escapedName}(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }")
        }
        val prefix = if (inContextsFile) "$parserName." else ""
        sb.appendLine("\t\t@Override public int getRuleIndex() { return ${prefix}RULE_${struct.derivedFromName}; }")
        sb.appendLine("\t}")
    }

    private fun renderAltLabelStructDecl(
        sb: StringBuilder,
        parserName: String,
        struct: AltLabelStructDecl,
        parentCtxName: String,
    ) {
        sb.appendLine("\tpublic static class ${struct.escapedName} extends $parentCtxName {")
        renderJavaStructAttrsAndGetters(sb, parserName, struct)
        sb.appendLine("\t\tpublic ${struct.escapedName}($parentCtxName ctx) { copyFrom(ctx); }")
        sb.appendLine("\t}")
    }

    private fun renderJavaStructAttrsAndGetters(
        sb: StringBuilder,
        parserName: String,
        struct: StructDecl,
    ) {
        for (a in struct.attrs) {
            when (a) {
                is TokenListDecl -> sb.appendLine("\t\tpublic List<Token> ${a.escapedName} = new ArrayList<Token>();")
                is TokenDecl -> sb.appendLine("\t\tpublic Token ${a.escapedName};")
                is TokenTypeDecl -> sb.appendLine("\t\tpublic int ${a.escapedName};")
                is RuleContextListDecl -> sb.appendLine("\t\tpublic List<${a.ctxName}> ${a.escapedName} = new ArrayList<${a.ctxName}>();")
                is RuleContextDecl -> sb.appendLine("\t\tpublic ${a.ctxName} ${a.escapedName};")
                is AttributeDecl -> {
                    val initSuffix = if (a.initValue != null) " = ${a.initValue}" else ""
                    sb.appendLine("\t\tpublic ${a.type ?: "Object"} ${a.escapedName}$initSuffix;")
                }
            }
        }
        for (g in struct.getters) {
            val getter = g as? ContextGetterDecl ?: continue
            when (getter) {
                is ContextTokenGetterDecl ->
                    sb.appendLine("\t\tpublic TerminalNode ${getter.name}() { return getToken($parserName.${getter.name}, 0); }")
                is ContextTokenListGetterDecl ->
                    sb.appendLine("\t\tpublic List<TerminalNode> ${getter.name}() { return getTokens($parserName.${getter.name}); }")
                is ContextTokenListIndexedGetterDecl ->
                    sb.appendLine("\t\tpublic TerminalNode ${getter.name}(int i) { return getToken($parserName.${getter.name}, i); }")
                is ContextRuleGetterDecl ->
                    sb.appendLine("\t\tpublic ${getter.ctxName} ${getter.escapedName}() { return getRuleContext(${getter.ctxName}.class, 0); }")
                is ContextRuleListGetterDecl ->
                    sb.appendLine("\t\tpublic List<${getter.ctxName}> ${getter.escapedName}() { return getRuleContexts(${getter.ctxName}.class); }")
                is ContextRuleListIndexedGetterDecl ->
                    sb.appendLine("\t\tpublic ${getter.ctxName} ${getter.escapedName}(int i) { return getRuleContext(${getter.ctxName}.class, i); }")
            }
        }
    }

    private fun renderRuleSummary(
        sb: StringBuilder,
        rf: RuleFunction,
        ctxPrefix: String = "",
    ) {
        val argsSig =
            rf.args?.joinToString(", ") { a -> "${a.type ?: "Object"} ${a.escapedName}" }.orEmpty()
        sb.appendLine("\tpublic final $ctxPrefix${rf.ctxType} ${rf.escapedName}($argsSig) throws RecognitionException {")
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
        sb.appendLine("\tpublic static final ATN _ATN =")
        sb.appendLine("\t\tnew ATNDeserializer().deserialize(_serializedATN.toCharArray());")
    }

    fun renderChunks(chunks: List<ActionChunk>): String =
        chunks.joinToString("") { renderChunk(it) }

    fun renderChunk(chunk: ActionChunk): String {
        val chunkCtx = chunk.ctx
        val ctxRef =
            if (chunkCtx != null) {
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
            is ArgRef -> "_localctx.${chunk.escapedName}"
            is LocalRef -> "_localctx.${chunk.escapedName}"
            is RetValueRef -> "_localctx.${chunk.escapedName}"
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
            is ThisRulePropertyRefStart -> "_localctx.start"
            is ThisRulePropertyRefStop -> "_localctx.stop"
            is ThisRulePropertyRefText -> "_input.getText(_localctx.start, _input.LT(-1))"
            is ThisRulePropertyRefCtx -> "_localctx"
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
