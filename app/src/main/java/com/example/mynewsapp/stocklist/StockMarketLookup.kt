package com.example.mynewsapp.stocklist

/**
 * Maps a stock code to its market so quote requests use the right prefix:
 * `tse_` for listed stocks and `otc_` for over-the-counter stocks. A code that is not in the
 * lookup falls back to `tse` (the behavior before OTC support).
 */
class StockMarketLookup(private val marketByCode: Map<String, StockMarket>) {

    fun market(code: String): StockMarket = marketByCode[code] ?: StockMarket.TSE

    /** The `ex_ch` channel for one code, e.g. "otc_6488.tw". */
    fun exChannel(code: String): String = "${market(code).prefix}_$code.tw"

    /** The `ex_ch` query value for several codes, e.g. "otc_6488.tw|tse_2330.tw". */
    fun exChannelQuery(codes: List<String>): String = codes.joinToString("|") { exChannel(it) }
}
