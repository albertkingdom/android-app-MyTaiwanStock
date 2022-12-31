package com.example.mynewsapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stockDividend")
data class StockDividend (
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val stockNo: String,
    val amount: Float,
    val date: Long
)