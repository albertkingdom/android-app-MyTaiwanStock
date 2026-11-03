package com.example.mynewsapp.stocklist

/** The market a stock trades on; [prefix] is the quote API channel prefix. */
enum class StockMarket(val prefix: String) {
    /** Listed on the Taiwan Stock Exchange (including ETFs). */
    TSE("tse"),

    /** Traded over the counter on the Taipei Exchange. */
    OTC("otc");

    companion object {
        fun fromPrefix(value: String): StockMarket? = values().firstOrNull { it.prefix == value }
    }
}

data class StockListEntry(
    val code: String,
    val name: String,
    val market: StockMarket,
) {
    /** The "code name" form used by the add-stock search, e.g. "6488 環球晶". */
    val searchString: String get() = "$code $name"
}
