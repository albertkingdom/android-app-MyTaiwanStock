package com.example.mynewsapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cashDividend")
data class CashDividend(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val stockNo: String,
    val amount: Int,
    val date: Long
)
