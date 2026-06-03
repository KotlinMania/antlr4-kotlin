/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4

class CodePointBuffer private constructor(
    val type: Type,
    private var position: Int,
    private val limit: Int,
    private val byteArray: ByteArray?,
    private val charArray: CharArray?,
    private val intArray: IntArray?,
) {
    enum class Type {
        BYTE,
        CHAR,
        INT,
    }

    fun position(): Int = position

    fun position(newPosition: Int) {
        require(newPosition in 0..limit) { "position $newPosition outside buffer limit $limit" }
        position = newPosition
    }

    fun remaining(): Int = limit - position

    fun get(offset: Int): Int {
        require(offset in 0..<limit) { "offset $offset outside buffer limit $limit" }
        return when (type) {
            Type.BYTE -> byteArray!![offset].toInt() and 0xFF
            Type.CHAR -> charArray!![offset].code
            Type.INT -> intArray!![offset]
        }
    }

    internal fun getType(): Type = type

    internal fun arrayOffset(): Int = 0

    internal fun byteArray(): ByteArray {
        require(type == Type.BYTE)
        return byteArray!!
    }

    internal fun charArray(): CharArray {
        require(type == Type.CHAR)
        return charArray!!
    }

    internal fun intArray(): IntArray {
        require(type == Type.INT)
        return intArray!!
    }

    companion object {
        fun withBytes(byteArray: ByteArray): CodePointBuffer =
            CodePointBuffer(Type.BYTE, 0, byteArray.size, byteArray.copyOf(), null, null)

        fun withChars(charArray: CharArray): CodePointBuffer =
            CodePointBuffer(Type.CHAR, 0, charArray.size, null, charArray.copyOf(), null)

        fun withInts(intArray: IntArray): CodePointBuffer =
            CodePointBuffer(Type.INT, 0, intArray.size, null, null, intArray.copyOf())

        fun builder(initialBufferSize: Int): Builder = Builder(initialBufferSize)
    }

    class Builder internal constructor(
        initialBufferSize: Int,
    ) {
        private var type: Type = Type.BYTE
        private var byteArray = ByteArray(maxOf(1, initialBufferSize))
        private var byteLength = 0
        private var charArray: CharArray? = null
        private var charLength = 0
        private var intArray: IntArray? = null
        private var intLength = 0

        internal fun getType(): Type = type

        fun build(): CodePointBuffer =
            when (type) {
                Type.BYTE ->
                    CodePointBuffer(
                        Type.BYTE,
                        0,
                        byteLength,
                        byteArray.copyOf(byteLength),
                        null,
                        null,
                    )

                Type.CHAR ->
                    CodePointBuffer(
                        Type.CHAR,
                        0,
                        charLength,
                        null,
                        charArray!!.copyOf(charLength),
                        null,
                    )

                Type.INT ->
                    CodePointBuffer(
                        Type.INT,
                        0,
                        intLength,
                        null,
                        null,
                        intArray!!.copyOf(intLength),
                    )
            }

        fun ensureRemaining(remainingNeeded: Int) {
            ensureCapacity(activeLength() + remainingNeeded)
        }

        fun append(input: String) {
            append(input.toCharArray(), 0, input.length)
        }

        fun append(input: CharArray) {
            append(input, 0, input.size)
        }

        fun append(
            input: CharArray,
            startIndex: Int,
            length: Int,
        ) {
            require(startIndex >= 0)
            require(length >= 0)
            require(startIndex + length <= input.size)

            var index = startIndex
            val stop = startIndex + length
            while (index < stop) {
                index =
                    when (type) {
                        Type.BYTE -> appendByte(input, index, stop)
                        Type.CHAR -> appendChar(input, index, stop)
                        Type.INT -> appendInt(input, index, stop)
                    }
            }
        }

        private fun appendByte(
            input: CharArray,
            index: Int,
            stop: Int,
        ): Int {
            val c = input[index]
            if (c.code <= 0xFF) {
                ensureByteCapacity(byteLength + 1)
                byteArray[byteLength++] = (c.code and 0xFF).toByte()
                return index + 1
            }

            if (isHighSurrogate(c)) {
                byteToIntBuffer(stop - index)
                return appendInt(input, index, stop)
            }

            byteToCharBuffer(stop - index)
            return appendChar(input, index, stop)
        }

        private fun appendChar(
            input: CharArray,
            index: Int,
            stop: Int,
        ): Int {
            val c = input[index]
            if (isHighSurrogate(c)) {
                charToIntBuffer(stop - index)
                return appendInt(input, index, stop)
            }

            ensureCharCapacity(charLength + 1)
            charArray!![charLength++] = c
            return index + 1
        }

        private fun appendInt(
            input: CharArray,
            index: Int,
            stop: Int,
        ): Int {
            val c = input[index]
            val codePoint =
                if (isHighSurrogate(c) && index + 1 < stop && isLowSurrogate(input[index + 1])) {
                    toCodePoint(c, input[index + 1])
                } else {
                    c.code
                }
            ensureIntCapacity(intLength + 1)
            intArray!![intLength++] = codePoint
            return if (codePoint > 0xFFFF) index + 2 else index + 1
        }

        private fun activeLength(): Int =
            when (type) {
                Type.BYTE -> byteLength
                Type.CHAR -> charLength
                Type.INT -> intLength
            }

        private fun ensureCapacity(requiredCapacity: Int) {
            when (type) {
                Type.BYTE -> ensureByteCapacity(requiredCapacity)
                Type.CHAR -> ensureCharCapacity(requiredCapacity)
                Type.INT -> ensureIntCapacity(requiredCapacity)
            }
        }

        private fun ensureByteCapacity(requiredCapacity: Int) {
            if (requiredCapacity > byteArray.size) {
                byteArray = byteArray.copyOf(roundUpToPowerOfTwo(requiredCapacity))
            }
        }

        private fun ensureCharCapacity(requiredCapacity: Int) {
            val chars = charArray!!
            if (requiredCapacity > chars.size) {
                charArray = chars.copyOf(roundUpToPowerOfTwo(requiredCapacity))
            }
        }

        private fun ensureIntCapacity(requiredCapacity: Int) {
            val ints = intArray!!
            if (requiredCapacity > ints.size) {
                intArray = ints.copyOf(roundUpToPowerOfTwo(requiredCapacity))
            }
        }

        private fun byteToCharBuffer(toAppend: Int) {
            val newCapacity = maxOf(byteLength + toAppend, byteArray.size / 2, 1)
            val newBuffer = CharArray(roundUpToPowerOfTwo(newCapacity))
            for (i in 0..<byteLength) {
                newBuffer[i] = (byteArray[i].toInt() and 0xFF).toChar()
            }
            type = Type.CHAR
            charArray = newBuffer
            charLength = byteLength
            byteArray = ByteArray(0)
            byteLength = 0
        }

        private fun byteToIntBuffer(toAppend: Int) {
            val newCapacity = maxOf(byteLength + toAppend, byteArray.size / 4, 1)
            val newBuffer = IntArray(roundUpToPowerOfTwo(newCapacity))
            for (i in 0..<byteLength) {
                newBuffer[i] = byteArray[i].toInt() and 0xFF
            }
            type = Type.INT
            intArray = newBuffer
            intLength = byteLength
            byteArray = ByteArray(0)
            byteLength = 0
        }

        private fun charToIntBuffer(toAppend: Int) {
            val chars = charArray!!
            val newCapacity = maxOf(charLength + toAppend, chars.size / 2, 1)
            val newBuffer = IntArray(roundUpToPowerOfTwo(newCapacity))
            for (i in 0..<charLength) {
                newBuffer[i] = chars[i].code and 0xFFFF
            }
            type = Type.INT
            intArray = newBuffer
            intLength = charLength
            charArray = null
            charLength = 0
        }
    }
}
