package com.example.mynewsapp.tradecost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TradeInputTest {
    @Test
    fun aPlainNumberIsAPrice() {
        for ((text, value) in listOf("100" to "100", "112.55" to "112.55", " 100 " to "100", "105." to "105", ".5" to "0.5", "0" to "0")) {
            val price = TradeInput.parsePrice(text)
            assertTrue("$text gave $price", price != null && price.compareTo(BigDecimal(value)) == 0)
        }
    }

    @Test
    fun anythingElseIsNotAPrice() {
        for (text in listOf(null, "", "  ", ".", "1e3", "1e50", "Infinity", "NaN", "-5", "1.2.3", "abc", "１００", "1,000")) {
            assertNull("$text", TradeInput.parsePrice(text))
        }
    }

    @Test
    fun sharesAreAWholeNumberFromOne() {
        assertEquals(1, TradeInput.parseShares("1"))
        assertEquals(1000, TradeInput.parseShares(" 1000 "))
        assertEquals(32768, TradeInput.parseShares("32768"))
        assertEquals(Int.MAX_VALUE, TradeInput.parseShares("2147483647"))
    }

    @Test
    fun anythingElseIsNotAShareCount() {
        for (text in listOf(null, "", "0", "-5", "1.5", "x", "５", "2147483648", "99999999999", "+5")) {
            assertNull("$text", TradeInput.parseShares(text))
        }
    }

    @Test
    fun aFeeOrTaxIsAWholeNumberFromZero() {
        for (text in listOf("0", "5", "2147483647", " 22 ", "007")) {
            assertEquals(text.trim().toInt(), TradeInput.parseFeeOrTax(text))
        }
    }

    @Test
    fun anythingElseIsNotAFeeOrTax() {
        for (text in listOf(null, "-5", "5.5", "abc", "1e3", "2147483648", "99999999999", "", "  ", "５")) {
            assertNull("$text", TradeInput.parseFeeOrTax(text))
        }
    }
}
