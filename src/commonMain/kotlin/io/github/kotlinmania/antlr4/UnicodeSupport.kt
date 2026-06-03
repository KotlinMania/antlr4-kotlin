package io.github.kotlinmania.antlr4

internal fun isHighSurrogate(c: Char): Boolean = c in '\uD800'..'\uDBFF'

internal fun isLowSurrogate(c: Char): Boolean = c in '\uDC00'..'\uDFFF'

internal fun toCodePoint(
    high: Char,
    low: Char,
): Int = ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000

internal fun appendCodePoint(
    builder: StringBuilder,
    codePoint: Int,
) {
    if (codePoint <= 0xFFFF) {
        builder.append(codePoint.toChar())
        return
    }

    val value = codePoint - 0x10000
    builder.append(((value ushr 10) + 0xD800).toChar())
    builder.append(((value and 0x3FF) + 0xDC00).toChar())
}

internal fun codePointsToString(
    codePoints: IntArray,
    startIndex: Int,
    length: Int,
): String {
    val builder = StringBuilder(length)
    for (i in startIndex until startIndex + length) {
        appendCodePoint(builder, codePoints[i])
    }
    return builder.toString()
}

internal fun latin1BytesToString(
    bytes: ByteArray,
    startIndex: Int,
    length: Int,
): String {
    val builder = StringBuilder(length)
    for (i in startIndex until startIndex + length) {
        builder.append((bytes[i].toInt() and 0xFF).toChar())
    }
    return builder.toString()
}

internal fun signum(value: Int): Int =
    when {
        value < 0 -> -1
        value > 0 -> 1
        else -> 0
    }

internal fun roundUpToPowerOfTwo(value: Int): Int {
    var rounded = 1
    while (rounded < value) {
        rounded = rounded shl 1
    }
    return rounded
}
