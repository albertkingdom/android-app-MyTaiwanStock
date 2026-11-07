package com.example.mynewsapp.ui.statistics

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The slice colors are random, picked when slices are shown. Leaving the app and coming back
 * collects the same slices again; the chart must keep showing them as they were, not repaint
 * them in new colors. New slices must still replace what the chart shows.
 */
@RunWith(AndroidJUnit4::class)
class StatisticPieColorsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val repository = ApplicationProvider.getApplicationContext<MyApplication>().repository
    // Unique per run so a record left by a failed run is never counted here.
    private val stockNo = "T" + java.util.UUID.randomUUID().toString().take(8)
    private val otherStockNo = "T" + java.util.UUID.randomUUID().toString().take(8)
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking {
            repository.deleteAllHistory(stockNo)
            repository.deleteAllHistory(otherStockNo)
        }
    }

    @Test
    fun leavingTheAppAndComingBack_keepsTheSliceColors() {
        val scenario = openStatisticScreenShowing(stockNo)
        val before = awaitSliceColors(scenario, stockNo)

        scenario.moveToState(Lifecycle.State.CREATED) // stopped, as when the app goes to the background
        scenario.moveToState(Lifecycle.State.RESUMED)
        instrumentation.waitForIdleSync()

        assertEquals("slice colors after leaving and coming back", before, sliceColors(scenario))
    }

    @Test
    fun aNewStockInTheSlices_isShownOnTheChart() {
        val scenario = openStatisticScreenShowing(stockNo)
        awaitSliceColors(scenario, stockNo)

        // A different set of slices, not a repeat: the chart must take it.
        scenario.onActivity { it.statisticViewModel.setStockPriceInfo(priceOf(stockNo to "10", otherStockNo to "20")) }
        runBlocking {
            repository.insertHistory(InvestHistory(0, otherStockNo, date = 0L, price = 18.0, amount = 500, status = 0))
        }
        val after = awaitSliceColors(scenario, otherStockNo)

        assertEquals("both stocks on the chart", setOf(stockNo, otherStockNo), after.keys.filter { it in setOf(stockNo, otherStockNo) }.toSet())
    }

    /** Opens the statistic screen and records [stockNo] with a price, so the pie is calculated with it. */
    private fun openStatisticScreenShowing(stockNo: String): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java).also { this.scenario = it }
        scenario.onActivity { activity ->
            navHost(activity).navController.navigate(R.id.statisticFragment)
        }
        instrumentation.waitForIdleSync()
        // The price goes in after the screen is up, then the record: the pie is calculated from both.
        scenario.onActivity { it.statisticViewModel.setStockPriceInfo(priceOf(stockNo to "10")) }
        runBlocking {
            repository.insertHistory(InvestHistory(0, stockNo, date = 0L, price = 8.0, amount = 1000, status = 0))
        }
        return scenario
    }

    /** Waits until the chart shows [stock], then returns the color of each slice by stock. */
    private fun awaitSliceColors(scenario: ActivityScenario<MainActivity>, stock: String): Map<String, Int> {
        val deadline = System.currentTimeMillis() + 15_000
        var colors = sliceColors(scenario)
        while (stock !in colors && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            colors = sliceColors(scenario)
        }
        assertNotNull("The chart never showed $stock, only ${colors.keys}", colors[stock])
        return colors
    }

    private fun sliceColors(scenario: ActivityScenario<MainActivity>): Map<String, Int> {
        var colors: Map<String, Int> = emptyMap()
        scenario.onActivity { activity ->
            val dataSet = statisticFragment(activity).requireView().findViewById<PieChart>(R.id.pie_chart)
                .data?.getDataSetByIndex(0)
            if (dataSet != null) {
                colors = (0 until dataSet.entryCount).associate { i -> dataSet.getEntryForIndex(i).label to dataSet.getColor(i) }
            }
        }
        return colors
    }

    private fun navHost(activity: MainActivity): NavHostFragment =
        activity.supportFragmentManager.findFragmentById(R.id.StockNavHostFragment) as NavHostFragment

    private fun statisticFragment(activity: MainActivity): Fragment =
        navHost(activity).childFragmentManager.fragments.first { it is StatisticFragment }

    private fun priceOf(vararg prices: Pair<String, String>): Resource<StockPriceInfoResponse> =
        Resource.Success(
            StockPriceInfoResponse(
                prices.map { (stockNo, price) ->
                    MsgArray(stockNo = stockNo, ch = "$stockNo.tw", lastDayPrice = price, currentPrice = price, stockName = "Test", time = "13:30:00")
                }
            )
        )
}
