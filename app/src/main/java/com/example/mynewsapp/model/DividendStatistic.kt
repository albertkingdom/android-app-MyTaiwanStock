package com.example.mynewsapp.model

data class DividendStatistic(
    val stockNo: String,
    val cashAmount: Int,
    val stockAmount: Float,
    val date: Long? = null
)
