package com.example.mynewsapp.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "stocks")
data class Stock (
    @PrimaryKey
    val stockNo: String,
    @ColumnInfo(defaultValue = "0")
    val parentFollowingListId: Int,
    @ColumnInfo(defaultValue = "0")
    val price: String
)