package io.github.kotlinmania.antlr4.misc

class ParseCancellationException : RuntimeException {
    constructor() : super(null, null)
    constructor(message: String?) : super(message, null)
    constructor(cause: Throwable?) : super(cause?.toString(), cause)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
