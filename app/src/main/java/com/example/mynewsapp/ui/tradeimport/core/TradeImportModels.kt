package com.example.mynewsapp.ui.tradeimport.core

import java.time.LocalDate
import java.time.ZoneId

/**
 * One piece of recognized text with its position.
 *
 * Coordinates are normalized (0 to 1) with the origin at the lower left corner, so a larger
 * [y] is higher on the screen. This is the convention of the iOS Vision framework; keeping it
 * lets the iOS test data be used unchanged.
 */
data class TextBox(
    val text: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    val maxX: Double get() = x + width
    val midX: Double get() = x + width / 2
    val midY: Double get() = y + height / 2
}

/** Buy or sell. [status] is the value `InvestHistory.status` stores. */
enum class TradeSide(val status: Int) {
    BUY(0),
    SELL(1),
}

/**
 * A trade read from a screenshot. Missing values mean the screenshot did not show them,
 * which makes the trade incomplete in the preview.
 */
data class ParsedTrade(
    val stockName: String,
    /** Calendar day of the trade in the Asia/Taipei time zone. */
    val date: LocalDate?,
    val price: Double?,
    /** Shares, the unit `InvestHistory.amount` uses. */
    val amount: Int?,
    val side: TradeSide,
    val tradeTypeText: String,
    val isSupportedType: Boolean,
)

val TAIPEI: ZoneId = ZoneId.of("Asia/Taipei")
