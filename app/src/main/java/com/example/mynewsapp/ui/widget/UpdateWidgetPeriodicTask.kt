package com.example.mynewsapp.ui.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.model.WidgetStockData
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.util.Constant.Companion.WORKER_INPUT_DATA_KEY
import com.example.mynewsapp.ui.widget.WidgetUtil.Companion.updateWidget
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

// 繼承CoroutineWorker因為要在coroutine裡執行
// 一般情形下就繼承Worker class
class UpdateWidgetPeriodicTask(appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams) {

    val jsonAdapter: JsonAdapter<List<String>>
        get() {
            val moshi: Moshi = Moshi.Builder().build()
            val type: Type = Types.newParameterizedType(
                List::class.java,
                String::class.java
            )
            return moshi.adapter<List<String>>(type)
        }


    override suspend fun doWork(): Result {
        // Indicate whether the work finished successfully with the Result
        return try {
            val listOfWidgetStockData = fetchData()

            if (listOfWidgetStockData != null) {
                updateWidget(listOfWidgetStockData, context = applicationContext)
            }
            Result.success()
        } catch (error: Throwable) {
            Result.failure()
        }
    }

    private suspend fun fetchData(): List<WidgetStockData>? {
        val stockNoStrings = inputData.getString(WORKER_INPUT_DATA_KEY)
        val stockNoList = jsonAdapter.fromJson(stockNoStrings ?: "") // e.g. [2330, 0050]
        val stockNoQuery = stockNoList?.joinToString("|") {
            "tse_${it}.tw"
        } // e.g. tse_2330.tw|tse_0050.tw

        val newsRepository = NewsRepository((applicationContext as MyApplication).database.stockDao())

        val response = stockNoQuery?.let { newsRepository.getStockPriceInfo(it).body() }

        val listOfMsgArray = response?.msgArray

        val listOfWidgetStockData = listOfMsgArray?.map { msgArray ->
            WidgetStockData(stockNo = msgArray.stockNo, stockPrice = msgArray.currentPrice, stockName = msgArray.stockName, yesterDayPrice = msgArray.lastDayPrice)
        }

        return listOfWidgetStockData
    }


}