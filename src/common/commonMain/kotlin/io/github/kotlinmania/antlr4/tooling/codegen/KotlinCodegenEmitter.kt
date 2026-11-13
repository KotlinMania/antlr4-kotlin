/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.codegen

import io.github.kotlinmania.antlr4.tooling.ast.QuantifierAST
import io.github.kotlinmania.antlr4.tooling.codegen.model.Action
import io.github.kotlinmania.antlr4.tooling.codegen.model.AddToLabelList
import io.github.kotlinmania.antlr4.tooling.codegen.model.AltBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.BaseListenerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.BaseVisitorFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.CaptureNextToken
import io.github.kotlinmania.antlr4.tooling.codegen.model.CaptureNextTokenType
import io.github.kotlinmania.antlr4.tooling.codegen.model.CodeBlockForOuterMostAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.DispatchMethod
import io.github.kotlinmania.antlr4.tooling.codegen.model.ExceptionClause
import io.github.kotlinmania.antlr4.tooling.codegen.model.InvokeRule
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1AltBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1OptionalBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1OptionalBlockSingleAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1PlusBlockSingleAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.LL1StarBlockSingleAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.LeftRecursiveRuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.Lexer
import io.github.kotlinmania.antlr4.tooling.codegen.model.LexerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.ListenerDispatchMethod
import io.github.kotlinmania.antlr4.tooling.codegen.model.ListenerFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.MatchNotSet
import io.github.kotlinmania.antlr4.tooling.codegen.model.MatchSet
import io.github.kotlinmania.antlr4.tooling.codegen.model.MatchToken
import io.github.kotlinmania.antlr4.tooling.codegen.model.OptionalBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.OutputModelObject
import io.github.kotlinmania.antlr4.tooling.codegen.model.Parser
import io.github.kotlinmania.antlr4.tooling.codegen.model.ParserFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.PlusBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleActionFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.RuleSempredFunction
import io.github.kotlinmania.antlr4.tooling.codegen.model.SemPred
import io.github.kotlinmania.antlr4.tooling.codegen.model.SerializedATN
import io.github.kotlinmania.antlr4.tooling.codegen.model.SrcOp
import io.github.kotlinmania.antlr4.tooling.codegen.model.StarBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.Sync
import io.github.kotlinmania.antlr4.tooling.codegen.model.TestSetInline
import io.github.kotlinmania.antlr4.tooling.codegen.model.ThrowEarlyExitException
import io.github.kotlinmania.antlr4.tooling.codegen.model.ThrowNoViableAlt
import io.github.kotlinmania.antlr4.tooling.codegen.model.VisitorDispatchMethod
import io.github.kotlinmania.antlr4.tooling.codegen.model.VisitorFile
import io.github.kotlinmania.antlr4.tooling.codegen.model.Wildcard
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
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.CodeBlock
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextRuleGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextRuleListGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextRuleListIndexedGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextTokenGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextTokenListGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.ContextTokenListIndexedGetterDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.Decl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.RuleContextDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.RuleContextListDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.StructDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenListDecl
import io.github.kotlinmania.antlr4.tooling.codegen.model.decl.TokenTypeDecl

/**
 * Pure-Kotlin, template-free, reflection-free emitter rendering [OutputModelObject] hierarchies
 * into clean, warning-free Kotlin Multiplatform source files targeting `io.github.kotlinmania.antlr4`.
 */
internal object KotlinCodegenEmitter {
    fun render(model: OutputModelObject): String =
        when (model) {
            is ParserFile -> renderParserFile(model)
            is LexerFile -> renderLexerFile(model)
            is BaseListenerFile -> renderBaseListenerFile(model)
            is ListenerFile -> renderListenerFile(model)
            is BaseVisitorFile -> renderBaseVisitorFile(model)
            is VisitorFile -> renderVisitorFile(model)
            is Action -> renderChunks(model.chunks, localCtxExpr = "localCtx")
            is ActionChunk -> renderChunk(model, localCtxExpr = "localCtx")
            else -> model.toString()
        }

    private fun renderParserFile(file: ParserFile): String {
        val p = requireNotNull(file.parser) { "ParserFile.parser must be populated." }
        val body = StringBuilder()
        renderParserClass(body, file, p)
        val bodyText = body.toString()

        val out = StringBuilder()
        out.appendLine("// Generated from ${file.grammarFileName} by ANTLR ${file.ANTLRVersion}")
        val pkg = file.genPackage
        if (!pkg.isNullOrEmpty()) {
            out.appendLine("package $pkg")
            out.appendLine()
        }
        val headerAction = file.namedActions["header"]
        if (headerAction != null) {
            out.appendLine(renderChunks(headerAction.chunks, "localCtx").trim())
            out.appendLine()
        }

        val imports = collectNeededImports(bodyText, isLexer = false)
        if (imports.isNotEmpty()) {
            for (imp in imports) {
                out.appendLine("import $imp")
            }
            out.appendLine()
        }
        out.append(bodyText)
        return out.toString()
    }

    private fun renderLexerFile(file: LexerFile): String {
        val l = requireNotNull(file.lexer) { "LexerFile.lexer must be populated." }
        val body = StringBuilder()
        renderLexerClass(body, file, l)
        val bodyText = body.toString()

        val out = StringBuilder()
        out.appendLine("// Generated from ${file.grammarFileName} by ANTLR ${file.ANTLRVersion}")
        val pkg = file.genPackage
        if (!pkg.isNullOrEmpty()) {
            out.appendLine("package $pkg")
            out.appendLine()
        }
        val headerAction = file.namedActions["header"]
        if (headerAction != null) {
            out.appendLine(renderChunks(headerAction.chunks, "localCtx").trim())
            out.appendLine()
        }

        val imports = collectNeededImports(bodyText, isLexer = true)
        if (imports.isNotEmpty()) {
            for (imp in imports) {
                out.appendLine("import $imp")
            }
            out.appendLine()
        }
        out.append(bodyText)
        return out.toString()
    }

    private fun renderListenerFile(file: ListenerFile): String {
        val out = StringBuilder()
        out.appendLine("// Generated from ${file.grammarFileName} by ANTLR ${file.ANTLRVersion}")
        val pkg = file.genPackage
        if (!pkg.isNullOrEmpty()) {
            out.appendLine("package $pkg")
            out.appendLine()
        }
        val header = file.header
        if (header != null) {
            out.appendLine(renderChunks(header.chunks, "ctx").trim())
            out.appendLine()
        }
        out.appendLine("import io.github.kotlinmania.antlr4.tree.ParseTreeListener")
        out.appendLine()
        val vis = visibilityPrefix(file.accessLevel)
        if (file.listenerNames.isEmpty()) {
            out.appendLine("${vis}interface ${file.grammarName}Listener : ParseTreeListener")
            return out.toString()
        }
        out.appendLine("${vis}interface ${file.grammarName}Listener : ParseTreeListener {")
        var first = true
        for (lname in file.listenerNames) {
            if (!first) out.appendLine()
            first = false
            val cap = capitalize(lname)
            val ctxType = "${file.parserName}.${cap}Context"
            out.appendLine("    fun enter$cap(ctx: $ctxType)")
            out.appendLine()
            out.appendLine("    fun exit$cap(ctx: $ctxType)")
        }
        out.appendLine("}")
        return out.toString()
    }

    private fun renderBaseListenerFile(file: BaseListenerFile): String {
        val out = StringBuilder()
        out.appendLine("// Generated from ${file.grammarFileName} by ANTLR ${file.ANTLRVersion}")
        val pkg = file.genPackage
        if (!pkg.isNullOrEmpty()) {
            out.appendLine("package $pkg")
            out.appendLine()
        }
        val header = file.header
        if (header != null) {
            out.appendLine(renderChunks(header.chunks, "ctx").trim())
            out.appendLine()
        }
        out.appendLine("import io.github.kotlinmania.antlr4.ParserRuleContext")
        out.appendLine("import io.github.kotlinmania.antlr4.tree.ErrorNode")
        out.appendLine("import io.github.kotlinmania.antlr4.tree.TerminalNode")
        out.appendLine()
        val vis = visibilityPrefix(file.accessLevel)
        out.appendLine("${vis}open class ${file.grammarName}BaseListener : ${file.grammarName}Listener {")
        for (lname in file.listenerNames) {
            val cap = capitalize(lname)
            val ctxType = "${file.parserName}.${cap}Context"
            out.appendLine("    override fun enter$cap(ctx: $ctxType) {}")
            out.appendLine()
            out.appendLine("    override fun exit$cap(ctx: $ctxType) {}")
            out.appendLine()
        }
        out.appendLine("    override fun enterEveryRule(ctx: ParserRuleContext?) {}")
        out.appendLine()
        out.appendLine("    override fun exitEveryRule(ctx: ParserRuleContext?) {}")
        out.appendLine()
        out.appendLine("    override fun visitTerminal(node: TerminalNode?) {}")
        out.appendLine()
        out.appendLine("    override fun visitErrorNode(node: ErrorNode?) {}")
        out.appendLine("}")
        return out.toString()
    }

    private fun renderVisitorFile(file: VisitorFile): String {
        val out = StringBuilder()
        out.appendLine("// Generated from ${file.grammarFileName} by ANTLR ${file.ANTLRVersion}")
        val pkg = file.genPackage
        if (!pkg.isNullOrEmpty()) {
            out.appendLine("package $pkg")
            out.appendLine()
        }
        val header = file.header
        if (header != null) {
            out.appendLine(renderChunks(header.chunks, "ctx").trim())
            out.appendLine()
        }
        out.appendLine("import io.github.kotlinmania.antlr4.tree.ParseTreeVisitor")
        out.appendLine()
        val vis = visibilityPrefix(file.accessLevel)
        if (file.visitorNames.isEmpty()) {
            out.appendLine("${vis}interface ${file.grammarName}Visitor<out T> : ParseTreeVisitor<T>")
            return out.toString()
        }
        out.appendLine("${vis}interface ${file.grammarName}Visitor<out T> : ParseTreeVisitor<T> {")
        var first = true
        for (vname in file.visitorNames) {
            if (!first) out.appendLine()
            first = false
            val cap = capitalize(vname)
            val ctxType = "${file.parserName}.${cap}Context"
            out.appendLine("    fun visit$cap(ctx: $ctxType): T?")
        }
        out.appendLine("}")
        return out.toString()
    }

    private fun renderBaseVisitorFile(file: BaseVisitorFile): String {
        val out = StringBuilder()
        out.appendLine("// Generated from ${file.grammarFileName} by ANTLR ${file.ANTLRVersion}")
        val pkg = file.genPackage
        if (!pkg.isNullOrEmpty()) {
            out.appendLine("package $pkg")
            out.appendLine()
        }
        val header = file.header
        if (header != null) {
            out.appendLine(renderChunks(header.chunks, "ctx").trim())
            out.appendLine()
        }
        out.appendLine("import io.github.kotlinmania.antlr4.tree.AbstractParseTreeVisitor")
        out.appendLine()
        val vis = visibilityPrefix(file.accessLevel)
        if (file.visitorNames.isEmpty()) {
            out.appendLine("${vis}open class ${file.grammarName}BaseVisitor<T> : AbstractParseTreeVisitor<T>(), ${file.grammarName}Visitor<T>")
            return out.toString()
        }
        out.appendLine("${vis}open class ${file.grammarName}BaseVisitor<T> : AbstractParseTreeVisitor<T>(), ${file.grammarName}Visitor<T> {")
        var first = true
        for (vname in file.visitorNames) {
            if (!first) out.appendLine()
            first = false
            val cap = capitalize(vname)
            val ctxType = "${file.parserName}.${cap}Context"
            out.appendLine("    override fun visit$cap(ctx: $ctxType): T? = visitChildren(ctx)")
        }
        out.appendLine("}")
        return out.toString()
    }

    private fun renderParserClass(
        sb: StringBuilder,
        file: ParserFile,
        p: Parser,
    ) {
        val vis = visibilityPrefix(p.accessLevel)
        if (file.genContexts) {
            sb.appendLine("${vis}open class ${p.name}Contexts {")
            val ctxSuper = file.contextSuperClass?.let { renderChunk(it, "localCtx") } ?: "ParserRuleContext"
            var first = true
            for (rf in p.funcs) {
                if (!first) sb.appendLine()
                first = false
                renderRuleContextStruct(sb, file, p, rf, rf.ruleCtx, ctxSuper)
                val altCtxs = rf.altLabelCtxs
                if (altCtxs != null) {
                    for (altStruct in altCtxs.values) {
                        sb.appendLine()
                        renderAltLabelStruct(sb, file, p, altStruct, rf.ruleCtx.name)
                    }
                }
            }
            sb.appendLine("}")
            return
        }
        if (file.genDFA) {
            sb.appendLine("${vis}object ${p.name}DFA {")
            renderRecognizerCompanionBody(sb, p, isLexer = false, indent = "    ")
            sb.appendLine("}")
            return
        }
        val superCls = p.superClass?.let { renderChunk(it, "localCtx") } ?: "Parser"
        sb.appendLine("${vis}open class ${p.name}(")
        sb.appendLine("    input: TokenStream?,")
        sb.appendLine(") : $superCls(input) {")
        val dfaRef = if (file.genLean) "${p.name}DFA." else ""
        sb.appendLine("    init {")
        sb.appendLine("        interpreter = ParserATNSimulator(this, ${dfaRef}ATN_DATA, ${dfaRef}decisionToDFA, ${dfaRef}sharedContextCache)")
        sb.appendLine("    }")
        sb.appendLine()
        sb.appendLine("    override val grammarFileName: String")
        sb.appendLine("        get() = ${quoteString(p.grammarFileName)}")
        sb.appendLine()
        sb.appendLine("    override val tokenNames: List<String>")
        sb.appendLine("        get() = ${dfaRef}TOKEN_NAMES")
        sb.appendLine()
        sb.appendLine("    override val ruleNames: List<String>")
        sb.appendLine("        get() = ${dfaRef}RULE_NAMES")
        sb.appendLine()
        sb.appendLine("    override val vocabulary: Vocabulary")
        sb.appendLine("        get() = ${dfaRef}VOCABULARY")
        sb.appendLine()
        sb.appendLine("    override val serializedATN: String")
        sb.appendLine("        get() = ${dfaRef}SERIALIZED_ATN")
        sb.appendLine()
        sb.appendLine("    override val atn: ATN")
        sb.appendLine("        get() = ${dfaRef}ATN_DATA")

        val membersAction = file.namedActions["members"]
        if (membersAction != null) {
            sb.appendLine()
            appendIndented(sb, renderChunks(membersAction.chunks, "ctx").trim(), "    ")
        }

        if (p.tokens.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("    object Tokens {")
            for ((tokName, tokType) in p.tokens) {
                val escaped =
                    p.factory
                        ?.generator
                        ?.target
                        ?.escapeIfNeeded(tokName) ?: tokName
                sb.appendLine("        const val $escaped: Int = $tokType")
            }
            sb.appendLine("    }")
        }

        val ctxSuper = file.contextSuperClass?.let { renderChunk(it, "localCtx") } ?: "ParserRuleContext"
        for (rf in p.funcs) {
            if (!file.genLean) {
                sb.appendLine()
                renderRuleContextStruct(sb, file, p, rf, rf.ruleCtx, ctxSuper)
                val altCtxs = rf.altLabelCtxs
                if (altCtxs != null) {
                    for (altStruct in altCtxs.values) {
                        sb.appendLine()
                        renderAltLabelStruct(sb, file, p, altStruct, rf.ruleCtx.name)
                    }
                }
            }
            sb.appendLine()
            renderRuleFunction(sb, p, rf)
        }

        if (p.sempredFuncs.isNotEmpty()) {
            sb.appendLine()
            renderSempredDispatch(sb, p.sempredFuncs, isLexer = false)
        }

        if (!file.genLean) {
            sb.appendLine()
            renderRecognizerCompanion(sb, p, isLexer = false)
        }
        sb.appendLine("}")
    }

    private fun renderLexerClass(
        sb: StringBuilder,
        file: LexerFile,
        l: Lexer,
    ) {
        val vis = visibilityPrefix(l.accessLevel)
        val superCls = l.superClass?.let { renderChunk(it, "ctx") } ?: "Lexer"
        sb.appendLine("${vis}open class ${l.name}(")
        sb.appendLine("    input: CharStream?,")
        sb.appendLine(") : $superCls(input) {")
        sb.appendLine("    init {")
        sb.appendLine("        interpreter = LexerATNSimulator(this, ATN_DATA, decisionToDFA, sharedContextCache)")
        sb.appendLine("    }")
        sb.appendLine()
        sb.appendLine("    override val grammarFileName: String")
        sb.appendLine("        get() = ${quoteString(l.grammarFileName)}")
        sb.appendLine()
        sb.appendLine("    override val tokenNames: List<String>")
        sb.appendLine("        get() = TOKEN_NAMES")
        sb.appendLine()
        sb.appendLine("    override val ruleNames: List<String>")
        sb.appendLine("        get() = RULE_NAMES")
        sb.appendLine()
        sb.appendLine("    override val vocabulary: Vocabulary")
        sb.appendLine("        get() = VOCABULARY")
        sb.appendLine()
        sb.appendLine("    override val serializedATN: String")
        sb.appendLine("        get() = SERIALIZED_ATN")
        sb.appendLine()
        sb.appendLine("    override val channelNames: List<String>")
        sb.appendLine("        get() = CHANNEL_NAMES")
        sb.appendLine()
        sb.appendLine("    override val modeNames: List<String>")
        sb.appendLine("        get() = MODE_NAMES")
        sb.appendLine()
        sb.appendLine("    override val atn: ATN")
        sb.appendLine("        get() = ATN_DATA")

        val membersAction = file.namedActions["members"]
        if (membersAction != null) {
            sb.appendLine()
            appendIndented(sb, renderChunks(membersAction.chunks, "ctx").trim(), "    ")
        }

        if (l.tokens.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("    object Tokens {")
            for ((tokName, tokType) in l.tokens) {
                val escaped =
                    l.factory
                        ?.generator
                        ?.target
                        ?.escapeIfNeeded(tokName) ?: tokName
                sb.appendLine("        const val $escaped: Int = $tokType")
            }
            sb.appendLine("    }")
        }

        if (l.channels.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("    object Channels {")
            for ((chName, chVal) in l.channels) {
                val escaped =
                    l.factory
                        ?.generator
                        ?.target
                        ?.escapeIfNeeded(chName) ?: chName
                sb.appendLine("        const val $escaped: Int = $chVal")
            }
            sb.appendLine("    }")
        }

        val nonDefaultModes = l.modes.drop(1)
        if (nonDefaultModes.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("    object Modes {")
            var idx = 1
            for (modeName in nonDefaultModes) {
                val escaped =
                    l.factory
                        ?.generator
                        ?.target
                        ?.escapeIfNeeded(modeName) ?: modeName
                sb.appendLine("        const val $escaped: Int = $idx")
                idx++
            }
            sb.appendLine("    }")
        }

        if (l.actionFuncs.isNotEmpty()) {
            sb.appendLine()
            renderLexerActionDispatch(sb, l.actionFuncs)
        }

        if (l.sempredFuncs.isNotEmpty()) {
            sb.appendLine()
            renderSempredDispatch(sb, l.sempredFuncs, isLexer = true)
        }

        sb.appendLine()
        renderRecognizerCompanion(sb, l, isLexer = true)
        sb.appendLine("}")
    }

    private fun renderRuleContextStruct(
        sb: StringBuilder,
        file: ParserFile,
        parser: Parser,
        rf: RuleFunction,
        struct: StructDecl,
        ctxSuper: String,
    ) {
        val ruleConst = ruleIndexConstName(rf.name)
        sb.appendLine("    open class ${struct.name} : $ctxSuper {")
        renderStructFields(sb, struct, "        ")

        sb.appendLine("        constructor(parent: ParserRuleContext?, invokingState: Int) : super(parent, invokingState)")
        if (struct.ctorAttrs.isNotEmpty()) {
            val params =
                struct.ctorAttrs.joinToString(", ") { attr ->
                    "${attr.escapedName}: ${mapKotlinType((attr as? AttributeDecl)?.type, nonNull = false)}"
                }
            sb.appendLine()
            sb.appendLine("        constructor(parent: ParserRuleContext?, invokingState: Int, $params) : super(parent, invokingState) {")
            for (attr in struct.ctorAttrs) {
                sb.appendLine("            this.${attr.escapedName} = ${attr.escapedName}")
            }
            sb.appendLine("        }")
        }
        if (struct.provideCopyFrom) {
            sb.appendLine()
            sb.appendLine("        constructor() : super()")
            sb.appendLine()
            sb.appendLine("        fun copyFrom(ctx: ${struct.name}) {")
            sb.appendLine("            super.copyFrom(ctx)")
            for (attr in struct.attrs) {
                if (attr is TokenListDecl || attr is RuleContextListDecl) {
                    sb.appendLine("            this.${attr.escapedName}.clear()")
                    sb.appendLine("            this.${attr.escapedName}.addAll(ctx.${attr.escapedName})")
                } else {
                    sb.appendLine("            this.${attr.escapedName} = ctx.${attr.escapedName}")
                }
            }
            sb.appendLine("        }")
        }

        sb.appendLine()
        sb.appendLine("        override var ruleIndex: Int")
        sb.appendLine("            get() = $ruleConst")
        sb.appendLine("            set(_) {}")

        renderStructGetters(sb, struct, "        ")
        renderStructDispatchMethods(sb, file, struct, "        ")
        sb.appendLine("    }")
    }

    private fun renderAltLabelStruct(
        sb: StringBuilder,
        file: ParserFile,
        parser: Parser,
        struct: AltLabelStructDecl,
        parentCtxName: String,
    ) {
        val hasMembers =
            struct.attrs.isNotEmpty() || struct.getters.isNotEmpty() || !struct.dispatchMethods.isNullOrEmpty()
        if (!hasMembers) {
            sb.appendLine("    class ${struct.name}(")
            sb.appendLine("        ctx: $parentCtxName,")
            sb.appendLine("    ) : $parentCtxName() {")
            sb.appendLine("        init {")
            sb.appendLine("            copyFrom(ctx)")
            sb.appendLine("        }")
            sb.appendLine("    }")
            return
        }
        sb.appendLine("    class ${struct.name}(")
        sb.appendLine("        ctx: $parentCtxName,")
        sb.appendLine("    ) : $parentCtxName() {")
        renderStructFields(sb, struct, "        ")
        if (struct.attrs.isNotEmpty()) {
            sb.appendLine()
        }
        sb.appendLine("        init {")
        sb.appendLine("            copyFrom(ctx)")
        sb.appendLine("        }")
        renderStructGetters(sb, struct, "        ")
        renderStructDispatchMethods(sb, file, struct, "        ")
        sb.appendLine("    }")
    }

    private fun renderStructFields(
        sb: StringBuilder,
        struct: StructDecl,
        indent: String,
    ) {
        for (d in struct.attrs) {
            when (d) {
                is TokenListDecl -> sb.appendLine("${indent}val ${d.escapedName}: MutableList<Token> = ArrayList()")
                is TokenDecl -> sb.appendLine("${indent}var ${d.escapedName}: Token? = null")
                is TokenTypeDecl -> sb.appendLine("${indent}var ${d.escapedName}: Int = 0")
                is RuleContextListDecl -> sb.appendLine("${indent}val ${d.escapedName}: MutableList<${d.ctxName}> = ArrayList()")
                is RuleContextDecl -> sb.appendLine("${indent}var ${d.escapedName}: ${d.ctxName}? = null")
                is AttributeDecl -> {
                    val ktType = mapKotlinType(d.type, nonNull = d.initValue != null)
                    val initExpr = d.initValue ?: defaultInitializerForType(ktType)
                    sb.appendLine("${indent}var ${d.escapedName}: $ktType = $initExpr")
                }
            }
        }
        if (struct.attrs.isNotEmpty()) {
            sb.appendLine()
        }
    }

    private fun renderStructGetters(
        sb: StringBuilder,
        struct: StructDecl,
        indent: String,
    ) {
        for (g in struct.getters) {
            val getter = g as? ContextGetterDecl ?: continue
            sb.appendLine()
            val methodName = getter.escapedName
            when (getter) {
                is ContextTokenGetterDecl -> {
                    val tokConst = tokenConstExpr(struct, getter.name)
                    sb.appendLine("${indent}fun $methodName(): TerminalNode? = getToken($tokConst, 0)")
                }

                is ContextTokenListIndexedGetterDecl -> {
                    val tokConst = tokenConstExpr(struct, getter.name)
                    sb.appendLine("${indent}fun $methodName(i: Int): TerminalNode? = getToken($tokConst, i)")
                }

                is ContextTokenListGetterDecl -> {
                    val tokConst = tokenConstExpr(struct, getter.name)
                    sb.appendLine("${indent}fun $methodName(): List<TerminalNode> = getTokens($tokConst)")
                }

                is ContextRuleGetterDecl -> {
                    sb.appendLine("${indent}fun $methodName(): ${getter.ctxName}? = getRuleContextOfType<${getter.ctxName}>(0)")
                }

                is ContextRuleListIndexedGetterDecl -> {
                    sb.appendLine("${indent}fun $methodName(i: Int): ${getter.ctxName}? = getRuleContextOfType<${getter.ctxName}>(i)")
                }

                is ContextRuleListGetterDecl -> {
                    sb.appendLine("${indent}fun $methodName(): List<${getter.ctxName}> = getRuleContextsOfType<${getter.ctxName}>()")
                }
            }
        }
    }

    private fun renderStructDispatchMethods(
        sb: StringBuilder,
        file: ParserFile,
        struct: StructDecl,
        indent: String,
    ) {
        val methods = struct.dispatchMethods ?: return
        val cap = capitalize(struct.derivedFromName)
        for (dm: DispatchMethod in methods) {
            when (dm) {
                is ListenerDispatchMethod -> {
                    sb.appendLine()
                    val fn = if (dm.isEnter) "enterRule" else "exitRule"
                    val targetFn = if (dm.isEnter) "enter$cap" else "exit$cap"
                    sb.appendLine("${indent}override fun $fn(listener: ParseTreeListener?) {")
                    sb.appendLine("$indent    if (listener is ${file.grammarName}Listener) {")
                    sb.appendLine("$indent        listener.$targetFn(this)")
                    sb.appendLine("$indent    }")
                    sb.appendLine("$indent}")
                }

                is VisitorDispatchMethod -> {
                    sb.appendLine()
                    sb.appendLine("${indent}override fun <T> accept(visitor: ParseTreeVisitor<T>?): T? =")
                    sb.appendLine("$indent    if (visitor is ${file.grammarName}Visitor) {")
                    sb.appendLine("$indent        visitor.visit$cap(this)")
                    sb.appendLine("$indent    } else {")
                    sb.appendLine("$indent        visitor?.visitChildren(this)")
                    sb.appendLine("$indent    }")
                }
            }
        }
    }

    private fun renderRuleFunction(
        sb: StringBuilder,
        parser: Parser,
        rf: RuleFunction,
    ) {
        val ruleConst = ruleIndexConstName(rf.name)
        val argParams =
            rf.args
                ?.joinToString(", ") { arg ->
                    "${arg.escapedName}: ${mapKotlinType(arg.type, nonNull = false)}"
                }.orEmpty()
        val ctorArgs =
            if (rf.args.isNullOrEmpty()) {
                ""
            } else {
                ", " + rf.args.orEmpty().joinToString(", ") { it.escapedName }
            }

        if (rf is LeftRecursiveRuleFunction) {
            val callArgs =
                if (rf.args.isNullOrEmpty()) {
                    "0"
                } else {
                    "0, " + rf.args.orEmpty().joinToString(", ") { it.escapedName }
                }
            sb.appendLine("    fun ${rf.escapedName}($argParams): ${rf.ctxType} = ${rf.escapedName}($callArgs)")
            sb.appendLine()
            val recParams = if (argParams.isEmpty()) "precValue: Int" else "precValue: Int, $argParams"
            sb.appendLine("    private fun ${rf.escapedName}($recParams): ${rf.ctxType} {")
            sb.appendLine("        val parentCtx = ctx")
            sb.appendLine("        val parentState = state")
            sb.appendLine("        var localCtx = ${rf.ctxType}(ctx, parentState$ctorArgs)")
            sb.appendLine("        var prevCtx = localCtx")
            sb.appendLine("        val startState = ${rf.startState}")
            sb.appendLine("        enterRecursionRule(localCtx, ${rf.startState}, $ruleConst, precValue)")
            renderLocalDecls(sb, rf.locals, "        ")
            val initAction = rf.namedActions["init"]
            if (initAction != null) {
                appendIndented(sb, renderChunks(initAction.chunks, "localCtx").trim(), "        ")
            }
            sb.appendLine("        try {")
            if (rf.hasLookaheadBlock) {
                sb.appendLine("            var altChoice: Int")
            }
            sb.appendLine("            executeRuleGuarded(localCtx) {")
            renderOps(sb, rf.code.orEmpty(), "                ", "localCtx", rf)
            renderOps(sb, rf.postamble.orEmpty(), "                ", "localCtx", rf)
            val afterAction = rf.namedActions["after"]
            if (afterAction != null) {
                appendIndented(sb, renderChunks(afterAction.chunks, "localCtx").trim(), "                ")
            }
            sb.appendLine("            }")
            sb.appendLine("        } finally {")
            val finallyAct = rf.finallyAction
            if (finallyAct != null) {
                appendIndented(sb, renderChunks(finallyAct.chunks, "localCtx").trim(), "            ")
            }
            sb.appendLine("            unrollRecursionContexts(parentCtx)")
            sb.appendLine("        }")
            sb.appendLine("        return localCtx")
            sb.appendLine("    }")
            return
        }

        val needsVarLocalCtx = !rf.altLabelCtxs.isNullOrEmpty()
        val ctxDeclKeyword = if (needsVarLocalCtx) "var" else "val"
        sb.appendLine("    fun ${rf.escapedName}($argParams): ${rf.ctxType} {")
        sb.appendLine("        $ctxDeclKeyword localCtx = ${rf.ctxType}(ctx, state$ctorArgs)")
        sb.appendLine("        enterRule(localCtx, ${rf.startState}, $ruleConst)")
        renderLocalDecls(sb, rf.locals, "        ")
        val initAction = rf.namedActions["init"]
        if (initAction != null) {
            appendIndented(sb, renderChunks(initAction.chunks, "localCtx").trim(), "        ")
        }
        sb.appendLine("        try {")
        if (rf.hasLookaheadBlock) {
            sb.appendLine("            var altChoice: Int")
        }
        sb.appendLine("            executeRuleGuarded(localCtx) {")
        renderOps(sb, rf.code.orEmpty(), "                ", "localCtx", rf)
        renderOps(sb, rf.postamble.orEmpty(), "                ", "localCtx", rf)
        val afterAction = rf.namedActions["after"]
        if (afterAction != null) {
            appendIndented(sb, renderChunks(afterAction.chunks, "localCtx").trim(), "                ")
        }
        sb.appendLine("            }")
        renderExceptionClauses(sb, rf.exceptions.orEmpty(), "        ")
        sb.appendLine("        } finally {")
        val finallyAct = rf.finallyAction
        if (finallyAct != null) {
            appendIndented(sb, renderChunks(finallyAct.chunks, "localCtx").trim(), "            ")
        }
        sb.appendLine("            exitRule()")
        sb.appendLine("        }")
        sb.appendLine("        return localCtx")
        sb.appendLine("    }")
    }

    private fun renderExceptionClauses(
        sb: StringBuilder,
        clauses: List<ExceptionClause>,
        indent: String,
    ) {
        for (clause in clauses) {
            val arg = renderChunks(clause.catchArg.chunks, "localCtx").trim()
            val body = renderChunks(clause.catchAction.chunks, "localCtx").trim()
            sb.appendLine("$indent} catch ($arg) {")
            if (body.isNotEmpty()) {
                appendIndented(sb, body, "$indent    ")
            }
        }
    }

    private fun renderLocalDecls(
        sb: StringBuilder,
        locals: List<Decl>,
        indent: String,
    ) {
        for (d in locals) {
            when (d) {
                is TokenTypeDecl -> sb.appendLine("${indent}var ${d.escapedName}: Int")
                is TokenDecl -> sb.appendLine("${indent}var ${d.escapedName}: Token? = null")
                is RuleContextDecl -> sb.appendLine("${indent}var ${d.escapedName}: ${d.ctxName}? = null")
                else -> Unit
            }
        }
    }

    private fun renderOps(
        sb: StringBuilder,
        ops: List<SrcOp>,
        indent: String,
        localCtxExpr: String,
        rf: RuleFunction,
    ) {
        for (op in ops) {
            renderOp(sb, op, indent, localCtxExpr, rf)
        }
    }

    private fun renderOp(
        sb: StringBuilder,
        op: SrcOp,
        indent: String,
        localCtxExpr: String,
        rf: RuleFunction,
    ) {
        when (op) {
            is CodeBlockForOuterMostAlt -> {
                val altStruct = op.altLabel
                var activeCtx = localCtxExpr
                if (altStruct != null && rf !is LeftRecursiveRuleFunction) {
                    sb.appendLine("${indent}localCtx = ${altStruct.name}(localCtx)")
                    activeCtx = "(localCtx as ${altStruct.name})"
                }
                if (rf !is LeftRecursiveRuleFunction) {
                    sb.appendLine("${indent}enterOuterAlt(localCtx, ${op.alt.altNum})")
                }
                renderLocalDecls(sb, op.locals.orEmpty(), indent)
                renderOps(sb, op.preamble.orEmpty(), indent, activeCtx, rf)
                renderOps(sb, op.ops.orEmpty(), indent, activeCtx, rf)
            }

            is CodeBlock -> {
                renderLocalDecls(sb, op.locals.orEmpty(), indent)
                renderOps(sb, op.preamble.orEmpty(), indent, localCtxExpr, rf)
                renderOps(sb, op.ops.orEmpty(), indent, localCtxExpr, rf)
            }

            is InvokeRule -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                val argsText = op.argExprsChunks?.let { renderChunks(it, localCtxExpr) }.orEmpty()
                val assignPrefix = renderLabelAssignments(op.labels, localCtxExpr, rf)
                sb.appendLine("$indent$assignPrefix${op.escapedName}($argsText)")
            }

            is MatchNotSet -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                renderOp(sb, op.capture, indent, localCtxExpr, rf)
                val cond = renderInlineTestSet(op.expr)
                sb.appendLine("${indent}if (${op.varName} <= 0 || ($cond)) {")
                val assignErr = renderLabelAssignments(op.labels, localCtxExpr, rf)
                if (assignErr.isNotEmpty()) {
                    sb.appendLine("$indent    ${assignErr}errorHandler.recoverInline(this)")
                } else {
                    sb.appendLine("$indent    errorHandler.recoverInline(this)")
                }
                sb.appendLine("$indent} else {")
                sb.appendLine("$indent    if (tokenStream.LA(1) == Token.EOF) isMatchedEOF = true")
                sb.appendLine("$indent    errorHandler.reportMatch(this)")
                sb.appendLine("$indent    consume()")
                sb.appendLine("$indent}")
            }

            is MatchSet -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                renderOp(sb, op.capture, indent, localCtxExpr, rf)
                val cond = renderInlineTestSet(op.expr)
                sb.appendLine("${indent}if (!($cond)) {")
                val assignErr = renderLabelAssignments(op.labels, localCtxExpr, rf)
                if (assignErr.isNotEmpty()) {
                    sb.appendLine("$indent    ${assignErr}errorHandler.recoverInline(this)")
                } else {
                    sb.appendLine("$indent    errorHandler.recoverInline(this)")
                }
                sb.appendLine("$indent} else {")
                sb.appendLine("$indent    if (tokenStream.LA(1) == Token.EOF) isMatchedEOF = true")
                sb.appendLine("$indent    errorHandler.reportMatch(this)")
                sb.appendLine("$indent    consume()")
                sb.appendLine("$indent}")
            }

            is Wildcard -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                val assignPrefix = renderLabelAssignments(op.labels, localCtxExpr, rf)
                sb.appendLine("$indent${assignPrefix}matchWildcard()")
            }

            is MatchToken -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                val assignPrefix = renderLabelAssignments(op.labels, localCtxExpr, rf)
                sb.appendLine("$indent${assignPrefix}match(${op.name})")
            }

            is AddToLabelList -> {
                val targetList = qualifyContextField(op.listName, localCtxExpr, rf)
                val elemRef = qualifyDeclRef(op.label, localCtxExpr, rf)
                sb.appendLine("${indent}val addedElem${op.uniqueID} = $elemRef")
                sb.appendLine("${indent}if (addedElem${op.uniqueID} != null) {")
                sb.appendLine("$indent    $targetList.add(addedElem${op.uniqueID})")
                sb.appendLine("$indent}")
            }

            is CaptureNextToken -> {
                val targetVar = qualifyContextField(op.varName, localCtxExpr, rf)
                sb.appendLine("$indent$targetVar = tokenStream.LT(1)")
            }

            is CaptureNextTokenType -> {
                sb.appendLine("$indent${op.varName} = tokenStream.LA(1)")
            }

            is SemPred -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                val predExpr = renderChunks(op.chunks, localCtxExpr).trim()
                val failMsg =
                    when {
                        op.failChunks != null -> renderChunks(op.failChunks.orEmpty(), localCtxExpr).trim()
                        op.msg != null -> op.msg
                        else -> null
                    }
                sb.appendLine("${indent}if (!($predExpr)) {")
                if (failMsg != null) {
                    sb.appendLine("$indent    throwFailedPredicate(${quoteString(op.predicate)}, $failMsg)")
                } else {
                    sb.appendLine("$indent    throwFailedPredicate(${quoteString(op.predicate)})")
                }
                sb.appendLine("$indent}")
            }

            is Action -> {
                val rendered = renderChunks(op.chunks, localCtxExpr).trim()
                if (rendered.isNotEmpty()) {
                    appendIndented(sb, rendered, indent)
                }
            }

            is Sync -> {
                sb.appendLine("${indent}state = ${op.stateNumber}")
                sb.appendLine("${indent}errorHandler.sync(this)")
            }

            is ThrowNoViableAlt -> {
                sb.appendLine("${indent}throwNoViableAlt()")
            }

            is ThrowEarlyExitException -> {
                sb.appendLine("${indent}throwNoViableAlt()")
            }

            is LL1OptionalBlockSingleAlt -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                renderOps(sb, op.preamble, indent, localCtxExpr, rf)
                val cond = renderConditionOp(op.expr)
                sb.appendLine("${indent}if ($cond) {")
                op.label?.let { lbl ->
                    sb.appendLine("$indent    ${qualifyDeclRef(lbl, localCtxExpr, rf)} = tokenStream.LT(1)")
                }
                renderOps(sb, op.alts, "$indent    ", localCtxExpr, rf)
                sb.appendLine("$indent}")
            }

            is LL1OptionalBlock -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                renderOps(sb, op.preamble, indent, localCtxExpr, rf)
                sb.appendLine("${indent}when (tokenStream.LA(1)) {")
                for (i in op.alts.indices) {
                    val tokens = op.altLook.getOrNull(i).orEmpty()
                    if (tokens.isEmpty()) continue
                    val cases = tokens.joinToString(", ") { it.name.orEmpty() }
                    sb.appendLine("$indent    $cases -> {")
                    op.label?.let { lbl ->
                        sb.appendLine("$indent        ${qualifyDeclRef(lbl, localCtxExpr, rf)} = tokenStream.LT(1)")
                    }
                    renderOp(sb, op.alts[i], "$indent        ", localCtxExpr, rf)
                    sb.appendLine("$indent    }")
                }
                sb.appendLine("$indent    else -> Unit")
                sb.appendLine("$indent}")
            }

            is LL1AltBlock -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                renderOps(sb, op.preamble, indent, localCtxExpr, rf)
                sb.appendLine("${indent}when (tokenStream.LA(1)) {")
                for (i in op.alts.indices) {
                    val tokens = op.altLook.getOrNull(i).orEmpty()
                    if (tokens.isEmpty()) continue
                    val cases = tokens.joinToString(", ") { it.name.orEmpty() }
                    sb.appendLine("$indent    $cases -> {")
                    op.label?.let { lbl ->
                        sb.appendLine("$indent        ${qualifyDeclRef(lbl, localCtxExpr, rf)} = tokenStream.LT(1)")
                    }
                    renderOp(sb, op.alts[i], "$indent        ", localCtxExpr, rf)
                    sb.appendLine("$indent    }")
                }
                sb.appendLine("$indent    else -> throwNoViableAlt()")
                sb.appendLine("$indent}")
            }

            is LL1StarBlockSingleAlt -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                renderOps(sb, op.preamble, indent, localCtxExpr, rf)
                val cond = op.loopExpr?.let { renderConditionOp(it) } ?: "false"
                sb.appendLine("${indent}while ($cond) {")
                renderOps(sb, op.alts, "$indent    ", localCtxExpr, rf)
                if (op.loopBackStateNumber >= 0) {
                    sb.appendLine("$indent    state = ${op.loopBackStateNumber}")
                }
                renderOps(sb, op.iteration, "$indent    ", localCtxExpr, rf)
                sb.appendLine("$indent}")
            }

            is LL1PlusBlockSingleAlt -> {
                if (op.blockStartStateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.blockStartStateNumber}")
                }
                renderOps(sb, op.preamble, indent, localCtxExpr, rf)
                val cond = op.loopExpr?.let { renderConditionOp(it) } ?: "false"
                sb.appendLine("${indent}do {")
                renderOps(sb, op.alts, "$indent    ", localCtxExpr, rf)
                if (op.stateNumber >= 0) {
                    sb.appendLine("$indent    state = ${op.stateNumber}")
                }
                renderOps(sb, op.iteration, "$indent    ", localCtxExpr, rf)
                sb.appendLine("$indent} while ($cond)")
            }

            is OptionalBlock -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                renderOps(sb, op.preamble, indent, localCtxExpr, rf)
                val nonGreedyOffset = if ((op.ast as? QuantifierAST)?.isGreedy == false) 1 else 0
                sb.appendLine("${indent}altChoice = adaptivePredict(${op.decision})")
                sb.appendLine("${indent}when (altChoice) {")
                for (i in op.alts.indices) {
                    val altIdx = i + 1 + nonGreedyOffset
                    sb.appendLine("$indent    $altIdx -> {")
                    op.label?.let { lbl ->
                        sb.appendLine("$indent        ${qualifyDeclRef(lbl, localCtxExpr, rf)} = tokenStream.LT(1)")
                    }
                    renderOp(sb, op.alts[i], "$indent        ", localCtxExpr, rf)
                    sb.appendLine("$indent    }")
                }
                sb.appendLine("$indent}")
            }

            is AltBlock -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                renderOps(sb, op.preamble, indent, localCtxExpr, rf)
                sb.appendLine("${indent}altChoice = adaptivePredict(${op.decision})")
                sb.appendLine("${indent}when (altChoice) {")
                for (i in op.alts.indices) {
                    val altIdx = i + 1
                    sb.appendLine("$indent    $altIdx -> {")
                    op.label?.let { lbl ->
                        sb.appendLine("$indent        ${qualifyDeclRef(lbl, localCtxExpr, rf)} = tokenStream.LT(1)")
                    }
                    renderOp(sb, op.alts[i], "$indent        ", localCtxExpr, rf)
                    sb.appendLine("$indent    }")
                }
                if (op.error != null) {
                    sb.appendLine("$indent    else -> throwNoViableAlt()")
                }
                sb.appendLine("$indent}")
            }

            is StarBlock -> {
                if (op.stateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.stateNumber}")
                }
                renderOps(sb, op.preamble, indent, localCtxExpr, rf)
                val nonGreedyOffset = if (op.exitAlt == 1) 1 else 0
                sb.appendLine("${indent}altChoice = adaptivePredict(${op.decision})")
                sb.appendLine("${indent}while (altChoice != ${op.exitAlt} && altChoice != ATN.INVALID_ALT_NUMBER) {")
                if (op.alts.size == 1) {
                    val singleAltNum = 1 + nonGreedyOffset
                    sb.appendLine("$indent    if (altChoice == $singleAltNum) {")
                    renderOp(sb, op.alts[0], "$indent        ", localCtxExpr, rf)
                    sb.appendLine("$indent    }")
                } else {
                    sb.appendLine("$indent    when (altChoice) {")
                    for (i in op.alts.indices) {
                        val altNum = i + 1 + nonGreedyOffset
                        if (altNum == op.exitAlt) continue
                        sb.appendLine("$indent        $altNum -> {")
                        renderOp(sb, op.alts[i], "$indent            ", localCtxExpr, rf)
                        sb.appendLine("$indent        }")
                    }
                    sb.appendLine("$indent    }")
                }
                if (op.loopBackStateNumber >= 0) {
                    sb.appendLine("$indent    state = ${op.loopBackStateNumber}")
                }
                renderOps(sb, op.iteration, "$indent    ", localCtxExpr, rf)
                sb.appendLine("$indent    altChoice = adaptivePredict(${op.decision})")
                sb.appendLine("$indent}")
            }

            is PlusBlock -> {
                if (op.blockStartStateNumber >= 0) {
                    sb.appendLine("${indent}state = ${op.blockStartStateNumber}")
                }
                renderOps(sb, op.preamble, indent, localCtxExpr, rf)
                val nonGreedyOffset = if (op.exitAlt == 1) 1 else 0
                sb.appendLine("${indent}altChoice = ${1 + nonGreedyOffset}")
                sb.appendLine("${indent}do {")
                sb.appendLine("$indent    when (altChoice) {")
                for (i in op.alts.indices) {
                    val altNum = i + 1 + nonGreedyOffset
                    if (altNum == op.exitAlt) continue
                    sb.appendLine("$indent        $altNum -> {")
                    renderOp(sb, op.alts[i], "$indent            ", localCtxExpr, rf)
                    sb.appendLine("$indent        }")
                }
                sb.appendLine("$indent        else -> {")
                renderOp(sb, op.error, "$indent            ", localCtxExpr, rf)
                sb.appendLine("$indent        }")
                sb.appendLine("$indent    }")
                if (op.loopBackStateNumber >= 0) {
                    sb.appendLine("$indent    state = ${op.loopBackStateNumber}")
                }
                renderOps(sb, op.iteration, "$indent    ", localCtxExpr, rf)
                sb.appendLine("$indent    altChoice = adaptivePredict(${op.decision})")
                sb.appendLine("$indent} while (altChoice != ${op.exitAlt} && altChoice != ATN.INVALID_ALT_NUMBER)")
            }
        }
    }

    private fun renderConditionOp(op: SrcOp): String =
        when (op) {
            is TestSetInline -> renderInlineTestSet(op)
            else -> "false"
        }

    private fun renderInlineTestSet(set: TestSetInline): String {
        if (set.bitsets.isEmpty()) return "false"
        if (set.bitsets.size == 1 && set.bitsets[0].tokens.size <= 4) {
            return set.bitsets[0].tokens.joinToString(" || ") { tok ->
                "${set.varName} == ${tok.name}"
            }
        }
        return set.bitsets.joinToString(" || ") { bs ->
            if (bs.tokens.size == 1) {
                "${set.varName} == ${bs.tokens[0].name}"
            } else if (bs.shift == 0) {
                "((${set.varName} and -64) == 0 && ((1L shl ${set.varName}) and ${bs.calculated}L) != 0L)"
            } else {
                "(((${set.varName} - ${bs.shift}) and -64) == 0 && ((1L shl (${set.varName} - ${bs.shift})) and ${bs.calculated}L) != 0L)"
            }
        }
    }

    private fun renderLabelAssignments(
        labels: List<Decl>,
        localCtxExpr: String,
        rf: RuleFunction,
    ): String {
        if (labels.isEmpty()) return ""
        val sb = StringBuilder()
        for (lbl in labels) {
            sb.append(qualifyDeclRef(lbl, localCtxExpr, rf)).append(" = ")
        }
        return sb.toString()
    }

    private fun qualifyDeclRef(
        decl: Decl,
        localCtxExpr: String,
        rf: RuleFunction,
    ): String {
        val ownerCtx = decl.ctx
        if (ownerCtx != null) {
            if (ownerCtx is AltLabelStructDecl && localCtxExpr == "localCtx") {
                return "(localCtx as ${ownerCtx.name}).${decl.escapedName}"
            }
            return "$localCtxExpr.${decl.escapedName}"
        }
        return qualifyContextField(decl.escapedName, localCtxExpr, rf)
    }

    private fun qualifyContextField(
        fieldName: String,
        localCtxExpr: String,
        rf: RuleFunction,
    ): String {
        if (rf.ruleCtx.attrs.any { it.escapedName == fieldName || it.name == fieldName }) {
            return "localCtx.$fieldName"
        }
        val altCtxs = rf.altLabelCtxs
        if (altCtxs != null) {
            for (altStruct in altCtxs.values) {
                if (altStruct.attrs.any { it.escapedName == fieldName || it.name == fieldName }) {
                    return if (localCtxExpr.contains(altStruct.name)) {
                        "$localCtxExpr.$fieldName"
                    } else {
                        "(localCtx as ${altStruct.name}).$fieldName"
                    }
                }
            }
        }
        return fieldName
    }

    private fun renderChunks(
        chunks: List<ActionChunk>,
        localCtxExpr: String,
    ): String {
        val sb = StringBuilder()
        for (chunk in chunks) {
            sb.append(renderChunk(chunk, localCtxExpr))
        }
        return sb.toString()
    }

    private fun chunkCtxRef(
        chunk: ActionChunk,
        defaultCtxExpr: String,
    ): String {
        val struct = chunk.ctx
        if (struct is AltLabelStructDecl && defaultCtxExpr == "localCtx") {
            return "(localCtx as ${struct.name})"
        }
        return defaultCtxExpr
    }

    private fun renderChunk(
        chunk: ActionChunk,
        localCtxExpr: String,
    ): String {
        val ctxRef = chunkCtxRef(chunk, localCtxExpr)
        return when (chunk) {
            is ActionText -> chunk.text.orEmpty()
            is ActionTemplate -> renderActionTemplate(chunk, localCtxExpr)
            is SetNonLocalAttr -> {
                val ctxClass = "${capitalize(chunk.ruleName)}Context"
                val rhs = renderChunks(chunk.rhsChunks, localCtxExpr)
                "(getInvokingContext(${chunk.ruleIndex}) as $ctxClass).${chunk.escapedName} = $rhs"
            }
            is NonLocalAttrRef -> {
                val ctxClass = "${capitalize(chunk.ruleName)}Context"
                "(getInvokingContext(${chunk.ruleIndex}) as $ctxClass).${chunk.escapedName}"
            }
            is SetAttr -> "$ctxRef.${chunk.escapedName} = ${renderChunks(chunk.rhsChunks, localCtxExpr)}"
            is QRetValueRef -> "$ctxRef.${chunk.dict}?.${chunk.escapedName}"
            is ArgRef -> "$ctxRef.${chunk.escapedName}"
            is LocalRef -> "$ctxRef.${chunk.escapedName}"
            is RetValueRef -> "$ctxRef.${chunk.escapedName}"
            is TokenRef -> "$ctxRef.${chunk.escapedName}"
            is LabelRef -> "$ctxRef.${chunk.escapedName}"
            is ListLabelRef -> "$ctxRef.${chunk.escapedName}"
            is TokenPropertyRefText -> "$ctxRef.${chunk.label}?.text"
            is TokenPropertyRefType -> "($ctxRef.${chunk.label}?.type ?: 0)"
            is TokenPropertyRefLine -> "($ctxRef.${chunk.label}?.line ?: 0)"
            is TokenPropertyRefPos -> "($ctxRef.${chunk.label}?.charPositionInLine ?: -1)"
            is TokenPropertyRefChannel -> "($ctxRef.${chunk.label}?.channel ?: 0)"
            is TokenPropertyRefIndex -> "($ctxRef.${chunk.label}?.tokenIndex ?: -1)"
            is TokenPropertyRefInt -> "($ctxRef.${chunk.label}?.text?.toInt() ?: 0)"
            is ThisRulePropertyRefStart -> "$ctxRef.start"
            is ThisRulePropertyRefStop -> "$ctxRef.stop"
            is ThisRulePropertyRefText -> "tokenStream.getText($ctxRef.start, tokenStream.LT(-1))"
            is ThisRulePropertyRefCtx -> ctxRef
            is ThisRulePropertyRefParser -> "this"
            is RulePropertyRefStart -> "$ctxRef.${chunk.label}?.start"
            is RulePropertyRefStop -> "$ctxRef.${chunk.label}?.stop"
            is RulePropertyRefText ->
                "$ctxRef.${chunk.label}?.let { subCtx -> tokenStream.getText(subCtx.start, subCtx.stop) }"
            is RulePropertyRefCtx -> "$ctxRef.${chunk.label}"
            is RulePropertyRefParser -> "this"
            else -> ""
        }
    }

    private fun renderActionTemplate(
        tmpl: ActionTemplate,
        localCtxExpr: String,
    ): String {
        val s = tmpl.spec
        return when (s.kind) {
            LeftRecursiveActionKind.REPLACE_CONTEXT -> {
                val altCtx = requireNotNull(s.ctxName)
                "localCtx = $altCtx(localCtx)\nctx = localCtx\nprevCtx = localCtx"
            }

            LeftRecursiveActionKind.SET_STOP_TOKEN -> "ctx?.stop = tokenStream.LT(-1)"

            LeftRecursiveActionKind.SET_PREV_CTX -> "prevCtx = localCtx"

            LeftRecursiveActionKind.ALT_START -> {
                val baseCtx = requireNotNull(s.ctxName)
                val ruleConst = ruleIndexConstName(requireNotNull(s.ruleName))
                val sb = StringBuilder()
                sb.appendLine("localCtx = $baseCtx(parentCtx, parentState)")
                if (s.label != null) {
                    if (s.isListLabel) {
                        sb.appendLine("localCtx.${s.label}.add(prevCtx)")
                    } else {
                        sb.appendLine("localCtx.${s.label} = prevCtx")
                    }
                }
                sb.append("pushNewRecursionContext(localCtx, startState, $ruleConst)")
                sb.toString()
            }

            LeftRecursiveActionKind.LABELED_ALT_START -> {
                val baseCtx = requireNotNull(s.ctxName)
                val altCtx = requireNotNull(s.currentAltLabel)
                val ruleConst = ruleIndexConstName(requireNotNull(s.ruleName))
                val sb = StringBuilder()
                sb.appendLine("val nextAltCtx = $altCtx($baseCtx(parentCtx, parentState))")
                if (s.label != null) {
                    if (s.isListLabel) {
                        sb.appendLine("nextAltCtx.${s.label}.add(prevCtx)")
                    } else {
                        sb.appendLine("nextAltCtx.${s.label} = prevCtx")
                    }
                }
                sb.appendLine("localCtx = nextAltCtx")
                sb.append("pushNewRecursionContext(localCtx, startState, $ruleConst)")
                sb.toString()
            }
        }
    }

    private fun renderLexerActionDispatch(
        sb: StringBuilder,
        actionFuncs: Map<io.github.kotlinmania.antlr4.tooling.tool.Rule, RuleActionFunction>,
    ) {
        sb.appendLine("    override fun action(localctx: RuleContext?, ruleIndex: Int, actionIndex: Int) {")
        sb.appendLine("        when (ruleIndex) {")
        for ((r, func) in actionFuncs) {
            sb.appendLine("            ${r.index} -> ${func.escapedName}Action(localctx, actionIndex)")
        }
        sb.appendLine("        }")
        sb.appendLine("    }")
        for ((_, func) in actionFuncs) {
            sb.appendLine()
            sb.appendLine("    private fun ${func.escapedName}Action(localctx: RuleContext?, actionIndex: Int) {")
            sb.appendLine("        if (localctx == null && actionIndex < 0) return")
            sb.appendLine("        when (actionIndex) {")
            for ((actIdx, act) in func.actions) {
                val body = renderChunks(act.chunks, "localctx").trim()
                sb.appendLine("            $actIdx -> {")
                appendIndented(sb, body, "                ")
                sb.appendLine("            }")
            }
            sb.appendLine("        }")
            sb.appendLine("    }")
        }
    }

    private fun renderSempredDispatch(
        sb: StringBuilder,
        sempredFuncs: Map<io.github.kotlinmania.antlr4.tooling.tool.Rule, RuleSempredFunction>,
        isLexer: Boolean,
    ) {
        sb.appendLine("    override fun sempred(localctx: RuleContext?, ruleIndex: Int, actionIndex: Int): Boolean =")
        sb.appendLine("        when (ruleIndex) {")
        for ((r, func) in sempredFuncs) {
            if (isLexer) {
                sb.appendLine("            ${r.index} -> ${func.escapedName}Sempred(localctx, actionIndex)")
            } else {
                sb.appendLine("            ${r.index} -> ${func.escapedName}Sempred(localctx as? ${func.ctxType}, actionIndex)")
            }
        }
        sb.appendLine("            else -> true")
        sb.appendLine("        }")
        for ((_, func) in sempredFuncs) {
            val ctxParamType = if (isLexer) "RuleContext?" else "${func.ctxType}?"
            sb.appendLine()
            sb.appendLine("    private fun ${func.escapedName}Sempred(localCtx: $ctxParamType, predIndex: Int): Boolean {")
            sb.appendLine("        if (localCtx == null && predIndex < 0) return true")
            sb.appendLine("        return when (predIndex) {")
            for ((predIdx, pred) in func.actions) {
                val expr = renderChunks(pred.chunks, "localCtx").trim()
                sb.appendLine("            $predIdx -> $expr")
            }
            sb.appendLine("            else -> true")
            sb.appendLine("        }")
            sb.appendLine("    }")
        }
    }

    private fun renderRecognizerCompanion(
        sb: StringBuilder,
        rec: io.github.kotlinmania.antlr4.tooling.codegen.model.Recognizer,
        isLexer: Boolean,
    ) {
        if (!isLexer && rec.funcsNeedAdaptivePredict(rec)) {
            sb.appendLine("    private fun adaptivePredict(decision: Int): Int {")
            sb.appendLine("        val sim = requireNotNull(interpreter) { \"Parser interpreter is not initialized.\" }")
            sb.appendLine("        return sim.adaptivePredict(tokenStream, decision, ctx)")
            sb.appendLine("    }")
            sb.appendLine()
        }

        sb.appendLine("    companion object {")
        renderRecognizerCompanionBody(sb, rec, isLexer, indent = "        ", dfaPrivate = true)
        sb.appendLine("    }")
    }

    private fun renderRecognizerCompanionBody(
        sb: StringBuilder,
        rec: io.github.kotlinmania.antlr4.tooling.codegen.model.Recognizer,
        isLexer: Boolean,
        indent: String,
        dfaPrivate: Boolean = false,
    ) {
        for (r in rec.rules) {
            sb.appendLine("${indent}const val ${ruleIndexConstName(r.name)}: Int = ${r.index}")
        }
        if (rec.rules.isNotEmpty()) {
            sb.appendLine()
        }

        val ruleNamesInit =
            if (rec.ruleNames.isEmpty()) {
                "emptyList()"
            } else {
                "listOf(" + rec.ruleNames.joinToString(", ") { quoteString(it) } + ")"
            }
        sb.appendLine("${indent}val RULE_NAMES: List<String> = $ruleNamesInit")

        if (isLexer && rec is Lexer) {
            val allChannels = ArrayList<String>()
            allChannels.add("DEFAULT_TOKEN_CHANNEL")
            allChannels.add("HIDDEN")
            for (chName in rec.channels.keys) {
                allChannels.add(chName)
            }
            sb.appendLine("${indent}val CHANNEL_NAMES: List<String> = listOf(${allChannels.joinToString(", ") { quoteString(it) }})")
            val modeList = rec.modes.toList()
            val modesInit =
                if (modeList.isEmpty()) {
                    "emptyList()"
                } else {
                    "listOf(${modeList.joinToString(", ") { quoteString(it) }})"
                }
            sb.appendLine("${indent}val MODE_NAMES: List<String> = $modesInit")
        }

        val litInit =
            if (rec.literalNames.isEmpty()) {
                "emptyList()"
            } else {
                "listOf(" + rec.literalNames.joinToString(", ") + ")"
            }
        val symInit =
            if (rec.symbolicNames.isEmpty()) {
                "emptyList()"
            } else {
                "listOf(" + rec.symbolicNames.joinToString(", ") + ")"
            }
        sb.appendLine("${indent}private val LITERAL_NAMES: List<String> = $litInit")
        sb.appendLine("${indent}private val SYMBOLIC_NAMES: List<String> = $symInit")
        sb.appendLine("${indent}val VOCABULARY: Vocabulary = VocabularyImpl(LITERAL_NAMES, SYMBOLIC_NAMES)")
        sb.appendLine(
            "${indent}val TOKEN_NAMES: List<String> = List(VOCABULARY.maxTokenType + 1) { idx -> " +
                "VOCABULARY.getLiteralName(idx) ?: VOCABULARY.getSymbolicName(idx) ?: \"<INVALID>\" }",
        )
        sb.appendLine()

        val atnModel: SerializedATN? = rec.atn
        val ints = atnModel?.serializedInts ?: IntArray(0)
        val atnChunkMaxInts = 2000
        if (ints.size <= atnChunkMaxInts) {
            if (ints.isEmpty()) {
                sb.appendLine("${indent}private val SERIALIZED_ATN_INTS: IntArray = intArrayOf()")
            } else {
                sb.appendLine("${indent}private val SERIALIZED_ATN_INTS: IntArray =")
                sb.appendLine("$indent    intArrayOf(")
                for (value in ints) {
                    sb.appendLine("$indent        $value,")
                }
                sb.appendLine("$indent    )")
            }
        } else {
            sb.appendLine("${indent}private fun concatIntArrays(chunks: List<IntArray>): IntArray {")
            sb.appendLine("$indent    var total = 0")
            sb.appendLine("$indent    for (chunk in chunks) {")
            sb.appendLine("$indent        total += chunk.size")
            sb.appendLine("$indent    }")
            sb.appendLine("$indent    val result = IntArray(total)")
            sb.appendLine("$indent    var offset = 0")
            sb.appendLine("$indent    for (chunk in chunks) {")
            sb.appendLine("$indent        chunk.copyInto(result, destinationOffset = offset)")
            sb.appendLine("$indent        offset += chunk.size")
            sb.appendLine("$indent    }")
            sb.appendLine("$indent    return result")
            sb.appendLine("$indent}")
            sb.appendLine()
            val numChunks = (ints.size + atnChunkMaxInts - 1) / atnChunkMaxInts
            for (chunkIdx in 0 until numChunks) {
                val chunkStart = chunkIdx * atnChunkMaxInts
                val chunkEnd = minOf(ints.size, chunkStart + atnChunkMaxInts)
                sb.appendLine("${indent}private fun serializedAtnChunk$chunkIdx(): IntArray =")
                sb.appendLine("$indent    intArrayOf(")
                for (idx in chunkStart until chunkEnd) {
                    sb.appendLine("$indent        ${ints[idx]},")
                }
                sb.appendLine("$indent    )")
                sb.appendLine()
            }
            sb.appendLine("${indent}private val SERIALIZED_ATN_INTS: IntArray =")
            sb.appendLine("$indent    concatIntArrays(")
            sb.appendLine("$indent        listOf(")
            for (chunkIdx in 0 until numChunks) {
                sb.appendLine("$indent            serializedAtnChunk$chunkIdx(),")
            }
            sb.appendLine("$indent        ),")
            sb.appendLine("$indent    )")
        }
        sb.appendLine(
            "${indent}val SERIALIZED_ATN: String by lazy {\n" +
                "$indent    val list = IntegerList(SERIALIZED_ATN_INTS.size)\n" +
                "$indent    list.addAll(SERIALIZED_ATN_INTS)\n" +
                "$indent    val words16 = ATNDeserializer.encodeIntsWith16BitWords(list)\n" +
                "$indent    CharArray(words16.size()) { i -> words16.get(i).toChar() }.concatToString()\n" +
                "$indent}",
        )
        sb.appendLine("${indent}val ATN_DATA: ATN = ATNDeserializer().deserialize(SERIALIZED_ATN_INTS)")
        val dfaVis = if (dfaPrivate) "private " else "internal "
        sb.appendLine(
            "$indent${dfaVis}val decisionToDFA: List<DFA> = " +
                "List(ATN_DATA.numberOfDecisions) { d -> DFA(ATN_DATA.getDecisionState(d), d) }",
        )
        sb.appendLine("$indent${dfaVis}val sharedContextCache: PredictionContextCache = PredictionContextCache()")
    }

    private fun io.github.kotlinmania.antlr4.tooling.codegen.model.Recognizer.funcsNeedAdaptivePredict(
        rec: io.github.kotlinmania.antlr4.tooling.codegen.model.Recognizer,
    ): Boolean = (rec as? Parser)?.funcs?.any { it.hasLookaheadBlock } == true

    private fun collectNeededImports(
        body: String,
        isLexer: Boolean,
    ): List<String> {
        val imports = LinkedHashSet<String>()
        if (isLexer) {
            imports.add("io.github.kotlinmania.antlr4.CharStream")
            imports.add("io.github.kotlinmania.antlr4.Lexer")
            imports.add("io.github.kotlinmania.antlr4.atn.LexerATNSimulator")
        } else {
            imports.add("io.github.kotlinmania.antlr4.Parser")
            imports.add("io.github.kotlinmania.antlr4.TokenStream")
            imports.add("io.github.kotlinmania.antlr4.atn.ParserATNSimulator")
        }
        if (containsWord(body, "Vocabulary")) imports.add("io.github.kotlinmania.antlr4.Vocabulary")
        if (containsWord(body, "VocabularyImpl")) imports.add("io.github.kotlinmania.antlr4.VocabularyImpl")
        if (containsWord(body, "ATN")) imports.add("io.github.kotlinmania.antlr4.atn.ATN")
        if (containsWord(body, "ATNDeserializer")) imports.add("io.github.kotlinmania.antlr4.atn.ATNDeserializer")
        if (containsWord(body, "PredictionContextCache")) imports.add("io.github.kotlinmania.antlr4.atn.PredictionContextCache")
        if (containsWord(body, "DFA")) imports.add("io.github.kotlinmania.antlr4.dfa.DFA")
        if (containsWord(body, "IntegerList")) imports.add("io.github.kotlinmania.antlr4.misc.IntegerList")

        if (containsWord(body, "ParserRuleContext")) imports.add("io.github.kotlinmania.antlr4.ParserRuleContext")
        if (containsWord(body, "RuleContext")) imports.add("io.github.kotlinmania.antlr4.RuleContext")
        if (containsWord(body, "Token")) imports.add("io.github.kotlinmania.antlr4.Token")
        if (containsWord(body, "RecognitionException")) imports.add("io.github.kotlinmania.antlr4.RecognitionException")
        if (containsWord(body, "ParseTreeListener")) imports.add("io.github.kotlinmania.antlr4.tree.ParseTreeListener")
        if (containsWord(body, "ParseTreeVisitor")) imports.add("io.github.kotlinmania.antlr4.tree.ParseTreeVisitor")
        if (containsWord(body, "TerminalNode")) imports.add("io.github.kotlinmania.antlr4.tree.TerminalNode")
        return imports.sorted()
    }

    private fun containsWord(
        text: String,
        word: String,
    ): Boolean {
        var idx = text.indexOf(word)
        while (idx >= 0) {
            val beforeOk = idx == 0 || (!text[idx - 1].isLetterOrDigit() && text[idx - 1] != '_')
            val afterPos = idx + word.length
            val afterOk = afterPos == text.length || (!text[afterPos].isLetterOrDigit() && text[afterPos] != '_')
            if (beforeOk && afterOk) return true
            idx = text.indexOf(word, idx + 1)
        }
        return false
    }

    private fun tokenConstExpr(
        struct: StructDecl,
        tokenName: String,
    ): String {
        if (tokenName == "EOF") return "Token.EOF"
        val escaped =
            struct.factory
                ?.generator
                ?.target
                ?.escapeIfNeeded(tokenName) ?: tokenName
        return "Tokens.$escaped"
    }

    private fun ruleIndexConstName(ruleName: String): String {
        val sb = StringBuilder("RULE_")
        for (i in ruleName.indices) {
            val ch = ruleName[i]
            if (ch.isUpperCase() && i > 0 && ruleName[i - 1].isLowerCase()) {
                sb.append('_')
            }
            sb.append(ch.uppercaseChar())
        }
        return sb.toString()
    }

    private fun mapKotlinType(
        rawType: String?,
        nonNull: Boolean,
    ): String {
        val trimmed = rawType?.trim().orEmpty()
        val base =
            when (trimmed) {
                "", "Any", "Object" -> "Any"
                "int", "Integer", "Int" -> "Int"
                "long", "Long" -> "Long"
                "boolean", "Boolean" -> "Boolean"
                "double", "Double" -> "Double"
                "float", "Float" -> "Float"
                "char", "Character", "Char" -> "Char"
                "byte", "Byte" -> "Byte"
                "short", "Short" -> "Short"
                "String" -> "String"
                else -> trimmed
            }
        if (base in VALUE_KOTLIN_TYPES || base.endsWith("?") || nonNull) {
            return base
        }
        return "$base?"
    }

    private fun defaultInitializerForType(ktType: String): String =
        when (ktType) {
            "Int" -> "0"
            "Long" -> "0L"
            "Boolean" -> "false"
            "Double" -> "0.0"
            "Float" -> "0.0f"
            "Char" -> "'\\u0000'"
            "Byte" -> "0"
            "Short" -> "0"
            "String" -> "\"\""
            else -> "null"
        }

    private fun visibilityPrefix(accessLevel: String?): String =
        when (accessLevel?.trim()) {
            "internal" -> "internal "
            "private" -> "private "
            else -> ""
        }

    private fun capitalize(s: String): String =
        s.replaceFirstChar { it.uppercaseChar() }

    private fun quoteString(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '$' -> sb.append("\\$")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun appendIndented(
        sb: StringBuilder,
        text: String,
        indent: String,
    ) {
        if (text.isEmpty()) return
        for (line in text.lines()) {
            if (line.isBlank()) {
                sb.appendLine()
            } else {
                sb.appendLine("$indent$line")
            }
        }
    }

    private val VALUE_KOTLIN_TYPES: Set<String> =
        setOf("Int", "Long", "Boolean", "Double", "Float", "Char", "Byte", "Short")
}
