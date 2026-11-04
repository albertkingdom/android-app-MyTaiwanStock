package com.example.mynewsapp.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsQueryTest {

    @Test
    fun taiwan_stock_query_searches_the_topic_in_chinese() {
        val query = NewsQuery.TaiwanStock
        assertEquals("台股", query.q)
        assertNull(query.qInTitle)
        assertEquals("zh", query.language)
        assertEquals("publishedAt", query.sortBy)
    }

    @Test
    fun stock_query_searches_the_title_only() {
        val query = NewsQuery.forStock("台積電")
        assertEquals(NewsQuery.Stock("台積電"), query)
        assertNull(query.q)
        assertEquals("台積電", query.qInTitle)
        assertNull(query.language)
        assertEquals("publishedAt", query.sortBy)
    }

    @Test
    fun stock_name_is_trimmed() {
        assertEquals("環球晶", NewsQuery.forStock("  環球晶 ").qInTitle)
    }

    @Test
    fun missing_or_blank_name_falls_back_to_the_taiwan_stock_query() {
        assertEquals(NewsQuery.TaiwanStock, NewsQuery.forStock(null))
        assertEquals(NewsQuery.TaiwanStock, NewsQuery.forStock(""))
        assertEquals(NewsQuery.TaiwanStock, NewsQuery.forStock("  "))
    }
}
