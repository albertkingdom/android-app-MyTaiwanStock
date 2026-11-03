package com.example.mynewsapp.ui.tradeimport.core

/** Reads the stock directory entries written as "code name", for example "0050 元大台灣50". */
object StockDirectoryParser {
    /** Splits each item at its first whitespace; the name itself can contain spaces. Malformed items are skipped. */
    fun parse(items: List<String>): List<StockEntry> = items.mapNotNull { item ->
        val trimmed = item.trim()
        val split = trimmed.indexOfFirst { it.isWhitespace() }
        if (split <= 0) return@mapNotNull null
        val name = trimmed.substring(split + 1).trim()
        if (name.isEmpty()) null else StockEntry(trimmed.substring(0, split), name)
    }
}
