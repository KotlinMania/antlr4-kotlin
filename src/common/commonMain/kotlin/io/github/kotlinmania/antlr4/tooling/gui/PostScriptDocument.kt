/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.gui

import kotlin.math.abs
import kotlin.math.roundToLong

class PostScriptDocument(
    fontName: String? = DEFAULT_FONT,
    fontSize: Int = 12,
) {
    private var boundingBoxWidth: Int = 0
    private var boundingBoxHeight: Int = 0
    private var fontMetrics: StandardFontMetrics = StandardFontMetrics(fontName)
    var fontName: String = fontMetrics.psFontName
        private set
    var fontSize: Int = fontSize
        private set
    private var lineWidthValue: Double = 0.3
    private var boundingBoxLine: String? = null
    private val psBuffer: StringBuilder = StringBuilder()
    private var closed: Boolean = false

    init {
        setFont(fontName, fontSize)
    }

    val ps: String
        get() {
            close()
            return header().toString() + psBuffer.toString()
        }

    fun getPS(): String = ps

    fun boundingBox(
        w: Int,
        h: Int,
    ) {
        boundingBoxWidth = w
        boundingBoxHeight = h
        boundingBoxLine = "%%BoundingBox: 0 0 $boundingBoxWidth $boundingBoxHeight\n"
    }

    fun close() {
        if (closed) return
        psBuffer.append("%%Trailer\n")
        closed = true
    }

    private fun header(): StringBuilder {
        val b = StringBuilder()
        b.append("%!PS-Adobe-3.0 EPSF-3.0\n")
        b.append(boundingBoxLine).append('\n')
        b.append("0.3 setlinewidth\n")
        b.append(
            "%% x y w h highlight\n" +
                "/highlight {\n" +
                "        4 dict begin\n" +
                "        /h exch def\n" +
                "        /w exch def\n" +
                "        /y exch def\n" +
                "        /x exch def\n" +
                "        gsave\n" +
                "        newpath\n" +
                "        x y moveto\n" +
                "        0 h rlineto     % up to left corner\n" +
                "        w 0 rlineto     % to upper right corner\n" +
                "        0 h neg rlineto % to lower right corner\n" +
                "        w neg 0 rlineto % back home to lower left corner\n" +
                "        closepath\n" +
                "        .95 .83 .82 setrgbcolor\n" +
                "        fill\n" +
                "        grestore\n" +
                "        end\n" +
                "} def\n",
        )
        return b
    }

    fun setFont(
        fontName: String?,
        fontSize: Int,
    ) {
        this.fontMetrics = StandardFontMetrics(fontName)
        this.fontName = fontMetrics.psFontName
        this.fontSize = fontSize
        val psname = POSTSCRIPT_FONT_NAMES[this.fontName] ?: this.fontName
        psBuffer.append("/$psname findfont $fontSize scalefont setfont\n")
    }

    fun lineWidth(w: Double) {
        lineWidthValue = w
        psBuffer.append(w).append(" setlinewidth\n")
    }

    fun move(
        x: Double,
        y: Double,
    ) {
        psBuffer.append("${formatFixed3(x)} ${formatFixed3(y)} moveto\n")
    }

    fun lineto(
        x: Double,
        y: Double,
    ) {
        psBuffer.append("${formatFixed3(x)} ${formatFixed3(y)} lineto\n")
    }

    fun line(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ) {
        move(x1, y1)
        lineto(x2, y2)
    }

    fun rect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
    ) {
        line(x, y, x, y + height)
        line(x, y + height, x + width, y + height)
        line(x + width, y + height, x + width, y)
        line(x + width, y, x, y)
    }

    /** Emit a highlighted box for an error node. */
    fun highlight(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
    ) {
        psBuffer.append("${formatFixed3(x)} ${formatFixed3(y)} ${formatFixed3(width)} ${formatFixed3(height)} highlight\n")
    }

    fun stroke() {
        psBuffer.append("stroke\n")
    }

    fun text(
        s: String,
        x: Double,
        y: Double,
    ) {
        val buf = StringBuilder()
        for (c in s) {
            when (c) {
                '\\', '(', ')' -> {
                    buf.append('\\')
                    buf.append(c)
                }

                else -> {
                    buf.append(c)
                }
            }
        }
        move(x, y)
        psBuffer.append("($buf) show\n")
        stroke()
    }

    fun getWidth(c: Char): Double = fontMetrics.getWidth(c, fontSize)

    fun getWidth(s: String): Double = fontMetrics.getWidth(s, fontSize)

    val lineHeight: Double
        get() = fontMetrics.getLineHeight(fontSize)

    companion object {
        const val DEFAULT_FONT: String = "CourierNew"

        val POSTSCRIPT_FONT_NAMES: Map<String, String> =
            mapOf(
                "SansSerif.plain" to "ArialMT",
                "SansSerif.bold" to "Arial-BoldMT",
                "SansSerif.italic" to "Arial-ItalicMT",
                "SansSerif.bolditalic" to "Arial-BoldItalicMT",
                "Serif.plain" to "TimesNewRomanPSMT",
                "Serif.bold" to "TimesNewRomanPS-BoldMT",
                "Serif.italic" to "TimesNewRomanPS-ItalicMT",
                "Serif.bolditalic" to "TimesNewRomanPS-BoldItalicMT",
                "Monospaced.plain" to "CourierNewPSMT",
                "Monospaced.bold" to "CourierNewPS-BoldMT",
                "Monospaced.italic" to "CourierNewPS-ItalicMT",
                "Monospaced.bolditalic" to "CourierNewPS-BoldItalicMT",
                "CourierNew" to "CourierNewPSMT",
            )

        internal fun formatFixed3(value: Double): String {
            val scaled = (value * 1000.0).roundToLong()
            val sign = if (scaled < 0L) "-" else ""
            val absScaled = abs(scaled)
            val whole = absScaled / 1000L
            val frac = (absScaled % 1000L).toString().padStart(3, '0')
            return "$sign$whole.$frac"
        }
    }
}
