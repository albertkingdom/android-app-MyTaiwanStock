package com.example.mynewsapp.stocklist

object StockSearch {
    /** Strings containing [query], ignoring letter case; an empty query returns every string. */
    fun filter(strings: List<String>, query: String): List<String> =
        if (query.isEmpty()) strings else strings.filter { it.contains(query, ignoreCase = true) }

    /** The code part of an add-stock input such as " 6488 環球晶" or "00981a". */
    fun codeFromInput(text: String): String = text.trim().substringBefore(" ").uppercase()
}
