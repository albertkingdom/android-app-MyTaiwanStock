package com.example.mynewsapp.ui.list

import com.example.mynewsapp.model.MsgArray
import java.util.Locale

enum class Trend { UP, DOWN, FLAT }

/** The text a watchlist row shows, and the direction that picks the change label's color. */
data class StockRowText(val subtitle: String, val price: String, val change: String, val trend: Trend)

/** How a watchlist row shows a quote. Numbers use "." and no grouping, whatever the device language. */
object StockRowFormatter {

    fun format(stock: MsgArray, showAsPercent: Boolean, previousCloseLabel: String): StockRowText {
        val hasLastTrade = stock.currentPrice.toFloatOrNull() != null
        val subtitle = "${stock.stockNo} · ${if (hasLastTrade) stock.time.take(5) else previousCloseLabel}"
        val displayPrice = stock.displayPrice
            ?: return StockRowText(subtitle, price = "-", change = "-", trend = Trend.FLAT)

        val diff = stock.diff
        val trend = when {
            diff > 0f -> Trend.UP
            diff < 0f -> Trend.DOWN
            else -> Trend.FLAT
        }
        val suffix = if (showAsPercent) "%" else ""
        val change = when (trend) {
            // Exactly unchanged: never "-0.00".
            Trend.FLAT -> "0.00$suffix"
            else -> String.format(Locale.US, "%+.2f", if (showAsPercent) stock.diffPercent else diff) + suffix
        }
        return StockRowText(subtitle, String.format(Locale.US, "%.2f", displayPrice), change, trend)
    }
}
