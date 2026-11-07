package com.example.mynewsapp.model

/** One stock's share of the portfolio, as plain data (no chart objects), so a ViewModel can hold it. */
data class PieSlice(
    val stockNo: String,
    /** Share of the total, 0..100. */
    val percent: Float
)
