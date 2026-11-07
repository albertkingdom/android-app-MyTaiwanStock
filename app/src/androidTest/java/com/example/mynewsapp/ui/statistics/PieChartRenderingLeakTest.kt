package com.example.mynewsapp.ui.statistics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mynewsapp.model.PieSlice
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PieChartRenderingLeakTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /**
     * StatisticViewModel is activity-scoped and outlives the chart across a configuration change,
     * and the PieData built for a chart holds PercentFormatter(chart). So showPieData must not
     * leave that PieData anywhere that outlives the chart (a static, a cache). That the ViewModel
     * itself holds no chart types is checked by StatisticViewModelHoldsNoChartTest.
     */
    @Test
    fun slicesHeldByTheViewModel_doNotKeepAShownChartAlive() {
        val slicesHeldByTheViewModel = listOf(PieSlice("2330", 25f), PieSlice("0056", 75f))
        val chartRef = showOnThrowawayChart(slicesHeldByTheViewModel)

        var tries = 0
        while (chartRef.get() != null && tries < 20) {
            tries++
            Runtime.getRuntime().gc()
            System.runFinalization()
            Thread.sleep(100)
        }

        assertNull("The slices still reference the PieChart they were shown on", chartRef.get())
        // Keep the slices reachable until after the check, as the ViewModel does.
        assertEquals(2, slicesHeldByTheViewModel.size)
    }

    @Test
    fun sliceLabelsShowPercentSign() {
        // The formatter's decimal separator follows the default locale, read when it is created.
        val original = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val chart = newChartShowing(listOf(PieSlice("2330", 25f), PieSlice("0056", 75f)))

            val formatter = chart.data.getDataSetByIndex(0).valueFormatter
            assertEquals("25.0 %", formatter.getPieLabel(25f, PieEntry(25f, "2330")))
            assertEquals("75.0 %", formatter.getPieLabel(75f, PieEntry(75f, "0056")))
        } finally {
            Locale.setDefault(original)
        }
    }

    /** No slices means nothing to show: back to a chart that was never given data, not a blank pie. */
    @Test
    fun showingNoSlices_emptiesAChartThatShowedSlices() {
        val chart = newChartShowing(listOf(PieSlice("2330", 25f), PieSlice("0056", 75f)))
        assertEquals("slices before", 2, chart.data.entryCount)

        onMain { chart.showPieData(emptyList()) }

        assertTrue("The chart still shows slices", chart.isEmpty)
        assertNull("The chart keeps an empty PieData", chart.data)
    }

    private fun newChartShowing(slices: List<PieSlice>): PieChart = onMain {
        PieChart(ApplicationProvider.getApplicationContext<Context>()).also { it.showPieData(slices) }
    }

    /** Created in its own frame so no local in the test keeps the chart reachable. */
    private fun showOnThrowawayChart(slices: List<PieSlice>): WeakReference<PieChart> =
        onMain { WeakReference(PieChart(ApplicationProvider.getApplicationContext<Context>()).also { it.showPieData(slices) }) }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
