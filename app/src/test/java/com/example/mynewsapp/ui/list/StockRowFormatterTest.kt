package com.example.mynewsapp.ui.list

import com.example.mynewsapp.model.MsgArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StockRowFormatterTest {

    private fun stock(stockNo: String, current: String, lastDay: String, time: String) =
        MsgArray(stockNo = stockNo, ch = "$stockNo.tw", lastDayPrice = lastDay, currentPrice = current, stockName = "", time = time)

    private fun format(stock: MsgArray, percent: Boolean) =
        StockRowFormatter.format(stock, showAsPercent = percent, previousCloseLabel = "昨收")

    // One test per row of the spec example "Row text and label color".
    @Test
    fun `rising stock in amount mode`() {
        assertEquals(StockRowText("2330 · 13:30", "1085.00", "+15.00", Trend.UP), format(stock("2330", "1085", "1070", "13:30:00"), false))
    }

    @Test
    fun `rising stock in percent mode`() {
        assertEquals(StockRowText("2330 · 13:30", "1085.00", "+1.40%", Trend.UP), format(stock("2330", "1085", "1070", "13:30:00"), true))
    }

    @Test
    fun `falling stock in amount mode`() {
        assertEquals(StockRowText("0050 · 13:30", "195.30", "-1.20", Trend.DOWN), format(stock("0050", "195.30", "196.50", "13:30:00"), false))
    }

    @Test
    fun `no last trade in amount mode`() {
        assertEquals(StockRowText("2344 · 昨收", "127.50", "0.00", Trend.FLAT), format(stock("2344", "-", "127.50", "13:19:51"), false))
    }

    @Test
    fun `no last trade in percent mode`() {
        assertEquals(StockRowText("2344 · 昨收", "127.50", "0.00%", Trend.FLAT), format(stock("2344", "-", "127.50", "13:19:51"), true))
    }

    @Test
    fun `no price at all`() {
        assertEquals(StockRowText("2344 · 昨收", "-", "-", Trend.FLAT), format(stock("2344", "-", "-", "13:19:51"), false))
    }

    @Test
    fun `falling stock in percent mode`() {
        assertEquals(StockRowText("0050 · 13:30", "195.30", "-0.61%", Trend.DOWN), format(stock("0050", "195.30", "196.50", "13:30:00"), true))
    }

    @Test
    fun `a rise that rounds to zero percent keeps the plus sign`() {
        assertEquals(StockRowText("2330 · 13:30", "1070.01", "+0.00%", Trend.UP), format(stock("2330", "1070.01", "1070", "13:30:00"), true))
    }

    @Test
    fun `unchanged price`() {
        assertEquals(StockRowText("2330 · 13:30", "1070.00", "0.00", Trend.FLAT), format(stock("2330", "1070", "1070", "13:30:00"), false))
    }

    @Test
    fun `large price has no thousands separator`() {
        assertEquals(StockRowText("9999 · 13:30", "12345.50", "+345.50", Trend.UP), format(stock("9999", "12345.5", "12000", "13:30:00"), false))
    }

    @Test
    fun `decimal point does not follow the device language`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(StockRowText("2330 · 13:30", "1085.00", "+1.40%", Trend.UP), format(stock("2330", "1085", "1070", "13:30:00"), true))
        } finally {
            Locale.setDefault(original)
        }
    }
}
