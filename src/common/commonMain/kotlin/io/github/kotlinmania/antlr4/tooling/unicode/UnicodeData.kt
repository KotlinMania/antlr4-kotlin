/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package io.github.kotlinmania.antlr4.tooling.unicode

import io.github.kotlinmania.antlr4.internal.synchronized
import io.github.kotlinmania.antlr4.misc.Interval
import io.github.kotlinmania.antlr4.misc.IntervalSet
import io.github.kotlinmania.regexsyntax.unicode.ClassQuery
import io.github.kotlinmania.regexsyntax.unicode.unicodeClass

/**
 * Pure-Kotlin utility mapping Unicode properties (general category codes, binary
 * property names, scripts, blocks, enumerated break/width properties, and ANTLR4
 * emoji presentation properties) to readonly [IntervalSet] code point ranges.
 */
object UnicodeData {
    private val lock = Any()
    private val cache = HashMap<String, IntervalSet>()
    private val negativeCache = HashSet<String>()

    private val OBSOLETE_GCB_WB_VALUES: Set<String> =
        setOf(
            "ebase",
            "eb",
            "ebasegaz",
            "ebg",
            "emodifier",
            "em",
            "glueafterzwj",
            "gaz",
        )

    private val NON_OTHER_GCB_VALUES: List<String> =
        listOf(
            "CR",
            "LF",
            "Control",
            "Extend",
            "ZWJ",
            "Regional_Indicator",
            "Prepend",
            "SpacingMark",
            "L",
            "V",
            "T",
            "LV",
            "LVT",
        )

    private val TR35_EXTENDED_PICTOGRAPHIC_EXTRA_RANGES: Array<IntArray> =
        arrayOf(
            intArrayOf(0x1F774, 0x1F77F),
            intArrayOf(0x2700, 0x2701),
            intArrayOf(0x2703, 0x2704),
            intArrayOf(0x270E, 0x270E),
            intArrayOf(0x2710, 0x2711),
            intArrayOf(0x2765, 0x2767),
            intArrayOf(0x1F030, 0x1F093),
            intArrayOf(0x1F094, 0x1F09F),
            intArrayOf(0x1F10D, 0x1F10F),
            intArrayOf(0x1F12F, 0x1F12F),
            intArrayOf(0x1F16C, 0x1F16F),
            intArrayOf(0x1F1AD, 0x1F1E5),
            intArrayOf(0x1F260, 0x1F265),
            intArrayOf(0x1F203, 0x1F20F),
            intArrayOf(0x1F23C, 0x1F23F),
            intArrayOf(0x1F249, 0x1F24F),
            intArrayOf(0x1F252, 0x1F25F),
            intArrayOf(0x1F266, 0x1F2FF),
            intArrayOf(0x1F7D5, 0x1F7FF),
            intArrayOf(0x1F000, 0x1F003),
            intArrayOf(0x1F005, 0x1F02B),
            intArrayOf(0x1F02C, 0x1F02F),
            intArrayOf(0x1F322, 0x1F323),
            intArrayOf(0x1F394, 0x1F395),
            intArrayOf(0x1F398, 0x1F398),
            intArrayOf(0x1F39C, 0x1F39D),
            intArrayOf(0x1F3F1, 0x1F3F2),
            intArrayOf(0x1F3F6, 0x1F3F6),
            intArrayOf(0x1F4FE, 0x1F4FE),
            intArrayOf(0x1F53E, 0x1F548),
            intArrayOf(0x1F54F, 0x1F54F),
            intArrayOf(0x1F568, 0x1F56E),
            intArrayOf(0x1F571, 0x1F572),
            intArrayOf(0x1F57B, 0x1F586),
            intArrayOf(0x1F588, 0x1F589),
            intArrayOf(0x1F58E, 0x1F58F),
            intArrayOf(0x1F591, 0x1F594),
            intArrayOf(0x1F597, 0x1F5A3),
            intArrayOf(0x1F5A6, 0x1F5A7),
            intArrayOf(0x1F5A9, 0x1F5B0),
            intArrayOf(0x1F5B3, 0x1F5BB),
            intArrayOf(0x1F5BD, 0x1F5C1),
            intArrayOf(0x1F5C5, 0x1F5D0),
            intArrayOf(0x1F5D4, 0x1F5DB),
            intArrayOf(0x1F5DF, 0x1F5E0),
            intArrayOf(0x1F5E2, 0x1F5E2),
            intArrayOf(0x1F5E4, 0x1F5E7),
            intArrayOf(0x1F5E9, 0x1F5EE),
            intArrayOf(0x1F5F0, 0x1F5F2),
            intArrayOf(0x1F5F4, 0x1F5F9),
            intArrayOf(0x2605, 0x2605),
            intArrayOf(0x2607, 0x260D),
            intArrayOf(0x260F, 0x2610),
            intArrayOf(0x2612, 0x2612),
            intArrayOf(0x2616, 0x2617),
            intArrayOf(0x2619, 0x261C),
            intArrayOf(0x261E, 0x261F),
            intArrayOf(0x2621, 0x2621),
            intArrayOf(0x2624, 0x2625),
            intArrayOf(0x2627, 0x2629),
            intArrayOf(0x262B, 0x262D),
            intArrayOf(0x2630, 0x2637),
            intArrayOf(0x263B, 0x2647),
            intArrayOf(0x2654, 0x265F),
            intArrayOf(0x2661, 0x2662),
            intArrayOf(0x2664, 0x2664),
            intArrayOf(0x2667, 0x2667),
            intArrayOf(0x2669, 0x267A),
            intArrayOf(0x267C, 0x267E),
            intArrayOf(0x2680, 0x2691),
            intArrayOf(0x2695, 0x2695),
            intArrayOf(0x2698, 0x2698),
            intArrayOf(0x269A, 0x269A),
            intArrayOf(0x269D, 0x269F),
            intArrayOf(0x26A2, 0x26A9),
            intArrayOf(0x26AC, 0x26AF),
            intArrayOf(0x26B2, 0x26BC),
            intArrayOf(0x26BF, 0x26C3),
            intArrayOf(0x26C6, 0x26C7),
            intArrayOf(0x26C9, 0x26CD),
            intArrayOf(0x26D0, 0x26D0),
            intArrayOf(0x26D2, 0x26D2),
            intArrayOf(0x26D5, 0x26E8),
            intArrayOf(0x26EB, 0x26EF),
            intArrayOf(0x26F6, 0x26F6),
            intArrayOf(0x26FB, 0x26FC),
            intArrayOf(0x26FE, 0x26FF),
            intArrayOf(0x2388, 0x2388),
            intArrayOf(0x1FA00, 0x1FFFD),
            intArrayOf(0x1F0A0, 0x1F0AE),
            intArrayOf(0x1F0B1, 0x1F0BF),
            intArrayOf(0x1F0C1, 0x1F0CF),
            intArrayOf(0x1F0D1, 0x1F0F5),
            intArrayOf(0x1F0AF, 0x1F0B0),
            intArrayOf(0x1F0C0, 0x1F0C0),
            intArrayOf(0x1F0D0, 0x1F0D0),
            intArrayOf(0x1F0F6, 0x1F0FF),
            intArrayOf(0x1F80C, 0x1F80F),
            intArrayOf(0x1F848, 0x1F84F),
            intArrayOf(0x1F85A, 0x1F85F),
            intArrayOf(0x1F888, 0x1F88F),
            intArrayOf(0x1F8AE, 0x1F8FF),
            intArrayOf(0x1F900, 0x1F90B),
            intArrayOf(0x1F91F, 0x1F91F),
            intArrayOf(0x1F928, 0x1F92F),
            intArrayOf(0x1F931, 0x1F932),
            intArrayOf(0x1F94C, 0x1F94C),
            intArrayOf(0x1F95F, 0x1F96B),
            intArrayOf(0x1F992, 0x1F997),
            intArrayOf(0x1F9D0, 0x1F9E6),
            intArrayOf(0x1F90C, 0x1F90F),
            intArrayOf(0x1F93F, 0x1F93F),
            intArrayOf(0x1F94D, 0x1F94F),
            intArrayOf(0x1F96C, 0x1F97F),
            intArrayOf(0x1F998, 0x1F9BF),
            intArrayOf(0x1F9C1, 0x1F9CF),
            intArrayOf(0x1F9E7, 0x1F9FF),
            intArrayOf(0x1F6C6, 0x1F6CA),
            intArrayOf(0x1F6D3, 0x1F6D4),
            intArrayOf(0x1F6E6, 0x1F6E8),
            intArrayOf(0x1F6EA, 0x1F6EA),
            intArrayOf(0x1F6F1, 0x1F6F2),
            intArrayOf(0x1F6F7, 0x1F6F8),
            intArrayOf(0x1F6D5, 0x1F6DF),
            intArrayOf(0x1F6ED, 0x1F6EF),
            intArrayOf(0x1F6F9, 0x1F6FF),
        )

    private fun normalize(propertyCodeOrAlias: String): String =
        propertyCodeOrAlias.lowercase().replace('-', '_')

    private fun stripSeparators(s: String): String =
        s
            .lowercase()
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")

    /**
     * Given a Unicode property (general category code, binary property name, script name,
     * block name, or enumerated `Property=Value`), returns the readonly [IntervalSet] of
     * Unicode code point ranges having that property, or `null` if unknown.
     */
    fun getPropertyCodePoints(propertyCodeOrAlias: String): IntervalSet? {
        if (propertyCodeOrAlias.isEmpty()) return null
        val key = normalize(propertyCodeOrAlias)
        return synchronized(lock) {
            val cached = cache[key]
            if (cached != null) return@synchronized cached
            if (key in negativeCache) return@synchronized null
            val computed = computePropertyCodePoints(propertyCodeOrAlias)
            if (computed != null) {
                if (!computed.isReadonly()) {
                    computed.makeReadonly()
                }
                cache[key] = computed
            } else {
                negativeCache.add(key)
            }
            computed
        }
    }

    private fun computePropertyCodePoints(raw: String): IntervalSet? {
        val eqIndex = raw.indexOf('=')
        return if (eqIndex >= 0) {
            val propRaw = raw.substring(0, eqIndex)
            val valRaw = raw.substring(eqIndex + 1)
            if (propRaw.isEmpty() || valRaw.isEmpty()) null else computeByValue(propRaw, valRaw)
        } else {
            computeBare(raw)
        }
    }

    private fun computeByValue(
        propRaw: String,
        valRaw: String,
    ): IntervalSet? {
        val propStripped = stripSeparators(propRaw)
        val valNormalized = normalize(valRaw)
        val valStripped = stripSeparators(valRaw)

        return when (propStripped) {
            "emojipresentation" ->
                when (valStripped) {
                    "emojidefault" -> {
                        val emoji = queryBinary("Emoji") ?: return null
                        val pres = queryBinary("Emoji_Presentation") ?: return null
                        emoji.and(pres)
                    }
                    "textdefault" -> {
                        val emoji = queryBinary("Emoji") ?: return null
                        val pres = queryBinary("Emoji_Presentation") ?: return null
                        emoji.subtract(pres)
                    }
                    "text" -> {
                        val emoji = queryBinary("Emoji") ?: return null
                        IntervalSet.COMPLETE_CHAR_SET.subtract(emoji)
                    }
                    else -> null
                }

            "blk", "block" -> {
                val idx =
                    UnicodeBlocksData.BLOCK_ALIASES[valNormalized]
                        ?: UnicodeBlocksData.BLOCK_ALIASES[valStripped]
                        ?: return null
                fromFlatSortedRanges(UnicodeBlocksData.BLOCK_RANGES[idx])
            }

            "ea", "eastasianwidth" -> {
                val idx =
                    UnicodeEastAsianWidthData.EAW_ALIASES[valNormalized]
                        ?: UnicodeEastAsianWidthData.EAW_ALIASES[valStripped]
                        ?: return null
                fromFlatSortedRanges(UnicodeEastAsianWidthData.EAW_RANGES[idx])
            }

            "gc", "generalcategory" ->
                when (valStripped) {
                    "cs", "surrogate" -> IntervalSet.of(0xD800, 0xDFFF)
                    "c", "other" -> {
                        val base = queryRegex(ClassQuery.ByValue("General_Category", "Other")) ?: return null
                        base.add(0xD800, 0xDFFF)
                        base
                    }
                    "any" -> IntervalSet(IntervalSet.COMPLETE_CHAR_SET)
                    "assigned" -> {
                        val unassigned = queryRegex(ClassQuery.ByValue("General_Category", "Unassigned")) ?: return null
                        IntervalSet.COMPLETE_CHAR_SET.subtract(unassigned)
                    }
                    else -> queryRegex(ClassQuery.ByValue("General_Category", valRaw))
                }

            "gcb", "graphemeclusterbreak" ->
                when {
                    valStripped in OBSOLETE_GCB_WB_VALUES -> IntervalSet()
                    valStripped == "other" || valStripped == "xx" -> computeGcbOther()
                    else -> queryRegex(ClassQuery.ByValue("Grapheme_Cluster_Break", valRaw))
                }

            "wb", "wordbreak" ->
                if (valStripped in OBSOLETE_GCB_WB_VALUES) {
                    IntervalSet()
                } else {
                    queryRegex(ClassQuery.ByValue("Word_Break", valRaw))
                }

            else -> queryRegex(ClassQuery.ByValue(propRaw, valRaw))
        }
    }

    private fun computeBare(raw: String): IntervalSet? {
        val norm = normalize(raw)
        val stripped = stripSeparators(raw)

        if (norm.startsWith("in") && norm.length > 2) {
            val blockKey = norm.substring(2)
            val blockStripped = stripped.substring(2)
            val blockIdx =
                UnicodeBlocksData.BLOCK_ALIASES[blockKey]
                    ?: UnicodeBlocksData.BLOCK_ALIASES[blockStripped]
            if (blockIdx != null) {
                return fromFlatSortedRanges(UnicodeBlocksData.BLOCK_RANGES[blockIdx])
            }
        }

        return when (stripped) {
            "ep", "extendedpictographic" -> computeExtendedPictographic()
            "emojirk" -> computeEmojiRK()
            "emojinrk" -> {
                val emoji = queryBinary("Emoji") ?: return null
                val rk = computeEmojiRK() ?: return null
                emoji.subtract(rk)
            }
            "cs", "surrogate" -> IntervalSet.of(0xD800, 0xDFFF)
            "c", "other", "control" -> {
                val base = queryRegex(ClassQuery.Binary("Other")) ?: return null
                base.add(0xD800, 0xDFFF)
                base
            }
            "cntrl" -> queryRegex(ClassQuery.ByValue("General_Category", "Control"))
            "space" -> queryRegex(ClassQuery.Binary("Separator"))
            "any" -> IntervalSet(IntervalSet.COMPLETE_CHAR_SET)
            "assigned" -> {
                val unassigned = queryRegex(ClassQuery.ByValue("General_Category", "Unassigned")) ?: return null
                IntervalSet.COMPLETE_CHAR_SET.subtract(unassigned)
            }
            "alnum" -> {
                val alpha = queryBinary("Alphabetic") ?: return null
                val digit = queryRegex(ClassQuery.ByValue("General_Category", "Decimal_Number")) ?: return null
                alpha.or(digit)
            }
            "blank" -> {
                val zs = queryRegex(ClassQuery.ByValue("General_Category", "Space_Separator")) ?: return null
                zs.add('\t'.code)
                zs
            }
            "graph" -> computePosixGraph()
            "print" -> {
                val graph = computePosixGraph() ?: return null
                val zs = queryRegex(ClassQuery.ByValue("General_Category", "Space_Separator")) ?: return null
                zs.add('\t'.code)
                val cc = queryRegex(ClassQuery.ByValue("General_Category", "Control")) ?: return null
                graph.or(zs).subtract(cc)
            }
            "xdigit" -> {
                val digit = queryRegex(ClassQuery.ByValue("General_Category", "Decimal_Number")) ?: return null
                val hex = queryBinary("Hex_Digit") ?: return null
                digit.or(hex)
            }
            else -> queryRegex(ClassQuery.Binary(raw))
        }
    }

    private fun computeExtendedPictographic(): IntervalSet {
        val base = queryBinary("Extended_Pictographic") ?: IntervalSet()
        for (pair in TR35_EXTENDED_PICTOGRAPHIC_EXTRA_RANGES) {
            base.add(pair[0], pair[1])
        }
        return base
    }

    private fun computeEmojiRK(): IntervalSet? {
        val ri =
            queryRegex(ClassQuery.ByValue("Grapheme_Cluster_Break", "Regional_Indicator"))
                ?: return null
        ri.add('*'.code)
        ri.add('#'.code)
        ri.add('0'.code, '9'.code)
        ri.add(0x00A9)
        ri.add(0x00AE)
        ri.add(0x2122)
        ri.add(0x3030)
        ri.add(0x303D)
        return ri
    }

    private fun computePosixGraph(): IntervalSet? {
        val ws = queryBinary("White_Space") ?: return null
        val cc = queryRegex(ClassQuery.ByValue("General_Category", "Control")) ?: return null
        val cn = queryRegex(ClassQuery.ByValue("General_Category", "Unassigned")) ?: return null
        val excluded = ws.or(cc).or(cn)
        excluded.add(0xD800, 0xDFFF)
        return IntervalSet.COMPLETE_CHAR_SET.subtract(excluded)
    }

    private fun computeGcbOther(): IntervalSet? {
        val union = IntervalSet()
        for (name in NON_OTHER_GCB_VALUES) {
            val part = queryRegex(ClassQuery.ByValue("Grapheme_Cluster_Break", name)) ?: return null
            union.addAll(part)
        }
        return IntervalSet.COMPLETE_CHAR_SET.subtract(union)
    }

    private fun queryBinary(name: String): IntervalSet? =
        queryRegex(ClassQuery.Binary(name))

    private fun queryRegex(query: ClassQuery): IntervalSet? {
        val classUnicode = unicodeClass(query).getOrNull() ?: return null
        val ranges = classUnicode.ranges()
        val intervals = ArrayList<Interval>(ranges.size)
        for (r in ranges) {
            intervals.add(Interval(r.start(), r.end()))
        }
        return IntervalSet(intervals)
    }

    private fun fromFlatSortedRanges(flat: IntArray): IntervalSet {
        val intervals = ArrayList<Interval>(flat.size / 2)
        var i = 0
        while (i < flat.size) {
            intervals.add(Interval(flat[i], flat[i + 1]))
            i += 2
        }
        return IntervalSet(intervals)
    }
}
