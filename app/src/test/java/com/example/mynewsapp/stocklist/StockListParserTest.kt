package com.example.mynewsapp.stocklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StockListParserTest {

    private fun otcJson(vararg rows: Pair<String, String>): String =
        rows.joinToString(",", "[", "]") { (code, name) ->
            """{"SecuritiesCompanyCode":"$code","CompanyName":"$name","Close":"1"}"""
        }

    private fun twseJson(vararg rows: Pair<String, String>): String =
        rows.joinToString(",", "[", "]") { (code, name) ->
            """{"Code":"$code","Name":"$name","TradeVolume":"1","Change":{"nested":[1,2]}}"""
        }

    @Test
    fun otc_warrants_are_excluded_and_stocks_and_etfs_are_kept() {
        val entries = StockListParser.parseOtc(
            otcJson(
                "710000" to "權證A", "700019" to "權證B", "73000U" to "權證C", "72400U" to "權證D",
                "6488" to "環球晶", "00679B" to "元大美債20年", "006201" to "元大富櫃50", "00411A" to "主動ETF"
            )
        )
        val codes = entries.map { it.code }.toSet()
        assertEquals(setOf("6488", "00679B", "006201", "00411A"), codes)
        assertTrue(entries.all { it.market == StockMarket.OTC })
    }

    @Test
    fun filtering_rule_examples() {
        assertTrue(StockListParser.isKeptOtcCode("6488"))
        assertTrue(StockListParser.isKeptOtcCode("00679B"))
        assertFalse(StockListParser.isKeptOtcCode("710000"))
        assertFalse(StockListParser.isKeptOtcCode("73000U"))
        // Every Taiwan Stock Exchange entry is kept, including 6-character ETF codes.
        val tse = StockListParser.parseTwse(twseJson("00981A" to "主動統一台股增長"))
        assertEquals(listOf(StockListEntry("00981A", "主動統一台股增長", StockMarket.TSE)), tse)
    }

    @Test
    fun code_in_both_sources_keeps_the_tse_entry() {
        val tse = StockListParser.parseTwse(twseJson("1234" to "上市版", "2330" to "台積電"))
        val otc = StockListParser.parseOtc(otcJson("1234" to "上櫃版", "6488" to "環球晶"))
        val merged = StockListParser.merge(tse, otc)
        assertEquals(1, merged.count { it.code == "1234" })
        assertEquals(StockMarket.TSE, merged.first { it.code == "1234" }.market)
        assertEquals(listOf("1234", "2330", "6488"), merged.map { it.code })
    }

    @Test
    fun merged_list_is_sorted_by_code() {
        val tse = StockListParser.parseTwse(twseJson("2330" to "台積電", "0050" to "元大台灣50"))
        val otc = StockListParser.parseOtc(otcJson("6488" to "環球晶", "1259" to "安心"))
        assertEquals(listOf("0050", "1259", "2330", "6488"), StockListParser.merge(tse, otc).map { it.code })
    }

    @Test
    fun whitespace_is_trimmed_and_empty_fields_are_dropped() {
        val tse = StockListParser.parseTwse(twseJson(" 2330 " to " 台積電 ", "  " to "空代號", "1101" to "  "))
        assertEquals(listOf(StockListEntry("2330", "台積電", StockMarket.TSE)), tse)
        val otc = StockListParser.parseOtc(otcJson("6488  " to "環球晶 "))
        assertEquals(listOf(StockListEntry("6488", "環球晶", StockMarket.OTC)), otc)
    }

    @Test(expected = IOException::class)
    fun not_an_array_fails() {
        StockListParser.parseTwse("""{"stat":"error"}""")
    }

    @Test(expected = IOException::class)
    fun garbage_fails() {
        StockListParser.parseOtc("not json")
    }

    @Test(expected = IOException::class)
    fun row_without_code_field_fails() {
        StockListParser.parseTwse("""[{"Name":"台積電"}]""")
    }

    @Test(expected = IOException::class)
    fun row_without_name_field_fails() {
        StockListParser.parseOtc("""[{"SecuritiesCompanyCode":"6488"}]""")
    }

    @Test
    fun file_format_round_trips() {
        val entries = listOf(
            StockListEntry("2330", "台積電", StockMarket.TSE),
            StockListEntry("6488", "環球晶", StockMarket.OTC),
        )
        val json = StockListParser.encodeFile(entries)
        assertTrue(json.contains("\"market\":\"otc\""))
        assertEquals(entries, StockListParser.decodeFile(json))
    }

    @Test(expected = IOException::class)
    fun unknown_market_value_fails_to_decode() {
        StockListParser.decodeFile("""{"entries":[{"code":"2330","name":"台積電","market":"emerging"}]}""")
    }

    @Test
    fun search_string_is_code_space_name() {
        assertEquals("6488 環球晶", StockListEntry("6488", "環球晶", StockMarket.OTC).searchString)
    }
}
