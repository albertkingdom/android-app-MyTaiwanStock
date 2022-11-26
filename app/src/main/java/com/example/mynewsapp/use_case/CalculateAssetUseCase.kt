package com.example.mynewsapp.use_case

import android.graphics.Color
import androidx.lifecycle.MutableLiveData
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.model.StockPriceInfoResponse
import com.example.mynewsapp.model.StockStatistic
import com.example.mynewsapp.util.Resource
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import timber.log.Timber
import kotlin.random.Random

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

        priceInfo.value?.data?.msgArray?.map {

            mapOfStockNoToCurrentPrice[it.stockNo] = (if (it.currentPrice != "-") it.currentPrice else it.lastDayPrice).toFloat()
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

    fun calculatePieData(dataSource: List<StockStatistic>): PieData {

        // entries -> pieDataset -> pieData -> pieChart
        val colors = mutableListOf<Int>()
        var entries: List<PieEntry>

        Timber.d("dataSource...${dataSource}")

        if (dataSource.isEmpty()) {
            return PieData()
        }

        dataSource.map { entry->
            val randomColor = Color.argb(255, Random.nextInt(0, 255), Random.nextInt(0, 255), Random.nextInt(0, 255))
            colors.add(randomColor)
        }

        val sumOfAsset: Float =
            dataSource.map { stockStatistic -> stockStatistic.totalAssets }.reduce{ acc, item -> acc + item }

        entries = dataSource.map{ entry -> PieEntry(entry.totalAssets / sumOfAsset * 100, entry.stockNo) }

        val pieDataSet: PieDataSet = PieDataSet(entries, "stockNo.")
        pieDataSet.colors = colors

        val pieData: PieData = PieData(pieDataSet)

        return pieData
    }
}