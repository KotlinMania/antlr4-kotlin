/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.misc

import io.github.kotlinmania.encodingrs.Encoding
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.readByteArray
import io.github.kotlinmania.io.readString
import io.github.kotlinmania.io.writeString

internal object CommonUtils {
    fun <T> join(
        iter: Iterator<T?>,
        separator: String?,
    ): String {
        val buf = StringBuilder()
        while (iter.hasNext()) {
            buf.append(iter.next())
            if (iter.hasNext()) buf.append(separator)
        }
        return buf.toString()
    }

    fun <T> join(
        array: Array<T?>,
        separator: String?,
    ): String {
        val builder = StringBuilder()
        for (i in array.indices) {
            builder.append(array[i])
            if (i < array.size - 1) builder.append(separator)
        }
        return builder.toString()
    }

    fun numNonnull(data: Array<Any?>?): Int {
        var n = 0
        if (data == null) return n
        for (o in data) if (o != null) n++
        return n
    }

    fun <T> removeAllElements(
        data: MutableCollection<T?>?,
        value: T?,
    ) {
        if (data == null) return
        while (data.contains(value)) data.remove(value)
    }

    fun escapeWhitespace(
        s: String,
        escapeSpaces: Boolean,
    ): String {
        val buf = StringBuilder()
        for (c in s) {
            when {
                c == ' ' && escapeSpaces -> buf.append('\u00B7')
                c == '\t' -> buf.append("\\t")
                c == '\n' -> buf.append("\\n")
                c == '\r' -> buf.append("\\r")
                else -> buf.append(c)
            }
        }
        return buf.toString()
    }

    fun toSet(bits: BitSet): IntervalSet {
        val s = IntervalSet()
        var i = bits.nextSetBit(0)
        while (i >= 0) {
            s.add(i)
            i = bits.nextSetBit(i + 1)
        }
        return s
    }

    fun expandTabs(
        s: String?,
        tabSize: Int,
    ): String? {
        if (s == null) return null
        val buf = StringBuilder()
        var col = 0
        for (c in s) {
            when (c) {
                '\n' -> {
                    col = 0
                    buf.append(c)
                }

                '\t' -> {
                    val n = tabSize - col % tabSize
                    col += n
                    buf.append(spaces(n))
                }

                else -> {
                    col++
                    buf.append(c)
                }
            }
        }
        return buf.toString()
    }

    fun spaces(n: Int): String = sequence(n, " ")

    fun newlines(n: Int): String = sequence(n, "\n")

    fun sequence(
        n: Int,
        s: String?,
    ): String {
        val buf = StringBuilder()
        repeat(n) { buf.append(s) }
        return buf.toString()
    }

    fun count(
        s: String,
        x: Char,
    ): Int = s.count { it == x }

    fun decodeBytes(
        bytes: ByteArray,
        encoding: String?,
    ): String {
        if (encoding == null) {
            return bytes.decodeToString()
        }
        val normalized = encoding.trim().uppercase().replace('-', '_')
        if (normalized == "UTF_8" || normalized == "UTF8") {
            return bytes.decodeToString()
        }
        if (normalized == "ISO_8859_1" || normalized == "ISO8859_1" || normalized == "LATIN1" || normalized == "LATIN_1") {
            return CharArray(bytes.size) { idx -> (bytes[idx].toInt() and 0xFF).toChar() }.concatToString()
        }
        if (normalized == "US_ASCII" || normalized == "ASCII") {
            return CharArray(bytes.size) { idx ->
                val b = bytes[idx].toInt() and 0xFF
                if (b <= 0x7F) b.toChar() else '\uFFFD'
            }.concatToString()
        }
        val enc =
            Encoding.forLabel(encoding.trim())
                ?: throw IllegalArgumentException("Unsupported encoding: $encoding")
        return enc.decode(bytes).first
    }

    fun encodeString(
        content: String,
        encoding: String?,
    ): ByteArray {
        if (encoding == null) {
            return content.encodeToByteArray()
        }
        val normalized = encoding.trim().uppercase().replace('-', '_')
        if (normalized == "UTF_8" || normalized == "UTF8") {
            return content.encodeToByteArray()
        }
        if (normalized == "ISO_8859_1" || normalized == "ISO8859_1" || normalized == "LATIN1" || normalized == "LATIN_1") {
            return ByteArray(content.length) { idx ->
                val code = content[idx].code
                if (code <= 0xFF) code.toByte() else '?'.code.toByte()
            }
        }
        if (normalized == "US_ASCII" || normalized == "ASCII") {
            return ByteArray(content.length) { idx ->
                val code = content[idx].code
                if (code <= 0x7F) code.toByte() else '?'.code.toByte()
            }
        }
        if (normalized == "UTF_16LE" || normalized == "UTF16LE") {
            val out = ByteArray(content.length * 2)
            for (i in content.indices) {
                val u = content[i].code
                out[i * 2] = (u and 0xFF).toByte()
                out[i * 2 + 1] = ((u ushr 8) and 0xFF).toByte()
            }
            return out
        }
        if (normalized == "UTF_16BE" || normalized == "UTF16BE" || normalized == "UTF_16" || normalized == "UTF16") {
            val out = ByteArray(content.length * 2)
            for (i in content.indices) {
                val u = content[i].code
                out[i * 2] = ((u ushr 8) and 0xFF).toByte()
                out[i * 2 + 1] = (u and 0xFF).toByte()
            }
            return out
        }
        val enc =
            Encoding.forLabel(encoding.trim())
                ?: throw IllegalArgumentException("Unsupported encoding: $encoding")
        return enc.encode(content).first
    }
}

object Utils {
    internal fun <T> join(
        iter: Iterator<T?>,
        separator: String?,
    ): String = CommonUtils.join(iter, separator)

    internal fun <T> join(
        array: Array<T?>,
        separator: String?,
    ): String = CommonUtils.join(array, separator)

    fun <T : Any> joinList(
        items: List<T>,
        separator: String,
    ): String = items.joinToString(separator)

    internal fun numNonnull(data: Array<Any?>?): Int = CommonUtils.numNonnull(data)

    internal fun <T> removeAllElements(
        data: MutableCollection<T?>?,
        value: T?,
    ) {
        CommonUtils.removeAllElements(data, value)
    }

    fun escapeWhitespace(
        s: String,
        escapeSpaces: Boolean,
    ): String = CommonUtils.escapeWhitespace(s, escapeSpaces)

    fun writeFile(
        fileName: String,
        content: String,
    ) {
        writeFile(fileName, content, null)
    }

    fun writeFile(
        fileName: String,
        content: String,
        encoding: String?,
    ) {
        val path = Path(fileName)
        path.parent?.let { parent ->
            SystemFileSystem.createDirectories(parent)
        }
        if (encoding == null) {
            SystemFileSystem.sink(path).buffered().use { sink ->
                sink.writeString(content)
            }
        } else {
            val encoded = CommonUtils.encodeString(content, encoding)
            SystemFileSystem.sink(path).buffered().use { sink ->
                sink.write(encoded)
            }
        }
    }

    fun readFile(fileName: String): CharArray = readFile(fileName, null)

    fun readFile(
        fileName: String,
        encoding: String?,
    ): CharArray = readFileAsString(fileName, encoding).toCharArray()

    internal fun readFileAsString(
        fileName: String,
        encoding: String?,
    ): String {
        val path = Path(fileName)
        return if (encoding == null) {
            SystemFileSystem.source(path).buffered().use { source ->
                source.readString()
            }
        } else {
            val bytes =
                SystemFileSystem.source(path).buffered().use { source ->
                    source.readByteArray()
                }
            CommonUtils.decodeBytes(bytes, encoding)
        }
    }

    internal fun toMap(keys: Array<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for (i in keys.indices) {
            result[keys[i]] = i
        }
        return result
    }

    fun toMap(keys: List<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for (i in keys.indices) {
            result[keys[i]] = i
        }
        return result
    }

    fun toCharArray(data: IntegerList?): CharArray? = data?.toCharArray()

    fun toSet(bits: BitSet): IntervalSet = CommonUtils.toSet(bits)

    fun expandTabs(
        s: String?,
        tabSize: Int,
    ): String? = CommonUtils.expandTabs(s, tabSize)

    fun spaces(n: Int): String = CommonUtils.spaces(n)

    fun newlines(n: Int): String = CommonUtils.newlines(n)

    fun sequence(
        n: Int,
        s: String?,
    ): String = CommonUtils.sequence(n, s)

    fun count(
        s: String,
        x: Char,
    ): Int = CommonUtils.count(s, x)
}
