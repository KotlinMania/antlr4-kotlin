/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.tool

import io.github.kotlinmania.antlr4.RecognitionException
import io.github.kotlinmania.antlr4.Token
import io.github.kotlinmania.antlr4.internal.withHostFileSystemOrNull
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem

/**
 * Manages formatting, counting, deduplicating one-off severities, and dispatching ANTLR 4 tool diagnostics.
 */
class ErrorManager(
    var tool: ToolErrorContext? = null,
) {
    var numErrors: Int = 0
    var warnings: Int = 0

    internal val errorTypes: MutableSet<ErrorType> = LinkedHashSet()

    var formatName: String = "antlr"
        private set

    private var layout: MessageRenderer.MessageLayout = MessageRenderer.MessageLayout.ANTLR
    private val fallbackListener: DefaultToolListener = DefaultToolListener(this)

    val recordedErrorTypes: List<ErrorType>
        get() = errorTypes.toList()

    fun resetErrorState() {
        numErrors = 0
        warnings = 0
        errorTypes.clear()
    }

    fun formatMessage(msg: ANTLRMessage): String {
        val verbose = tool?.longMessages ?: false
        val messageText = msg.renderMessageText(verbose)
        var locationValid = false
        if (msg.line != -1) {
            locationValid = true
        }
        if (msg.charPosition != -1) {
            locationValid = true
        }
        val rawFileName = msg.fileName
        var displayFileName: String? = rawFileName
        if (rawFileName != null) {
            if (formatName == "antlr") {
                val shortened =
                    withHostFileSystemOrNull {
                        val path = Path(rawFileName)
                        if (path.name != rawFileName && SystemFileSystem.exists(path)) {
                            path.name
                        } else {
                            null
                        }
                    }
                if (shortened != null) {
                    displayFileName = shortened
                }
            }
            locationValid = true
        }
        val locationStr =
            if (locationValid) {
                layout.formatLocation(displayFileName, msg.line, msg.charPosition)
            } else {
                null
            }
        return layout.formatReport(
            location = locationStr,
            severityText = msg.errorType.severity.text,
            id = msg.errorType.code,
            messageText = messageText,
        )
    }

    fun formatWantsSingleLineMessage(): Boolean = layout.wantsSingleLine

    fun info(msg: String?) {
        val sink = tool ?: fallbackListener
        sink.info(msg)
    }

    fun syntaxError(
        etype: ErrorType,
        fileName: String?,
        token: Token?,
        detail: String,
    ) {
        val msg = GrammarSyntaxMessage(etype, fileName, token, detail)
        emit(etype, msg)
    }

    internal fun syntaxError(
        etype: ErrorType,
        fileName: String?,
        token: Token?,
        antlrException: RecognitionException?,
        vararg args: Any?,
    ) {
        val msg = GrammarSyntaxMessage(etype, fileName, token, antlrException, args.toList())
        emit(etype, msg)
    }

    internal fun toolError(
        errorType: ErrorType,
        vararg args: Any?,
    ) {
        val msg = ToolMessage(errorType, null, args.toList())
        emit(errorType, msg)
    }

    internal fun toolErrorWithCause(
        errorType: ErrorType,
        cause: Throwable?,
        vararg args: Any?,
    ) {
        val msg = ToolMessage(errorType, cause, args.toList())
        emit(errorType, msg)
    }

    internal fun grammarError(
        etype: ErrorType,
        fileName: String?,
        token: Token?,
        vararg args: Any?,
    ) {
        val msg = GrammarSemanticsMessage(etype, fileName, token, args.toList())
        emit(etype, msg)
    }

    internal fun leftRecursionCycles(
        fileName: String?,
        cycles: Collection<Collection<ErrorTemplateObject>>,
    ) {
        numErrors++
        val msg = LeftRecursionCyclesMessage(fileName, cycles)
        val sink = tool ?: fallbackListener
        sink.error(msg)
    }

    fun emit(
        etype: ErrorType,
        msg: ANTLRMessage,
    ) {
        val sink = tool ?: fallbackListener
        when (etype.severity) {
            ErrorSeverity.WARNING_ONE_OFF -> {
                if (!errorTypes.contains(etype)) {
                    warnings++
                    sink.warning(msg)
                }
            }

            ErrorSeverity.WARNING -> {
                warnings++
                sink.warning(msg)
            }

            ErrorSeverity.ERROR_ONE_OFF -> {
                if (!errorTypes.contains(etype)) {
                    numErrors++
                    sink.error(msg)
                }
            }

            ErrorSeverity.ERROR, ErrorSeverity.FATAL -> {
                numErrors++
                sink.error(msg)
            }

            ErrorSeverity.INFO -> {
                sink.info(msg.renderMessageText(tool?.longMessages ?: false))
            }
        }
        errorTypes.add(etype)
    }

    fun setFormat(name: String) {
        val resolved = MessageRenderer.MessageLayout.fromName(name)
        if (resolved == null) {
            rawError("no such message format file $FORMATS_DIR$name.stg retrying with default ANTLR format")
            formatName = "antlr"
            layout = MessageRenderer.MessageLayout.ANTLR
            return
        }
        formatName = name
        layout = resolved
    }

    internal fun panic(
        errorType: ErrorType,
        vararg args: Any?,
    ) {
        val msg = ToolMessage(errorType, args.toList())
        var outputMsg = formatMessage(msg)
        if (formatWantsSingleLineMessage()) {
            outputMsg = outputMsg.replace('\n', ' ')
        }
        panic(outputMsg)
    }

    companion object {
        const val FORMATS_DIR: String = "org/antlr/v4/tool/templates/messages/formats/"

        internal var errorSink: ((String) -> Unit)? = null

        internal fun fatalInternalError(
            error: String?,
            e: Throwable,
        ) {
            internalError(error, e)
            throw IllegalStateException(error, e)
        }

        internal fun internalError(
            error: String?,
            e: Throwable,
        ) {
            rawError("internal error: Exception $e: $error")
        }

        fun internalError(error: String?) {
            rawError("internal error: $error")
        }

        fun rawError(msg: String?) {
            if (msg == null) return
            val sink = errorSink
            if (sink != null) {
                sink(msg)
            } else {
                println(msg)
            }
        }

        internal fun rawError(
            msg: String?,
            e: Throwable,
        ) {
            rawError(msg)
            rawError(e.stackTraceToString())
        }

        fun panic(msg: String?) {
            rawError(msg)
            panic()
        }

        fun panic(): Nothing =
            throw IllegalStateException("ANTLR ErrorManager panic")
    }
}
