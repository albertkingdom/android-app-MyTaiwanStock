package com.example.mynewsapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mynewsapp.tradecost.HoldingsCalculator
import com.example.mynewsapp.tradecost.TradeLine
import com.example.mynewsapp.tradecost.TradeSide

@Entity(tableName = "investHistory")
data class InvestHistory (
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val stockNo: String,
    val date: Long,
    val price: Double,
    val amount: Int,
    val status: Int, //0: buy, 1: sell
    /** Whole dollars; null for a record saved before fees were stored. */
    val fee: Int? = null,
    /** Whole dollars; null for a record saved before taxes were stored. A buy stores 0. */
    val tax: Int? = null,
    /** Null for a record saved before memos were stored. */
    val memo: String? = null,
)

/**
 * The record reduced to the numbers of the overview. A price that is not a finite number, which an earlier
 * version could store, has no decimal; the record then counts only for the holdings.
 */
fun InvestHistory.tradeLine(): TradeLine =
    TradeLine(TradeSide.fromStatus(status), amount, HoldingsCalculator.priceOf(price), fee)
