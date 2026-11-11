/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.gui

/**
 * Font advance-width and line-height metrics in 1000ths of an em for
 * PostScript parse-tree generation.
 */
open class BasicFontMetrics {
    protected var maxCharHeight: Int = 0
    protected var widths: IntArray = IntArray(MAX_CHAR + 1)

    fun getWidth(
        s: String,
        fontSize: Int,
    ): Double {
        var w = 0.0
        for (c in s) {
            w += getWidth(c, fontSize)
        }
        return w
    }

    fun getWidth(
        c: Char,
        fontSize: Int,
    ): Double {
        if (c.code > MAX_CHAR || widths[c.code] == 0) {
            return widths['m'.code] / 1000.0 * fontSize
        }
        return widths[c.code] / 1000.0 * fontSize
    }

    fun getLineHeight(fontSize: Int): Double = maxCharHeight / 1000.0 * fontSize

    companion object {
        const val MAX_CHAR: Int = 0xFF
    }
}

/**
 * Pure-Kotlin Adobe Core-14 font metrics (Courier, Helvetica/Arial, Times)
 * providing deterministic glyph widths across all platforms without AWT.
 */
class StandardFontMetrics(
    requestedFontName: String?,
) : BasicFontMetrics() {
    val psFontName: String

    init {
        val raw = (requestedFontName ?: PostScriptDocument.DEFAULT_FONT).trim()
        val key = raw.lowercase()
        when {
            key.contains("courier") || key.contains("mono") -> {
                psFontName = PostScriptDocument.POSTSCRIPT_FONT_NAMES[raw] ?: "CourierNewPSMT"
                maxCharHeight = 833
                for (ch in 32..MAX_CHAR) {
                    widths[ch] = 600
                }
            }

            key.contains("times") || key.contains("serif") && !key.contains("sans") -> {
                psFontName = PostScriptDocument.POSTSCRIPT_FONT_NAMES[raw] ?: "TimesNewRomanPSMT"
                maxCharHeight = 891
                populateTimesWidths(widths)
            }

            else -> {
                psFontName =
                    PostScriptDocument.POSTSCRIPT_FONT_NAMES[raw]
                        ?: if (raw.equals("Helvetica", ignoreCase = true)) "Helvetica" else raw
                maxCharHeight = 931
                populateHelveticaWidths(widths)
            }
        }
    }

    companion object {
        private fun populateHelveticaWidths(target: IntArray) {
            for (ch in 32..MAX_CHAR) target[ch] = 556
            target[' '.code] = 278
            target['!'.code] = 278
            target['"'.code] = 355
            target['\''.code] = 191
            target['('.code] = 333
            target[')'.code] = 333
            target['*'.code] = 389
            target['+'.code] = 584
            target[','.code] = 278
            target['-'.code] = 333
            target['.'.code] = 278
            target['/'.code] = 278
            target[':'.code] = 278
            target[';'.code] = 278
            target['<'.code] = 584
            target['='.code] = 584
            target['>'.code] = 584
            target['@'.code] = 1015
            target['A'.code] = 667
            target['B'.code] = 667
            target['C'.code] = 722
            target['D'.code] = 722
            target['E'.code] = 667
            target['F'.code] = 611
            target['G'.code] = 778
            target['H'.code] = 722
            target['I'.code] = 278
            target['J'.code] = 500
            target['K'.code] = 667
            target['L'.code] = 556
            target['M'.code] = 833
            target['N'.code] = 722
            target['O'.code] = 778
            target['P'.code] = 667
            target['Q'.code] = 778
            target['R'.code] = 722
            target['S'.code] = 667
            target['T'.code] = 611
            target['U'.code] = 722
            target['V'.code] = 667
            target['W'.code] = 944
            target['X'.code] = 667
            target['Y'.code] = 667
            target['Z'.code] = 611
            target['['.code] = 278
            target['\\'.code] = 278
            target[']'.code] = 278
            target['^'.code] = 469
            target['`'.code] = 333
            target['c'.code] = 500
            target['f'.code] = 278
            target['i'.code] = 222
            target['j'.code] = 222
            target['k'.code] = 500
            target['l'.code] = 222
            target['m'.code] = 833
            target['r'.code] = 333
            target['s'.code] = 500
            target['t'.code] = 278
            target['v'.code] = 500
            target['w'.code] = 722
            target['x'.code] = 500
            target['y'.code] = 500
            target['z'.code] = 500
            target['{'.code] = 334
            target['|'.code] = 260
            target['}'.code] = 334
            target['~'.code] = 584
        }

        private fun populateTimesWidths(target: IntArray) {
            for (ch in 32..MAX_CHAR) target[ch] = 500
            target[' '.code] = 250
            target['!'.code] = 333
            target['"'.code] = 408
            target['\''.code] = 180
            target['('.code] = 333
            target[')'.code] = 333
            target[','.code] = 250
            target['-'.code] = 333
            target['.'.code] = 250
            target['/'.code] = 278
            target[':'.code] = 278
            target[';'.code] = 278
            target['A'.code] = 722
            target['B'.code] = 667
            target['C'.code] = 667
            target['D'.code] = 722
            target['E'.code] = 611
            target['F'.code] = 556
            target['G'.code] = 722
            target['H'.code] = 722
            target['I'.code] = 333
            target['J'.code] = 389
            target['K'.code] = 722
            target['L'.code] = 611
            target['M'.code] = 889
            target['N'.code] = 722
            target['O'.code] = 722
            target['P'.code] = 556
            target['Q'.code] = 722
            target['R'.code] = 667
            target['S'.code] = 556
            target['T'.code] = 611
            target['U'.code] = 722
            target['V'.code] = 722
            target['W'.code] = 944
            target['X'.code] = 722
            target['Y'.code] = 722
            target['Z'.code] = 611
            target['a'.code] = 444
            target['c'.code] = 444
            target['e'.code] = 444
            target['f'.code] = 333
            target['i'.code] = 278
            target['j'.code] = 278
            target['l'.code] = 278
            target['m'.code] = 778
            target['r'.code] = 333
            target['s'.code] = 389
            target['t'.code] = 278
            target['w'.code] = 722
            target['z'.code] = 444
        }
    }
}
