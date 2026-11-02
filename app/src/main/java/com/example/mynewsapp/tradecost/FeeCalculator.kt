package com.example.mynewsapp.tradecost

import java.math.BigDecimal
import java.math.RoundingMode

/** The side of a trade, stored in `InvestHistory.status` as 0 for a buy and 1 for a sell. */
enum class TradeSide(val status: Int) {
    BUY(0),
    SELL(1);

    companion object {
        /** Only 0 is a buy; the records list shows any other status as a sell. */
        fun fromStatus(status: Int): TradeSide = if (status == BUY.status) BUY else SELL
    }
}

/** The securities transaction tax category of a sell. */
enum class TaxCategory(val rate: BigDecimal, val title: String) {
    STOCK(BigDecimal("0.003"), "一般股票"),
    DAY_TRADE(BigDecimal("0.0015"), "現股當沖"),
    ETF(BigDecimal("0.001"), "ETF"),
    EXEMPT(BigDecimal.ZERO, "免稅"),
}

/** The fee and the tax of one trade, in whole New Taiwan dollars. */
data class FeeAndTax(val fee: Int, val tax: Int)

/** The brokerage fee and the transaction tax of one trade, in decimals and rounded down to whole dollars. */
object FeeCalculator {
    val STANDARD_FEE_RATE = BigDecimal("0.001425")

    /**
     * A trade of this many shares or more is charged the whole-lot minimum, even when it is not a multiple
     * of it: the broker would split such a trade into a whole lot and an odd lot, which is not modeled.
     */
    const val WHOLE_LOT_SHARES = 1000

    /** A record stores its fee and its tax as an Int. */
    private val LARGEST_AMOUNT = BigDecimal(Int.MAX_VALUE)

    /**
     * Null when the price is not greater than 0, there is less than one share, or the fee or the tax would
     * not fit in a record.
     */
    fun calculate(
        price: BigDecimal,
        shares: Int,
        side: TradeSide,
        settings: FeeSettings,
        taxCategory: TaxCategory,
    ): FeeAndTax? {
        if (price.signum() <= 0 || shares < 1) return null
        val value = price.multiply(BigDecimal(shares))
        val fee = fee(value, shares, settings) ?: return null
        val tax = if (side == TradeSide.SELL) roundedDown(value.multiply(taxCategory.rate)) ?: return null else 0
        return FeeAndTax(fee, tax)
    }

    private fun fee(value: BigDecimal, shares: Int, settings: FeeSettings): Int? {
        val calculated = roundedDown(
            value.multiply(STANDARD_FEE_RATE).multiply(settings.discount).divide(BigDecimal.TEN)
        ) ?: return null
        val minimum = if (shares >= WHOLE_LOT_SHARES) settings.wholeLotMinimumFee else settings.oddLotMinimumFee
        return maxOf(calculated, minimum)
    }

    /** Null when the result does not fit in a record. */
    private fun roundedDown(value: BigDecimal): Int? {
        val result = value.setScale(0, RoundingMode.DOWN)
        return if (result > LARGEST_AMOUNT) null else result.toInt()
    }
}
