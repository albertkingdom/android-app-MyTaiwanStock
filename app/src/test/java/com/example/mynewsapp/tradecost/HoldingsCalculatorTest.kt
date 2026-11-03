package com.example.mynewsapp.tradecost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class HoldingsCalculatorTest {
    private fun line(side: TradeSide, shares: Int, price: String, fee: Int? = null) =
        TradeLine(side, shares, BigDecimal(price), fee)

    private fun buy(shares: Int, price: String, fee: Int? = null) = line(TradeSide.BUY, shares, price, fee)
    private fun sell(shares: Int, price: String, fee: Int? = null) = line(TradeSide.SELL, shares, price, fee)

    private fun assertDecimal(expected: String, actual: BigDecimal?) =
        assertTrue("expected $expected, was $actual", actual != null && BigDecimal(expected).compareTo(actual) == 0)

    // holdings

    @Test
    fun holdingsAreBuysMinusSells() {
        assertEquals(600L, HoldingsCalculator(listOf(buy(1000, "100"), sell(400, "105"))).amount)
    }

    @Test
    fun buyingAndSellingTheSameSharesLeavesNothing() {
        assertEquals(0L, HoldingsCalculator(listOf(buy(1000, "100"), sell(1000, "105"))).amount)
    }

    @Test
    fun onlySellsGiveNegativeHoldings() {
        assertEquals(-400L, HoldingsCalculator(listOf(sell(400, "105"))).amount)
    }

    @Test
    fun holdingsDoNotOverflowAnInt() {
        val sut = HoldingsCalculator(listOf(buy(2_000_000_000, "1"), buy(2_000_000_000, "1")))

        assertEquals(4_000_000_000L, sut.amount)
    }

    // averages

    @Test
    fun averageBuyPriceIgnoresSells() {
        val sut = HoldingsCalculator(listOf(buy(1000, "100"), buy(1000, "110"), sell(500, "120")))

        assertDecimal("105", sut.averageBuyPrice())
        assertEquals(1500L, sut.amount)
    }

    @Test
    fun averageSellPriceIsTheValueOfTheSellsOverTheirShares() {
        val sut = HoldingsCalculator(listOf(buy(1000, "100"), sell(200, "120"), sell(300, "130")))

        assertDecimal("126", sut.averageSellPrice())
    }

    @Test
    fun withoutBuysThereIsNoAverageBuyPrice() {
        val sut = HoldingsCalculator(listOf(sell(400, "105")))

        assertNull(sut.averageBuyPrice())
        assertDecimal("105", sut.averageSellPrice())
    }

    @Test
    fun withoutSellsThereIsNoAverageSellPrice() {
        assertNull(HoldingsCalculator(listOf(buy(1000, "100"))).averageSellPrice())
    }

    @Test
    fun noRecordsGiveNoAveragesAndNoHoldings() {
        val sut = HoldingsCalculator(emptyList())

        assertEquals(0L, sut.amount)
        assertNull(sut.averageBuyPrice())
        assertNull(sut.averageSellPrice())
        assertNull(sut.marketValue(BigDecimal(105)))
        assertFalse(sut.hasBuyWithUnknownFee)
    }

    @Test
    fun pricesAreShownWithTwoDecimalsRoundedHalfUp() {
        assertEquals("100.15", HoldingsCalculator.format(BigDecimal("100.145")))
        assertEquals("105.15", HoldingsCalculator.format(BigDecimal("105.149")))
        assertEquals("105.00", HoldingsCalculator.format(BigDecimal(105)))
        assertEquals("-", HoldingsCalculator.format(null))
    }

    @Test
    fun aPriceOfADoubleKeepsItsShortestText() {
        // 112.55 as a Double is 112.5500000000000113...; the line has to carry 112.55.
        assertEquals(0, HoldingsCalculator.priceOf(112.55)!!.compareTo(BigDecimal("112.55")))
        assertEquals("112.55", HoldingsCalculator.priceOf(112.55)!!.toPlainString())
    }

    @Test
    fun aPriceThatIsNotAFiniteNumberHasNoDecimal() {
        // An earlier version could store "Infinity" as a price; reading it has to give no price, not a crash.
        assertNull(HoldingsCalculator.priceOf(Double.POSITIVE_INFINITY))
        assertNull(HoldingsCalculator.priceOf(Double.NEGATIVE_INFINITY))
        assertNull(HoldingsCalculator.priceOf(Double.NaN))
    }

    @Test
    fun aRecordWithoutAUsablePriceStillCountsItsSharesAndItsMissingFee() {
        val noPrice = TradeLine(TradeSide.BUY, 1000, price = null, fee = null)
        val sut = HoldingsCalculator(listOf(buy(1000, "100", fee = 142), noPrice))

        assertEquals(2000L, sut.amount)
        assertTrue(sut.hasBuyWithUnknownFee)
        assertDecimal("100.142", sut.averageBuyPrice())
        assertNull(HoldingsCalculator.profitPercent(noPrice, BigDecimal(105)))
    }

    @Test
    fun onlyRecordsWithoutAUsablePriceGiveNoAverage() {
        val sut = HoldingsCalculator(listOf(TradeLine(TradeSide.SELL, 400, price = null, fee = 1)))

        assertEquals(-400L, sut.amount)
        assertNull(sut.averageSellPrice())
    }

    // fees in the average buy price

    @Test
    fun averageBuyPriceIncludesTheBuyFees() {
        val sut = HoldingsCalculator(listOf(buy(1000, "100", fee = 142), buy(1000, "110", fee = 156)))

        assertDecimal("105.149", sut.averageBuyPrice())
        assertEquals("105.15", HoldingsCalculator.format(sut.averageBuyPrice()))
    }

    @Test
    fun aFeeThatRoundsAverageBuyPriceHalfUp() {
        val sut = HoldingsCalculator(listOf(buy(1000, "100", fee = 145)))

        assertDecimal("100.145", sut.averageBuyPrice())
        assertEquals("100.15", HoldingsCalculator.format(sut.averageBuyPrice()))
    }

    @Test
    fun anUnknownFeeCountsAsZeroInTheAverageBuyPrice() {
        val sut = HoldingsCalculator(listOf(buy(1000, "100", fee = 142), buy(1000, "110", fee = null)))

        assertDecimal("105.071", sut.averageBuyPrice())
    }

    @Test
    fun sellFeesStayOutOfBothAverages() {
        val sut = HoldingsCalculator(listOf(buy(1000, "100", fee = 142), sell(1000, "105", fee = 150)))

        assertDecimal("100.142", sut.averageBuyPrice())
        assertDecimal("105", sut.averageSellPrice())
    }

    @Test
    fun withoutABuyLeftThereIsNoAverageBuyPrice() {
        val sut = HoldingsCalculator(listOf(sell(1000, "105", fee = 150)))

        assertNull(sut.averageBuyPrice())
        assertEquals("-", HoldingsCalculator.format(sut.averageBuyPrice()))
    }

    // the note about buys without a fee

    @Test
    fun aBuyWithoutAFeeRaisesTheNote() {
        assertTrue(HoldingsCalculator(listOf(buy(1000, "100", fee = 142), buy(1000, "110"))).hasBuyWithUnknownFee)
    }

    @Test
    fun aSellWithoutAFeeDoesNotRaiseTheNote() {
        assertFalse(HoldingsCalculator(listOf(buy(1000, "100", fee = 142), sell(1000, "105"))).hasBuyWithUnknownFee)
    }

    @Test
    fun buysWithAFeeOfZeroAreKnown() {
        assertFalse(HoldingsCalculator(listOf(buy(1000, "100", fee = 0))).hasBuyWithUnknownFee)
    }

    // market value

    @Test
    fun marketValueIsHoldingsTimesThePriceWhileHoldingSomething() {
        val sut = HoldingsCalculator(listOf(buy(1000, "100"), sell(400, "105")))

        assertDecimal("63000", sut.marketValue(BigDecimal(105)))
        assertEquals("63000", HoldingsCalculator.formatMarketValue(sut.marketValue(BigDecimal(105))))
    }

    @Test
    fun marketValueIsRoundedToWholeDollarsForDisplay() {
        val sut = HoldingsCalculator(listOf(buy(5, "100")))

        assertEquals("528", HoldingsCalculator.formatMarketValue(sut.marketValue(BigDecimal("105.55"))))
        assertEquals("-", HoldingsCalculator.formatMarketValue(null))
    }

    @Test
    fun marketValueIsMissingWhenHoldingsAreZeroOrNegative() {
        val flat = HoldingsCalculator(listOf(buy(1000, "100"), sell(1000, "105")))
        val short = HoldingsCalculator(listOf(sell(400, "105")))

        assertNull(flat.marketValue(BigDecimal(105)))
        assertNull(short.marketValue(BigDecimal(105)))
    }

    @Test
    fun marketValueIsMissingWithoutACurrentPrice() {
        assertNull(HoldingsCalculator(listOf(buy(1000, "100"))).marketValue(null))
    }

    // profit of one buy record

    @Test
    fun profitUsesThePriceIncludingTheFee() {
        val profit = HoldingsCalculator.profitPercent(buy(1000, "100", fee = 142), BigDecimal(105))

        assertEquals("4.85", HoldingsCalculator.format(profit))
    }

    @Test
    fun profitTreatsAnUnknownFeeAsZero() {
        val profit = HoldingsCalculator.profitPercent(buy(1000, "100"), BigDecimal(105))

        assertEquals("5.00", HoldingsCalculator.format(profit))
    }

    @Test
    fun aLossIsNegative() {
        val profit = HoldingsCalculator.profitPercent(buy(1000, "100", fee = 142), BigDecimal(95))!!

        assertTrue(profit.signum() < 0)
        assertEquals("-5.13", HoldingsCalculator.format(profit))
    }

    @Test
    fun aSellRecordHasNoProfit() {
        assertNull(HoldingsCalculator.profitPercent(sell(1000, "100", fee = 142), BigDecimal(105)))
    }

    @Test
    fun profitIsMissingWithoutACurrentPrice() {
        assertNull(HoldingsCalculator.profitPercent(buy(1000, "100", fee = 142), null))
    }
}
