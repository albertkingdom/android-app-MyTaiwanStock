package com.example.mynewsapp.ui.statistics

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.model.MsgArray
import com.example.mynewsapp.model.StockPriceInfoResponse
import com.example.mynewsapp.model.StockStatistic
import com.example.mynewsapp.util.Resource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The statistic screen calls collectData() each time its view is created. Collecting again must
 * recalculate the totals with the latest price, as it did before the collectors were deduplicated.
 */
@RunWith(AndroidJUnit4::class)
class StatisticViewModelPriceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val repository = ApplicationProvider.getApplicationContext<MyApplication>().repository
    // Unique per run so a record left by a failed run is never counted here.
    private val stockNo = "T" + java.util.UUID.randomUUID().toString().take(8)
    private val store = ViewModelStore()
    private lateinit var viewModel: StatisticViewModel

    @Before
    fun setUp() {
        instrumentation.runOnMainSync {
            viewModel = ViewModelProvider(store, StatisticViewModelFactory(repository))[StatisticViewModel::class.java]
            viewModel.collectData()
        }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { store.clear() }
        runBlocking { repository.deleteAllHistory(stockNo) }
    }

    @Test
    fun collectingAgainAfterANewPrice_updatesTheStockTotal() {
        instrumentation.runOnMainSync { viewModel.setStockPriceInfo(priceOf(stockNo, "10")) }
        runBlocking {
            repository.insertHistory(InvestHistory(0, stockNo, date = 0L, price = 8.0, amount = 1000, status = 0))
        }
        // 1000 shares × 10: the record has been read with the first price.
        assertEquals(StockStatistic(stockNo, 1000, 10000f), awaitStatistic { it != null })

        instrumentation.runOnMainSync {
            viewModel.setStockPriceInfo(priceOf(stockNo, "12.5"))
            viewModel.collectData() // the view is created again, e.g. back from the dividend detail
        }

        // 1000 shares × 12.5
        assertEquals(StockStatistic(stockNo, 1000, 12500f), awaitStatistic { it?.totalAssets == 12500f })
    }

    private fun awaitStatistic(done: (StockStatistic?) -> Boolean): StockStatistic? {
        val deadline = System.currentTimeMillis() + 15_000
        var current: StockStatistic? = null
        while (System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                current = viewModel.combineData.value.second.firstOrNull { it.stockNo == stockNo }
            }
            if (done(current)) return current
            Thread.sleep(100)
        }
        return current
    }

    private fun priceOf(stockNo: String, price: String): Resource<StockPriceInfoResponse> =
        Resource.Success(
            StockPriceInfoResponse(
                listOf(MsgArray(stockNo = stockNo, ch = "$stockNo.tw", lastDayPrice = price, currentPrice = price, stockName = "Test", time = "13:30:00"))
            )
        )
}
