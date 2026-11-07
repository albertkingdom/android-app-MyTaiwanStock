package com.example.mynewsapp.ui.statistics

import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter

/** Shows [pieData] on this chart with a percentage label on each slice. */
fun PieChart.showPieData(pieData: PieData) {
    data = pieData
    data.setDrawValues(true)
    data.setValueFormatter(SlicePercentFormatter()) // display % on chart
    data.setValueTextSize(12f)
    setUsePercentValues(true) // display % on chart
    invalidate() //refresh
    legend.isEnabled = false
    val descriptions = Description() // @ bottom right of chart
    descriptions.text = "" // remove description
    description = descriptions
}

/**
 * Labels each slice as a percentage without holding the chart. PercentFormatter(chart) would put
 * the chart into the PieData, which the activity-scoped StatisticViewModel keeps across a
 * configuration change, so the chart and the destroyed activity instance would leak. Assumes
 * setUsePercentValues(true), as set in showPieData.
 */
private class SlicePercentFormatter : PercentFormatter() {
    override fun getPieLabel(value: Float, pieEntry: PieEntry?): String = getFormattedValue(value)
}
