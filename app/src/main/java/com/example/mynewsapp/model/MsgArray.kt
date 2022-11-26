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
    val time: String
)