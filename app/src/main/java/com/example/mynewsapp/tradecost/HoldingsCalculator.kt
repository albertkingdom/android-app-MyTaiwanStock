package com.example.mynewsapp.tradecost

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** One buy or sell record reduced to the numbers the overview needs. */
data class TradeLine(
    val side: TradeSide,
    val shares: Int,
    /** Null when the stored price is not a finite number; such a line counts only for the holdings. */
    val price: BigDecimal?,
    /** Null for a record saved before fees were stored. */
    val fee: Int?,
)

/**
 * Holdings and average prices of one stock. Everything is computed in decimals so that a price such as
 * 100.145 rounds the way it reads. The average buy price includes the buy fees; an unknown fee counts as 0.
 */
class HoldingsCalculator(private val lines: List<TradeLine>) {
    /** Shares bought minus shares sold. Negative when more was sold than bought. */
    val amount: Long = lines.fold(0L) { total, line ->
        if (line.side == TradeSide.BUY) total + line.shares else total - line.shares
    }

    /** True when a buy was saved before fees were stored, so the average buy price may be too low. */
    val hasBuyWithUnknownFee: Boolean = lines.any { it.side == TradeSide.BUY && it.fee == null }

    /** Total value of the buys plus their fees, divided by the shares bought; null when there is no buy. */
    fun averageBuyPrice(): BigDecimal? = average(lines.filter { it.side == TradeSide.BUY }, includingFees = true)

    /** Total value of the sells divided by the shares sold, without fees; null when there is no sell. */
    fun averageSellPrice(): BigDecimal? = average(lines.filter { it.side == TradeSide.SELL }, includingFees = false)

    /** The holdings at the current price; null without a price or when nothing is held. */
    fun marketValue(currentPrice: BigDecimal?): BigDecimal? {
        if (currentPrice == null || amount <= 0) return null
        return currentPrice.multiply(BigDecimal(amount))
    }

    private fun average(lines: List<TradeLine>, includingFees: Boolean): BigDecimal? {
        val trades = lines.filter { it.price != null }
        val shares = trades.fold(0L) { total, line -> total + line.shares }
        if (shares <= 0) return null
        val value = trades.fold(BigDecimal.ZERO) { total, line -> total + line.price!!.multiply(BigDecimal(line.shares)) }
        val fees = if (includingFees) trades.fold(0L) { total, line -> total + (line.fee ?: 0) } else 0L
        return (value + BigDecimal(fees)).divide(BigDecimal(shares), MathContext.DECIMAL128)
    }

    companion object {
        private val HUNDRED = BigDecimal(100)

        /**
         * The profit of one buy as a percentage of its cost including the fee; null for a sell, without a
         * current price, or when the record has no cost.
         */
        fun profitPercent(line: TradeLine, currentPrice: BigDecimal?): BigDecimal? {
            val price = line.price
            if (line.side != TradeSide.BUY || price == null || currentPrice == null || line.shares <= 0) return null
            val shares = BigDecimal(line.shares)
            val cost = (price.multiply(shares) + BigDecimal(line.fee ?: 0)).divide(shares, MathContext.DECIMAL128)
            if (cost.signum() <= 0) return null
            return (currentPrice - cost).divide(cost, MathContext.DECIMAL128).multiply(HUNDRED)
        }

        /** A price or percentage for display: two decimal places rounded half up, or a dash when there is none. */
        fun format(value: BigDecimal?): String = value?.let { rounded(it).toPlainString() } ?: "-"

        /** Two decimal places, rounded half up: what [format] shows. */
        fun rounded(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

        /** A market value for display: whole dollars rounded half up, or a dash when there is none. */
        fun formatMarketValue(value: BigDecimal?): String =
            value?.setScale(0, RoundingMode.HALF_UP)?.toPlainString() ?: "-"

        /**
         * A stored price as a decimal, through its shortest text: 112.55 as a Double is
         * 112.5500000000000113…, and the line has to carry 112.55. Null for NaN or an infinity, which an
         * earlier version could store.
         */
        fun priceOf(value: Double): BigDecimal? = if (value.isFinite()) BigDecimal.valueOf(value) else null
    }
}
