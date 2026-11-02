package com.example.mynewsapp.ui.tradeimport.core

import java.text.Normalizer

/** One stock of the stock directory. */
data class StockEntry(val code: String, val name: String)

/**
 * Turns the stock name read from a screenshot into a stock code.
 *
 * The broker screen shows names only. Both sides are normalized the same way (NFKC, so
 * full-width becomes half-width, then all whitespace removed) and compared exactly. There is no
 * fuzzy matching: a name that is missing or shared by more than one stock stays unresolved so
 * the user picks the code instead of the app guessing. Ported from the iOS app.
 */
class StockNameResolver(entries: List<StockEntry>) {

    /** Normalized name to the codes that carry it. */
    private val codesByName: Map<String, List<String>> =
        entries.groupBy({ normalize(it.name) }, { it.code })

    private val namesByCode: Map<String, String> = entries.associate { it.code to it.name }

    /** The directory name of [code], or null when the code is unknown. */
    fun nameOf(code: String): String? = namesByCode[code]

    /** The stock code for [name], or null when the name is empty, unknown or ambiguous. */
    fun resolve(name: String): String? {
        val normalized = normalize(name)
        if (normalized.isEmpty()) return null
        codesByName[normalized]?.let { return it.singleOrNull() }

        // OCR may also read 一 as an ASCII hyphen. That hyphen is a real character in some names,
        // so it is only reinterpreted when nothing matches as written.
        val asCharacterOne = normalized.replace('-', '一')
        if (asCharacterOne == normalized) return null
        return codesByName[asCharacterOne]?.singleOrNull()
    }

    companion object {
        /**
         * OCR often reads the character 一 (as in 統一) as a long dash. The ASCII hyphen is left
         * alone: it is a real character in names like 富邦美債1-3.
         */
        private val dashLookalikes = setOf('–', '—', '―', '─', 'ー')

        fun normalize(name: String): String =
            Normalizer.normalize(name, Normalizer.Form.NFKC)
                .filterNot { it.isWhitespace() }
                .map { if (it in dashLookalikes) '一' else it }
                .joinToString("")
    }
}
