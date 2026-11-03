package com.example.mynewsapp.tradecost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class FeeCalculatorTest {
    private data class Case(
        val side: TradeSide,
        val shares: Int,
        val price: String,
        val discount: String,
        val category: TaxCategory,
        val fee: Int,
        val tax: Int,
        val wholeLotMinimum: Int = FeeSettings.DEFAULT_WHOLE_LOT_MINIMUM_FEE,
        val oddLotMinimum: Int = FeeSettings.DEFAULT_ODD_LOT_MINIMUM_FEE,
    )

    /** The example table of the specification. */
    private val cases = listOf(
        Case(TradeSide.BUY, 1000, "100", "10", TaxCategory.STOCK, fee = 142, tax = 0),
        Case(TradeSide.SELL, 1000, "105", "6", TaxCategory.STOCK, fee = 89, tax = 315),
        Case(TradeSide.BUY, 5, "112.55", "6", TaxCategory.STOCK, fee = 1, tax = 0),
        Case(TradeSide.SELL, 10, "757", "6", TaxCategory.STOCK, fee = 6, tax = 22),
        Case(TradeSide.BUY, 1, "2470", "6", TaxCategory.STOCK, fee = 2, tax = 0),
        Case(TradeSide.BUY, 1000, "20", "6", TaxCategory.STOCK, fee = 20, tax = 0),
        Case(TradeSide.SELL, 2000, "22", "6", TaxCategory.ETF, fee = 37, tax = 44),
        Case(TradeSide.SELL, 1000, "100", "10", TaxCategory.EXEMPT, fee = 142, tax = 0),
        Case(TradeSide.BUY, 999, "1", "10", TaxCategory.STOCK, fee = 1, tax = 0),
        Case(TradeSide.BUY, 1500, "1", "10", TaxCategory.STOCK, fee = 20, tax = 0),
        Case(TradeSide.BUY, 5, "112.55", "6", TaxCategory.STOCK, fee = 5, tax = 0, oddLotMinimum = 5),
        Case(TradeSide.BUY, 1000, "20", "6", TaxCategory.STOCK, fee = 30, tax = 0, wholeLotMinimum = 30),
        Case(TradeSide.BUY, 1000, "20", "6", TaxCategory.STOCK, fee = 17, tax = 0, wholeLotMinimum = 0),
    )

    @Test
    fun matchesTheSpecificationTable() {
        for (case in cases) {
            val settings = FeeSettings(BigDecimal(case.discount), case.wholeLotMinimum, case.oddLotMinimum)

            val result = FeeCalculator.calculate(BigDecimal(case.price), case.shares, case.side, settings, case.category)

            assertEquals(case.toString(), FeeAndTax(case.fee, case.tax), result)
        }
    }

    @Test
    fun aDecimalDiscountDoesNotLoseADollar() {
        // 1,000,000 x 0.001425 x 0.28 is exactly 399; a binary 2.8 would give 398.
        val discount = BigDecimal.valueOf(2.8)

        val result = FeeCalculator.calculate(
            BigDecimal(1000), 1000, TradeSide.SELL, FeeSettings(discount), TaxCategory.STOCK
        )

        assertEquals(399, result?.fee)
    }

    @Test
    fun sameDayTradeTaxIsHalfTheStockTax() {
        val result = FeeCalculator.calculate(
            BigDecimal(100), 1000, TradeSide.SELL, FeeSettings(BigDecimal.TEN), TaxCategory.DAY_TRADE
        )

        assertEquals(150, result?.tax)
    }

    @Test
    fun aBuyNeverPaysTax() {
        val result = FeeCalculator.calculate(
            BigDecimal(100), 1000, TradeSide.BUY, FeeSettings(BigDecimal.TEN), TaxCategory.STOCK
        )

        assertEquals(0, result?.tax)
    }

    @Test
    fun noResultWithoutAPositivePriceAndAShare() {
        val inputs = listOf(BigDecimal.ZERO to 10, BigDecimal(-1) to 10, BigDecimal(100) to 0, BigDecimal(100) to -5)
        for ((price, shares) in inputs) {
            val result = FeeCalculator.calculate(
                price, shares, TradeSide.BUY, FeeSettings(BigDecimal.TEN), TaxCategory.STOCK
            )

            assertNull("$price x $shares", result)
        }
    }

    @Test
    fun noResultWhenTheFeeOrTheTaxWouldNotFitInTheRecord() {
        val price = BigDecimal("99999999999999999999")
        val discount = BigDecimal("0.1")

        val buy = FeeCalculator.calculate(price, 30000, TradeSide.BUY, FeeSettings(BigDecimal.TEN), TaxCategory.STOCK)
        // The fee of this trade is 17100000 and fits in a record; only the tax of the sell does not.
        val fee = FeeCalculator.calculate(
            BigDecimal(40_000_000), 30000, TradeSide.BUY, FeeSettings(discount), TaxCategory.STOCK
        )
        val sell = FeeCalculator.calculate(
            BigDecimal(40_000_000), 30000, TradeSide.SELL, FeeSettings(discount), TaxCategory.STOCK
        )

        assertNull(buy)
        assertEquals(FeeAndTax(17_100_000, 0), fee)
        assertNull(sell)
    }

    @Test
    fun theStatusOfARecordGivesItsSide() {
        assertEquals(TradeSide.BUY, TradeSide.fromStatus(0))
        assertEquals(TradeSide.SELL, TradeSide.fromStatus(1))
        // The records list shows anything other than 0 as a sell; the overview has to agree.
        assertEquals(TradeSide.SELL, TradeSide.fromStatus(2))
    }
}
