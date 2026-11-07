package com.example.mynewsapp.ui.statistics

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.model.MsgArray
import com.example.mynewsapp.model.StockPriceInfoResponse
import com.example.mynewsapp.ui.MainActivity
import com.example.mynewsapp.util.Resource
import com.github.mikephil.charting.charts.PieChart
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With no holdings left there is nothing to show: the pie must go empty, not keep the slices of
 * the holdings that were just removed.
 *
 * Like the other statistics tests it assumes no other holdings in the database, which holds for the
 * fresh install that connectedDebugAndroidTest makes; it only removes the record it added.
 */
@RunWith(AndroidJUnit4::class)
class StatisticEmptyPieChartTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val repository = ApplicationProvider.getApplicationContext<MyApplication>().repository
    // Unique per run so a record left by a failed run is never counted here.
    private val stockNo = "T" + java.util.UUID.randomUUID().toString().take(8)
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking { repository.deleteAllHistory(stockNo) }
    }

    @Test
    fun removingTheLastHolding_emptiesThePieChart() {
        val scenario = ActivityScenario.launch(MainActivity::class.java).also { this.scenario = it }
        scenario.onActivity { navHost(it).navController.navigate(R.id.statisticFragment) }
        instrumentation.waitForIdleSync()
        // The price goes in after the screen is up, then the record: the pie is calculated from both.
        scenario.onActivity { it.statisticViewModel.setStockPriceInfo(priceOf(stockNo, "10")) }
        runBlocking {
            repository.insertHistory(InvestHistory(0, stockNo, date = 0L, price = 8.0, amount = 1000, status = 0))
        }
        assertTrue("The chart never showed $stockNo", awaitChart(scenario) { !it.isEmpty })

        runBlocking { repository.deleteAllHistory(stockNo) }

        assertTrue("The chart still shows slices after the last holding was removed", awaitChart(scenario) { it.isEmpty })
        // Back to what a chart that was never given data shows (its "no data" text), not a blank pie.
        scenario.onActivity { assertNull("The chart keeps an empty PieData", pieChart(it).data) }
    }

    private fun awaitChart(scenario: ActivityScenario<MainActivity>, done: (PieChart) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            var reached = false
            scenario.onActivity { reached = done(pieChart(it)) }
            if (reached) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun pieChart(activity: MainActivity): PieChart =
        statisticFragment(activity).requireView().findViewById(R.id.pie_chart)

    private fun navHost(activity: MainActivity): NavHostFragment =
        activity.supportFragmentManager.findFragmentById(R.id.StockNavHostFragment) as NavHostFragment

    private fun statisticFragment(activity: MainActivity): Fragment =
        navHost(activity).childFragmentManager.fragments.first { it is StatisticFragment }

    private fun priceOf(stockNo: String, price: String): Resource<StockPriceInfoResponse> =
        Resource.Success(
            StockPriceInfoResponse(
                listOf(MsgArray(stockNo = stockNo, ch = "$stockNo.tw", lastDayPrice = price, currentPrice = price, stockName = "Test", time = "13:30:00"))
            )
        )
}
