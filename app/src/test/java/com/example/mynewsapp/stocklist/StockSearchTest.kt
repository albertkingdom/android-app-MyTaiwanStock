package com.example.mynewsapp.stocklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockSearchTest {

    private val strings = listOf("2330 台積電", "6488 環球晶", "1234 黑松", "0050 元大台灣50")

    @Test
    fun search_by_name_substring() {
        assertEquals(listOf("6488 環球晶"), StockSearch.filter(strings, "環球"))
    }

    @Test
    fun search_by_partial_code() {
        val result = StockSearch.filter(strings, "23")
        assertTrue(result.contains("2330 台積電"))
        assertTrue(result.contains("1234 黑松"))
        assertEquals(2, result.size)
    }

    @Test
    fun search_ignores_letter_case() {
        val withEtf = strings + "00981A 主動統一台股增長"
        assertEquals(listOf("00981A 主動統一台股增長"), StockSearch.filter(withEtf, "00981a"))
    }

    @Test
    fun empty_query_returns_everything() {
        assertEquals(strings, StockSearch.filter(strings, ""))
    }

    @Test
    fun code_from_input() {
        assertEquals("6488", StockSearch.codeFromInput(" 6488 環球晶"))
        assertEquals("00981A", StockSearch.codeFromInput("00981a"))
        assertEquals("2330", StockSearch.codeFromInput("2330"))
        assertEquals("", StockSearch.codeFromInput(""))
        assertEquals("", StockSearch.codeFromInput("   "))
    }
}
