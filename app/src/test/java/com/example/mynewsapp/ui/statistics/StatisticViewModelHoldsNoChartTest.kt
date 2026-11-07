package com.example.mynewsapp.ui.statistics

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StatisticViewModel is activity-scoped and outlives the activity across a configuration change,
 * so anything it holds that references a chart leaks the destroyed activity. It keeps plain data
 * only; the fragment builds the chart's PieData (see PieChartRenderingLeakTest). A shallow guard on
 * purpose: it reads field types, not lambdas, jobs or what the repository holds.
 */
class StatisticViewModelHoldsNoChartTest {

    @Test
    fun `no field of the view model is a chart library type or holds one as a type argument`() {
        val chartTypes = StatisticViewModel::class.java.declaredFields
            .map { it.name to it.toGenericString() }
            .filter { (_, signature) -> signature.contains("com.github.mikephil") }

        assertTrue("StatisticViewModel holds chart library types: $chartTypes", chartTypes.isEmpty())
    }
}
