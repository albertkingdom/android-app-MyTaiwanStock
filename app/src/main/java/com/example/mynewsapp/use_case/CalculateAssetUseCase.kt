package com.example.mynewsapp.use_case

import androidx.lifecycle.MutableLiveData
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.model.PieSlice
import com.example.mynewsapp.model.StockPriceInfoResponse
import com.example.mynewsapp.model.StockStatistic
import com.example.mynewsapp.util.Resource
import timber.log.Timber

class CalculateAssetUseCase {

    fun calculatePieChartSourceData(
        list: List<InvestHistory>,
        priceInfo: MutableLiveData<Resource<StockPriceInfoResponse>>
    ): List<StockStatistic> {
        val mapOfStockNoToAmount = mutableMapOf<String, Int>() // {0056 to 200}
        val mapOfStockNoToCurrentPrice = mutableMapOf<String, Float>() //{0056 to 32}
        val mapOfStockNoToTotalMoney = mutableMapOf<String, Float>() // {0056 to 200*32}

        list.map { history ->
            if (mapOfStockNoToAmount[history.stockNo] == null) {

                mapOfStockNoToAmount[history.stockNo] =
                    history.amount * (if (history.status == 0) 1 else -1)
            } else {
                mapOfStockNoToAmount[history.stockNo] =
                    mapOfStockNoToAmount[history.stockNo]!! + history.amount * (if (history.status == 0) 1 else -1)
            }
        }

        priceInfo.value?.data?.msgArray?.forEach { stock ->
            // A stock without any price is left out, like one missing from the response.
            stock.displayPrice?.let { mapOfStockNoToCurrentPrice[stock.stockNo] = it }
        }

        mapOfStockNoToAmount.map { entry ->
            if (mapOfStockNoToCurrentPrice[entry.key] != null) {
                mapOfStockNoToTotalMoney[entry.key] =
                    entry.value * mapOfStockNoToCurrentPrice[entry.key]!!
            }

        }


        val listOfStockStatistic: List<StockStatistic> = mapOfStockNoToTotalMoney.map { entry ->
            val amount = mapOfStockNoToAmount[entry.key] ?: 0
            StockStatistic(entry.key, amount, entry.value)
        }
        return listOfStockStatistic
    }

    /** Each stock's share of the total assets, in percent. Empty when there is nothing to show. */
    fun calculatePieData(dataSource: List<StockStatistic>): List<PieSlice> {
        Timber.d("dataSource...${dataSource}")

        val sumOfAsset: Float = dataSource.fold(0f) { sum, entry -> sum + entry.totalAssets }
        // Nothing to divide by: the percentages would be NaN or infinite.
        if (sumOfAsset == 0f) return emptyList()

        return dataSource.map { entry -> PieSlice(entry.stockNo, entry.totalAssets / sumOfAsset * 100) }
    }
}
