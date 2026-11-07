package com.example.mynewsapp.ui.statistics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PieChartRenderingLeakTest {

    /**
     * StatisticViewModel is activity-scoped and outlives the chart across a configuration change.
     * The PieData it holds must not keep the chart (and through it the destroyed activity) alive.
     */
    @Test
    fun pieDataShownOnAChart_doesNotKeepTheChartAlive() {
        val pieData = PieData(PieDataSet(listOf(PieEntry(25f, "2330"), PieEntry(75f, "0056")), "stockNo."))
        val chartRef = showOnThrowawayChart(pieData)

        var tries = 0
        while (chartRef.get() != null && tries++ < 10) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Thread.sleep(100)
        }

        assertNull("PieData still references the PieChart it was shown on", chartRef.get())
        // Keep pieData reachable until after the check, as the ViewModel does.
        assertEquals(1, pieData.dataSetCount)
    }

    @Test
    fun sliceLabelsShowPercentSign() {
        // The formatter's decimal separator follows the default locale, read when it is created.
        val original = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val pieData = PieData(PieDataSet(listOf(PieEntry(25f, "2330")), "stockNo."))
            showOnThrowawayChart(pieData)

            val label = pieData.getDataSetByIndex(0).valueFormatter.getPieLabel(25f, PieEntry(25f, "2330"))

            assertEquals("25.0 %", label)
        } finally {
            Locale.setDefault(original)
        }
    }

    /** Created in its own frame so no local in the test keeps the chart reachable. */
    private fun showOnThrowawayChart(pieData: PieData): WeakReference<PieChart> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var ref: WeakReference<PieChart>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val chart = PieChart(context)
            chart.showPieData(pieData)
            ref = WeakReference(chart)
        }
        return ref!!
    }
}
