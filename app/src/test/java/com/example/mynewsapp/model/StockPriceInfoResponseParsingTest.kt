package com.example.mynewsapp.model

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockPriceInfoResponseParsingTest {

    private val adapter = Moshi.Builder().build().adapter(StockPriceInfoResponse::class.java)

    private fun response(z: String, y: String = "127.5000") =
        """{"msgArray":[{"c":"2344","ch":"2344.tw","y":"$y","z":"$z","n":"華邦電","t":"13:19:51"}]}"""

    @Test
    fun `a quote without a last trade price is parsed`() {
        // The quote API sends "-" in z when a stock has no last trade price in the snapshot.
        val stock = adapter.fromJson(response(z = "-"))!!.msgArray.single()

        assertEquals("-", stock.currentPrice)
        // Shown like the price column: the previous close stands in, so the change is 0.
        assertEquals(0f, stock.diffPercent, 0f)
    }

    @Test
    fun `the change percent is computed from the last trade price`() {
        val stock = adapter.fromJson(response(z = "140.2500"))!!.msgArray.single()

        assertEquals(10f, stock.diffPercent, 0.001f)
    }

    @Test
    fun `a quote without a last trade price shows the previous close`() {
        val stock = adapter.fromJson(response(z = "-"))!!.msgArray.single()

        assertEquals(127.5f, stock.displayPrice!!, 0f)
        assertEquals(0f, stock.diff, 0f)
    }

    @Test
    fun `the change is computed from the last trade price`() {
        val stock = adapter.fromJson(response(z = "140.2500"))!!.msgArray.single()

        assertEquals(140.25f, stock.displayPrice!!, 0f)
        assertEquals(12.75f, stock.diff, 0.001f)
    }

    @Test
    fun `a quote without a previous close has no change`() {
        val stock = adapter.fromJson(response(z = "140.2500", y = "-"))!!.msgArray.single()

        assertEquals(140.25f, stock.displayPrice!!, 0f)
        assertEquals(0f, stock.diff, 0f)
        assertEquals(0f, stock.diffPercent, 0f)
    }

    @Test
    fun `a quote without any price has no price to show`() {
        val stock = adapter.fromJson(response(z = "-", y = "-"))!!.msgArray.single()

        assertNull(stock.displayPrice)
        assertEquals(0f, stock.diff, 0f)
        assertEquals(0f, stock.diffPercent, 0f)
    }
}
