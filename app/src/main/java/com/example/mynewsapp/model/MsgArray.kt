package com.example.mynewsapp.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MsgArray(

    @Json(name = "c")
    val stockNo: String, // 0050
    val ch: String, //0050.tw
    @Json(name = "y")
    val lastDayPrice: String,
    @Json(name = "z")
    val currentPrice: String,
    @Json(name = "n")
    val stockName: String,
    @Json(name ="t")
    val time: String,
) {
    // The quote API sends "-" for a price it does not have (for example no last trade yet), so the
    // prices are read with toFloatOrNull. Nothing here is a constructor value: parsing must not fail.

    /** The previous close, or null when the API has none. */
    val lastDayPriceValue: Float?
        get() = lastDayPrice.toFloatOrNull()

    /** The price to show: the last trade, else the previous close, else null. */
    val displayPrice: Float?
        get() = currentPrice.toFloatOrNull() ?: lastDayPriceValue

    /** Change of [displayPrice] from the previous close; 0 when either is missing. */
    val diff: Float
        get() {
            val lastDay = lastDayPriceValue ?: return 0f
            return (currentPrice.toFloatOrNull() ?: return 0f) - lastDay
        }

    /** [diff] in percent of the previous close; 0 when the previous close is missing or 0. */
    val diffPercent: Float
        get() {
            val lastDay = lastDayPriceValue?.takeIf { it != 0f } ?: return 0f
            return 100 * diff / lastDay
        }
}