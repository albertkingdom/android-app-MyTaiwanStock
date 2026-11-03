package com.example.mynewsapp.use_case.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandleRowsTest {

    // STOCK_DAY row: date, shares, value, open, high, low, close, change, transactions
    private fun row(date: String, close: String = "100.00", shares: String = "1,000") =
        listOf(date, shares, "100,000", close, close, close, close, "+1.00", "10")

    @Test
    fun missing_last_month_keeps_current_month() {
        val current = listOf(row("115/11/01"), row("115/11/02"))
        assertEquals(current, CandleRows.merge(null, current))
    }

    @Test
    fun both_months_missing_is_empty() {
        assertTrue(CandleRows.merge(null, null).isEmpty())
    }

    @Test
    fun months_are_concatenated_in_order() {
        val last = listOf(row("115/10/31"))
        val current = listOf(row("115/11/01"))
        assertEquals(listOf("115/10/31", "115/11/01"), CandleRows.merge(last, current).map { it[0] })
    }

    @Test
    fun suspended_day_and_short_rows_are_dropped() {
        val rows = listOf(
            row("1"), row("2"), row("3", close = "--"), row("4"), row("5"), row("6"),
        )
        val merged = CandleRows.merge(rows, null)
        assertEquals(5, merged.size)
        assertEquals(listOf("1", "2", "4", "5", "6"), merged.map { it[0] })
        assertTrue(CandleRows.merge(listOf(listOf("1", "2", "3")), null).isEmpty())
        assertTrue(CandleRows.merge(listOf(row("1", shares = "--")), null).isEmpty())
    }

    @Test
    fun thousands_separators_are_numbers() {
        assertEquals(1, CandleRows.merge(listOf(row("1", close = "1,085.00", shares = "12,345,678")), null).size)
        assertEquals(1085f, CandleRows.number("1,085.00")!!, 1e-4f)
    }

    @Test
    fun price_change_parsing() {
        assertEquals(0f, CandleRows.priceChange("X0.00"), 1e-4f)
        assertEquals(1.5f, CandleRows.priceChange("+1.50"), 1e-4f)
        assertEquals(-2.25f, CandleRows.priceChange("-2.25"), 1e-4f)
        assertEquals(0f, CandleRows.priceChange("--"), 1e-4f)
        assertEquals(0f, CandleRows.priceChange(""), 1e-4f)
    }
}
