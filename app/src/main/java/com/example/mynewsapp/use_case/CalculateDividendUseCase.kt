package com.example.mynewsapp.use_case

import com.example.mynewsapp.db.CashDividend
import com.example.mynewsapp.db.StockDividend
import com.example.mynewsapp.model.DividendStatistic

class CalculateDividendUseCase {
    fun calculateDividend(cashDividends: List<CashDividend>, stockDividends: List<StockDividend>): List<DividendStatistic> {
        val stockNos = mutableListOf<String>()
        val dividends = mutableListOf<DividendStatistic>()
        val cashDividendMap = mutableMapOf<String, Int>()
        val stockDividendMap = mutableMapOf<String, Float>()


        for (cash in cashDividends) {
            if (cashDividendMap.containsKey(cash.stockNo)) {
                cashDividendMap[cash.stockNo] = cashDividendMap[cash.stockNo]!!.plus(cash.amount)
            } else {
                cashDividendMap[cash.stockNo] = cash.amount
            }
            if (!stockNos.contains(cash.stockNo)) {
                stockNos.add(cash.stockNo)
            }
        }

        for (stock in stockDividends) {
            if (stockDividendMap.containsKey(stock.stockNo)) {
                stockDividendMap[stock.stockNo] = stockDividendMap[stock.stockNo]!!.plus(stock.amount)
            } else {
                stockDividendMap[stock.stockNo] = stock.amount
            }
            if (!stockNos.contains(stock.stockNo)) {
                stockNos.add(stock.stockNo)
            }
        }

        for (stockNo in stockNos) {
            val cash = cashDividendMap[stockNo] ?: 0
            val stock = stockDividendMap[stockNo] ?: 0

            val dividend = DividendStatistic(stockNo=stockNo, cashAmount = cash, stockAmount = stock.toFloat())
            dividends.add(dividend)
        }

        return dividends
    }
}